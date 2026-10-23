package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import static dev.ryvrook.feats.model.Category.ENCHANTING;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

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

    /** The counter prefix, {@code enchant:}. */
    static final String P = StatKeys.ENCHANT_PREFIX;

    /** Adds the 6 Simple Enchantments feats planned in docs/MODS-PLAN.md. */
    public static void define(FeatRegistry.Definer d) {
        d.feat("enchant_first", "First Enchantment", "Enchant an item.", ENCHANTING, BRONZE, "Rune_Empty", P + "items", 1);
        d.feat("enchant_fifty", "Fifty Enchantments", "Enchant 50 items.", ENCHANTING, SILVER, "Rune_Damage_Up", P + "items", 50);
        d.feat("enchant_level_3", "Third Level", "Apply an enchantment at level 3.", ENCHANTING, GOLD, "Rune_Aoe_Up", P + "highest_level", 3);
        d.feat("enchant_ten_kinds", "Ten Enchantments", "Apply 10 different enchantments.", ENCHANTING, GOLD, "Deco_Book_Pile_Small", P + "enchant_kinds", 10);
        d.feat("enchant_procs_1000", "Thousand Triggers", "Trigger enchantments 1,000 times.", ENCHANTING, SILVER, "Rune_Quickening", P + "procs", 1_000);
        d.feat("enchant_procs_25k", "Enchantment Master", "Trigger enchantments 25,000 times.", ENCHANTING, PLATINUM, "Ingredient_Rune_Shard", P + "procs", 25_000);
    }
}
