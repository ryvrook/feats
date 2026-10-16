package dev.ryvrook.feats.systems;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where each player was at the last sample as far as portal worlds go. A sample inside a timed portal
 * world keeps the seconds it had left. The next sample elsewhere, within {@link #RECENT_MILLIS}, with
 * under a minute left on record, is an exit with the clock nearly out. A disconnect is not an exit.
 */
public final class WorldPortals {
    /** Leaving with less than this many seconds left is a close exit. */
    public static final double CLOSE_SECONDS = 60.0;
    /** How recent the last inside sample has to be for the next sample elsewhere to count as the exit. */
    public static final long RECENT_MILLIS = 5_000L;

    private record Stay(String world, boolean timed, double remaining, long at) {
    }

    private final Map<UUID, Stay> stays = new ConcurrentHashMap<>();

    /** A sample inside a portal world. */
    public void inside(UUID player, String world, boolean timed, double remainingSeconds, long nowMillis) {
        if (player != null) {
            stays.put(player, new Stay(world, timed, remainingSeconds, nowMillis));
        }
    }

    /**
     * A sample in a world that is not a portal world, or a different one. True when the player just
     * left a timed portal world with under a minute on the clock.
     */
    public boolean elsewhere(UUID player, String world, long nowMillis) {
        Stay stay = player == null ? null : stays.remove(player);
        if (stay == null) {
            return false;
        }
        if (stay.world() != null && stay.world().equals(world)) {
            return false; // still there, the caller misjudged the world
        }
        return stay.timed() && stay.remaining() > 0 && stay.remaining() < CLOSE_SECONDS
                && nowMillis - stay.at() <= RECENT_MILLIS;
    }

    /** The player disconnected. */
    public void forget(UUID player) {
        if (player != null) {
            stays.remove(player);
        }
    }
}
