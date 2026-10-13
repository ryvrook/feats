package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.BossLedger;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;

/**
 * Tracks damage dealt by players and damage taken by players, and notes every player who hits a boss
 * in the {@link BossLedger} so the kill can credit the whole group.
 */
public final class DamageSystem extends EntityEventSystem<EntityStore, Damage> {
    private final FeatsTracker tracker;
    private final BossLedger ledger;

    public DamageSystem(FeatsTracker tracker) {
        super(Damage.class);
        this.tracker = tracker;
        this.ledger = BossLedger.of(tracker);
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
        long amount = Math.round(damage.getAmount());
        if (amount <= 0) {
            return;
        }
        Ref<EntityStore> victim = chunk.getReferenceTo(index);
        PlayerRef victimPlayer = store.getComponent(victim, PlayerRef.getComponentType());
        if (victimPlayer != null) {
            tracker.increment(victimPlayer.getUuid(), StatKeys.DAMAGE_TAKEN, amount);
        }
        if (damage.getSource() instanceof Damage.EntitySource source) {
            Ref<EntityStore> attacker = source.getRef();
            // An attacker in another world (an arrow still flying after its shooter took a portal) has
            // a ref from another store, and reading it here would throw.
            if (attacker != null && attacker.isValid() && attacker.getStore() == store && !attacker.equals(victim)) {
                PlayerRef attackerPlayer = store.getComponent(attacker, PlayerRef.getComponentType());
                if (attackerPlayer != null) {
                    tracker.increment(attackerPlayer.getUuid(), StatKeys.DAMAGE_DEALT, amount);
                    if (victimPlayer == null) {
                        noteBossHit(victim, store, attackerPlayer);
                    }
                }
            }
        }
    }

    private void noteBossHit(Ref<EntityStore> victim, Store<EntityStore> store, PlayerRef attacker) {
        NPCEntity npc = store.getComponent(victim, NPCEntity.getComponentType());
        if (npc == null || !Classify.isBoss(npc.getNPCTypeId()) || !store.isInThread()) {
            return;
        }
        UUIDComponent id = store.getComponent(victim, UUIDComponent.getComponentType());
        if (id != null) {
            ledger.record(id.getUuid(), attacker.getUuid(), System.nanoTime());
        }
    }
}
