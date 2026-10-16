package dev.ryvrook.feats.systems;

import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.LastKillers;
import dev.ryvrook.feats.track.Windows;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

/**
 * In memory state shared by the combat systems of one plugin instance. Holds the multikill
 * {@link Windows}, the {@link LastKillers} record and three short lived notes per player.
 *
 * <ul>
 * <li>A fall the player just took. The engine applies damage and adds the death component from the same
 * event, in an order this plugin does not control, so whether the player survived is settled a moment
 * later. A fall still on record {@link #SETTLE_MILLIS} after the hit, with no death in between, was
 * survived and raises {@code fall_survived_max}.</li>
 * <li>A drowning episode. Drowning damage ticks about once a second while the player is out of air, so
 * one episode ends {@link #EPISODE_GAP_MILLIS} after its last tick and then counts one
 * {@code drowning_survived}, again only when no death cleared it.</li>
 * <li>The distance of the player's last projectile hit, shooter to impact, read while the projectile was
 * still there. The death system asks for it when a projectile kill arrives and the projectile is gone.</li>
 * </ul>
 *
 * <p>{@link #flush} runs on the plugin's 1 s tick, the rest on world threads, so the notes are kept under
 * one lock. Bounded at {@link #MAX_PLAYERS} notes, the least recently touched one is dropped past that.
 */
public final class CombatMemory {
    /** A fall with no death this long after it was survived. */
    public static final long SETTLE_MILLIS = 1_000L;
    /** A drowning episode ends when no drowning damage arrived for this long. */
    public static final long EPISODE_GAP_MILLIS = 3_000L;
    /** A remembered projectile hit older than this is not the killing blow. */
    static final long RANGE_NANOS = TimeUnit.SECONDS.toNanos(2);
    static final int MAX_PLAYERS = 1024;

    private static final Map<FeatsTracker, CombatMemory> SHARED = new WeakHashMap<>();

    /** The memory shared by every combat system of one plugin instance. */
    public static CombatMemory of(FeatsTracker tracker) {
        synchronized (SHARED) {
            return SHARED.computeIfAbsent(tracker, CombatMemory::new);
        }
    }

    /** One player's notes. A value of zero or below means nothing is on record for that note. */
    private static final class Note {
        private long fallAmount;
        private long fallAt;
        private long drowningFirst;
        private long drowningLast;
        private double rangeMetres = -1;
        private long rangeAt;
    }

    private final FeatsTracker tracker;
    private final Windows windows = new Windows();
    private final LastKillers lastKillers = new LastKillers();
    private final LinkedHashMap<UUID, Note> notes = new LinkedHashMap<>(16, 0.75f, true);

    public CombatMemory(FeatsTracker tracker) {
        this.tracker = tracker;
    }

    public Windows windows() {
        return windows;
    }

    public LastKillers lastKillers() {
        return lastKillers;
    }

    /** A player took fall damage. Several falls before the first settles keep the largest. */
    public synchronized void fall(UUID player, long amount, long nowMillis) {
        if (player == null || amount <= 0) {
            return;
        }
        Note note = note(player);
        note.fallAmount = Math.max(note.fallAmount, amount);
        note.fallAt = nowMillis;
    }

    /** A player took drowning damage. */
    public synchronized void drowning(UUID player, long nowMillis) {
        if (player == null) {
            return;
        }
        Note note = note(player);
        if (note.drowningFirst <= 0) {
            note.drowningFirst = nowMillis;
        }
        note.drowningLast = nowMillis;
    }

    /** A player's projectile hit something {@code metres} from them, at {@code nowNanos} from {@link System#nanoTime()}. */
    public synchronized void rangedHit(UUID player, double metres, long nowNanos) {
        if (player == null || metres < 0) {
            return;
        }
        Note note = note(player);
        note.rangeMetres = metres;
        note.rangeAt = nowNanos;
    }

    /** The distance of the player's last projectile hit if it was within the last two seconds, or -1. */
    public synchronized double rangedDistance(UUID player, long nowNanos) {
        Note note = player == null ? null : notes.get(player);
        if (note == null || note.rangeMetres < 0 || nowNanos - note.rangeAt > RANGE_NANOS) {
            return -1;
        }
        return note.rangeMetres;
    }

    /** A player died. Whatever fall or drowning was pending was not survived. */
    public synchronized void died(UUID player) {
        Note note = player == null ? null : notes.get(player);
        if (note != null) {
            note.fallAmount = 0;
            note.fallAt = 0;
            note.drowningFirst = 0;
            note.drowningLast = 0;
        }
    }

    /** Drops every note of a player, on leave. The last killer record stays. */
    public synchronized void forget(UUID player) {
        if (player != null) {
            notes.remove(player);
        }
        windows.forget(player);
    }

    /** Settles the falls and drowning episodes that are old enough. Once a second, any thread. */
    public void flush(long nowMillis) {
        synchronized (this) {
            if (notes.isEmpty()) {
                return;
            }
        }
        // Collect first and count after, so the tracker's unlock work never runs under this lock.
        UUID[] fallWho = null;
        long[] fallHow = null;
        UUID[] drownWho = null;
        int falls = 0;
        int drownings = 0;
        synchronized (this) {
            for (Map.Entry<UUID, Note> e : notes.entrySet()) {
                Note note = e.getValue();
                if (note.fallAmount > 0 && nowMillis - note.fallAt >= SETTLE_MILLIS) {
                    if (fallWho == null) {
                        fallWho = new UUID[notes.size()];
                        fallHow = new long[notes.size()];
                    }
                    fallWho[falls] = e.getKey();
                    fallHow[falls++] = note.fallAmount;
                    note.fallAmount = 0;
                    note.fallAt = 0;
                }
                if (note.drowningFirst > 0 && nowMillis - note.drowningLast >= EPISODE_GAP_MILLIS) {
                    if (drownWho == null) {
                        drownWho = new UUID[notes.size()];
                    }
                    drownWho[drownings++] = e.getKey();
                    note.drowningFirst = 0;
                    note.drowningLast = 0;
                }
            }
        }
        for (int i = 0; i < falls; i++) {
            tracker.raiseTo(fallWho[i], StatKeys.FALL_SURVIVED_MAX, fallHow[i]);
        }
        for (int i = 0; i < drownings; i++) {
            tracker.increment(drownWho[i], StatKeys.DROWNING_SURVIVED, 1);
        }
    }

    /** Notes held, for tests and diagnostics. */
    public synchronized int size() {
        return notes.size();
    }

    private Note note(UUID player) {
        Note note = notes.get(player);
        if (note == null) {
            note = new Note();
            notes.put(player, note);
            if (notes.size() > MAX_PLAYERS) {
                Iterator<UUID> eldest = notes.keySet().iterator();
                eldest.next();
                eldest.remove();
            }
        }
        return note;
    }
}
