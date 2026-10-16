package dev.ryvrook.feats.track;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's running count of kills since they last died, and alongside it their running count of
 * player kills. Kept in memory only, like the anti farming memory, so it survives a relog and is dropped
 * by the plugin once the player has been away for an hour. The best streaks themselves are the saved
 * {@code kill_streak} and {@code player_kill_streak} counters.
 */
public final class KillStreaks {
    private static final int ALL = 0;
    private static final int PLAYERS = 1;

    private final Map<UUID, int[]> running = new ConcurrentHashMap<>();

    /** Counts one kill of anything and returns the streak it makes. */
    public int kill(UUID player) {
        return bump(player, ALL);
    }

    /** Counts one player kill and returns the player kill streak it makes. Call {@link #kill} as well. */
    public int playerKill(UUID player) {
        return bump(player, PLAYERS);
    }

    private int bump(UUID player, int which) {
        if (player == null) {
            return 0;
        }
        int[] streak = running.computeIfAbsent(player, id -> new int[2]);
        synchronized (streak) {
            if (streak[which] < Integer.MAX_VALUE) {
                streak[which]++;
            }
            return streak[which];
        }
    }

    /** Ends both streaks, the next kill starts again at one. */
    public void died(UUID player) {
        if (player != null) {
            running.remove(player);
        }
    }

    /** The running streak, zero when the player has none. */
    public int current(UUID player) {
        return read(player, ALL);
    }

    /** The running player kill streak, zero when the player has none. */
    public int currentPlayerKills(UUID player) {
        return read(player, PLAYERS);
    }

    private int read(UUID player, int which) {
        int[] streak = player == null ? null : running.get(player);
        if (streak == null) {
            return 0;
        }
        synchronized (streak) {
            return streak[which];
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
