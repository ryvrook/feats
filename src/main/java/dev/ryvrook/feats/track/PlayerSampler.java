package dev.ryvrook.feats.track;

import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.builtin.portals.resources.PortalWorld;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.stats.StatKeys;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The once a second look at every online player, run as one task per world on that world's thread. For
 * each player it measures the distance travelled, with the share swum, glided and ridden, notes the
 * bottom and the top of the world outside portal instances, and reads their recorded Memories. A player
 * who moved to another world after the tick grouped them is skipped quietly and sampled there next time.
 *
 * <p>Distance comes from the player's transform as it always has. Swimming and gliding come from the
 * movement states the client reports, which the server keeps in {@link MovementStatesComponent}. Riding
 * is a {@link MountedComponent} that points at an entity, a mount or a minecart. Seats are mounts on a
 * block and do not move. The ridden distance is measured from the mount's own position, so it counts
 * whether or not the rider's transform follows the mount. Each of these reads switches itself off with
 * one warning if its classes are missing on this server, leaving its counter unfed.
 */
public final class PlayerSampler {
    private final FeatsTracker tracker;
    private final MemoryTracker memories;
    private final HytaleLogger logger;
    private final MovementSampler moves = new MovementSampler();
    private final MovementSampler rides = new MovementSampler();
    /** The entity each riding player rode at the last sample. Cleared as soon as they stop riding. */
    private final Map<UUID, Ref<EntityStore>> lastMount = new ConcurrentHashMap<>();
    private volatile boolean statesOff;
    private volatile boolean mountsOff;
    private volatile ComponentType<EntityStore, MountedComponent> mounted;

    public PlayerSampler(FeatsTracker tracker, MemoryTracker memories, HytaleLogger logger) {
        this.tracker = tracker;
        this.memories = memories;
        this.logger = logger;
    }

    /** Samples every player of one world. Call it on that world's thread. */
    public void sampleWorld(List<PlayerRef> players, boolean trackDistance) {
        Boolean instance = null; // read once per world task, from the first player who is still here
        for (int i = 0, n = players.size(); i < n; i++) {
            PlayerRef player = players.get(i);
            try {
                UUID uuid = player.getUuid();
                Ref<EntityStore> ref = player.getReference();
                if (uuid == null || ref == null || !ref.isValid()) {
                    continue;
                }
                Store<EntityStore> store = ref.getStore();
                if (!store.isInThread()) {
                    continue; // moved to another world since the tick grouped them, sampled there next time
                }
                if (instance == null) {
                    instance = isPortalWorld(store);
                }
                sample(uuid, player, ref, store, trackDistance, instance);
            } catch (RuntimeException e) {
                logger.at(Level.FINE).withCause(e).log("Feats skipped a player sample");
            }
        }
    }

    private void sample(UUID uuid, PlayerRef player, Ref<EntityStore> ref, Store<EntityStore> store,
                        boolean trackDistance, boolean instance) {
        Transform transform = player.getTransform();
        Vector3d pos = transform != null ? transform.getPosition() : null;
        if (pos != null) {
            if (trackDistance) {
                walk(uuid, ref, store, pos);
            }
            // Portal instances such as The Deepworks have floors and voids of their own, so only real worlds count.
            if (!instance) {
                if (WorldBounds.atFloor(pos.y)) {
                    tracker.raiseTo(uuid, StatKeys.BOTTOM_REACHED, 1);
                } else if (WorldBounds.atCeiling(pos.y)) {
                    tracker.raiseTo(uuid, StatKeys.TOP_REACHED, 1);
                }
            }
        }
        memories.read(uuid, ref);
    }

