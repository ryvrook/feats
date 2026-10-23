package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;

/**
 * The Wan's Wonder Weapons feats, read from the {@code relics:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.WONDER_WEAPONS} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.RELICS}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: relics:abilities and relics:abilities:<ability>, relics:relic_kind:<relic> markers with relics:relic_kinds
     * (distinct relics whose signature ability was used), relics:form_switch. Kills by relic stay inside TrackerAPI,
     * no record carries them.
 */
public final class RelicsFeats {
    private RelicsFeats() {
    }

    /** Adds the Wan's Wonder Weapons feats, 8 planned in docs/MODS-PLAN.md. Empty until the definitions land. */
    public static void define(FeatRegistry.Definer d) {
        // The Wan's Wonder Weapons feats go here.
    }
}
