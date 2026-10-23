package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;

/**
 * The Perfect Parries feats, read from the {@code parries:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.PERFECT_PARRIES} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.PARRIES}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: parries:parry and parries:parry:pvp, parries:counterattack and parries:counterattack:pvp. The parry streak
     * stays inside TrackerAPI, no record carries it.
 */
public final class ParriesFeats {
    private ParriesFeats() {
    }

    /** Adds the Perfect Parries feats, 8 planned in docs/MODS-PLAN.md. Empty until the definitions land. */
    public static void define(FeatRegistry.Definer d) {
        // The Perfect Parries feats go here.
    }
}
