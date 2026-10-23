package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;

/**
 * The Lucky Mining feats, read from the {@code lucky:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.LUCKY_MINING} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.LUCKY_MINING}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: lucky:drops and lucky:drops:<ore>, lucky:streak_best (best).
 */
public final class LuckyFeats {
    private LuckyFeats() {
    }

    /** Adds the Lucky Mining feats, 5 planned in docs/MODS-PLAN.md. Empty until the definitions land. */
    public static void define(FeatRegistry.Definer d) {
        // The Lucky Mining feats go here.
    }
}
