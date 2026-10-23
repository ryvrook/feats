package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import static dev.ryvrook.feats.model.Category.VEIN_MINING;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

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

    /** The counter prefix, {@code vein:}. */
    static final String P = StatKeys.VEIN_PREFIX;

    /** Adds the 6 Vein Mining feats planned in docs/MODS-PLAN.md. The default vein limit of the mod is 50 blocks. */
    public static void define(FeatRegistry.Definer d) {
        d.feat("vein_first", "First Vein", "Mine a vein.", VEIN_MINING, BRONZE, "Tool_Pickaxe_Copper", P + "veins", 1);
        d.feat("vein_hundred", "Hundred Veins", "Mine 100 veins.", VEIN_MINING, SILVER, "Tool_Pickaxe_Iron", P + "veins", 100);
        d.feat("vein_thousand", "Thousand Veins", "Mine 1,000 veins.", VEIN_MINING, GOLD, "Tool_Pickaxe_Cobalt", P + "veins", 1_000);
        d.feat("vein_blocks_10k", "Ten Thousand Blocks", "Mine 10,000 blocks through veins.", VEIN_MINING, GOLD, "Tool_Drill_Scrap", P + "blocks", 10_000);
        d.feat("vein_largest_25", "Big Vein", "Mine a vein of 25 blocks.", VEIN_MINING, SILVER, "Ore_Iron", P + "largest", 25);
        d.feat("vein_largest_50", "Full Vein", "Mine a vein of 50 blocks, the default limit.", VEIN_MINING, PLATINUM, "Ore_Cobalt", P + "largest", 50);
    }
}
