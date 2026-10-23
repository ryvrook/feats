package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Requirement;
import dev.ryvrook.feats.stats.StatKeys;

import java.util.List;

import static dev.ryvrook.feats.model.Category.GARDEN;
import static dev.ryvrook.feats.model.FeatRegistry.Definer.after;
import static dev.ryvrook.feats.model.FeatRegistry.Definer.req;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

/**
 * The Lost Garden feats of the Sig's Garden asset pack 0.5.8, see docs/PACKS-RESEARCH.md section 5. An asset pack has
 * no code, so every feat reads a key Feats counts itself, {@code portal:}, {@code kill:}, {@code crafted:} and
 * {@code obtained:}. The registry calls {@link #define} with {@code Sibling.SIGS_GARDEN} alone as the section
 * requirement. The tab exists only with the pack, so the feat icons may be the pack's own items, the World tab
 * icon stays vanilla.
 */
public final class GardenFeats {
    private GardenFeats() {
    }

    /** The seven herbs of the tea chain. */
    static final List<String> HERBS = List.of("Chamomile", "Dandelion", "Hibiscus", "Lavender", "Nettle", "Red_Rose", "White_Rose");
    /** The ten Celestium weapons, the shield included. */
    static final List<String> CELESTIUM = List.of("Axe", "Battleaxe", "Club", "Dagger", "Longsword", "Mace", "Shield",
            "Shortbow", "Spear", "Sword");
    static final List<String> TULIPS = List.of("Candied", "Flame", "Frost", "Poison", "Red");

    // ---- every key the feats read, in one place ----
    static final String FRAGMENT = StatKeys.CRAFTED_PREFIX + "PortalKey_Lost_Garden";
    static final String ENTERED = StatKeys.PORTAL_PREFIX + "Lost_Garden";
    static final String PHOENIX = StatKeys.KILL_PREFIX + "Prisma_Phoenix";
    static final String FROST_ARCHMAGE = StatKeys.KILL_PREFIX + "Skeleton_Frost_Archmage";
    static final String SAND_ARCHMAGE = StatKeys.KILL_PREFIX + "Skeleton_Sand_Archmage";
    static final List<String> TEA_BENCHES = List.of(StatKeys.CRAFTED_PREFIX + "Sig_Benches_Tea_Brew",
            StatKeys.CRAFTED_PREFIX + "Sig_Benches_Tea_Dry", StatKeys.CRAFTED_PREFIX + "Sig_Benches_Tea_Grinder");
    static final String DRIED = StatKeys.OBTAINED_PREFIX + "Sig_Dried_";
    static final String TEA_CUP = StatKeys.CRAFTED_PREFIX + "Sig_Tea_Cup_";
    static final String CELESTIUM_BAR = StatKeys.OBTAINED_PREFIX + "Ingredient_Bar_Celestium";
    static final String CELESTIUM_ORE = StatKeys.OBTAINED_PREFIX + "Sig_Ore_Celestium";
    static final String CELESTIUM_WEAPON = StatKeys.CRAFTED_PREFIX + "Sig_Weapon_Celestium_";
    static final String TULIP = StatKeys.OBTAINED_PREFIX + "Sig_Plant_Tulip_";

    /** Every key above, the ones built from a list in full. */
    public static final List<String> KEYS = keys();

    private static List<String> keys() {
        List<String> out = new java.util.ArrayList<>(List.of(FRAGMENT, ENTERED, PHOENIX, FROST_ARCHMAGE, SAND_ARCHMAGE,
                CELESTIUM_BAR, CELESTIUM_ORE));
        out.addAll(TEA_BENCHES);
        HERBS.forEach(h -> out.add(DRIED + h));
        HERBS.forEach(h -> out.add(TEA_CUP + h));
        CELESTIUM.forEach(w -> out.add(CELESTIUM_WEAPON + w));
        TULIPS.forEach(t -> out.add(TULIP + t));
        return List.copyOf(out);
    }

    private static Requirement[] each(String prefix, List<String> ids) {
        return ids.stream().map(id -> req(prefix + id, 1)).toArray(Requirement[]::new);
    }

    /** Adds the 12 Lost Garden feats and the server first for a Prisma Phoenix. */
    public static void define(FeatRegistry.Definer d) {
        // ---- the realm ----
        d.feat("garden_fragment", "Fragment Maker", "Craft a Lost Garden fragment.", GARDEN, BRONZE, "PortalKey_Lost_Garden", FRAGMENT, 1);
        d.feat("garden_enter", "The Lost Garden", "Enter the Lost Garden.", GARDEN, SILVER, "Portal_Device", ENTERED, 1);
        d.feat("garden_regular", "Garden Regular", "Enter the Lost Garden 10 times.", GARDEN, GOLD, "Portal_Device", ENTERED, 10);
        d.hidden("garden_phoenix", "Prisma Phoenix", "Defeat a Prisma Phoenix.", GARDEN, SILVER, "Ingredient_Feathers_Blue", PHOENIX, 1);
        d.multi("garden_archmages", "Manor Cleared", "Defeat the 2 archmages of the manor.", GARDEN, GOLD, "Weapon_Staff_Frost",
                req(FROST_ARCHMAGE, 1), req(SAND_ARCHMAGE, 1));
        // ---- tea ----
        d.multi("garden_tea_room", "Tea Room", "Craft the 3 tea benches.", GARDEN, BRONZE, "Sig_Benches_Tea_Brew",
                TEA_BENCHES.stream().map(k -> req(k, 1)).toArray(Requirement[]::new));
        d.multi("garden_herbalist", "Herb Drying", "Dry all 7 herbs on a drying rack.", GARDEN, SILVER, "Sig_Dried_Lavender",
                each(DRIED, HERBS));
        d.multi("garden_tea_service", "Tea Service", "Brew a cup of all 7 teas.", GARDEN, GOLD, "Sig_Tea_Cup_Lavender",
                each(TEA_CUP, HERBS));
        // ---- Celestium ----
        d.feat("garden_celestium", "Celestium Ingot", "Smelt a Celestium ingot.", GARDEN, BRONZE, "Ingredient_Bar_Celestium", CELESTIUM_BAR, 1);
        d.feat("garden_celestium_miner", "Celestium Miner", "Mine 100 Celestium ore.", GARDEN, SILVER, "Sig_Ore_Celestium", CELESTIUM_ORE, 100);
        d.multi("garden_arsenal", "Celestium Arsenal", "Craft all 10 Celestium weapons, the shield included.", GARDEN, PLATINUM,
                "Sig_Weapon_Celestium_Longsword", each(CELESTIUM_WEAPON, CELESTIUM));
        // ---- flowers ----
        d.multi("garden_tulips", "Tulip Collector", "Collect all 5 tulips of the garden.", GARDEN, SILVER, "Sig_Plant_Tulip_Red",
                each(TULIP, TULIPS));

        // ---- World tab ----
        d.first("first_garden_phoenix", "Phoenix Hunter", "Be the first on this server to defeat a Prisma Phoenix.",
                PLATINUM, "Ingredient_Feathers_Blue", after("garden_phoenix"));
    }
}
