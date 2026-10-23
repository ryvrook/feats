package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import java.util.List;

import static dev.ryvrook.feats.model.Category.FISHING;
import static dev.ryvrook.feats.model.FeatRegistry.Definer.after;
import static dev.ryvrook.feats.model.FeatRegistry.Definer.req;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

/**
 * The Angler's Almanac feats, read from the {@code fish:} counters TrackerAPI 1.3.0 publishes for the binding
 * {@code fish}, mirrored by {@code integration/ModKinds} (actions and discoveries) and {@code integration/TallyKinds}
 * (per id sums and bests). The registry calls {@link #define} with {@code Sibling.FISHING} and
 * {@code Sibling.TRACKER_API} as the section requirement, and only when TrackerAPI is at least 1.3.0. Personal feats
 * sit in {@code Category.FISHING}.
 *
 * <p>The numbers follow Angler's Almanac 1.2.2. It ships 40 fish, 16 of them legendary and 4 golden, an Almanac of
 * 35 pages over 6 zones, and grades a catch perfect, great, good or fail. The five legendary fish of the ocean are
 * the loot ids {@code Legendary_Whale}, {@code Legendary_Shark}, {@code Legendary_Crab}, {@code Legendary_Lobster} and
 * {@code Legendary_Jellyfish_Man_Of_War}.
 */
public final class FishingFeats {
    private FishingFeats() {
    }

    // ---- every key the binding writes, in one place so a change of the binding is one edit here ----
    /** Fish caught, junk apart. */
    static final String CAUGHT = StatKeys.FISH_PREFIX + "caught";
    /** Fish caught per loot id, {@code fish:caught:<lootId>}, a per id key. */
    static final String CAUGHT_ID = StatKeys.FISH_PREFIX + "caught:";
    static final String JUNK = StatKeys.FISH_PREFIX + "junk";
    static final String JUNK_ID = StatKeys.FISH_PREFIX + "junk:";
    /** Every catch, with {@code fish:rarity:<rarity>} per lower case rarity. */
    static final String RARITY = StatKeys.FISH_PREFIX + "rarity";
    static final String RARITY_ID = StatKeys.FISH_PREFIX + "rarity:";
    /** {@code fish:grade:<grade>} for perfect, great, good and fail. */
    static final String GRADE_ID = StatKeys.FISH_PREFIX + "grade:";
    /** Perfect 4, great 3, good 2, fail 1. */
    static final String GRADE_BEST = StatKeys.FISH_PREFIX + "grade_best";
    static final String PERFORMANCE_BEST = StatKeys.FISH_PREFIX + "performance_best";
    static final String PERFECT_STREAK_BEST = StatKeys.FISH_PREFIX + "perfect_streak_best";
    static final String SIZE_BEST = StatKeys.FISH_PREFIX + "size_best";
    static final String LEGENDARY = StatKeys.FISH_PREFIX + "legendary";
    static final String GOLDEN = StatKeys.FISH_PREFIX + "golden";
    /** Distinct species, marks {@code fish:species:<id>}. */
    static final String SPECIES = StatKeys.FISH_PREFIX + "species";
    /** Distinct Almanac pages, marks {@code fish:page:<id>}. */
    static final String PAGES = StatKeys.FISH_PREFIX + "pages";
    /** Zones with every page filled, marks {@code fish:zone:<id>}. */
    static final String ZONES = StatKeys.FISH_PREFIX + "zones";
    static final String ESCAPED = StatKeys.FISH_PREFIX + "escaped";
    static final String CAST = StatKeys.FISH_PREFIX + "cast";
    static final String HOOKED = StatKeys.FISH_PREFIX + "hooked";
    static final String HOOKED_PVP = StatKeys.FISH_PREFIX + "hooked:pvp";

    /** The keys above. One ending in a colon is a per id prefix. */
    public static final List<String> KEYS = List.of(CAUGHT, CAUGHT_ID, JUNK, JUNK_ID, RARITY, RARITY_ID, GRADE_ID,
            GRADE_BEST, PERFORMANCE_BEST, PERFECT_STREAK_BEST, SIZE_BEST, LEGENDARY, GOLDEN, SPECIES, PAGES, ZONES,
            ESCAPED, CAST, HOOKED, HOOKED_PVP);

