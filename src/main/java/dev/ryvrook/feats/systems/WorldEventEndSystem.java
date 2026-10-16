package dev.ryvrook.feats.systems;

import com.hypixel.hytale.builtin.adventure.worldevents.component.EventEndEcsEvent;
import com.hypixel.hytale.builtin.adventure.worldevents.context.LocationContext;
import com.hypixel.hytale.builtin.adventure.worldevents.event.WorldEvent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.FeatsTracker;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * World events that ended in success, such as a Goblin Breach or a Hunt. The engine fires
 * {@link EventEndEcsEvent} on the entity that carries the {@link WorldEvent} component, so this system
 * queries that component. The players credited are those within {@link #CREDIT_RADIUS} metres of any of
 * the event's locations, taken from its {@link LocationContext} entries and the entity's own transform.
 * An event with no known location credits everyone in its world, since such events are world wide.
 */
public final class WorldEventEndSystem extends EntityEventSystem<EntityStore, EventEndEcsEvent> {
    public static final double CREDIT_RADIUS = 64.0;

    private final FeatsTracker tracker;

    public WorldEventEndSystem(FeatsTracker tracker) {
        super(EventEndEcsEvent.class);
        this.tracker = tracker;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return WorldEvent.getComponentType();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, EventEndEcsEvent event) {
        try {
            track(index, chunk, store, event);
        } catch (RuntimeException | LinkageError e) {
            PlayerEventSystem.failed(e);
        }
    }

    private void track(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, EventEndEcsEvent event) {
        if (event.getReason() != EventEndEcsEvent.Reason.SUCCESS) {
            return;
        }
        WorldEvent worldEvent = event.getWorldEvent();
        String id = worldEvent != null ? worldEvent.asset : null;
        if (id == null || id.isBlank()) {
            return;
        }
        List<Vector3d> places = new ArrayList<>(2);
        if (worldEvent.context != null) {
            worldEvent.context.forEach(LocationContext.class, places, null, (loc, out, unused) -> {
                Vector3i at = loc != null ? loc.location() : null;
                if (at != null) {
                    out.add(new Vector3d(at.x, at.y, at.z));
                }
            });
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        TransformComponent transform = ref != null ? store.getComponent(ref, TransformComponent.getComponentType()) : null;
        Vector3d own = transform != null ? transform.getPosition() : null;
        if (own != null) {
            places.add(new Vector3d(own));
        }
        EntityStore entities = store.getExternalData();
        World world = entities != null ? entities.getWorld() : null;
        if (world == null) {
            return;
        }
        for (PlayerRef player : world.getPlayerRefs()) {
            UUID uuid = player != null ? player.getUuid() : null;
            if (uuid == null || !tracker.isLoaded(uuid)) {
                continue;
            }
            if (places.isEmpty() || near(player, places)) {
                tracker.increment(uuid, StatKeys.WORLD_EVENT_PREFIX + id, 1);
                tracker.increment(uuid, StatKeys.WORLD_EVENTS_WON, 1);
            }
        }
    }

    private static boolean near(PlayerRef player, List<Vector3d> places) {
        Transform transform = player.getTransform();
        Vector3d pos = transform != null ? transform.getPosition() : null;
        if (pos == null) {
            return false;
        }
        double limit = CREDIT_RADIUS * CREDIT_RADIUS;
        for (Vector3d place : places) {
            double dx = pos.x - place.x;
            double dy = pos.y - place.y;
            double dz = pos.z - place.z;
            if (dx * dx + dy * dy + dz * dz <= limit) {
                return true;
            }
        }
        return false;
    }
}
