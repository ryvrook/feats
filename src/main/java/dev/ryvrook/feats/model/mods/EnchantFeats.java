package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;

/**
 * The Simple Enchantments feats, read from the {@code enchant:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.SIMPLE_ENCHANTMENTS} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.ENCHANTING}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: enchant:items and enchant:items:<enchantment>, enchant:highest_level (best), enchant:procs and
     * enchant:procs:<enchantment>, enchant:enchant_kind:<enchantment> markers with enchant:enchant_kinds.
 */
public final class EnchantFeats {
    private EnchantFeats() {
    }

    /** Adds the Simple Enchantments feats, 6 planned in docs/MODS-PLAN.md. Empty until the definitions land. */
    public static void define(FeatRegistry.Definer d) {
        // The Simple Enchantments feats go here.
    }
}
