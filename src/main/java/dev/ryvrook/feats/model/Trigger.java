package dev.ryvrook.feats.model;

/**
 * What raises a world or first feat, which have no counters of their own.
 *
 * @param kind     a game flag reaching a level, or a personal feat unlocked by anyone
 * @param key      the game flag name for {@link Kind#FLAG}, the personal feat id for {@link Kind#AFTER}
 * @param minLevel the lowest flag level that counts, unused for {@link Kind#AFTER}
 */
public record Trigger(Kind kind, String key, int minLevel) {
    public enum Kind {
        FLAG,
        AFTER
    }

    public Trigger {
        if (kind == null || key == null || key.isBlank()) {
            throw new IllegalArgumentException("trigger needs a kind and a key");
        }
        if (kind == Kind.FLAG && minLevel <= 0) {
            throw new IllegalArgumentException("flag trigger level must be positive for " + key);
        }
    }

    /** Raised once the server wide game flag reaches {@code minLevel}. */
    public static Trigger flag(String gameFlag, int minLevel) {
        return new Trigger(Kind.FLAG, gameFlag, minLevel);
    }

    /** Raised the first time any player unlocks the personal feat {@code featId}. */
    public static Trigger after(String featId) {
        return new Trigger(Kind.AFTER, featId, 0);
    }
}
