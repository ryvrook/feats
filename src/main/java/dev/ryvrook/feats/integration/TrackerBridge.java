package dev.ryvrook.feats.integration;

import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.systems.CombatKinds;
import dev.ryvrook.feats.systems.CombatMemory;
import dev.ryvrook.feats.systems.DeepworksRuns;
import dev.ryvrook.feats.systems.WorldKeys;
import dev.ryvrook.feats.track.ArmourSets;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.MovementEdges;
import dev.ryvrook.feats.track.WorldBounds;
import dev.ryvrook.feats.track.Windows;
import dev.ryvrook.trackerapi.api.Actor;
import dev.ryvrook.trackerapi.api.Subscription;
import dev.ryvrook.trackerapi.api.TrackerApi;
import dev.ryvrook.trackerapi.api.TrackerEvent;
import dev.ryvrook.trackerapi.api.event.BlockBroken;
import dev.ryvrook.trackerapi.api.event.BlockPlaced;
import dev.ryvrook.trackerapi.api.event.BlockUsed;
import dev.ryvrook.trackerapi.api.event.CreatureTamed;
import dev.ryvrook.trackerapi.api.event.DamageDealt;
import dev.ryvrook.trackerapi.api.event.EntityDied;
import dev.ryvrook.trackerapi.api.event.EntityUsed;
import dev.ryvrook.trackerapi.api.event.GameModeChanged;
import dev.ryvrook.trackerapi.api.event.GameTimeSampled;
import dev.ryvrook.trackerapi.api.event.InstanceDiscovered;
import dev.ryvrook.trackerapi.api.event.InteractionStarted;
import dev.ryvrook.trackerapi.api.event.ItemBroke;
import dev.ryvrook.trackerapi.api.event.ItemConsumed;
import dev.ryvrook.trackerapi.api.event.ItemCrafted;
import dev.ryvrook.trackerapi.api.event.ItemEquipped;
import dev.ryvrook.trackerapi.api.event.ItemPickedUp;
import dev.ryvrook.trackerapi.api.event.ItemsGained;
import dev.ryvrook.trackerapi.api.event.MemoryRecorded;
import dev.ryvrook.trackerapi.api.event.MountChanged;
import dev.ryvrook.trackerapi.api.event.MovementEdge;
import dev.ryvrook.trackerapi.api.event.PlayerChatted;
import dev.ryvrook.trackerapi.api.event.PlayerLeft;
import dev.ryvrook.trackerapi.api.event.PlayerMoved;
import dev.ryvrook.trackerapi.api.event.PlayerSlept;
import dev.ryvrook.trackerapi.api.event.PlayerWorldChanged;
import dev.ryvrook.trackerapi.api.event.ProjectileLanded;
import dev.ryvrook.trackerapi.api.event.ProjectileLaunched;
import dev.ryvrook.trackerapi.api.event.TriggerVolumeFired;
import dev.ryvrook.trackerapi.api.event.VitalsSampled;
import dev.ryvrook.trackerapi.api.event.WeatherChanged;
import dev.ryvrook.trackerapi.api.event.WorldAdded;
import dev.ryvrook.trackerapi.api.event.WorldEventEnded;
import dev.ryvrook.trackerapi.api.event.WorldRemoved;
import dev.ryvrook.trackerapi.api.event.ZoneDiscovered;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Feeds the Feats counters from TrackerAPI events instead of the Feats engine systems. This is the only
 * class of Feats that uses TrackerAPI types, and it is only loaded once setup found TrackerAPI installed
 * and new enough, see {@link TrackerSwitch}.
 *
 * <p>Each listener calls exactly what the matching Feats system called, with the same {@link Classify}
 * keys, reading the facts off the record instead of the engine. The anti farm rules (placed blocks, own
 * drops, stashed items, the consumed rule and the boss ledger) are applied by TrackerAPI and arrive as
 * fields on the records, so they are used as they are.
 *
 * <p>Every listener is ASYNC and runs on the TrackerAPI dispatch thread. The Feats tracker is safe from any
 * thread and already counted chat and playtime off the world thread. The per player {@link Body} and per
 * world {@link Sky} notes are only touched from that one dispatch thread.
 *
 * <p>Counters the bridge leaves to Feats on both paths, because the hooks run there whatever the path:
 * vitals (close calls, low oxygen, emptied mana), game days and life days, respawn points, hardcore lives,
 * recipes known and permission groups added. Counters no TrackerAPI record carries, so they stay unfed on
 * this path: rolls, mantles and double jumps (MovementEdge has no such edges and no extra jump count),
 * seats (MountChanged names the seat type, not its block), blocks (DamageDealt has no blocked flag),
 * memory_by_kill (MemoryRecorded has no unlock method), void_stage_max and portal_close_exits (they need the
 * portal world clock), merchants, repairs and referral joins.
 */
public final class TrackerBridge {
    /** The owner name Feats subscribes under. */
    static final String OWNER = "Feats";

    private final TrackerHost host;
    private final FeatsTracker tracker;
    private final DeepworksRuns runs;
    private final List<Subscription> subscriptions = new ArrayList<>();
    /** Names of the portal instance worlds seen so far, lower case, for the bottom and top of the world. */
    private final Set<String> instanceWorlds = ConcurrentHashMap.newKeySet();
    /** The fall, drowning and projectile notes, multikill windows and last killers the combat systems share. */
    private final CombatMemory memory;
    private final Map<UUID, Body> bodies = new ConcurrentHashMap<>();
    private final Map<String, Sky> skies = new ConcurrentHashMap<>();

