package dev.ryvrook.feats.track;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When each online player's session began, for the longest session record. A session runs from the
 * first PlayerReady of a connection to the disconnect. World changes inside it do not restart it, and
 * neither does a Feats reload, which picks the start time back up from the player's file, see
 * {@link #resume}.
 */
public final class SessionClock {
    private final Map<UUID, Long> startedAt = new ConcurrentHashMap<>();

    /** Starts a session at {@code nowMillis} unless one is already running for the player. */
    public void start(UUID player, long nowMillis) {
        if (player != null) {
            startedAt.putIfAbsent(player, nowMillis);
        }
    }

    /**
     * Picks a session back up after a Feats reload for a player who stayed connected. Uses the start time
     * saved in their file when there is a sensible one, otherwise starts now. Returns the start in use.
     */
    public long resume(UUID player, long savedStartMillis, long nowMillis) {
        long start = savedStartMillis > 0 && savedStartMillis <= nowMillis ? savedStartMillis : nowMillis;
        if (player == null) {
            return start;
        }
        Long running = startedAt.putIfAbsent(player, start);
        return running != null ? running : start;
    }

    /** Whole seconds since the session began, or zero when the player has none running. */
    public long seconds(UUID player, long nowMillis) {
        Long start = player == null ? null : startedAt.get(player);
        return start == null ? 0L : Math.max(0L, (nowMillis - start) / 1000L);
    }

    /** Ends the session and returns its length in whole seconds, zero when none was running. */
    public long end(UUID player, long nowMillis) {
        Long start = player == null ? null : startedAt.remove(player);
        return start == null ? 0L : Math.max(0L, (nowMillis - start) / 1000L);
    }

    public boolean isRunning(UUID player) {
        return player != null && startedAt.containsKey(player);
    }
}
