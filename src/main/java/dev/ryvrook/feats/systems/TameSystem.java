package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.TameWatch;

import java.util.UUID;

/**
 * Counts animals a player tames. In 0.7.1 an animal is tamed by its own role logic. A player uses it
 * with the right food in hand, the animal locks that player into its InteractionTarget slot, eats the
 * food and asks for a role change to its Tamed_ role. The role change system removes the entity with
 * the UNLOAD reason and adds it back under the new role with the same UUID, so this system sees both
 * halves and {@link TameWatch} pairs them. There is no taming event and the tamed animal keeps no owner.
 *
 * <p>Chunk loads and unloads also add and remove animals with these reasons, but they never come with a
 * pending role change, so they are ignored.
 */
public final class TameSystem extends RefSystem<EntityStore> {
    /** The marked entity slot the animal role locks the feeding player into. */
    static final String FEEDER_SLOT = "InteractionTarget";

    private final FeatsTracker tracker;
    private final TameWatch watch = new TameWatch();

    public TameSystem(FeatsTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return NPCEntity.getComponentType();
    }

    @Override
    public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store,
                               CommandBuffer<EntityStore> commandBuffer) {
        if (reason != RemoveReason.UNLOAD) {
            return;
        }
        try {
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            Role role = npc != null ? npc.getRole() : null;
            if (role == null || !role.isRoleChangeRequested()) {
                return; // a chunk unload, not a role change
            }
            String oldRole = npc.getNPCTypeId();
            if (oldRole == null || oldRole.startsWith(TameWatch.TAMED_PREFIX)) {
                return;
            }
            MarkedEntitySupport marks = store.getComponent(ref, MarkedEntitySupport.getComponentType());
            Ref<EntityStore> feeder = marks != null ? marks.getMarkedEntityRef(FEEDER_SLOT) : null;
            if (feeder == null || !feeder.isValid() || feeder.getStore() != store) {
                return;
            }
            PlayerRef player = store.getComponent(feeder, PlayerRef.getComponentType());
            UUIDComponent id = store.getComponent(ref, UUIDComponent.getComponentType());
            if (player != null && id != null) {
                watch.leaving(id.getUuid(), oldRole, player.getUuid(), System.currentTimeMillis());
            }
        } catch (RuntimeException | LinkageError e) {
            PlayerEventSystem.failed(e);
        }
    }

    @Override
    public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store,
                              CommandBuffer<EntityStore> commandBuffer) {
        if (reason != AddReason.LOAD || watch.idle()) {
            return; // nothing is waiting, which is nearly always
        }
        try {
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            UUIDComponent id = store.getComponent(ref, UUIDComponent.getComponentType());
            if (npc == null || id == null) {
                return;
            }
            String role = npc.getNPCTypeId();
            UUID tamer = watch.arrived(id.getUuid(), role, System.currentTimeMillis());
            if (tamer != null) {
                // The tamed total and the kind, with the young counted as their adult role.
                tracker.incrementAll(tamer, Classify.tamed(role), 1);
            }
        } catch (RuntimeException | LinkageError e) {
            PlayerEventSystem.failed(e);
        }
    }
}
