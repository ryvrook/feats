package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;

/**
 * The Endgame and QoL feats, read from the {@code endgame:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.ENDGAME} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.ENDGAME}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: endgame:bosses and endgame:bosses:<boss>, endgame:boss_type:<boss> markers with endgame:boss_types,
     * endgame:achievement:<id> with endgame:achievements, endgame:bestiary:<id> with endgame:bestiaries,
     * endgame:rune:<id> with endgame:runes, endgame:wave:<arena>/<wave> with endgame:waves, endgame:runs and
     * endgame:runs:<dungeon> (dungeon entries), endgame:cleared and endgame:cleared:<arena> (trials completed),
     * endgame:failed and endgame:failed:<arena>, endgame:dungeon_type:<arena> with endgame:dungeon_types, and
     * endgame:fast_trial (trials completed under 5 minutes). The best wave and best combo stay inside TrackerAPI.
 */
public final class EndgameFeats {
    private EndgameFeats() {
    }

    /** Adds the Endgame and QoL feats, 20 planned in docs/MODS-PLAN.md. Empty until the definitions land. */
    public static void define(FeatRegistry.Definer d) {
        // The Endgame and QoL feats go here.
    }
}
