package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import java.util.List;

import static dev.ryvrook.feats.model.Category.MACHINES;
import static dev.ryvrook.feats.model.FeatRegistry.Definer.after;
import static dev.ryvrook.feats.model.FeatRegistry.Definer.req;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

/**
 * The Machine Blocks feats. Machine Blocks has no binding, so every feat reads a {@code crafted:}, {@code placed:} or
 * {@code obtained:} key Feats counts itself on either tracking path, with the item ids of Machine Blocks 1.4.5. The
 * registry calls {@link #define} with {@code Sibling.MACHINES} alone as the section requirement. Personal feats sit in
 * {@code Category.MACHINES}. Each machine tier is crafted from two of the tier below, so an ultimate crusher stands on
 * eight basic ones. Pure ore is the Ore Cleaning Unit's output, collected by hand from the machine.
 */
public final class MachinesFeats {
    private MachinesFeats() {
    }

    // ---- every key the feats read, in one place ----
    static final String BENCH = StatKeys.CRAFTED_PREFIX + "MachineBlocks_Crafting_Bench";
    static final String BASIC_FURNACE = StatKeys.PLACED_PREFIX + "Basic_Furnace";
    static final String BASIC_CRUSHER = StatKeys.PLACED_PREFIX + "Basic_Crusher";
    static final String BASIC_DRYER = StatKeys.PLACED_PREFIX + "Basic_Dryer";
    static final String BASIC_SALVAGER = StatKeys.PLACED_PREFIX + "Basic_Salvager";
    static final String ELITE_FURNACE = StatKeys.CRAFTED_PREFIX + "Elite_Furnace";
    static final String ULTIMATE_CRUSHER = StatKeys.PLACED_PREFIX + "Ultimate_Crusher";
    static final String FACTORY_FURNACE = StatKeys.PLACED_PREFIX + "Factory_Furnace_Basic";
    static final String IRON_SIEVE = StatKeys.PLACED_PREFIX + "IronMiner";
    static final String PURE_IRON = StatKeys.OBTAINED_PREFIX + "Ore_Pure_Iron";
    static final String WIND_GENERATOR = StatKeys.PLACED_PREFIX + "Wind_Generator_Controller";
    static final String SINGULARITY = StatKeys.PLACED_PREFIX + "Singularity_Power_Core";

    public static final List<String> KEYS = List.of(BENCH, BASIC_FURNACE, BASIC_CRUSHER, BASIC_DRYER, BASIC_SALVAGER,
            ELITE_FURNACE, ULTIMATE_CRUSHER, FACTORY_FURNACE, IRON_SIEVE, PURE_IRON, WIND_GENERATOR, SINGULARITY);

    /** Adds the 10 Machines feats and the server first for an ultimate crusher. */
    public static void define(FeatRegistry.Definer d) {
        d.feat("machines_bench", "Machine Workshop", "Craft the Machine Blocks Workbench.", MACHINES, BRONZE, "Tool_Hammer_Iron", BENCH, 1);
        d.feat("machines_crusher", "Crusher Built", "Place a basic crusher.", MACHINES, SILVER, "Ore_Iron", BASIC_CRUSHER, 1);
        d.multi("machines_basic_line", "Basic Line", "Place all 4 basic machines, a furnace, a crusher, a dryer and a salvager.",
                MACHINES, GOLD, "Bench_Furnace",
                req(BASIC_FURNACE, 1), req(BASIC_CRUSHER, 1), req(BASIC_DRYER, 1), req(BASIC_SALVAGER, 1));
        d.feat("machines_elite_furnace", "Elite Furnace", "Craft an elite furnace.", MACHINES, GOLD, "Bench_Furnace", ELITE_FURNACE, 1);
        d.feat("machines_ultimate_crusher", "Ultimate Crusher", "Place an ultimate crusher.", MACHINES, PLATINUM, "Tool_Pickaxe_Adamantite", ULTIMATE_CRUSHER, 1);
        d.feat("machines_factory", "Factory Floor", "Place a basic factory furnace.", MACHINES, GOLD, "Metal_Iron_Pipe_Chimney", FACTORY_FURNACE, 1);
        d.feat("machines_ore_sieve", "Ore Sieve", "Place an iron ore sieve.", MACHINES, SILVER, "Ore_Iron", IRON_SIEVE, 1);
        d.feat("machines_pure_ore", "Pure Ore", "Collect 100 pure iron ore.", MACHINES, GOLD, "Ingredient_Bar_Iron", PURE_IRON, 100);
        d.feat("machines_wind", "Wind Power", "Place a wind generator.", MACHINES, SILVER, "Metal_Iron_Pipe_Large", WIND_GENERATOR, 1);
        d.hidden("machines_singularity", "Singularity Core", "Place a singularity power core.", MACHINES, PLATINUM, "Ingredient_Crystal_Void", SINGULARITY, 1);

        // ---- World tab ----
        d.first("first_machines_ultimate", "First Ultimate", "Be the first on this server to place an ultimate crusher.",
                PLATINUM, "Tool_Pickaxe_Adamantite", after("machines_ultimate_crusher"));
    }
}
