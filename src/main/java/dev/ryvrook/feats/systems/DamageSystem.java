package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.BossLedger;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Tracks damage dealt by players and damage taken by players, and notes every player who hits a boss
 * in the {@link BossLedger} so the kill can credit the whole group. Damage a player takes is noted in
 * the ledger too, for the untouched boss kill, and every hit a player deals raises their biggest hit.
 *
 * <p>On the victim side it also discovers the damage cause for {@code damage_type:}, counts hits the
 * engine marked blocked, and hands falls and drowning ticks to the {@link CombatMemory}, which settles
 * whether they were survived once the death system has had its say. On the attacker side a projectile
 * hit notes the shooter to impact distance while the projectile is still there, for the long range kills.
 */
public final class DamageSystem extends EntityEventSystem<EntityStore, Damage> {
    /** DamageCause asset ids of 0.7.1, Server/Entity/Damage, as {@code DamageCause.getId()} returns them. */
    static final String FALL_CAUSE = "Fall";
    static final String DROWNING_CAUSE = "Drowning";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final AtomicBoolean GROUP_WARNED = new AtomicBoolean();

    private final FeatsTracker tracker;
    private final BossLedger ledger;
    private final CombatMemory memory;
    private final CombatContext context = new CombatContext();

    public DamageSystem(FeatsTracker tracker) {
        super(Damage.class);
        this.tracker = tracker;
        this.ledger = BossLedger.of(tracker);
        this.memory = CombatMemory.of(tracker);
    }

    /**
     * Runs in the engine's inspect damage group, as its own TrackLastDamage and PlayerHitIndicators do, so the
     * hit is seen after the filter group. WieldingDamageReduction sets Damage.BLOCKED there and the armour and
     * wielding reductions lower the amount there. Without the group a block could be missed and an amount read
     * before its reductions. A server without the damage module falls back to no group, with one warning.
     */
    @Override
    public SystemGroup<EntityStore> getGroup() {
        try {
            return DamageModule.get().getInspectDamageGroup();
        } catch (RuntimeException | LinkageError e) {
            if (GROUP_WARNED.compareAndSet(false, true)) {
                LOGGER.at(Level.WARNING).withCause(e).log("Feats could not find the inspect damage group, so shield blocks and hit sizes may be read early");
            }
            return null;
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, Damage damage) {
        try {
            track(index, chunk, store, damage);
        } catch (RuntimeException | LinkageError e) {
            PlayerEventSystem.failed(e);
        }
    }

    private void track(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, Damage damage) {
        if (damage.isCancelled()) {
            return;
        }
        Ref<EntityStore> victim = chunk.getReferenceTo(index);
        PlayerRef victimPlayer = store.getComponent(victim, PlayerRef.getComponentType());
        if (victimPlayer != null && blocked(damage)) {
            // A full block leaves no damage, so this comes before the amount check.
            tracker.increment(victimPlayer.getUuid(), StatKeys.BLOCKS, 1);
        }
        long amount = Math.round(damage.getAmount());
        if (amount <= 0) {
            return;
        }
        if (victimPlayer != null) {
            UUID uuid = victimPlayer.getUuid();
            tracker.increment(uuid, StatKeys.DAMAGE_TAKEN, amount);
            ledger.hurt(uuid, PlayerEventSystem.worldName(store), System.nanoTime());
            noteCause(uuid, damage, amount);
        }
        if (damage.getSource() instanceof Damage.EntitySource source) {
            Ref<EntityStore> attacker = source.getRef();
            // An attacker in another world (an arrow still flying after its shooter took a portal) has
            // a ref from another store, and reading it here would throw.
            if (attacker != null && attacker.isValid() && attacker.getStore() == store && !attacker.equals(victim)) {
                PlayerRef attackerPlayer = store.getComponent(attacker, PlayerRef.getComponentType());
                if (attackerPlayer != null) {
                    tracker.increment(attackerPlayer.getUuid(), StatKeys.DAMAGE_DEALT, amount);
                    tracker.raiseTo(attackerPlayer.getUuid(), StatKeys.BIGGEST_HIT, amount);
                    if (victimPlayer == null) {
                        noteBossHit(victim, store, attackerPlayer);
                    }
                    if (source instanceof Damage.ProjectileSource shot) {
                        noteRange(shot, attacker, store, attackerPlayer.getUuid());
                    }
                }
            }
        }
    }

    /** The damage cause taken, and the fall or drowning note the memory settles later. */
    private void noteCause(UUID uuid, Damage damage, long amount) {
        // The index lookup is what the engine's own death component does, Damage.getCause() is deprecated.
        DamageCause cause = DamageCause.getAssetMap().getAsset(damage.getDamageCauseIndex());
        String causeId = cause != null ? cause.getId() : null;
        if (causeId == null || causeId.isEmpty()) {
            return;
        }
        tracker.discover(uuid, StatKeys.DAMAGE_TYPE_PREFIX, causeId, StatKeys.DAMAGE_TYPES);
        if (FALL_CAUSE.equals(causeId)) {
            memory.fall(uuid, amount, System.currentTimeMillis());
        } else if (DROWNING_CAUSE.equals(causeId)) {
            memory.drowning(uuid, System.currentTimeMillis());
        }
    }

    /** True when the engine's wielding reduction marked the hit as blocked with a shield. */
    private static boolean blocked(Damage damage) {
        return Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED));
    }

    /** Shooter to impact distance of a projectile hit, read while the projectile is still in the store. */
    private void noteRange(Damage.ProjectileSource shot, Ref<EntityStore> shooter, Store<EntityStore> store, UUID uuid) {
        Ref<EntityStore> projectile = shot.getProjectile();
        if (projectile == null || !projectile.isValid() || projectile.getStore() != store) {
            return;
        }
        double metres = context.distance(projectile, shooter, store);
        if (metres >= 0) {
            memory.rangedHit(uuid, metres, System.nanoTime());
        }
    }

    private void noteBossHit(Ref<EntityStore> victim, Store<EntityStore> store, PlayerRef attacker) {
        NPCEntity npc = store.getComponent(victim, NPCEntity.getComponentType());
        if (npc == null || !Classify.isBoss(npc.getNPCTypeId()) || !store.isInThread()) {
            return;
        }
        UUIDComponent id = store.getComponent(victim, UUIDComponent.getComponentType());
        if (id != null) {
            ledger.record(id.getUuid(), attacker.getUuid(), PlayerEventSystem.worldName(store), System.nanoTime());
        }
    }
}
