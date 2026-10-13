package dev.ryvrook.feats.track;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Remembers which players hit a boss recently, so a group fight credits everyone who took part and
 * not only the player who landed the killing blow.
 *
 * <p>Bosses are keyed by the entity's UUID component. Multi phase bosses such as the Gobliterator swap
 * roles by removing the entity and adding it back, which hands out a new {@code Ref} but keeps the
 * UUID, so one fight stays one entry across every phase.
 *
 * <p>Only UUIDs and timestamps are kept, never refs, so a closed world is never held in memory. The map
 * is bounded. Every new boss prunes fights that have gone quiet, and past {@link #MAX_BOSSES} the fight
 * hit least recently is dropped. Each fight keeps at most {@link #MAX_PLAYERS} players.
 *
 * <p>Callers use it from the world thread that runs the fight. Several worlds tick on their own threads
 * and share one ledger, so every method is synchronized.
 */
public final class BossLedger {
    /** A hit older than this does not count towards the kill. */
    public static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(60);
    static final int MAX_BOSSES = 64;
    static final int MAX_PLAYERS = 32;

    private static final Map<FeatsTracker, BossLedger> SHARED = new WeakHashMap<>();

    /** The ledger shared by the damage and death systems of one plugin instance. */
    public static BossLedger of(FeatsTracker tracker) {
        synchronized (SHARED) {
            return SHARED.computeIfAbsent(tracker, t -> new BossLedger());
        }
    }

    /** One boss fight. Players in access order, so the eldest is the one who hit least recently. */
    private static final class Fight {
        private final LinkedHashMap<UUID, Long> hits = new LinkedHashMap<>(8, 0.75f, true);
        private long lastHit;
    }

    private final LinkedHashMap<UUID, Fight> fights = new LinkedHashMap<>(16, 0.75f, true);

    /** Notes that a player hit a boss at {@code now} (from {@link System#nanoTime()}). */
    public synchronized void record(UUID boss, UUID player, long now) {
        if (boss == null || player == null) {
            return;
        }
        Fight fight = fights.get(boss);
        if (fight == null) {
            prune(now);
            if (fights.size() >= MAX_BOSSES) {
                Iterator<UUID> eldest = fights.keySet().iterator();
                eldest.next();
                eldest.remove();
            }
            fight = new Fight();
            fights.put(boss, fight);
        }
        fight.lastHit = now;
        fight.hits.put(player, now);
        if (fight.hits.size() > MAX_PLAYERS) {
            Iterator<UUID> eldest = fight.hits.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
    }

    /** Removes a boss from the ledger and returns every player who hit it within the window. */
    public synchronized List<UUID> take(UUID boss, long now) {
        if (boss == null) {
            return List.of();
        }
        Fight fight = fights.remove(boss);
        if (fight == null) {
            return List.of();
        }
        List<UUID> players = new ArrayList<>(fight.hits.size());
        for (Map.Entry<UUID, Long> e : fight.hits.entrySet()) {
            if (now - e.getValue() <= WINDOW_NANOS) {
                players.add(e.getKey());
            }
        }
        return players;
    }

    /** Fights currently held, for tests and diagnostics. */
    public synchronized int size() {
        return fights.size();
    }

    private void prune(long now) {
        fights.values().removeIf(fight -> now - fight.lastHit > WINDOW_NANOS);
    }
}