    /** Weather has to hold this long before it counts as seen, as ten one second samples do on the own path. */
    static final long WEATHER_MILLIS = 10_000L;
    /** A health sample older than this says nothing about a kill. */
    static final long HEALTH_FRESH_MILLIS = 10_000L;
    /** A landing this close before a swim start is the same fall ending in water. */
    static final long SPLASH_MILLIS = 250L;
    /** The kill context and range rules of the DeathSystem, kept equal by hand since those are package private. */
    static final double FAR_METRES = 50;
    static final double PLAYER_FAR_METRES = 30;
    static final float LOW_HEALTH_SHARE = 0.1f;
    static final int SUNRISE_HOUR = 5;
    static final int SUNSET_HOUR = 19;
    static final String BLOOD_MOON_WEATHER = "Blood_Moon";
    /** DamageCause ids of 0.7.1, as the DamageSystem compares them. */
    static final String FALL_CAUSE = "Fall";
    static final String DROWNING_CAUSE = "Drowning";
    static final String MINECART = "Minecart";
    static final String ARMOR_SECTION = "Armor";

    TrackerBridge(TrackerHost host) {
        this.host = host;
        this.tracker = host.tracker();
        this.runs = DeepworksRuns.of(tracker);
        this.memory = CombatMemory.of(tracker);
    }

    /**
     * Hooks the bridge to TrackerAPI. It binds at once when TrackerAPI is already up and again whenever it
     * starts, and lets go when it stops. Closing the result removes the hooks and unbinds, for the Feats
     * shutdown.
     */
    public static AutoCloseable install(TrackerHost host) {
        TrackerBridge bridge = new TrackerBridge(host);
        Consumer<TrackerApi> enable = bridge::bind;
        Runnable disable = bridge::unbind;
        TrackerApi.onEnable(enable);
        TrackerApi.onDisable(disable);
        return () -> {
            TrackerApi.removeHooks(enable, disable);
            bridge.unbind();
        };
    }

    /** Subscribes every listener. Any earlier subscriptions are closed first. */
    synchronized void bind(TrackerApi api) {
        unbind();
        sub(api, BlockBroken.class, this::onBlockBroken);
        sub(api, BlockPlaced.class, this::onBlockPlaced);
        sub(api, BlockUsed.class, this::onBlockUsed);
        sub(api, ItemCrafted.class, this::onItemCrafted);
        sub(api, ItemPickedUp.class, this::onItemPickedUp);
        sub(api, ItemsGained.class, this::onItemsGained);
        sub(api, ItemConsumed.class, this::onItemConsumed);
        sub(api, DamageDealt.class, this::onDamageDealt);
        sub(api, EntityDied.class, this::onEntityDied);
        sub(api, CreatureTamed.class, this::onCreatureTamed);
        sub(api, InteractionStarted.class, this::onInteractionStarted);
        sub(api, ZoneDiscovered.class, this::onZoneDiscovered);
        sub(api, PlayerWorldChanged.class, this::onPlayerWorldChanged);
        sub(api, WorldAdded.class, this::onWorldAdded);
        sub(api, WorldRemoved.class, this::onWorldRemoved);
        sub(api, PlayerMoved.class, this::onPlayerMoved);
        sub(api, MemoryRecorded.class, this::onMemoryRecorded);
        sub(api, PlayerChatted.class, this::onPlayerChatted);
        sub(api, GameModeChanged.class, this::onGameModeChanged);
        // 1.7.2 kinds. Every one of these records ships in TrackerAPI 1.0.0, so no version check is needed.
        sub(api, MovementEdge.class, this::onMovementEdge);
        sub(api, MountChanged.class, this::onMountChanged);
        sub(api, VitalsSampled.class, this::onVitalsSampled);
        sub(api, ItemEquipped.class, this::onItemEquipped);
        sub(api, ItemBroke.class, this::onItemBroke);
        sub(api, ProjectileLaunched.class, this::onProjectileLaunched);
        sub(api, ProjectileLanded.class, this::onProjectileLanded);
        sub(api, InstanceDiscovered.class, this::onInstanceDiscovered);
        sub(api, TriggerVolumeFired.class, this::onTriggerVolumeFired);
        sub(api, WorldEventEnded.class, this::onWorldEventEnded);
        sub(api, PlayerSlept.class, this::onPlayerSlept);
        sub(api, WeatherChanged.class, this::onWeatherChanged);
        sub(api, GameTimeSampled.class, this::onGameTimeSampled);
        sub(api, EntityUsed.class, this::onEntityUsed);
        sub(api, PlayerLeft.class, this::onPlayerLeft);
        // Not subscribed. RecipeLearned and PermissionChanged, because the recipe poll and the group listener
        // of Feats run on both paths. PortalEntered, because PlayerWorldChanged already gives the portal keys.
        // MoonPhaseChanged, because GameTimeSampled carries the phase with the hour it needs.
        host.logger().at(Level.INFO).log("Feats is linked to TrackerAPI %s with %d listeners", api.version(),
                subscriptions.size());
    }

    /** Closes every subscription. Safe to call twice. */
    synchronized void unbind() {
        for (Subscription s : subscriptions) {
            try {
                s.close();
            } catch (RuntimeException ignored) {
                // already closed by TrackerAPI
            }
        }
        subscriptions.clear();
    }

    synchronized int subscriptionCount() {
        return subscriptions.size();
    }

    private <E extends TrackerEvent> void sub(TrackerApi api, Class<E> type, Consumer<? super E> listener) {
        subscriptions.add(api.subscribe(OWNER, type, listener));
    }

    // ---- blocks -------------------------------------------------------------------------------------------

