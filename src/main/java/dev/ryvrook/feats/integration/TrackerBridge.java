package dev.ryvrook.feats.integration;

import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.systems.DeepworksRuns;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.WorldBounds;
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
import dev.ryvrook.trackerapi.api.event.GameModeChanged;
import dev.ryvrook.trackerapi.api.event.InteractionStarted;
import dev.ryvrook.trackerapi.api.event.ItemConsumed;
import dev.ryvrook.trackerapi.api.event.ItemCrafted;
import dev.ryvrook.trackerapi.api.event.ItemPickedUp;
import dev.ryvrook.trackerapi.api.event.ItemsGained;
import dev.ryvrook.trackerapi.api.event.MemoryRecorded;
import dev.ryvrook.trackerapi.api.event.PlayerChatted;
import dev.ryvrook.trackerapi.api.event.PlayerMoved;
import dev.ryvrook.trackerapi.api.event.PlayerWorldChanged;
import dev.ryvrook.trackerapi.api.event.WorldAdded;
import dev.ryvrook.trackerapi.api.event.WorldRemoved;
import dev.ryvrook.trackerapi.api.event.ZoneDiscovered;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
 * thread and already counted chat and playtime off the world thread.
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

    TrackerBridge(TrackerHost host) {
        this.host = host;
        this.tracker = host.tracker();
        this.runs = DeepworksRuns.of(tracker);
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
    }

    /** PlaceBlockSystem. Placing again on a spot the player just built on and broke counts once. */
    void onBlockPlaced(BlockPlaced e) {
        if (e.repeatSpot()) {
            return;
        }
        tracker.incrementAll(e.player(), Classify.blockPlaced(e.itemId()), 1);
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

    // ---- combat -------------------------------------------------------------------------------------------

    /**
     * DamageSystem. Damage taken by a player, damage dealt by a player and their biggest hit. The boss
     * ledger is kept by TrackerAPI and comes back on the death record.
     */
    void onDamageDealt(DamageDealt e) {
        long amount = Math.round(e.amount());
        if (amount <= 0) {
            return;
        }
        Actor victim = e.victim();
        Actor attacker = e.attacker();
        UUID victimPlayer = victim != null ? victim.playerUuid() : null;
        if (victimPlayer != null) {
            tracker.increment(victimPlayer, StatKeys.DAMAGE_TAKEN, amount);
        }
        UUID attackerPlayer = attacker != null ? attacker.playerUuid() : null;
        if (attackerPlayer != null && !attackerPlayer.equals(victimPlayer)) {
            tracker.increment(attackerPlayer, StatKeys.DAMAGE_DEALT, amount);
            tracker.raiseTo(attackerPlayer, StatKeys.BIGGEST_HIT, amount);
        }
    }

    /**
     * DeathSystem. Player deaths with their cause and effect keys, player kills, boss credit for every
     * participant, the untouched boss kill, the Gobliterator feats and the kill streaks.
     */
    void onEntityDied(EntityDied e) {
        Actor victim = e.victim();
        Actor killer = e.killer();
        UUID victimPlayer = victim != null ? victim.playerUuid() : null;
        boolean deepworks = DeepworksRuns.PORTAL_TYPE.equals(e.portalTypeId());

        if (victimPlayer != null) {
            tracker.incrementAll(victimPlayer, Classify.death(e.causeId(), e.effectId()), 1);
            tracker.countDeath(victimPlayer);
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
        if (killerPlayer != null) {
            tracker.incrementAll(killerPlayer, Classify.kill(victimType, ranged), 1);
            tracker.countKill(killerPlayer);
        }
    }

    /** A boss kill credits the killer and every participant, then the untouched and Gobliterator feats. */
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
        for (UUID player : credited) {
            boolean blow = player.equals(killer);
            tracker.incrementAll(player, Classify.kill(bossType, blow && ranged), 1);
            tracker.countKill(player);
            if (untouched.contains(player)) {
                tracker.increment(player, StatKeys.BOSS_FLAWLESS, 1);
            }
        }
        if (Classify.isGobliteratorFinal(bossType)) {
            creditGobliterator(e, bossType, killer, credited, deepworks);
        }
    }

    /**
     * The Gobliterator challenge feats, for each credited player who is still in the world of the kill. The
     * record lists every participant who wore no armour at the kill, so the unarmoured feat goes to each of them.
     */
    private void creditGobliterator(EntityDied e, String bossType, UUID killer, Set<UUID> credited, boolean deepworks) {
        String world = e.world();
        if (world == null) {
            return;
        }
        String worldKey = world.toLowerCase(Locale.ROOT);
        boolean hard = Classify.isGobliteratorFinalHard(bossType);
        long now = e.at();
        for (UUID player : credited) {
            if (!worldKey.equals(host.worldOf(player))) {
                continue;
            }
            if (deepworks && runs.isClean(player, world)) {
                tracker.increment(player, StatKeys.GOBLITERATOR_DEATHLESS, 1);
            }
            if (deepworks && runs.isFast(player, world, now)) {
                tracker.increment(player, StatKeys.GOBLITERATOR_FAST, 1);
            }
            if (hard && e.armourless().contains(player)) {
                tracker.increment(player, StatKeys.GOBLITERATOR_HM_UNARMOURED, 1);
            }
        }
    }

    /** TameSystem. */
    void onCreatureTamed(CreatureTamed e) {
        tracker.increment(e.player(), StatKeys.TAMED, 1);
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
     * not loaded yet.
     */
    void onPlayerWorldChanged(PlayerWorldChanged e) {
        noteWorld(e.world(), e.instance());
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
     * top of the world outside portal instances.
     */
    void onPlayerMoved(PlayerMoved e) {
        UUID uuid = e.player();
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
}
