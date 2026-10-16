package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.MovementEdges;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads every player's movement flags once a tick and counts jumps, double jumps, rolls, mantles, glides,
 * water landings and the seconds spent climbing, sprinting and crouching. The flags come from
 * {@link MovementStatesComponent}, which the client reports and the engine keeps per player. The edge
 * logic is in {@link MovementEdges} and the counts wait in a per player state until a second has passed,
 * then go to the tracker in one flush, so the per tick work is a few component reads and comparisons.
 *
 * <p>The system runs on each world's thread for the players in that world. The state map is shared
 * across worlds, so it is concurrent, and a player is only ever in one world at a time.
 */
public final class MovementSystem extends EntityTickingSystem<EntityStore> {
    private final FeatsTracker tracker;
    private final Map<UUID, MovementEdges.State> states = new ConcurrentHashMap<>();
    private final ComponentType<EntityStore, PlayerRef> playerType = PlayerRef.getComponentType();
    private final ComponentType<EntityStore, MovementStatesComponent> statesType = MovementStatesComponent.getComponentType();
    private final ComponentType<EntityStore, TransformComponent> transformType = TransformComponent.getComponentType();

    public MovementSystem(FeatsTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public boolean isParallel(int archetypes, int entities) {
        return false;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        try {
            PlayerRef player = chunk.getComponent(index, playerType);
            MovementStatesComponent component = chunk.getComponent(index, statesType);
            MovementStates flags = component != null ? component.getMovementStates() : null;
            if (player == null || flags == null) {
                return;
            }
            UUID uuid = player.getUuid();
            if (uuid == null) {
                return;
            }
            MovementEdges.State state = states.get(uuid);
            if (state == null) {
                if (!tracker.isLoaded(uuid)) {
                    return; // nothing to credit until the file is loaded
                }
                state = states.computeIfAbsent(uuid, id -> new MovementEdges.State());
            }
            double y = 0;
            if (flags.falling != state.falling()) {
                // Only the start and the end of a fall need the height, so the transform is read on those ticks
                TransformComponent transform = chunk.getComponent(index, transformType);
                Vector3d pos = transform != null ? transform.getPosition() : null;
                y = pos != null ? pos.y : 0;
            }
            MovementEdges.step(state, flags.jumping, flags.extraJumpsUsed, flags.rolling, flags.mantling, flags.climbing,
                    flags.sprinting, flags.crouching, flags.gliding, flags.falling, flags.inFluid, y, dt);
            if (state.due()) {
                flush(uuid, state.drain());
            }
        } catch (RuntimeException | LinkageError e) {
            PlayerEventSystem.failed(e);
        }
    }

    private void flush(UUID uuid, MovementEdges.Drained d) {
        if (d.isEmpty()) {
            return;
        }
        add(uuid, StatKeys.JUMPS, d.jumps());
        add(uuid, StatKeys.DOUBLE_JUMPS, d.doubleJumps());
        add(uuid, StatKeys.ROLLS, d.rolls());
        add(uuid, StatKeys.MANTLES, d.mantles());
        add(uuid, StatKeys.GLIDES, d.glides());
        add(uuid, StatKeys.WATER_LANDINGS, d.waterLandings());
        add(uuid, StatKeys.CLIMB_SECONDS, d.climbSeconds());
        add(uuid, StatKeys.SPRINT_SECONDS, d.sprintSeconds());
        add(uuid, StatKeys.CROUCH_SECONDS, d.crouchSeconds());
        if (d.longestGlideSeconds() > 0) {
            tracker.raiseTo(uuid, StatKeys.LONGEST_GLIDE_SECONDS, d.longestGlideSeconds());
        }
    }

    private void add(UUID uuid, String key, int amount) {
        if (amount > 0) {
            tracker.increment(uuid, key, amount);
        }
    }

    /** The player left, so their flags and waiting counts go. */
    public void forget(UUID uuid) {
        if (uuid != null) {
            states.remove(uuid);
        }
    }

    /** Players with movement state in memory, for tests and diagnostics. */
    public int tracked() {
        return states.size();
    }
}
