package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import static dev.ryvrook.feats.model.Category.RPG;
import static dev.ryvrook.feats.model.FeatRegistry.Definer.after;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

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

    /** The counter prefix, {@code rpg:}. */
    static final String P = StatKeys.RPG_PREFIX;

    /**
     * Adds the 12 RPG Leveling feats planned in docs/MODS-PLAN.md and the server first for the level cap. The level
     * cap reads the {@code rpg:max_level_reached} flag rather than a number, since the cap is a config value of the mod.
     * The experience sources are the {@code XPSource} names of the mod's API, {@code ENTITY_KILL}, {@code BLOCK_BREAK}
     * and {@code HARVEST}.
     */
    public static void define(FeatRegistry.Definer d) {
        d.feat("rpg_level_5", "First Levels", "Reach level 5 in RPG Leveling.", RPG, BRONZE, "Rune_Empty", P + "level", 5);
        d.feat("rpg_level_15", "Level Fifteen", "Reach level 15 in RPG Leveling.", RPG, SILVER, "Rune_Fireball", P + "level", 15);
        d.feat("rpg_level_30", "Level Thirty", "Reach level 30 in RPG Leveling.", RPG, GOLD, "Rune_Enrage", P + "level", 30);
        d.feat("rpg_max_level", "Level Cap", "Reach the maximum level in RPG Leveling.", RPG, PLATINUM, "Bench_Abilities", P + "max_level_reached", 1);
        d.feat("rpg_hundred_levels", "Hundred Levels", "Gain 100 levels in total, relevels included.", RPG, GOLD, "Deco_Book_Pile_Small", P + "level_ups", 100);
        d.feat("rpg_xp_10k", "Ten Thousand XP", "Earn 10,000 experience points.", RPG, SILVER, "Potion_Signature_Greater", P + "xp", 10_000);
        d.feat("rpg_xp_million", "A Million XP", "Earn 1,000,000 experience points.", RPG, GOLD, "Ingredient_Crystal_Void", P + "xp", 1_000_000);
        d.feat("rpg_kill_xp", "Kill Experience", "Earn 5,000 experience points from kills.", RPG, SILVER, "Weapon_Battleaxe_Iron", P + "xp:ENTITY_KILL", 5_000);
        d.feat("rpg_mining_xp", "Mining Experience", "Earn 5,000 experience points from breaking blocks.", RPG, SILVER, "Tool_Pickaxe_Iron", P + "xp:BLOCK_BREAK", 5_000);
        d.feat("rpg_harvest_xp", "Harvest Experience", "Earn 2,500 experience points from harvesting crops.", RPG, SILVER, "Plant_Crop_Wheat_Item", P + "xp:HARVEST", 2_500);
        d.feat("rpg_class_chosen", "Class Chosen", "Pick a class.", RPG, BRONZE, "Deco_Scroll", P + "classes", 1);
        d.hidden("rpg_three_classes", "Three Classes", "Pick 3 different classes over time.", RPG, SILVER, "Deco_Book_Pile_Large", P + "classes", 3);

        d.first("first_rpg_level_cap", "First Ascent", "Be the first on this server to reach the maximum level in RPG Leveling.",
                PLATINUM, "Bench_Abilities", after("rpg_max_level"));
    }
}
