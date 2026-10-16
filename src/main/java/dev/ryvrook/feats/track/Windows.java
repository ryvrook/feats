package dev.ryvrook.feats.track;

import dev.ryvrook.feats.stats.StatKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kill timestamps of the last few seconds per player, for the multikill counters. Three windows are
 * watched at once, two kills in 3 s, three in 5 s and ten in 15 s. A window pays out once each time it
 * fills, and pays again only after it has emptied below its count. A burst of four kills in three seconds
 * therefore counts one {@code multikill_2} and one {@code multikill_3}, not one per kill.
 *
 * <p>Only the last ten timestamps are kept per player, which is exactly what the widest window needs.
 * Kept in memory only, like {@link KillStreaks}, and dropped when the player leaves.
 */
public final class Windows {
    /** Bit returned by {@link #kill} when the two kills in 3 s window filled. */
    public static final int DOUBLE = 1;
    /** Bit returned by {@link #kill} when the three kills in 5 s window filled. */
    public static final int TRIPLE = 2;
    /** Bit returned by {@link #kill} when the ten kills in 15 s window filled. */
    public static final int TEN = 4;

    private static final long[] SPAN_MILLIS = {3_000L, 5_000L, 15_000L};
    private static final int[] NEEDED = {2, 3, 10};
    private static final String[] KEYS = {StatKeys.MULTIKILL_2, StatKeys.MULTIKILL_3, StatKeys.MULTIKILL_10};
    /** Timestamps kept per player, the widest window's count. */
    static final int KEEP = 10;

    /** The counter keys for every combination of bits, built once so a kill allocates nothing. */
    private static final List<List<String>> LISTS = buildLists();

    private static List<List<String>> buildLists() {
        List<List<String>> lists = new ArrayList<>(8);
        for (int mask = 0; mask < 8; mask++) {
            List<String> keys = new ArrayList<>(3);
            for (int i = 0; i < KEYS.length; i++) {
                if ((mask & (1 << i)) != 0) {
                    keys.add(KEYS[i]);
                }
            }
            lists.add(List.copyOf(keys));
        }
        return List.copyOf(lists);
    }

    /** One player's recent kills, a ring of timestamps plus which windows are full right now. */
    private static final class Burst {
        private final long[] times = new long[KEEP];
        private int count;
        private int next;
        private int full;
    }

    private final Map<UUID, Burst> bursts = new ConcurrentHashMap<>();

    /**
     * Counts one creature kill at {@code nowMillis} and returns the windows that filled with it, as
     * {@link #DOUBLE}, {@link #TRIPLE} and {@link #TEN} bits, or zero when none did.
     */
    public int kill(UUID player, long nowMillis) {
        if (player == null) {
            return 0;
        }
        Burst burst = bursts.computeIfAbsent(player, id -> new Burst());
        synchronized (burst) {
            burst.times[burst.next] = nowMillis;
            burst.next = (burst.next + 1) % KEEP;
            if (burst.count < KEEP) {
                burst.count++;
            }
            int awarded = 0;
            int fullNow = 0;
            for (int w = 0; w < SPAN_MILLIS.length; w++) {
                int inside = 0;
                for (int i = 0; i < burst.count; i++) {
                    long age = nowMillis - burst.times[i];
                    if (age >= 0 && age <= SPAN_MILLIS[w]) {
                        inside++;
                    }
                }
                int bit = 1 << w;
                if (inside >= NEEDED[w]) {
                    fullNow |= bit;
                    if ((burst.full & bit) == 0) {
                        awarded |= bit;
                    }
                }
            }
            burst.full = fullNow;
            return awarded;
        }
    }

    /** The counter keys for the bits {@link #kill} returned, an immutable shared list. */
    public static List<String> keys(int awarded) {
        return LISTS.get(awarded & 7);
    }

    /** Drops the player's recent kills, on leave. */
    public void forget(UUID player) {
        if (player != null) {
            bursts.remove(player);
        }
    }

    /** Players with recent kills on record, for tests and diagnostics. */
    public int size() {
        return bursts.size();
    }
}