    /** BreakBlockSystem. The player's own recent placement earns nothing. */
    void onBlockBroken(BlockBroken e) {
        if (e.playerPlaced()) {
            return;
        }
        tracker.incrementAll(e.player(), Classify.blockBroken(e.blockId()), 1);
        String species = Classify.woodSpecies(e.blockId());
        if (species != null) {
            tracker.discover(e.player(), StatKeys.WOOD_PREFIX, species, StatKeys.WOOD_SPECIES);
        }
    }

    /**
     * PlaceBlockSystem. Seeds and traps count before the repeat spot rule, as there. Placing again on a spot
     * the player just built on and broke counts once.
     */
    void onBlockPlaced(BlockPlaced e) {
        UUID uuid = e.player();
        String itemId = e.itemId();
        List<String> seeds = Classify.seedPlanted(itemId);
        if (!seeds.isEmpty()) {
            tracker.incrementAll(uuid, seeds, 1);
        } else if (Classify.FISHING_TRAP.equals(itemId)) {
            tracker.increment(uuid, StatKeys.TRAPS_PLACED, 1);
        }
        if (e.repeatSpot()) {
            return;
        }
        tracker.incrementAll(uuid, Classify.blockPlaced(itemId), 1);
        if (itemId != null && !itemId.isEmpty()) {
            tracker.discover(uuid, StatKeys.PLACED_KIND_PREFIX, itemId, StatKeys.PLACED_KINDS);
        }
    }

    /** UseBlockSystem, and the Goblin Lab device ring FlagWatcher reads. */
    void onBlockUsed(BlockUsed e) {
        List<String> keys = Classify.blockUsed(e.blockId());
        if (!keys.isEmpty()) {
            tracker.incrementAll(e.player(), keys, 1);
        }
        host.labUses().remember(e.player(), e.blockId(), e.at());
    }

    // ---- items --------------------------------------------------------------------------------------------

    /** CraftSystem. */
    void onItemCrafted(ItemCrafted e) {
        tracker.incrementAll(e.player(), Classify.crafted(e.itemId()), Math.max(1, e.quantity()));
    }

    /** PickupSystem. {@code fresh} leaves out what the player dropped themself in the last five minutes. */
    void onItemPickedUp(ItemPickedUp e) {
        if (e.fresh() > 0) {
            tracker.incrementAll(e.player(), Classify.pickedUp(e.itemId()), e.fresh());
            if (Classify.isRareFish(e.itemId())) {
                tracker.increment(e.player(), StatKeys.FISH_RARE, e.fresh());
            }
        }
    }

    /**
     * InventorySystem gains. {@code fresh} leaves out items taken back from a container or off the ground
     * where the player put them. Creative mode is skipped as the inventory system did, its items are free.
     */
    void onItemsGained(ItemsGained e) {
        UUID uuid = e.player();
        if (e.fresh() <= 0 || host.isCreative(uuid)) {
            return;
        }
        tracker.incrementAll(uuid, Classify.obtained(e.itemId()), e.fresh());
    }

    /** InventorySystem meals and potions. TrackerAPI already left out a potion placed as a block. */
    void onItemConsumed(ItemConsumed e) {
        UUID uuid = e.player();
        if (host.isCreative(uuid)) {
            return;
        }
        List<String> keys = Classify.consumed(e.itemId());
        if (!keys.isEmpty()) {
            tracker.incrementAll(uuid, keys, 1);
        }
    }

    /**
     * InventorySystem armour sets. TrackerAPI names the slot that changed, so the bridge keeps the armour
     * slots it has seen and checks for a full set after each change. Armour worn since before the join only
     * counts once each of its slots changed in this session, since no record lists what is already worn.
     */
    void onItemEquipped(ItemEquipped e) {
        if (!ARMOR_SECTION.equals(e.section()) || e.player() == null) {
            return;
        }
        Map<Integer, String> armour = body(e.player()).armour;
        if (e.itemId() == null) {
            armour.remove(e.slot());
        } else {
            armour.put(e.slot(), e.itemId());
        }
        String material = ArmourSets.completeSet(new ArrayList<>(armour.values()));
        if (material != null) {
            tracker.discover(e.player(), StatKeys.ARMOUR_SET_PREFIX, material, StatKeys.ARMOUR_SETS);
        }
    }

    /**
     * InventorySystem wear. The own path never counts armour wearing through as a tool worn out, and the
     * record names no section, so armour ids are left out here.
     */
    void onItemBroke(ItemBroke e) {
        String itemId = e.itemId();
        if (itemId == null || itemId.startsWith("Armor_")) {
            return;
        }
        List<String> keys = Classify.toolBroken(itemId);
        if (!keys.isEmpty()) {
            tracker.incrementAll(e.player(), keys, 1);
        }
    }

    // ---- combat -------------------------------------------------------------------------------------------

    /**
     * DamageSystem. Damage taken by a player with its cause, damage dealt by a player and their biggest
     * hit. Falls and drowning go to the {@link CombatMemory}, which settles them once no death followed. The
     * boss ledger is kept by TrackerAPI and comes back on the death record. Hits blocked with a shield stay
     * on the own path, DamageDealt has no blocked flag.
     */
    void onDamageDealt(DamageDealt e) {
        settle(e.at());
        long amount = Math.round(e.amount());
        if (amount <= 0) {
            return;
        }
        Actor victim = e.victim();
        Actor attacker = e.attacker();
        UUID victimPlayer = victim != null ? victim.playerUuid() : null;
        if (victimPlayer != null) {
            tracker.increment(victimPlayer, StatKeys.DAMAGE_TAKEN, amount);
            noteCause(victimPlayer, e.causeId(), amount, e.at());
        }
        UUID attackerPlayer = attacker != null ? attacker.playerUuid() : null;
        if (attackerPlayer != null && !attackerPlayer.equals(victimPlayer)) {
            tracker.increment(attackerPlayer, StatKeys.DAMAGE_DEALT, amount);
            tracker.raiseTo(attackerPlayer, StatKeys.BIGGEST_HIT, amount);
        }
    }

