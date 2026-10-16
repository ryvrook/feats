package dev.ryvrook.feats.systems;

import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.builtin.mounts.minecart.MinecartComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.protocol.BlockMountType;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.List;
import java.util.UUID;

/**
 * Counts seats, mounts and minecart rides from the {@link MountedComponent} the mounts plugin puts on a
 * player while they sit or ride. The engine's own MountSystems.PlayerMount is a RefChangeSystem of the
 * same shape. Added means the player got on, removed means they got off. A seat is a block mount of type
 * Seat and counts once per block id through a discovery. A mount is an entity with an NPCMountComponent
 * and counts by its original role, the role it had before the mounts plugin swapped it for riding. A
 * minecart is an entity with a MinecartComponent. Beds count elsewhere, through the bed use. The ride
 * length at dismount raises the longest ride record, in seconds.
 */
public final class MountSystem extends RefChangeSystem<EntityStore, MountedComponent> {
    private final FeatsTracker tracker;
    private final ComponentType<EntityStore, MountedComponent> type = MountedComponent.getComponentType();

    public MountSystem(FeatsTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public ComponentType<EntityStore, MountedComponent> componentType() {
        return type;
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref, MountedComponent mounted, Store<EntityStore> store,
                                 CommandBuffer<EntityStore> commandBuffer) {
        try {
            UUID uuid = uuidOf(ref, store);
            if (uuid != null) {
                mountedOn(uuid, mounted, store);
            }
        } catch (RuntimeException | LinkageError e) {
            PlayerEventSystem.failed(e);
        }
    }

    @Override
    public void onComponentSet(Ref<EntityStore> ref, MountedComponent before, MountedComponent after,
                               Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        try {
            UUID uuid = uuidOf(ref, store);
            if (uuid == null || sameTarget(before, after)) {
                return;
            }
            if (before != null) {
                dismounted(uuid, before);
            }
            if (after != null) {
                mountedOn(uuid, after, store);
            }
        } catch (RuntimeException | LinkageError e) {
            PlayerEventSystem.failed(e);
        }
    }

    @Override
    public void onComponentRemoved(Ref<EntityStore> ref, MountedComponent mounted, Store<EntityStore> store,
                                   CommandBuffer<EntityStore> commandBuffer) {
        try {
            UUID uuid = uuidOf(ref, store);
            if (uuid != null && mounted != null) {
                dismounted(uuid, mounted);
            }
        } catch (RuntimeException | LinkageError e) {
            PlayerEventSystem.failed(e);
        }
    }

    private void mountedOn(UUID uuid, MountedComponent mounted, Store<EntityStore> store) {
        Ref<EntityStore> entity = mounted.getMountedToEntity();
        if (entity != null) {
            if (entity.isValid() && entity.getStore() == store) {
                rode(uuid, entity, store, mounted.getControllerType());
            }
            return;
        }
        if (mounted.getBlockMountType() == BlockMountType.Seat) {
            String blockId = seatBlockId(mounted.getMountedToBlock());
            if (blockId != null) {
                tracker.discover(uuid, StatKeys.SEAT_PREFIX, blockId, StatKeys.SEATS);
            }
        }
    }

    private void rode(UUID uuid, Ref<EntityStore> entity, Store<EntityStore> store, MountController controller) {
        if (controller == MountController.Minecart || store.getComponent(entity, MinecartComponent.getComponentType()) != null) {
            tracker.increment(uuid, StatKeys.MINECART_RIDES, 1);
            return;
        }
        String role = roleOf(entity, store);
        if (role == null) {
            tracker.increment(uuid, StatKeys.MOUNTS, 1);
        } else {
            tracker.incrementAll(uuid, List.of(StatKeys.MOUNTS, Classify.mountKey(role)), 1);
        }
    }

    private void dismounted(UUID uuid, MountedComponent mounted) {
        if (mounted.getMountedToEntity() == null) {
            return; // getting up from a seat or a bed is not a ride
        }
        long seconds = mounted.getMountedDurationMs() / 1000L;
        if (seconds > 0) {
            tracker.raiseTo(uuid, StatKeys.LONGEST_RIDE, seconds);
        }
    }

    /**
     * The role the mount had before it was ridden, from the role index the mounts plugin kept, falling
     * back to its current NPC type id. Null for an entity that is no NPC.
     */
    private static String roleOf(Ref<EntityStore> entity, Store<EntityStore> store) {
        NPCMountComponent mount = store.getComponent(entity, NPCMountComponent.getComponentType());
        if (mount != null) {
            try {
                String name = NPCPlugin.get().getName(mount.getOriginalRoleIndex());
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            } catch (RuntimeException | LinkageError e) {
                // fall through to the current role
            }
        }
        NPCEntity npc = store.getComponent(entity, NPCEntity.getComponentType());
        String id = npc != null ? npc.getNPCTypeId() : null;
        return id == null || id.isEmpty() ? null : id;
    }

    /** The block id of the seat, without the state marker the engine puts in front of a state variant. */
    private static String seatBlockId(Ref<ChunkStore> block) {
        if (block == null || !block.isValid()) {
            return null;
        }
        Store<ChunkStore> chunks = block.getStore();
        BlockMountComponent seat = chunks.getComponent(block, BlockMountComponent.getComponentType());
        BlockType type = seat != null ? seat.getExpectedBlockType() : null;
        String id = type != null ? type.getId() : null;
        if (id == null || id.isEmpty()) {
            return null;
        }
        return id.startsWith("*") ? id.substring(1) : id;
    }

    private static boolean sameTarget(MountedComponent a, MountedComponent b) {
        if (a == null || b == null) {
            return a == b;
        }
        Ref<EntityStore> ea = a.getMountedToEntity();
        Ref<EntityStore> eb = b.getMountedToEntity();
        if (ea != null || eb != null) {
            return ea != null && ea.equals(eb);
        }
        Ref<ChunkStore> ba = a.getMountedToBlock();
        return ba != null && ba.equals(b.getMountedToBlock());
    }

    private static UUID uuidOf(Ref<EntityStore> ref, Store<EntityStore> store) {
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        return player != null ? player.getUuid() : null;
    }
}
