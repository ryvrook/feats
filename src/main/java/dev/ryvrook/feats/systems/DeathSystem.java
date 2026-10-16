package dev.ryvrook.feats.systems;

import com.hypixel.hytale.builtin.portals.resources.PortalWorld;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.portalworld.PortalType;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.BossLedger;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.Windows;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fires once per death of any entity and records player deaths and player kills. A boss kill credits
 * every player the {@link BossLedger} saw hit it in the last minute, as well as the killer, and an
 * untouched boss kill to each of them the ledger never saw take damage during the fight. Every credited
 * kill extends the player's kill streak and a death ends it.
 *
 * <p>A creature kill also carries the killer's context, underwater, gliding, mounted, on low health, at
 * night, under a blood moon or from far away, feeds the multikill {@link Windows} and discovers the
 * creature kind. A player kill extends the player kill streak, checks the {@code LastKillers} record for
 * a vendetta and counts a long range player kill. A boss kill with three or five credited players counts
 * the cooperative keys for each of them.
 */
public final class DeathSystem extends DeathSystems.OnDeathSystem {
    /** A creature kill with a projectile from at least this far is a long range kill. */
    static final double FAR_METRES = 50;
    /** A player kill with a projectile from at least this far is a long range player kill. */
    static final double PLAYER_FAR_METRES = 30;
    private static final List<String> COOP_NONE = List.of();
    private static final List<String> COOP_3 = List.of(StatKeys.BOSS_COOP_3);
    private static final List<String> COOP_5 = List.of(StatKeys.BOSS_COOP_3, StatKeys.BOSS_COOP_5);

    private final FeatsTracker tracker;
    private final BossLedger ledger;
    private final DeepworksRuns runs;
    private final CombatMemory memory;
    private final CombatContext context = new CombatContext();

    public DeathSystem(FeatsTracker tracker) {
        this.tracker = tracker;
        this.ledger = BossLedger.of(tracker);
        this.runs = DeepworksRuns.of(tracker);
        this.memory = CombatMemory.of(tracker);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref, DeathComponent death, Store<EntityStore> store,
                                 CommandBuffer<EntityStore> commandBuffer) {
        try {
            track(ref, death, store);
        } catch (RuntimeException | LinkageError e) {
            PlayerEventSystem.failed(e);
        }
    }

    private void track(Ref<EntityStore> ref, DeathComponent death, Store<EntityStore> store) {
        PlayerRef victimPlayer = store.getComponent(ref, PlayerRef.getComponentType());
        Damage info = death.getDeathInfo();

        if (victimPlayer != null) {
            DamageCause damageCause = death.getDeathCause();
            String cause = damageCause != null ? damageCause.getId() : null;
            tracker.incrementAll(victimPlayer.getUuid(), Classify.death(cause, effectId(info)), 1);
            tracker.countDeath(victimPlayer.getUuid());
            memory.died(victimPlayer.getUuid());
            if (isDeepworks(store)) {
                runs.died(victimPlayer.getUuid(), PlayerEventSystem.worldName(store));
            }
        }

        PlayerRef killerPlayer = null;
        Ref<EntityStore> killer = null;
        boolean ranged = false;
        if (info != null && info.getSource() instanceof Damage.EntitySource source) {
            Ref<EntityStore> from = source.getRef();
            if (from != null && from.isValid() && from.getStore() == store && !from.equals(ref)) {
                killerPlayer = store.getComponent(from, PlayerRef.getComponentType());
                killer = from;
                ranged = source instanceof Damage.ProjectileSource;
            }
        }

        String victimType;
        if (victimPlayer != null) {
            victimType = "Player";
        } else {
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            victimType = npc != null ? npc.getNPCTypeId() : null;
        }

        if (victimPlayer == null && Classify.isBoss(victimType)) {
            creditBoss(ref, store, victimType, killerPlayer, killer, ranged, info);
            return;
        }
        if (killerPlayer != null) {
            UUID uuid = killerPlayer.getUuid();
            tracker.incrementAll(uuid, Classify.kill(victimType, ranged), 1);
            tracker.countKill(uuid);
            if (victimPlayer != null) {
                playerKill(uuid, victimPlayer.getUuid(), ranged ? rangedDistance(info, killer, store, uuid) : -1);
            } else {
                creatureKill(uuid, killer, store, victimType, ranged ? rangedDistance(info, killer, store, uuid) : -1);
            }
        }
    }

    /** The context, multikill and kind counters of one creature kill, the killing blow only. */
    private void creatureKill(UUID uuid, Ref<EntityStore> killer, Store<EntityStore> store, String victimType, double metres) {
        int flags = context.flags(killer, store);
        if (metres >= FAR_METRES) {
            flags |= CombatContext.FAR;
        }
        if (flags != 0) {
            tracker.incrementAll(uuid, CombatContext.keys(flags), 1);
        }
        int filled = memory.windows().kill(uuid, System.currentTimeMillis());
        if (filled != 0) {
            tracker.incrementAll(uuid, Windows.keys(filled), 1);
        }
        tracker.discover(uuid, StatKeys.KILL_KIND_PREFIX, CombatKinds.kind(victimType), StatKeys.KILL_KINDS);
    }

    /** The player kill streak, the vendetta and the long range counters of one player kill. */
    private void playerKill(UUID uuid, UUID victim, double metres) {
        if (!tracker.isPaused(uuid)) {
            tracker.raiseTo(uuid, StatKeys.PLAYER_KILL_STREAK, tracker.killStreaks().playerKill(uuid));
        }
        if (memory.lastKillers().killed(uuid, victim)) {
            tracker.increment(uuid, StatKeys.VENDETTAS, 1);
        }
        if (metres >= PLAYER_FAR_METRES) {
            tracker.increment(uuid, StatKeys.PLAYER_KILLS_FAR, 1);
        }
    }

