package dev.ryvrook.feats.track;

import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.Scope;
import dev.ryvrook.feats.model.Tier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The rules behind the top of screen banner, with no engine calls so a unit test can drive it. Two
 * questions are answered here. Whether an unlock qualifies for a banner at all, from the {@code Banner}
 * setting, the feat's tier, scope and flag and how rare the feat is. And whether a qualifying banner may
 * be shown to a player right now, from the player's recent banners.
 *
 * <p>Timing rules per player. At most {@link #MAX_IN_ROW} banners show within {@link #WINDOW_MILLIS},
 * the rest are dropped, the chat line already covers them. A banner that arrives while one is up replaces
 * it on screen, and the removal moves out to a full stay from now rather than keeping the earlier time.
 * Each show hands out a generation number, and a removal only takes effect when its generation is still
 * the one on screen, so the earlier removal timer becomes a no op.
 *
 * <p>Every time is a millisecond clock the caller passes in.
 */
public final class BannerQueue {
    /** How many banners a player sees in a row before the rest are dropped. */
    public static final int MAX_IN_ROW = 3;
    /** The window the row is counted over. */
    public static final long WINDOW_MILLIS = 60_000L;

    /** One banner that was granted. The removal is due at {@code removeAt} unless a later show replaces it. */
    public record Shown(int generation, long removeAt) {
    }

    private static final class State {
        final Deque<Long> recent = new ArrayDeque<>();
        int generation;
        long removeAt;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    // ---- qualifying -----------------------------------------------------------------------------------

    /**
     * Why the unlock goes on the banner, or null when it does not.
     *
     * @param setting          the {@code Banner} setting, OFF, PLATINUM, RARE or ALL
     * @param feat             the feat that unlocked
     * @param rarityFraction   the fraction of players owning the feat, 0 to 1, or -1 when unknown
     * @param rareBelowPercent feats owned by under this percent of players count as rare
     */
    public static String reason(String setting, Feat feat, double rarityFraction, int rareBelowPercent) {
        if (feat == null || setting == null) {
            return null;
        }
        String mode = setting.trim().toUpperCase(java.util.Locale.ROOT);
        if (mode.equals("OFF")) {
            return null;
        }
        if (!mode.equals("PLATINUM") && !mode.equals("RARE") && !mode.equals("ALL")) {
            mode = "RARE";
        }
        if (feat.scope() == Scope.FIRST) {
            return "Server first";
        }
        if (feat.scope() == Scope.WORLD) {
            return "Server milestone";
        }
        if (feat.tier() == Tier.PLATINUM) {
            return "Platinum";
        }
        if (mode.equals("PLATINUM")) {
            return null;
        }
        String rare = rareLine(rarityFraction, rareBelowPercent);
        if (rare != null) {
            return rare;
        }
        if (feat.banner() || mode.equals("ALL")) {
            return feat.tier().displayName();
        }
        return null;
    }

    /** The why line for a milestone or first shown to the rest of the server, with the holder's name. */
    public static String broadcastReason(Feat feat, String byName) {
        String head = feat != null && feat.scope() == Scope.FIRST ? "Server first" : "Server milestone";
        return byName == null || byName.isBlank() ? head : head + ", by " + byName.trim();
    }

    /** "Rare, under 1% of players" or "Rare, 3% of players", or null when not rare or unknown. */
    static String rareLine(double fraction, int rareBelowPercent) {
        if (fraction < 0 || rareBelowPercent <= 0) {
            return null;
        }
        double percent = fraction * 100.0;
        if (percent >= rareBelowPercent) {
            return null;
        }
        String shown = percent < 1.0 ? "under 1%" : Math.round(percent) + "%";
        return "Rare, " + shown + " of players";
    }

    // ---- timing ---------------------------------------------------------------------------------------

    /**
     * Asks to show a banner to the player now. Returns the granted show, or null when the player already
     * saw {@link #MAX_IN_ROW} banners in the last minute.
     *
     * @param uuid       the player
     * @param now        the clock in milliseconds
     * @param stayMillis how long the banner stays on screen from now
     */
    public Shown offer(UUID uuid, long now, long stayMillis) {
        if (uuid == null) {
            return null;
        }
        State st = states.computeIfAbsent(uuid, k -> new State());
        synchronized (st) {
            while (!st.recent.isEmpty() && st.recent.peekFirst() <= now - WINDOW_MILLIS) {
                st.recent.pollFirst();
            }
            if (st.recent.size() >= MAX_IN_ROW) {
                return null;
            }
            st.recent.addLast(now);
            st.generation++;
            // A banner that is already up is replaced, and the new one gets a full stay from now.
            st.removeAt = Math.max(st.removeAt, now + Math.max(0, stayMillis));
            return new Shown(st.generation, st.removeAt);
        }
    }

    /**
     * A removal timer firing. True when the banner from that show is still the one on screen, in which
     * case the player is marked as having nothing up and the caller removes the HUD. False when a later
     * show replaced it, that show has its own timer due at its own time.
     */
    public boolean expire(UUID uuid, int generation) {
        State st = uuid == null ? null : states.get(uuid);
        if (st == null) {
            return false;
        }
        synchronized (st) {
            if (st.generation != generation || st.removeAt == 0) {
                return false; // replaced by a later show, or already taken down
            }
            st.removeAt = 0;
            return true;
        }
    }

    /** True while a banner is on screen for the player. */
    public boolean isUp(UUID uuid, long now) {
        State st = uuid == null ? null : states.get(uuid);
        if (st == null) {
            return false;
        }
        synchronized (st) {
            return now < st.removeAt;
        }
    }

    /** The player left, forget their row. */
    public void clear(UUID uuid) {
        if (uuid != null) {
            states.remove(uuid);
        }
    }
}