    /** True for a portal instance world, the same check the portal tracker uses. A failed read counts as no. */
    private static boolean isPortalWorld(Store<EntityStore> store) {
        try {
            PortalWorld portal = store.getResource(PortalWorld.getResourceType());
            return portal != null && portal.exists();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private void walk(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, Vector3d pos) {
        MovementSampler.Step step = moves.sample(uuid, pos.x, pos.y, pos.z, mode(ref, store));
        if (step.metres() > 0) {
            tracker.increment(uuid, StatKeys.DISTANCE, step.metres());
        }
        if (step.modeMetres() > 0) {
            tracker.increment(uuid, step.mode() == MovementSampler.Mode.SWIM ? StatKeys.SWIM_DISTANCE
                    : StatKeys.GLIDE_DISTANCE, step.modeMetres());
        }
        Ref<EntityStore> mountRef = mountOf(ref, store);
        Vector3d mount = mountRef != null ? positionOf(mountRef, store) : null;
        if (mount == null) {
            rides.resetPosition(uuid);
            lastMount.remove(uuid);
            return;
        }
        if (!mountRef.equals(lastMount.put(uuid, mountRef))) {
            rides.resetPosition(uuid); // a different mount, so its first sample only sets the start
        }
        long ridden = rides.sample(uuid, mount.x, mount.y, mount.z, MovementSampler.Mode.WALK).metres();
        if (ridden > 0) {
            tracker.increment(uuid, StatKeys.RIDE_DISTANCE, ridden);
        }
    }

    /** Swimming or gliding from the movement states, WALK for anything else or when they cannot be read. */
    private MovementSampler.Mode mode(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (statesOff) {
            return MovementSampler.Mode.WALK;
        }
        try {
            MovementStatesComponent component = store.getComponent(ref, MovementStatesComponent.getComponentType());
            MovementStates states = component != null ? component.getMovementStates() : null;
            if (states == null) {
                return MovementSampler.Mode.WALK;
            }
            if (states.gliding) {
                return MovementSampler.Mode.GLIDE;
            }
            return states.swimming ? MovementSampler.Mode.SWIM : MovementSampler.Mode.WALK;
        } catch (LinkageError e) {
            statesOff = true;
            logger.at(Level.WARNING).withCause(e).log("Feats cannot read movement states on this server, swim and glide distance are off");
            return MovementSampler.Mode.WALK;
        }
    }

    /** The entity the player rides, or null when they ride nothing that moves. */
    private Ref<EntityStore> mountOf(Ref<EntityStore> ref, Store<EntityStore> store) {
        ComponentType<EntityStore, MountedComponent> type = mountedType();
        if (type == null) {
            return null;
        }
        MountedComponent mounted = store.getComponent(ref, type);
        Ref<EntityStore> mount = mounted != null ? mounted.getMountedToEntity() : null;
        return mount != null && mount.isValid() && mount.getStore() == store ? mount : null;
    }

    private static Vector3d positionOf(Ref<EntityStore> mount, Store<EntityStore> store) {
        TransformComponent transform = store.getComponent(mount, TransformComponent.getComponentType());
        return transform != null ? transform.getPosition() : null;
    }

    /**
     * The mounted component type, looked up once. The mounts plugin owns it, so a server without that
     * plugin has none and ride distance stays off.
     */
    private ComponentType<EntityStore, MountedComponent> mountedType() {
        ComponentType<EntityStore, MountedComponent> type = mounted;
        if (type != null || mountsOff) {
            return type;
        }
        try {
            type = MountedComponent.getComponentType();
        } catch (RuntimeException | LinkageError e) {
            type = null;
        }
        if (type == null) {
            mountsOff = true;
            logger.at(Level.WARNING).log("Feats cannot read mounts on this server, ride distance is off");
            return null;
        }
        mounted = type;
        return type;
    }

    /** The player entered a world, so the next sample starts a fresh step. */
    public void resetPosition(UUID uuid) {
        moves.resetPosition(uuid);
        rides.resetPosition(uuid);
        lastMount.remove(uuid);
    }

    /** The player left. */
    public void forget(UUID uuid) {
        moves.forget(uuid);
        rides.forget(uuid);
        lastMount.remove(uuid);
    }
}