    private void noteCause(UUID uuid, String causeId, long amount, long at) {
        if (causeId == null || causeId.isEmpty()) {
            return;
        }
        tracker.discover(uuid, StatKeys.DAMAGE_TYPE_PREFIX, causeId, StatKeys.DAMAGE_TYPES);
        if (FALL_CAUSE.equals(causeId)) {
            memory.fall(uuid, amount, at);
        } else if (DROWNING_CAUSE.equals(causeId)) {
            memory.drowning(uuid, at);
        }
    }

    /** Settles the falls and drowning episodes old enough, the job the own path gives its 1 s tick. */
    private void settle(long at) {
        memory.flush(at);
    }

    /** ProjectileLaunchSystem. */
    void onProjectileLaunched(ProjectileLaunched e) {
        UUID shooter = e.shooter() != null ? e.shooter().playerUuid() : null;
        if (shooter != null && e.byWeapon()) {
            tracker.increment(shooter, StatKeys.SHOTS_FIRED, 1);
        }
    }

    /**
     * DamageSystem range note. The impact point against where the shooter last stood, which the death record
     * asks for when the hit was the killing blow.
     */
    void onProjectileLanded(ProjectileLanded e) {
        UUID shooter = e.shooter() != null ? e.shooter().playerUuid() : null;
        Body body = shooter != null ? bodies.get(shooter) : null;
        if (e.hit() == null || body == null || !body.placed) {
            return;
        }
        double dx = e.x() - body.x;
        double dy = e.y() - body.y;
        double dz = e.z() - body.z;
        memory.rangedHit(shooter, Math.sqrt(dx * dx + dy * dy + dz * dz), System.nanoTime());
    }

    /**
     * DeathSystem. Player deaths with their cause and effect keys, player kills, boss credit for every
     * participant, the untouched boss kill, the Gobliterator feats and the kill streaks. A creature kill adds
     * its context, multikill windows and kind, a player kill the player streak, vendetta and long range kill.
     * {@code viaEffect} is left alone, the effect id already gives the effect key.
     */
    void onEntityDied(EntityDied e) {
        Actor victim = e.victim();
        Actor killer = e.killer();
        UUID victimPlayer = victim != null ? victim.playerUuid() : null;
        boolean deepworks = DeepworksRuns.PORTAL_TYPE.equals(e.portalTypeId());

        if (victimPlayer != null) {
            tracker.incrementAll(victimPlayer, Classify.death(e.causeId(), e.effectId()), 1);
            tracker.countDeath(victimPlayer);
            memory.died(victimPlayer);
            if (deepworks) {
                runs.died(victimPlayer, e.world());
            }
        }

        UUID killerPlayer = killer != null ? killer.playerUuid() : null;
        if (killerPlayer != null && killerPlayer.equals(victimPlayer)) {
            killerPlayer = null; // the old system never counted a player as their own killer
        }
        String victimType = victimPlayer != null ? "Player" : victim != null ? victim.typeId() : null;
        boolean ranged = killerPlayer != null && e.ranged();

        if (victimPlayer == null && Classify.isBoss(victimType)) {
            creditBoss(e, victimType, killerPlayer, ranged, deepworks);
            return;
        }
        if (killerPlayer == null) {
            return;
        }
        tracker.incrementAll(killerPlayer, Classify.kill(victimType, ranged), 1);
        tracker.countKill(killerPlayer);
        double metres = ranged ? memory.rangedDistance(killerPlayer, System.nanoTime()) : -1;
        if (victimPlayer != null) {
            playerKill(killerPlayer, victimPlayer, metres);
        } else {
            blow(killerPlayer, e, metres);
            tracker.discover(killerPlayer, StatKeys.KILL_KIND_PREFIX, CombatKinds.kind(victimType), StatKeys.KILL_KINDS);
        }
    }

    /** The context and multikill counters of a killing blow on a creature or boss. */
    private void blow(UUID killer, EntityDied e, double metres) {
        List<String> context = context(killer, e.world(), e.at());
        if (metres >= FAR_METRES) {
            context.add(StatKeys.KILLS_FAR);
        }
        if (!context.isEmpty()) {
            tracker.incrementAll(killer, context, 1);
        }
        int filled = memory.windows().kill(killer, e.at());
        if (filled != 0) {
            tracker.incrementAll(killer, Windows.keys(filled), 1);
        }
    }

    /**
     * The kill context the own path reads off the killer, from the last state the records gave. Underwater
     * and gliding from the movement edges, mounted from the mount record, low health from the last fresh
     * vitals sample, night and blood moon from the clock and weather of the world of the kill.
     */
    private List<String> context(UUID killer, String world, long at) {
        List<String> keys = new ArrayList<>(4);
        Body body = bodies.get(killer);
        if (body != null) {
            if (body.swimming) {
                keys.add(StatKeys.KILLS_UNDERWATER);
            }
            if (body.gliding.on) {
                keys.add(StatKeys.KILLS_GLIDING);
            }
            if (body.riding.on) {
                keys.add(StatKeys.KILLS_MOUNTED);
            }
            if (body.healthShare >= 0 && body.healthShare < LOW_HEALTH_SHARE && at - body.healthAt <= HEALTH_FRESH_MILLIS) {
                keys.add(StatKeys.KILLS_LOW_HEALTH);
            }
        }
        Sky sky = world != null ? skies.get(key(world)) : null;
        if (sky != null) {
            if (sky.night) {
                keys.add(StatKeys.KILLS_NIGHT);
            }
            if (BLOOD_MOON_WEATHER.equals(sky.weather)) {
                keys.add(StatKeys.KILLS_BLOOD_MOON);
            }
        }
        return keys;
    }

