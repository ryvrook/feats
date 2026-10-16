package dev.ryvrook.feats.track;

import com.hypixel.hytale.builtin.adventure.memories.component.PlayerMemories;
import com.hypixel.hytale.builtin.adventure.memories.memories.Memory;
import com.hypixel.hytale.builtin.adventure.memories.memories.npc.NPCMemory;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.role.Role;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Watches the Memories a player has recorded. The recorded set empties when the player hands their
 * Memories in, so every Memory id is remembered as a one time discovery and the total only grows.
 * A Memory seen for the first time whose NPC role only unlocks it by a kill also counts towards
 * {@code memory_by_kill}. The component is read on the world thread, see {@link #read}. If the Memories
 * classes ever change shape the tracker switches itself off with one warning.
 */
public final class MemoryTracker {
    private final FeatsTracker tracker;
    private final HytaleLogger logger;
    private static final long QUIET_NANOS = TimeUnit.MINUTES.toNanos(1);

    private volatile boolean disabled;
    private final AtomicLong lastSkipLog = new AtomicLong(System.nanoTime() - QUIET_NANOS);

    public MemoryTracker(FeatsTracker tracker, HytaleLogger logger) {
        this.tracker = tracker;
        this.logger = logger;
    }

    /**
     * Reads the player's recorded Memories right here. Call it on the world thread that owns the player.
     * The once a second pass in {@link PlayerSampler} does so for every player of a world in one task.
     */
    public void read(UUID uuid, Ref<EntityStore> ref) {
        if (disabled || ref == null || !ref.isValid()) {
            return;
        }
        try {
            Store<EntityStore> store = ref.getStore();
            PlayerMemories memories = store.getComponent(ref, PlayerMemories.getComponentType());
            if (memories == null) {
                return;
            }
            PlayerProgress progress = tracker.loadedProgress(uuid);
            for (Memory memory : memories.getRecordedMemories()) {
                if (memory == null) {
                    continue;
                }
                String id = memory.getId();
                boolean fresh = progress != null && id != null && progress.get(StatKeys.MEMORY_PREFIX + id) == 0;
                tracker.discover(uuid, StatKeys.MEMORY_PREFIX, id, StatKeys.MEMORIES);
                if (fresh && progress.get(StatKeys.MEMORY_PREFIX + id) > 0 && byKill(memory)) {
                    tracker.increment(uuid, StatKeys.MEMORY_BY_KILL, 1);
                }
            }
        } catch (LinkageError e) {
            disable(e); // the API this tracker reads is missing on this server
        } catch (RuntimeException e) {
            skip(e); // one player or one world misbehaved, carry on with the rest
        }
    }

    /** True for an NPC Memory that only a kill unlocks, false for proximity Memories and other kinds. */
    private static boolean byKill(Memory memory) {
        if (!(memory instanceof NPCMemory npc)) {
            return false;
        }
        Role.MemoriesUnlockMethod method = npc.getUnlockedBy();
        return method != null && "Kill".equals(method.name());
    }

    /**
     * A single failed read, for example a world that stopped taking tasks while it closed. Skips this
     * player this time and logs at most once a minute at FINE.
     */
    private void skip(RuntimeException cause) {
        long now = System.nanoTime();
        long last = lastSkipLog.get();
        if (now - last >= QUIET_NANOS && lastSkipLog.compareAndSet(last, now)) {
            logger.at(Level.FINE).withCause(cause).log("Feats skipped a Memories read");
        }
    }

    private void disable(Throwable cause) {
        if (!disabled) {
            disabled = true;
            logger.at(Level.WARNING).withCause(cause).log("Feats Memories tracking is not supported on this server and has been turned off");
        }
    }
}
