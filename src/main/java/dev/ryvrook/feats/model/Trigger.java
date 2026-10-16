package dev.ryvrook.feats.model;

/**
 * What raises a world or first feat, which have no counters of their own.
 *
 * @param kind     a game flag reaching a level, a personal feat unlocked by anyone, or a server total reaching a target
 * @param key      the game flag name for {@link Kind#FLAG}, the personal feat id for {@link Kind#AFTER}, the
 *                 server total key for {@link Kind#TOTAL}
 * @param minLevel the lowest flag level that counts, unused for the other kinds
 * @param target   the server total that counts for {@link Kind#TOTAL}, unused for the other kinds
 */
public record Trigger(Kind kind, String key, int minLevel, long target) {
    public enum Kind {
        FLAG,
        AFTER,
        TOTAL
    }

    public Trigger {
        if (kind == null || key == null || key.isBlank()) {
            throw new IllegalArgumentException("trigger needs a kind and a key");
        }
        if (kind == Kind.FLAG && minLevel <= 0) {
            throw new IllegalArgumentException("flag trigger level must be positive for " + key);
        }
        if (kind == Kind.TOTAL && target <= 0) {
            throw new IllegalArgumentException("total trigger target must be positive for " + key);
        }
    }

    /** The form from before server totals existed. */
    public Trigger(Kind kind, String key, int minLevel) {
        this(kind, key, minLevel, 0);
    }

    /** Raised once the server wide game flag reaches {@code minLevel}. */
    public static Trigger flag(String gameFlag, int minLevel) {
        return new Trigger(Kind.FLAG, gameFlag, minLevel, 0);
    }

    /** Raised the first time any player unlocks the personal feat {@code featId}. */
    public static Trigger after(String featId) {
        return new Trigger(Kind.AFTER, featId, 0, 0);
    }

    /** Raised once the server total {@code key} in server.json reaches {@code target}. */
    public static Trigger total(String key, long target) {
        return new Trigger(Kind.TOTAL, key, 0, target);
    }
}
