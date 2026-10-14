package dev.ryvrook.feats.systems;

import dev.ryvrook.feats.track.FeatsTracker;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Remembers, in memory only, whether a player has died during their current run of The Deepworks.
 * Each run is one instance world, told apart by world name.
 *
 * <p>A player is clean in a Deepworks world unless they died there. {@link #entered} starts a fresh,
 * clean run when the player enters a different Deepworks instance. Leaving the instance, respawning
 * elsewhere or logging out keeps the death on record, so coming back to the same instance never wipes
 * it. A new instance has a new name, so it starts clean anyway.
 *
 * <p>Each run also keeps when the player entered that instance, for the fast Gobliterator feat. Coming
 * back to the same instance keeps the first entry time, so stepping out and in does not restart the
 * clock. A run first seen through a death, such as after a plugin reload, has no known entry time and
 * never counts as fast.
 *
 * <p>Holds one small entry per player, capped at {@link #MAX_PLAYERS}, and only world names, never a
 * world. Shared by world threads, so every method is synchronized.
 */
public final class DeepworksRuns {
    /** Portal type id of The Deepworks. */
    public static final String PORTAL_TYPE = "The_Deepworks";
    static final int MAX_PLAYERS = 1024;
    /** A Gobliterator kill within this long of entering the run counts as fast. */
    public static final long FAST_MILLIS = 20L * 60 * 1000;
    /** Entry time of a run that was first seen through a death. */
    private static final long UNKNOWN = -1L;

    private static final Map<FeatsTracker, DeepworksRuns> SHARED = new WeakHashMap<>();

    /** The run tracker shared by every system of one plugin instance. */
    public static DeepworksRuns of(FeatsTracker tracker) {
        synchronized (SHARED) {
            return SHARED.computeIfAbsent(tracker, t -> new DeepworksRuns());
        }
    }

    /**
     * One run. {@code clean} is false once the player died in that world, {@code enteredAt} is the epoch
     * millis of the first entry or {@link #UNKNOWN}.
     */
    private record Run(String world, boolean clean, long enteredAt) {}

    private final LinkedHashMap<UUID, Run> runs = new LinkedHashMap<>(16, 0.75f, true);

    /**
     * A player entered a world. Entering a Deepworks instance other than the one on record starts a
     * clean run there. Any other world, and coming back to the same instance, keeps the record as it is,
     * so a respawn or a relog does not wipe the death that caused it.
     */
    public void entered(UUID player, String portalTypeId, String worldName) {
        entered(player, portalTypeId, worldName, System.currentTimeMillis());
    }

    /** As {@link #entered(UUID, String, String)}, at {@code nowMillis}. */
    public synchronized void entered(UUID player, String portalTypeId, String worldName, long nowMillis) {
        if (player == null || !PORTAL_TYPE.equals(portalTypeId) || worldName == null || worldName.isEmpty()) {
            return;
        }
        Run run = runs.get(player);
        if (run == null || !run.world().equals(worldName)) {
            put(player, new Run(worldName, true, nowMillis));
        }
    }

    /** A player died in the given Deepworks world. */
    public synchronized void died(UUID player, String worldName) {
        if (player == null || worldName == null || worldName.isEmpty()) {
            return;
        }
        Run run = runs.get(player);
        long enteredAt = run != null && run.world().equals(worldName) ? run.enteredAt() : UNKNOWN;
        put(player, new Run(worldName, false, enteredAt));
    }

    /**
     * True when the player entered this Deepworks world no more than {@link #FAST_MILLIS} before
     * {@code nowMillis}. Dying on the way does not matter here.
     */
    public synchronized boolean isFast(UUID player, String worldName, long nowMillis) {
        if (player == null || worldName == null || worldName.isEmpty()) {
            return false;
        }
        Run run = runs.get(player);
        return run != null && run.world().equals(worldName) && run.enteredAt() != UNKNOWN
                && nowMillis - run.enteredAt() <= FAST_MILLIS;
    }

    /** True when the player has not died in this Deepworks world since their run there began. */
    public synchronized boolean isClean(UUID player, String worldName) {
        if (player == null || worldName == null || worldName.isEmpty()) {
            return false;
        }
        Run run = runs.get(player);
        return run == null || !run.world().equals(worldName) || run.clean();
    }

    private void put(UUID player, Run run) {
        runs.put(player, run);
        if (runs.size() > MAX_PLAYERS) {
            Iterator<UUID> eldest = runs.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
    }
}