    /**
     * Shooter to impact distance of a projectile kill, or -1 when it cannot be told. Read from the
     * projectile while it is still in the store, otherwise from the distance the damage system noted at
     * the hit, which is the same blow when it is under two seconds old.
     */
    private double rangedDistance(Damage info, Ref<EntityStore> killer, Store<EntityStore> store, UUID uuid) {
        if (info != null && info.getSource() instanceof Damage.ProjectileSource shot) {
            Ref<EntityStore> projectile = shot.getProjectile();
            if (projectile != null && projectile.isValid() && projectile.getStore() == store) {
                double metres = context.distance(projectile, killer, store);
                if (metres >= 0) {
                    return metres;
                }
            }
        }
        return memory.rangedDistance(uuid, System.nanoTime());
    }

    /**
     * The id of the status effect whose damage killed, such as Lava_Burn, or null when the death came
     * from anything else. Effect damage carries the effect as its source, wrapped in an entity source
     * when the effect has a living owner.
     */
    static String effectId(Damage info) {
        if (info == null) {
            return null;
        }
        try {
            Damage.Source source = info.getSource();
            ActiveEntityEffect effect = source instanceof Damage.EntityEffectSource owned ? owned.getEffect()
                    : source instanceof ActiveEntityEffect bare ? bare : null;
            if (effect == null) {
                return null;
            }
            EntityEffect asset = EntityEffect.getAssetMap().getAsset(effect.getEntityEffectIndex());
            return asset != null ? asset.getId() : null;
        } catch (RuntimeException | LinkageError e) {
            return null; // the cause key still counts, only the effect key is lost
        }
    }

    /**
     * Credits a boss kill to the killer and to everyone who hit the boss within the ledger window, then
     * clears the boss from the ledger. Only the killer gets the ranged kill and the kill context, since
     * those are about the killing blow. Every credited player discovers the kind and, with three or five
     * of them, the cooperative keys.
     */
    private void creditBoss(Ref<EntityStore> ref, Store<EntityStore> store, String bossType, PlayerRef killerPlayer,
                            Ref<EntityStore> killerRef, boolean ranged, Damage info) {
        UUID killer = killerPlayer != null ? killerPlayer.getUuid() : null;
        Set<UUID> credited = new LinkedHashSet<>();
        if (killer != null) {
            credited.add(killer);
        }
        UUIDComponent id = store.getComponent(ref, UUIDComponent.getComponentType());
        BossLedger.Outcome fight = BossLedger.Outcome.NONE;
        if (id != null && store.isInThread()) {
            fight = ledger.finish(id.getUuid(), System.nanoTime());
            credited.addAll(fight.credited());
        }
        if (credited.isEmpty()) {
            return;
        }
        List<String> coop = credited.size() >= 5 ? COOP_5 : credited.size() >= 3 ? COOP_3 : COOP_NONE;
        String kind = CombatKinds.kind(bossType);
        for (UUID player : credited) {
            boolean blow = player.equals(killer);
            tracker.incrementAll(player, Classify.kill(bossType, blow && ranged), 1);
            tracker.countKill(player);
            if (fight.untouched(player)) {
                tracker.increment(player, StatKeys.BOSS_FLAWLESS, 1);
            }
            tracker.incrementAll(player, coop, 1);
            tracker.discover(player, StatKeys.KILL_KIND_PREFIX, kind, StatKeys.KILL_KINDS);
        }
        if (killer != null && killerRef != null) {
            int flags = context.flags(killerRef, store);
            if (ranged && rangedDistance(info, killerRef, store, killer) >= FAR_METRES) {
                flags |= CombatContext.FAR;
            }
            if (flags != 0) {
                tracker.incrementAll(killer, CombatContext.keys(flags), 1);
            }
            int filled = memory.windows().kill(killer, System.currentTimeMillis());
            if (filled != 0) {
                tracker.incrementAll(killer, Windows.keys(filled), 1);
            }
        }
        if (Classify.isGobliteratorFinal(bossType)) {
            creditGobliterator(store, bossType, credited);
        }
    }

    /** The Gobliterator challenge feats, read for each credited player who is still in this world. */
    private void creditGobliterator(Store<EntityStore> store, String bossType, Set<UUID> credited) {
        World world = store.getExternalData() != null ? store.getExternalData().getWorld() : null;
        if (world == null || !store.isInThread()) {
            return;
        }
        boolean hard = Classify.isGobliteratorFinalHard(bossType);
        boolean deepworks = isDeepworks(store);
        String worldName = PlayerEventSystem.worldName(store);
        long now = System.currentTimeMillis();
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            UUID player = playerRef.getUuid();
            if (player == null || !credited.contains(player)) {
                continue;
            }
            boolean clean = deepworks && runs.isClean(player, worldName);
            boolean fast = deepworks && runs.isFast(player, worldName, now);
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
            if (hard && wearsNoArmour(store, playerRef.getReference())) {
                tracker.increment(player, StatKeys.GOBLITERATOR_HM_UNARMOURED, 1);
            }
        }
    }

    private static boolean wearsNoArmour(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid() || ref.getStore() != store) {
            return false;
        }
        InventoryComponent.Armor armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        ItemContainer slots = armor != null ? armor.getInventory() : null;
        return slots != null && slots.isEmpty();
    }

    /** True when this store is a Deepworks instance. A failed read counts as no, so kills still count. */
    private static boolean isDeepworks(Store<EntityStore> store) {
        try {
            PortalWorld portal = store.getResource(PortalWorld.getResourceType());
            if (portal == null || !portal.exists()) {
                return false;
            }
            PortalType type = portal.getPortalType();
            return type != null && DeepworksRuns.PORTAL_TYPE.equals(type.getId());
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