    /** Adds the 15 Fishing feats and the 2 server firsts. */
    public static void define(FeatRegistry.Definer d) {
        // ---- catches ----
        d.feat("fish_first_catch", "First Catch", "Catch a fish with a rod.", FISHING, BRONZE, "Fish_Bluegill_Item", CAUGHT, 1);
        d.feat("fish_hundred", "Hundred Fish", "Catch 100 fish.", FISHING, SILVER, "Fish_Salmon_Item", CAUGHT, 100);
        d.feat("fish_thousand", "Thousand Fish", "Catch 1,000 fish.", FISHING, GOLD, "Fish_Catfish_Item", CAUGHT, 1_000);
        // ---- species and the Almanac ----
        d.feat("fish_species_10", "Ten Species", "Catch 10 different kinds of fish.", FISHING, BRONZE, "Fish_Clownfish_Item", SPECIES, 10);
        d.feat("fish_species_25", "Seasoned Angler", "Catch 25 different kinds of fish.", FISHING, SILVER, "Fish_Tang_Blue_Item", SPECIES, 25);
        d.feat("fish_species_40", "Every Fish", "Catch all 40 kinds of fish.", FISHING, PLATINUM, "Fish_Pufferfish_Item", SPECIES, 40);
        d.feat("fish_zone", "Zone Complete", "Complete 1 of the 6 Almanac zones.", FISHING, SILVER, "Deco_Map", ZONES, 1);
        d.feat("fish_pages_35", "Full Almanac", "Fill all 35 pages of the Almanac.", FISHING, PLATINUM, "Deco_Book_Pile_Large", PAGES, 35);
        // ---- legendary fish ----
        d.feat("fish_legendary", "Legendary Catch", "Catch a legendary fish.", FISHING, SILVER, "Fish_Pike_Item", LEGENDARY, 1);
        d.feat("fish_legendary_25", "Legend Hunter", "Catch 25 legendary fish.", FISHING, GOLD, "Fish_Shark_Hammerhead_Item", LEGENDARY, 25);
        d.multi("fish_ocean_legends", "Ocean Legends", "Catch all 5 legendary fish of the ocean.", FISHING, GOLD, "Fish_Whale_Humpback_Item",
                req(CAUGHT_ID + "Legendary_Whale", 1), req(CAUGHT_ID + "Legendary_Shark", 1), req(CAUGHT_ID + "Legendary_Crab", 1),
                req(CAUGHT_ID + "Legendary_Lobster", 1), req(CAUGHT_ID + "Legendary_Jellyfish_Man_Of_War", 1));
        // ---- the minigame ----
        d.feat("fish_perfect", "Perfect Catch", "Land a catch with a perfect grade.", FISHING, BRONZE, "Fish_Minnow_Item", GRADE_ID + "perfect", 1);
        d.feat("fish_perfect_streak", "Perfect Run", "Land 5 perfect catches in a row.", FISHING, GOLD, "Fish_Trout_Rainbow_Item", PERFECT_STREAK_BEST, 5);
        d.hidden("fish_golden", "Golden Fish", "Catch a golden fish.", FISHING, GOLD, "Deco_Treasure", GOLDEN, 1);
        d.hidden("fish_junk", "Junk Haul", "Reel in 25 pieces of junk.", FISHING, BRONZE, "Deco_Trash_Eaten_Fish", JUNK, 25);

        // ---- World tab ----
        d.first("first_fish_legendary", "First Legend", "Be the first on this server to catch a legendary fish.",
                PLATINUM, "Fish_Pike_Item", after("fish_legendary"));
        d.first("first_fish_almanac", "First Almanac", "Be the first on this server to fill all 35 pages of the Almanac.",
                PLATINUM, "Deco_Book_Pile_Large", after("fish_pages_35"));
    }
}
