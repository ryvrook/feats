package dev.ryvrook.feats.track;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.stats.PlayerProgress;

import java.util.List;
import java.util.UUID;

/**
 * One group of tracking hooks, such as the combat ones. {@link Hooks} calls every set at each point of the
 * plugin's life so the plugin class does not change when a hook is added. Every method has an empty default,
 * and every call is wrapped so one failing set never stops the others.
 */
public interface HookSet {
    /** Setup, only when Feats tracks with its own systems. Register ECS systems and bus listeners here. */
    default void registerOwn(Hooks.Context ctx, ComponentRegistryProxy<EntityStore> ecs, EventRegistry events) {
    }

    /** Setup, on both tracking paths. For hooks TrackerAPI does not carry, such as a poll of persisted data. */
    default void registerAlways(Hooks.Context ctx, ComponentRegistryProxy<EntityStore> ecs, EventRegistry events) {
    }

    /** Start, after the players already online were loaded. */
    default void start(Hooks.Context ctx) {
    }

    /** Shutdown, before the tracker saves. */
    default void stop(Hooks.Context ctx) {
    }

    /** Once a second on the engine scheduler with everyone online. Keep it to memory, no entity reads. */
    default void tick(Hooks.Context ctx, long nowMillis, List<PlayerRef> online) {
    }

    /**
     * Once a second on the world's own thread with the loaded players in it, on both tracking paths.
     * {@code ownPath} is false when TrackerAPI feeds the counters, so hooks it carries should stay quiet.
     */
    default void sampleWorld(Hooks.Context ctx, World world, List<PlayerRef> players, boolean ownPath, long nowMillis) {
    }

    /** The first PlayerReady of a connection, after the file loaded and the catch up ran. World thread. */
    default void joined(Hooks.Context ctx, UUID uuid, PlayerRef player, PlayerProgress progress, long nowMillis) {
    }

    /** Disconnect, before the file is saved and dropped. */
    default void left(Hooks.Context ctx, UUID uuid, long nowMillis) {
    }
}