    /** The player kill streak, the vendetta and the long range counters of one player kill. */
    private void playerKill(UUID killer, UUID victim, double metres) {
        if (!tracker.isPaused(killer)) {
            tracker.raiseTo(killer, StatKeys.PLAYER_KILL_STREAK, tracker.killStreaks().playerKill(killer));
        }
        if (memory.lastKillers().killed(killer, victim)) {
            tracker.increment(killer, StatKeys.VENDETTAS, 1);
        }
        if (metres >= PLAYER_FAR_METRES) {
            tracker.increment(killer, StatKeys.PLAYER_KILLS_FAR, 1);
        }
    }

    /**
     * A boss kill credits the killer and every participant, with the kind and the cooperative keys for each.
     * Only the killer gets the ranged kill and the kill context. Then the untouched and Gobliterator feats.
     */
    private void creditBoss(EntityDied e, String bossType, UUID killer, boolean ranged, boolean deepworks) {
        Set<UUID> credited = new LinkedHashSet<>();
        if (killer != null) {
            credited.add(killer);
        }
        if (e.participants() != null) {
            credited.addAll(e.participants());
        }
        if (credited.isEmpty()) {
            return;
        }
        Set<UUID> untouched = e.untouched() != null ? e.untouched() : Set.of();
        List<String> coop = credited.size() >= 5 ? List.of(StatKeys.BOSS_COOP_3, StatKeys.BOSS_COOP_5)
                : credited.size() >= 3 ? List.of(StatKeys.BOSS_COOP_3) : List.of();
        String kind = CombatKinds.kind(bossType);
        for (UUID player : credited) {
            boolean blow = player.equals(killer);
            tracker.incrementAll(player, Classify.kill(bossType, blow && ranged), 1);
            tracker.countKill(player);
            if (untouched.contains(player)) {
                tracker.increment(player, StatKeys.BOSS_FLAWLESS, 1);
            }
            if (!coop.isEmpty()) {
                tracker.incrementAll(player, coop, 1);
            }
            tracker.discover(player, StatKeys.KILL_KIND_PREFIX, kind, StatKeys.KILL_KINDS);
        }
        if (killer != null) {
            blow(killer, e, ranged ? memory.rangedDistance(killer, System.nanoTime()) : -1);
        }
        if (Classify.isGobliteratorFinal(bossType)) {
            creditGobliterator(e, bossType, killer, credited, deepworks);
        }
    }

    /**
     * The Gobliterator challenge feats, for each credited player who is still in the world of the kill. The
     * record lists every participant who wore no armour at the kill, so the unarmoured feat goes to each of
     * them. A record without that list still says it for the killer through {@code killerArmourless}.
     */
    private void creditGobliterator(EntityDied e, String bossType, UUID killer, Set<UUID> credited, boolean deepworks) {
        String world = e.world();
        if (world == null) {
            return;
        }
        String worldKey = key(world);
        boolean hard = Classify.isGobliteratorFinalHard(bossType);
        Set<UUID> armourless = !e.armourless().isEmpty() ? e.armourless()
                : e.killerArmourless() && killer != null ? Set.of(killer) : Set.of();
        long now = e.at();
        for (UUID player : credited) {
            if (!worldKey.equals(host.worldOf(player))) {
                continue;
            }
            boolean clean = deepworks && runs.isClean(player, world);
            boolean fast = deepworks && runs.isFast(player, world, now);
            if (clean) {
                tracker.increment(player, StatKeys.GOBLITERATOR_DEATHLESS, 1);
            }
            if (fast) {
                tracker.increment(player, StatKeys.GOBLITERATOR_FAST, 1);
            }
            if (hard && clean) {
                tracker.increment(player, StatKeys.GOBLITERATOR_HARD_DEATHLESS, 1);
            }
            if (hard && fast) {
                tracker.increment(player, StatKeys.GOBLITERATOR_HARD_FAST, 1);
            }
            if (hard && armourless.contains(player)) {
                tracker.increment(player, StatKeys.GOBLITERATOR_HM_UNARMOURED, 1);
            }
        }
    }

    /** Keeps the last health share of each player, for the low health kill. Feeds no counter itself. */
    void onVitalsSampled(VitalsSampled e) {
        settle(e.at());
        if (e.player() == null || e.maxHealth() <= 0) {
            return;
        }
        Body body = body(e.player());
        body.healthShare = (float) (e.health() / e.maxHealth());
        body.healthAt = e.at();
    }

    /** TameSystem, the total and the kind with the young counted as their adult role. */
    void onCreatureTamed(CreatureTamed e) {
        tracker.incrementAll(e.player(), Classify.tamed(e.tamedTypeId()), 1);
    }

    /**
     * InteractionSystem. Only chains the player starts directly, and an ability cast only when the player
     * could pay for it.
     */
    void onInteractionStarted(InteractionStarted e) {
        if (!isPlayerAction(e.type())) {
            return;
        }
        List<String> keys = Classify.interaction(e.rootId());
        if (keys.isEmpty()) {
            return;
        }
        if (keys.contains(StatKeys.ABILITY_CASTS) && !e.affordable()) {
            return;
        }
        tracker.incrementAll(e.player(), keys, 1);
    }

