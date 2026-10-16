package dev.ryvrook.feats.track;

import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is online together, fed once a second from the tick with everyone loaded. Players met go into
 * the player file through {@link PlayerProgress#meet}, which caps the set, and the count is mirrored
 * into {@code players_met}. Time with at least one other player online is counted in seconds here and
 * handed to {@code coop_hours} one hour at a time, so the partial hour of a session is not kept. The
 * most players online at once while the player was goes to {@code peak_with_me}.
 *
 * <p>Pairs are only walked when someone new arrives, so a full server costs one set compare a second.
 */
public final class Presence {
    public static final int SECONDS_PER_HOUR = 3600;

    private final FeatsTracker tracker;
    /** Everyone seen online at the last tick. Only the tick thread touches it. */
    private Set<UUID> lastOnline = Set.of();
    private final Map<UUID, Integer> coopSeconds = new ConcurrentHashMap<>();

    public Presence(FeatsTracker tracker) {
        this.tracker = tracker;
    }

    /** One second passed with these players online. */
    public void tick(List<UUID> online) {
        int count = online.size();
        List<UUID> newcomers = null;
        for (int i = 0; i < count; i++) {
            UUID uuid = online.get(i);
            if (uuid == null) {
                continue;
            }
            tracker.raiseTo(uuid, StatKeys.PEAK_WITH_ME, count);
            if (count >= 2) {
                int seconds = coopSeconds.merge(uuid, 1, Integer::sum);
                if (seconds % SECONDS_PER_HOUR == 0) {
                    tracker.increment(uuid, StatKeys.COOP_HOURS, 1);
                }
            }
            if (!lastOnline.contains(uuid)) {
                if (newcomers == null) {
                    newcomers = new ArrayList<>(4);
                }
                newcomers.add(uuid);
            }
        }
        if (newcomers != null && count >= 2) {
            for (UUID fresh : newcomers) {
                for (int i = 0; i < count; i++) {
                    UUID other = online.get(i);
                    if (other == null || other.equals(fresh)) {
                        continue;
                    }
                    meet(fresh, other);
                    meet(other, fresh);
                }
            }
        }
        lastOnline = count == 0 ? Set.of() : new HashSet<>(online);
    }

    private void meet(UUID who, UUID other) {
        PlayerProgress progress = tracker.loadedProgress(who);
        if (progress != null && progress.meet(other)) {
            tracker.raiseTo(who, StatKeys.PLAYERS_MET, progress.metCount());
        }
    }

    /** Seconds of the current hour spent with company, for tests and the stats line. */
    public int coopSeconds(UUID player) {
        Integer s = player == null ? null : coopSeconds.get(player);
        return s == null ? 0 : s;
    }

    /** The player left. The partial hour is dropped with them. */
    public void forget(UUID player) {
        if (player != null) {
            coopSeconds.remove(player);
        }
    }
}
