package dev.ryvrook.feats.track;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's running count of kills since they last died. Kept in memory only, like the anti farming
 * memory, so it survives a relog and is dropped by the plugin once the player has been away for an hour.
 * The best streak itself is the saved {@code kill_streak} counter.
 */
public final class KillStreaks {
    private final Map<UUID, int[]> running = new ConcurrentHashMap<>();

    /** Counts one kill and returns the streak it makes. */
    public int kill(UUID player) {
        if (player == null) {
            return 0;
        }
        int[] streak = running.computeIfAbsent(player, id -> new int[1]);
        synchronized (streak) {
            if (streak[0] < Integer.MAX_VALUE) {
                streak[0]++;
            }
            return streak[0];
        }
    }

    /** Ends the streak, the next kill starts again at one. */
    public void died(UUID player) {
        if (player != null) {
            running.remove(player);
        }
    }

    /** The running streak, zero when the player has none. */
    public int current(UUID player) {
        int[] streak = player == null ? null : running.get(player);
        if (streak == null) {
            return 0;
        }
        synchronized (streak) {
            return streak[0];
        }
    }

    public void forget(UUID player) {
        died(player);
    }

    /** Players with a running streak, for tests and diagnostics. */
    public int size() {
        return running.size();
    }
}
