package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;

/**
 * The Vein Mining feats, read from the {@code vein:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.VEIN_MINING} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.VEIN_MINING}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: vein:veins, vein:blocks and vein:blocks:<block>, vein:largest (best).
 */
public final class VeinFeats {
    private VeinFeats() {
    }

    /** Adds the Vein Mining feats, 6 planned in docs/MODS-PLAN.md. Empty until the definitions land. */
    public static void define(FeatRegistry.Definer d) {
        // The Vein Mining feats go here.
    }
}