    private static boolean isPlayerAction(String type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case "Primary", "Secondary", "Ability1", "Ability2", "Ability3", "Ability4", "Use" -> true;
            default -> false;
        };
    }

    // ---- players and worlds --------------------------------------------------------------------------------

    /** ZoneDiscoverySystem. The zone name, or the region when the zone has none. */
    void onZoneDiscovered(ZoneDiscovered e) {
        String name = e.zone();
        if (name == null || name.isBlank()) {
            name = e.region();
        }
        if (name == null || name.isBlank()) {
            return;
        }
        tracker.discover(e.player(), StatKeys.ZONE_PREFIX, name, StatKeys.ZONES_DISCOVERED);
    }

    /**
     * PortalTracker. Counts entries into portal worlds and tells the Deepworks run tracker which run the
     * player is on. The first world of a session is skipped, as the old tracker skipped a player who was
     * not loaded yet. Time inside a portal world is added up at each world change and at the leave.
     */
    void onPlayerWorldChanged(PlayerWorldChanged e) {
        noteWorld(e.world(), e.instance());
        if (e.player() != null) {
            Body body = body(e.player());
            leavePortal(e.player(), body, e.at());
            if (e.instance() && e.portalTypeId() != null) {
                body.portal.start(e.at());
            }
        }
        if (e.fromWorld() == null) {
            return;
        }
        UUID uuid = e.player();
        if (!tracker.isLoaded(uuid)) {
            return;
        }
        String typeId = e.portalTypeId();
        runs.entered(uuid, typeId, e.world(), e.at());
        if (!e.instance()) {
            return;
        }
        tracker.incrementAll(uuid, Classify.portal(typeId), 1);
        tracker.discover(uuid, StatKeys.PORTAL_TYPE_PREFIX, typeId, StatKeys.PORTAL_TYPES);
    }

    void onWorldAdded(WorldAdded e) {
        noteWorld(e.world(), e.instance());
    }

    void onWorldRemoved(WorldRemoved e) {
        if (e.world() != null) {
            instanceWorlds.remove(e.world().toLowerCase(Locale.ROOT));
        }
    }

    private void noteWorld(String world, boolean instance) {
        if (world == null) {
            return;
        }
        String key = world.toLowerCase(Locale.ROOT);
        if (instance) {
            instanceWorlds.add(key);
        } else {
            instanceWorlds.remove(key);
        }
    }

    /**
     * PlayerSampler. Whole metres travelled, with the share swum, glided and ridden, and the bottom and
     * top of the world outside portal instances, and the biome stood in. The position is kept for the range
     * of a projectile hit.
     */
    void onPlayerMoved(PlayerMoved e) {
        UUID uuid = e.player();
        settle(e.at());
        if (uuid == null) {
            return;
        }
        Body body = body(uuid);
        body.x = e.x();
        body.y = e.y();
        body.z = e.z();
        body.placed = true;
        if (e.biome() != null && !e.biome().isBlank()) {
            tracker.discover(uuid, StatKeys.BIOME_PREFIX, e.biome(), StatKeys.BIOMES_VISITED);
        }
        if (host.trackDistance() && e.metres() > 0) {
            // TrackerAPI publishes one sample per second, measured from the mount while riding, so a ride
            // sample stands in for the walk sample and counts toward the total as the rider's own transform did.
            PlayerMoved.Mode mode = e.mode();
            tracker.increment(uuid, StatKeys.DISTANCE, e.metres());
            if (mode == PlayerMoved.Mode.RIDE) {
                tracker.increment(uuid, StatKeys.RIDE_DISTANCE, e.metres());
            } else if (mode == PlayerMoved.Mode.SWIM) {
                tracker.increment(uuid, StatKeys.SWIM_DISTANCE, e.metres());
            } else if (mode == PlayerMoved.Mode.GLIDE) {
                tracker.increment(uuid, StatKeys.GLIDE_DISTANCE, e.metres());
            }
        }
        String world = e.world();
        if (world != null && instanceWorlds.contains(world.toLowerCase(Locale.ROOT))) {
            return; // portal instances have floors and voids of their own
        }
        if (WorldBounds.atFloor(e.y())) {
            tracker.raiseTo(uuid, StatKeys.BOTTOM_REACHED, 1);
        } else if (WorldBounds.atCeiling(e.y())) {
            tracker.raiseTo(uuid, StatKeys.TOP_REACHED, 1);
        }
    }

    /** MemoryTracker. Every Memory id is a one time discovery. */
    void onMemoryRecorded(MemoryRecorded e) {
        tracker.discover(e.player(), StatKeys.MEMORY_PREFIX, e.memoryId(), StatKeys.MEMORIES);
    }

    /** The chat counter, at most one counted message per 5 seconds. */
    void onPlayerChatted(PlayerChatted e) {
        if (e.cancelled() || e.player() == null) {
            return;
        }
        host.countChat(e.player(), e.at());
    }

    /** GameModeSystem, for the Creative pause. */
    void onGameModeChanged(GameModeChanged e) {
        if (e.player() == null || e.to() == null) {
            return;
        }
        host.setCreative(e.player(), "Creative".equalsIgnoreCase(e.to()));
    }

    // ---- movement and mounts -------------------------------------------------------------------------------

    /**
     * MovementSystem. Jumps and glides count on their start edge, climbing, sprinting and crouching add up
     * the time from start to end edge with the fraction of a second carried on, and a glide raises the
     * longest glide when it ends. A fall that started {@link MovementEdges#WATER_LANDING_METRES} or more
     * above the point where swimming starts is a water landing. Rolls, mantles and double jumps have no edge
     * on the record, so they stay with the own path.
     */
    void onMovementEdge(MovementEdge e) {
        UUID uuid = e.player();
        if (uuid == null || e.edge() == null) {
            return;
        }
        settle(e.at());
        Body b = body(uuid);
        long at = e.at();
        switch (e.edge()) {
            case JUMP -> tracker.increment(uuid, StatKeys.JUMPS, 1);
            case FALL_START -> b.fallStartY = e.y();
            case LANDED -> {
                b.landedDrop = e.fallDistance();
                b.landedAt = at;
                b.fallStartY = Double.NaN;
            }
            case SWIM_START -> {
                b.swimming = true;
                waterLanding(uuid, b, e.y(), at);
            }
            case SWIM_END -> b.swimming = false;
            case GLIDE_START -> {
                if (!b.gliding.on) {
                    tracker.increment(uuid, StatKeys.GLIDES, 1);
                }
                b.gliding.start(at);
            }
            case GLIDE_END -> raise(uuid, StatKeys.LONGEST_GLIDE_SECONDS, b.gliding.end(at) / 1000);
            case SPRINT_START -> b.sprint.start(at);
            case SPRINT_END -> add(uuid, StatKeys.SPRINT_SECONDS, b.sprint.stop(at));
            case CROUCH_START -> b.crouch.start(at);
            case CROUCH_END -> add(uuid, StatKeys.CROUCH_SECONDS, b.crouch.stop(at));
            case CLIMB_START -> b.climb.start(at);
            case CLIMB_END -> add(uuid, StatKeys.CLIMB_SECONDS, b.climb.stop(at));
            default -> {
                // FALL_FAR adds nothing, the height kept from FALL_START already tells a long fall
            }
        }
    }

    /** The fall in progress, or one that landed a moment ago, ending in water from high enough. */
    private void waterLanding(UUID uuid, Body b, double y, long at) {
        double drop = !Double.isNaN(b.fallStartY) ? b.fallStartY - y
                : at - b.landedAt <= SPLASH_MILLIS ? b.landedDrop : 0;
        b.fallStartY = Double.NaN;
        b.landedDrop = 0;
        if (drop >= MovementEdges.WATER_LANDING_METRES) {
            tracker.increment(uuid, StatKeys.WATER_LANDINGS, 1);
        }
    }

    /**
     * MountSystem. Getting on an entity counts a mount by its kind, or a minecart ride, and getting off
     * raises the longest ride. The record names the NPC type the mount has now, where the own path reads
     * the role it had before it was ridden, so a mount whose role changes for riding keys by the riding
     * role. Seats stay with the own path, the record names the seat type and not its block.
     */
    void onMountChanged(MountChanged e) {
        UUID uuid = e.player();
        if (uuid == null) {
            return;
        }
        Body b = body(uuid);
        endRide(uuid, b, e.at());
        String type = e.mountTypeId();
        boolean minecart = MINECART.equals(type);
        if (!e.mounted() || (e.entityUuid() == null && !minecart)) {
            return; // a dismount, or a seat or a bed
        }
        b.riding.start(e.at());
        if (minecart) {
            tracker.increment(uuid, StatKeys.MINECART_RIDES, 1);
        } else if (type == null || type.isEmpty()) {
            tracker.increment(uuid, StatKeys.MOUNTS, 1);
        } else {
            tracker.incrementAll(uuid, List.of(StatKeys.MOUNTS, Classify.mountKey(type)), 1);
        }
    }

    private void endRide(UUID uuid, Body b, long at) {
        if (b.riding.on) {
            raise(uuid, StatKeys.LONGEST_RIDE, b.riding.end(at) / 1000);
        }
    }

    // ---- world events, time and weather --------------------------------------------------------------------

    /** InstanceDiscoverySystem, keyed by the discovery title key. */
    void onInstanceDiscovered(InstanceDiscovered e) {
        String key = WorldKeys.instanceKey(e.instanceId());
        if (key != null) {
            tracker.discover(e.player(), StatKeys.INSTANCE_PREFIX, key, StatKeys.INSTANCES_DISCOVERED);
        }
    }

    /** The trigger volume listener. Entries only, keyed by the id prefix. */
    void onTriggerVolumeFired(TriggerVolumeFired e) {
        if (!"ENTER".equals(e.type())) {
            return;
        }
        String prefix = WorldKeys.volumePrefix(e.volumeId());
        UUID uuid = e.player() != null ? e.player() : e.entity() != null ? e.entity().playerUuid() : null;
        if (prefix != null && uuid != null) {
            tracker.increment(uuid, StatKeys.VOLUME_PREFIX + prefix, 1);
        }
    }

    /**
     * WorldEventEndSystem. A successful end credits the participants on the record. TrackerAPI lists every
     * player in the world of the event, where the own path credits those within 64 metres of it.
     */
    void onWorldEventEnded(WorldEventEnded e) {
        String id = e.eventId();
        if (!"SUCCESS".equals(e.outcome()) || id == null || id.isBlank() || e.participants() == null) {
            return;
        }
        for (UUID uuid : e.participants()) {
            if (uuid != null && tracker.isLoaded(uuid)) {
                tracker.increment(uuid, StatKeys.WORLD_EVENT_PREFIX + id, 1);
                tracker.increment(uuid, StatKeys.WORLD_EVENTS_WON, 1);
            }
        }
    }

    /** A player asleep in a world is noted, the night counts when that world's clock reaches morning. */
    void onPlayerSlept(PlayerSlept e) {
        if (e.asleep() && e.player() != null && e.world() != null) {
            sky(e.world()).sleepers.add(e.player());
        }
    }

    /** The weather that just ended counts for the players in the world when it held long enough. */
    void onWeatherChanged(WeatherChanged e) {
        if (e.world() == null) {
            return;
        }
        Sky sky = sky(e.world());
        creditWeather(e.world(), sky, e.at());
        sky.weather = e.weatherId();
        sky.weatherSince = e.at();
    }

    /**
     * The once a minute world clock. Night by the hour as the kill context reads it, moons seen at night by
     * every player in the world, the weather that has held long enough, and the night slept when the clock
     * passes from night to day. Game days are not counted here, Feats reads them on both paths.
     */
    void onGameTimeSampled(GameTimeSampled e) {
        settle(e.at());
        String world = e.world();
        if (world == null) {
            return;
        }
        Sky sky = sky(world);
        boolean night = isNight(e.hour());
        if (!night) {
            if (sky.known && sky.night) {
                for (UUID slept : sky.sleepers) {
                    tracker.increment(slept, StatKeys.NIGHTS_SLEPT, 1);
                }
            }
            sky.sleepers.clear();
        }
        sky.known = true;
        sky.night = night;
        if (night && e.moonPhase() >= 0) {
            String moon = WorldKeys.moonKey(e.moonPhase());
            for (UUID uuid : playersIn(world)) {
                tracker.discover(uuid, StatKeys.MOON_PREFIX, moon, StatKeys.MOONS_SEEN);
            }
        }
        creditWeather(world, sky, e.at());
    }

    private void creditWeather(String world, Sky sky, long at) {
        String id = sky.weather;
        if (id == null || id.isBlank() || at - sky.weatherSince < WEATHER_MILLIS) {
            return;
        }
        for (UUID uuid : playersIn(world)) {
            tracker.discover(uuid, StatKeys.WEATHER_PREFIX, id, StatKeys.WEATHERS_SEEN);
        }
    }

    /** True when the engine's clock says night, the hours the DeathSystem counts as night. */
    static boolean isNight(int hour) {
        return hour < SUNRISE_HOUR || hour >= SUNSET_HOUR;
    }

    /** UseEntitySystem. NPCs used, by type id. */
    void onEntityUsed(EntityUsed e) {
        Actor target = e.target();
        String typeId = target != null && target.isCreature() ? target.typeId() : null;
        if (typeId != null && !typeId.isEmpty()) {
            tracker.increment(e.player(), Classify.talkKey(typeId), 1);
        }
    }

    /** Ends what the player was doing, as a disconnect does on the own path, and drops their notes. */
    void onPlayerLeft(PlayerLeft e) {
        UUID uuid = e.player();
        if (uuid == null) {
            return;
        }
        Body body = bodies.remove(uuid);
        if (body != null) {
            endRide(uuid, body, e.at());
            leavePortal(uuid, body, e.at());
        }
        for (Sky sky : skies.values()) {
            sky.sleepers.remove(uuid);
        }
    }

    private void leavePortal(UUID uuid, Body body, long at) {
        add(uuid, StatKeys.PORTAL_SECONDS, body.portal.stop(at));
    }

    // ---- small helpers -------------------------------------------------------------------------------------

    private Body body(UUID uuid) {
        return bodies.computeIfAbsent(uuid, u -> new Body());
    }

    private Sky sky(String world) {
        return skies.computeIfAbsent(key(world), k -> new Sky());
    }

    private static String key(String world) {
        return world.toLowerCase(Locale.ROOT);
    }

    /** The loaded players whose world is this one. */
    private List<UUID> playersIn(String world) {
        String key = key(world);
        List<UUID> out = new ArrayList<>();
        for (PlayerProgress progress : tracker.loadedPlayers()) {
            UUID uuid = progress.uuid();
            if (uuid != null && key.equals(host.worldOf(uuid))) {
                out.add(uuid);
            }
        }
        return out;
    }

    private void add(UUID uuid, String key, long amount) {
        if (amount > 0) {
            tracker.increment(uuid, key, amount);
        }
    }

    private void raise(UUID uuid, String key, long value) {
        if (value > 0) {
            tracker.raiseTo(uuid, key, value);
        }
    }

    /** A state that starts and ends on two records, such as a sprint or a ride. */
    private static final class Span {
        boolean on;
        long since;
        /** Milliseconds under a whole second left from earlier spans, as the own path carries fractions. */
        long carry;

        void start(long at) {
            if (!on) {
                on = true;
                since = at;
            }
        }

        /** Ends the span and gives its length in milliseconds, zero when it was not on. */
        long end(long at) {
            if (!on) {
                return 0;
            }
            on = false;
            return Math.max(0, at - since);
        }

        /** Ends the span and gives its whole seconds, with the fraction carried to the next one. */
        long stop(long at) {
            long millis = carry + end(at);
            carry = millis % 1000;
            return millis / 1000;
        }
    }

    /** What the bridge keeps of one online player between records. */
    private static final class Body {
        double x;
        double y;
        double z;
        /** True once a position sample arrived. */
        boolean placed;
        boolean swimming;
        final Span gliding = new Span();
        final Span sprint = new Span();
        final Span crouch = new Span();
        final Span climb = new Span();
        final Span riding = new Span();
        final Span portal = new Span();
        double fallStartY = Double.NaN;
        double landedDrop;
        long landedAt;
        float healthShare = -1f;
        long healthAt;
        /** Armour slot to item id, as the equip records showed them. */
        final Map<Integer, String> armour = new HashMap<>(4);
    }

    /** What the bridge keeps of one world's clock and weather. */
    private static final class Sky {
        boolean known;
        boolean night;
        String weather;
        long weatherSince;
        final Set<UUID> sleepers = ConcurrentHashMap.newKeySet();
    }
}
