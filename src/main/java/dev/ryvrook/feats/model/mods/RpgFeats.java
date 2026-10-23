package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;

/**
 * The RPG Leveling feats, read from the {@code rpg:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.RPG_LEVELING} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.RPG}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: rpg:level (best, the main level), rpg:level_ups, rpg:max_level_reached (1 once the cap was reached),
     * rpg:xp and rpg:xp:<source>, rpg:class:<id> markers with the rpg:classes total.
 */
public final class RpgFeats {
    private RpgFeats() {
    }

    /** Adds the RPG Leveling feats, 12 planned in docs/MODS-PLAN.md. Empty until the definitions land. */
    public static void define(FeatRegistry.Definer d) {
        // The RPG Leveling feats go here.
    }
}
