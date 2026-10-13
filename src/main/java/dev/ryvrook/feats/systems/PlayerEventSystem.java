package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/** Base class for ECS event systems that only care about player entities. */
abstract class PlayerEventSystem<E extends EcsEvent> extends EntityEventSystem<EntityStore, E> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long QUIET_NANOS = TimeUnit.MINUTES.toNanos(1);
    private static final AtomicLong lastFailureLog = new AtomicLong(System.nanoTime() - QUIET_NANOS);

    protected final FeatsTracker tracker;

    PlayerEventSystem(Class<E> eventType, FeatsTracker tracker) {
        super(eventType);
        this.tracker = tracker;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer, E event) {
        try {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            handlePlayer(playerRef.getUuid(), ref, store, event);
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    /**
     * Tracking runs inside the world tick, so a failure is logged and swallowed instead of reaching
     * the engine. Logged at most once a minute so a broken hook cannot flood the console.
     */
    static void failed(Throwable cause) {
        long now = System.nanoTime();
        long last = lastFailureLog.get();
        if (now - last >= QUIET_NANOS && lastFailureLog.compareAndSet(last, now)) {
            LOGGER.at(Level.WARNING).withCause(cause).log("Feats skipped a tracked event after an error");
        }
    }

    protected abstract void handlePlayer(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, E event);

    /** Name of the world this store belongs to, or an empty string when it cannot be read. */
    protected static String worldName(Store<EntityStore> store) {
        try {
            EntityStore entities = store.getExternalData();
            World world = entities != null ? entities.getWorld() : null;
            String name = world != null ? world.getName() : null;
            return name != null ? name : "";
        } catch (RuntimeException e) {
            return "";
        }
    }
}
