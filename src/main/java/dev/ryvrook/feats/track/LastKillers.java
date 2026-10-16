package dev.ryvrook.feats.track;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Who last killed each player, player kills only, for the vendetta counter. A player who kills the
 * player that last killed them scores a vendetta, and that record is spent, so the next kill of the
 * same player is only a vendetta once they have killed again.
 *
 * <p>Kept in memory only and across relogs, since a grudge should survive a reconnect. Bounded at
 * {@link #MAX} victims, the least recently touched record is dropped past that. Shared by the world
 * threads, so every method is synchronized.
 */
public final class LastKillers {
    static final int MAX = 4096;

    private final LinkedHashMap<UUID, UUID> killers = new LinkedHashMap<>(16, 0.75f, true);

    /**
     * Records that {@code killer} killed {@code victim} and says whether that settled a score, which is
     * true when the victim was the killer's own last killer.
     */
    public synchronized boolean killed(UUID killer, UUID victim) {
        if (killer == null || victim == null || killer.equals(victim)) {
            return false;
        }
        boolean vendetta = victim.equals(killers.get(killer));
        if (vendetta) {
            killers.remove(killer);
        }
        killers.put(victim, killer);
        if (killers.size() > MAX) {
            Iterator<UUID> eldest = killers.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
        return vendetta;
    }

    /** The player who last killed {@code victim}, or null when nobody has, or the score was settled. */
    public synchronized UUID lastKillerOf(UUID victim) {
        return victim == null ? null : killers.get(victim);
    }

    /** Records held, for tests and diagnostics. */
    public synchronized int size() {
        return killers.size();
    }
}
