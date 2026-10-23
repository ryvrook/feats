package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;

/**
 * The Alec's Tamework feats, read from the {@code tamework:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.TAMEWORK} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.TAMEWORK}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: tamework:tamed and tamework:tamed:<species>, tamework:species:<species> markers with tamework:species,
     * tamework:bred and tamework:bred:<species>, tamework:companion_level_ups, tamework:companion_level_best (best),
     * tamework:companions_lost, tamework:xp and tamework:xp:<source>, tamework:harvest and tamework:cull.
 */
public final class TameworkFeats {
    private TameworkFeats() {
    }

    /** Adds the Alec's Tamework feats, 8 planned in docs/MODS-PLAN.md. Empty until the definitions land. */
    public static void define(FeatRegistry.Definer d) {
        // The Alec's Tamework feats go here.
    }
}
