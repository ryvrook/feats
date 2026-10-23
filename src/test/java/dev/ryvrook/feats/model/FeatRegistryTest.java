package dev.ryvrook.feats.model;

import dev.ryvrook.feats.stats.StatKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 1.7.2 feat tables against docs/EXPANSION-PLAN.md, sections 4 to 8 and the totals in section 12. */
class FeatRegistryTest {
    /** Every sibling and every pack, so every defined feat is in. */
    private static final FeatRegistry FULL = new FeatRegistry(EnumSet.allOf(Sibling.class), EnumSet.allOf(Pack.class),
            FeatRegistry.MODS_MINIMUM);
    /** A plain server, no sibling and every pack off. */
    private static final FeatRegistry PLAIN = new FeatRegistry();

    /** What Feats 1.6.0 shipped, see docs/TESTING.md. */
    private static final int OLD_COUNT = 327;
    private static final int OLD_POINTS = 15_075;
    private static final int OLD_WORLD = 18;
    private static final int OLD_FACTIONS = 15;
    private static final int OLD_FACTIONS_POINTS = 460;
    /** The nine mod tabs of 1.9.0, RPG 12, Dungeons 12, Parries 8, Relics 8, Zephyr 8, Enchanting 6, Vein 6, Lucky 5, Companions 8. */
    private static final int MODS_NINE = 73;
    private static final int MODS_NINE_POINTS = 420 + 455 + 320 + 280 + 295 + 260 + 260 + 210 + 295;
    /** 1.10.0, the tabs counted from Feats' own keys, Storage 8, Machines 10, Lost Garden 12, Decor 15. */
    private static final int COUNTED = 8 + 10 + 12 + 15;
    private static final int COUNTED_POINTS = 320 + 485 + 405 + 440;

    @AfterAll
    static void restore() {
        new FeatRegistry(false); // put the shared feat predicates back to the plain list for the other tests
    }

    // ---- 1.9.0, the mods TrackerAPI reads ----

    @Test
    void theModFeatsNeedTrackerApi120() {
        assertNull(FULL.modsNote(), "every sibling with TrackerAPI 1.2.0 defines the mod sections");
        assertEquals(FeatRegistry.MODS_MINIMUM, FULL.trackerVersion());
        FeatRegistry noVersion = new FeatRegistry(EnumSet.allOf(Sibling.class), EnumSet.noneOf(Pack.class));
        assertNotNull(noVersion.modsNote());
        assertTrue(noVersion.modsNote().contains("1.2.0"), noVersion.modsNote());
        FeatRegistry old = new FeatRegistry(EnumSet.allOf(Sibling.class), EnumSet.noneOf(Pack.class), "1.1.2");
        assertTrue(old.modsNote().contains("1.1.2"), old.modsNote());
        FeatRegistry noTracker = new FeatRegistry(EnumSet.of(Sibling.RPG_LEVELING), EnumSet.noneOf(Pack.class), "1.2.0");
        assertTrue(noTracker.modsNote().contains("not installed"), noTracker.modsNote());
        assertNull(new FeatRegistry(EnumSet.of(Sibling.TRACKER_API), EnumSet.noneOf(Pack.class), "1.3.0").modsNote(),
                "newer than the minimum is fine");
        for (Sibling mod : Sibling.values()) {
            if (mod.isMod()) {
                assertNotNull(Category.ofMod(mod), mod.name());
                assertTrue(PLAIN.inCategory(Category.ofMod(mod)).isEmpty(), mod + " has no tab on a plain server");
                for (Feat feat : FULL.inCategory(Category.ofMod(mod))) {
                    assertEquals(Set.of(mod, Sibling.TRACKER_API), feat.requires(), feat.id());
                }
            } else {
                assertNull(Category.ofMod(mod), mod.name());
            }
        }
        assertTrue(FULL.tabs().containsAll(List.of(Category.EXPLORATION, Category.WORLD)));
    }

    // ---- section 4, vanilla ----
    private static final List<String> EXPLORATION = List.of("biome_10", "biome_25", "biome_50", "biome_100", "storm_chaser",
            "aurora", "red_sky", "lunar_calendar", "temple_raider", "home_base", "waypoints", "checkpoint", "patched_up");
    private static final List<String> MINING_BUILDING = List.of("worn_out", "scrap_pile", "hard_use", "good_as_new",
            "timber_survey", "every_grain", "palette", "mosaic");
    private static final List<String> CRAFTING = List.of("recipe_book", "cookbook", "suited_up", "wardrobe", "armoury_complete");
    private static final List<String> COMBAT = List.of("hazard_course", "shield_up", "bulwark", "quiver", "longshot", "deep_strike",
            "death_from_above", "cavalry", "last_stand", "night_watch", "blood_moon_hunter", "double_kill", "triple_kill",
            "massacre", "close_call", "hard_to_kill", "hard_landing", "water_landing", "second_wind", "raid_party", "war_band");
    private static final List<String> SURVIVAL = List.of("deep_sleep", "sound_sleeper", "survivor", "hardened", "ageless", "habit",
            "routine", "clockwork", "year_round", "anniversary", "night_owl", "early_bird", "out_of_breath", "drained",
            "last_life", "ten_days_hardcore");
    private static final List<String> NATURE = List.of("sower", "sprinkler", "fertile", "henhouse", "egg_run", "catch_and_carry",
            "trapper", "bait_shop", "leviathan", "deep_catch", "aquarium", "first_ride", "stable", "rail_rider", "long_haul",
            "rancher", "menagerie", "zoologist", "bestiary");
    private static final List<String> CHAPTER = List.of("breach_closed", "hunt_master", "coffer_cracker", "eviction", "last_second",
            "portal_hour", "hard_won", "haggler", "trade_route", "elders_counsel", "overclocked_clean", "overclocked_fast");
    private static final List<String> MISC = List.of("take_a_seat", "hop", "bounce", "double_jump", "tumbler", "scaler", "mantle",
            "sprinter", "low_profile", "through_the_hub", "promoted");
    // ---- section 5 ----
    private static final List<String> ENDGAME = List.of("exploration_complete", "mining_complete", "building_complete",
            "crafting_complete", "combat_complete", "survival_complete", "nature_complete", "chapter_complete", "grand_slam",
            "ten_thousand", "twenty_thousand", "grandmaster", "all_of_it");
    // ---- section 6 ----
    private static final List<String> SOCIAL = List.of("company", "regular_company", "crowd", "familiar_faces", "hundred_faces", "shared_first");
    private static final List<String> HARDCORE = List.of("thirty_days_hardcore", "full_pool");
    private static final List<String> PVP = List.of("vendetta", "spree", "long_duel");
    private static final List<String> CALENDAR = List.of("new_year", "midsummer", "midwinter", "server_birthday");
    private static final List<String> GRIND = List.of("world_eater", "extinction", "thousand_hours");
    // ---- section 7 ----
    private static final List<String> WORLD = List.of("world_million_blocks", "world_ten_million_blocks",
            "world_hundred_thousand_kills", "world_thousand_bosses", "world_hundred_players", "world_thousand_players",
            "world_full_house", "world_first_year", "world_chunk_factory", "first_leviathan", "first_bestiary", "first_grand_slam");
    // ---- section 8 ----
    private static final List<String> COINAGE = List.of("first_coin", "payday", "well_off", "tycoon", "generous", "benefactor",
            "customer", "shopkeeper", "market_maker", "winning_bid", "trader", "saver", "compound", "borrower", "clean_slate",
            "defaulted", "gambler", "lucky", "high_roller", "heavy_losses", "jackpot", "daily_week", "daily_month", "paper_trail");
    private static final List<String> FACTIONS = List.of("enlisted", "exiled", "declaration", "pact", "upgraded", "fortified",
            "faction_market", "trespasser", "held_the_line", "breacher", "king_of_the_hill", "outpost", "powerhouse", "downsizing");
    /** 1.7.3, read from dominion:rank_changes that Dominion 0.7.2 starts sending. */
    private static final List<String> FACTIONS_173 = List.of("faction_rank");
    private static final List<String> CHRONICLE = List.of("busy_day", "big_week", "full_month", "level_10", "level_25", "level_50");
    private static final List<String> ATLAS = List.of("on_the_map", "surveyors_mark");
    private static final List<String> CROSS = List.of("war_chest", "allied_trade", "treasurer");

    /** Asserts every id is a personal feat of the full registry and returns the points they are worth. */
    private static int points(List<String> ids) {
        int sum = 0;
        for (String id : ids) {
            Feat feat = FULL.get(id);
            assertNotNull(feat, id + " is missing");
            sum += feat.tier().points();
        }
        return sum;
    }

    private static void block(List<String> ids, int count, int points) {
        assertEquals(count, ids.size(), "feats in the block");
        assertEquals(points, points(ids), "points in the block");
    }

    @Test
    void everyIdIsUniqueWithEverySiblingAndPackOn() {
        Set<String> ids = new HashSet<>();
        for (Feat feat : FULL.all()) {
            assertTrue(ids.add(feat.id()), "duplicate " + feat.id());
        }
        List<String> all = new ArrayList<>();
        for (List<String> block : List.of(EXPLORATION, MINING_BUILDING, CRAFTING, COMBAT, SURVIVAL, NATURE, CHAPTER, MISC,
                ENDGAME, SOCIAL, HARDCORE, PVP, CALENDAR, GRIND, WORLD, COINAGE, FACTIONS, CHRONICLE, ATLAS, CROSS)) {
            all.addAll(block);
        }
        assertEquals(all.size(), new HashSet<>(all).size(), "the plan's tables name each id once");
        for (String id : all) {
            assertNotNull(FULL.get(id), id);
        }
    }

    @Test
    void everyRequirementKeyIsAKnownCounterOrPrefix() throws Exception {
        Set<String> keys = new HashSet<>();
        Set<String> prefixes = new HashSet<>();
        for (Field f : StatKeys.class.getDeclaredFields()) {
            if (f.getType() != String.class || !Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers())) {
                continue;
            }
            String value = (String) f.get(null);
            if (f.getName().endsWith("_PREFIX")) {
                prefixes.add(value);
            } else {
                keys.add(value);
            }
        }
        // The counters the engine keeps under plain names that FeatRegistry writes out in full.
        keys.addAll(Set.of("swim_distance", "glide_distance", "bottom_reached", "top_reached", "kill_streak", "boss_flawless",
                "biggest_hit", "longest_session", "tamed", "ride_distance", "gobliterator_fast"));
        for (Feat feat : FULL.personal()) {
            for (Requirement r : feat.requirements()) {
                String key = r.key();
                boolean known = keys.contains(key) || prefixes.stream().anyMatch(p -> key.startsWith(p) && key.length() > p.length());
                assertTrue(known, feat.id() + " reads unknown key " + key);
            }
        }
    }

    @Test
    void vanillaAndEndgameMatchSectionTwelve() {
        block(EXPLORATION, 13, 415);
        block(MINING_BUILDING, 8, 310);
        block(CRAFTING, 5, 300);
        block(COMBAT, 21, 730);
        block(SURVIVAL, 16, 725);
        block(NATURE, 19, 920);
        block(CHAPTER, 12, 535);
        block(MISC, 11, 185);
        block(ENDGAME, 13, 1_250);
        assertEquals(OLD_COUNT + 118, PLAIN.count(), "a plain server, every pack off");
        assertEquals(OLD_POINTS + 5_370, PLAIN.totalPoints());
        assertEquals(OLD_WORLD + 12, PLAIN.countServer());
        for (String id : ENDGAME) {
            assertTrue(FULL.get(id).hidden() || id.equals("ten_thousand"), id + " is hidden");
        }
    }

    @Test
    void packsMatchSectionTwelve() {
        block(SOCIAL, 6, 235);
        block(HARDCORE, 2, 150);
        block(PVP, 3, 125);
        block(CALENDAR, 4, 55);
        block(GRIND, 3, 300);
        for (Pack pack : Pack.values()) {
            assertTrue(PLAIN.inCategory(pack.category()).isEmpty(), pack + " is off on a plain server");
            FeatRegistry one = new FeatRegistry(EnumSet.noneOf(Sibling.class), EnumSet.of(pack));
            assertFalse(one.inCategory(pack.category()).isEmpty(), pack + " fills its tab");
            for (Feat feat : one.inCategory(pack.category())) {
                assertSame(pack, feat.pack(), feat.id());
            }
        }
        // Last Life and Ten Days stay in Survival whatever the Hardcore switch says.
        assertEquals(Category.SURVIVAL, PLAIN.get("last_life").category());
        assertEquals(Category.SURVIVAL, PLAIN.get("ten_days_hardcore").category());
        assertNull(PLAIN.get("full_pool"));
        assertEquals("pack HARDCORE is off", PLAIN.whySkipped("full_pool"));
        assertNull(PLAIN.whySkipped("last_life"));
        assertNull(PLAIN.whySkipped("no_such_feat"));
    }

    @Test
    void siblingTabsMatchSectionTwelve() {
        block(COINAGE, 24, 720);
        block(FACTIONS, 14, 430);
        block(CHRONICLE, 6, 300);
        block(ATLAS, 2, 35);
        block(CROSS, 3, 175);
        block(FACTIONS_173, 1, 10);
        assertEquals(OLD_FACTIONS + 14 + 1, new FeatRegistry(true).inCategory(Category.FACTIONS).size(), "Dominion alone");
        assertEquals(OLD_FACTIONS_POINTS + 430 + 10, new FeatRegistry(true).inCategory(Category.FACTIONS).stream().mapToInt(f -> f.tier().points()).sum());
        assertEquals(24, new FeatRegistry(EnumSet.of(Sibling.COINAGE), EnumSet.noneOf(Pack.class)).inCategory(Category.COINAGE).size(), "Coinage alone");
        assertEquals(6, new FeatRegistry(EnumSet.of(Sibling.CHRONICLE), EnumSet.noneOf(Pack.class)).inCategory(Category.CHRONICLE).size());
        FeatRegistry atlas = new FeatRegistry(EnumSet.of(Sibling.ATLAS), EnumSet.noneOf(Pack.class));
        assertEquals(PLAIN.count() + 2, atlas.count());
        assertEquals(Category.EXPLORATION, atlas.get("on_the_map").category());
        for (String id : COINAGE) {
            assertEquals(Set.of(Sibling.COINAGE), FULL.get(id).requires(), id);
        }
        for (String id : FACTIONS_173) {
            assertEquals(Set.of(Sibling.DOMINION), FULL.get(id).requires(), id);
        }
        for (String id : FACTIONS) {
            assertEquals(Set.of(Sibling.DOMINION), FULL.get(id).requires(), id);
        }
        assertEquals(Set.of(Sibling.DOMINION), FULL.get("faction_founder").requires(), "the first fifteen need Dominion too");
        assertEquals(Set.of(Sibling.DOMINION, Sibling.COINAGE), FULL.get("war_chest").requires());
        assertEquals(Category.FACTIONS, FULL.get("treasurer").category());
        assertEquals(Category.COINAGE, FULL.get("allied_trade").category());
        assertTrue(new FeatRegistry(true).whySkipped("war_chest").startsWith("needs "), "Coinage is missing");
        assertNull(new FeatRegistry(true).get("war_chest"), "one of the two is missing");
        // Everything on at once. The plan's 512 leaves out the 15 Factions feats from 1.6.0.
        // The eleven mod tabs of 1.9.0 from docs/MODS-PLAN.md section 2: MODS_NINE from ModFeatsTest, MMO Skill
        // Tree 20 feats for 725 points and Endgame 20 for 785 from MmoEndgameFeatsTest.
        // 1.10.0 adds the four tabs counted from Feats' own keys. FULL is built on TrackerAPI 1.2.0, so the Fishing,
        // Arcanum and Music tabs of TrackerAPI 1.3.0 are out, see model/mods/PackFeatsTest.
        assertEquals(OLD_COUNT + 118 + 18 + 24 + OLD_FACTIONS + 14 + 1 + 6 + 2 + 3 + MODS_NINE + 20 + 20 + COUNTED, FULL.count());
        assertEquals(OLD_POINTS + 5_370 + 865 + 720 + OLD_FACTIONS_POINTS + 430 + 10 + 300 + 35 + 175 + MODS_NINE_POINTS + 725 + 785
                + COUNTED_POINTS, FULL.totalPoints());
    }

    @Test
    void closersAreTheHiddenBannerLastRowOfTheirTab() {
        for (Category tab : List.of(Category.EXPLORATION, Category.MINING, Category.BUILDING, Category.CRAFTING, Category.COMBAT,
                Category.SURVIVAL, Category.NATURE, Category.CHAPTER)) {
            List<Feat> rows = FULL.inCategory(tab);
            Feat last = rows.get(rows.size() - 1);
            assertEquals(tab.name().toLowerCase(java.util.Locale.ROOT) + "_complete", last.id(), tab + " ends with its closer");
            assertTrue(last.hidden() && last.banner(), last.id());
            assertEquals(Tier.PLATINUM, last.tier());
            assertEquals(List.of(new Requirement(StatKeys.CATEGORY_DONE_PREFIX + tab.name(), 1)), last.requirements());
            for (int i = 0; i < rows.size() - 1; i++) {
                Feat row = rows.get(i);
                assertFalse(row.requirements().stream().anyMatch(r -> r.key().startsWith(StatKeys.CATEGORY_DONE_PREFIX)),
                        "one closer per tab, " + row.id());
            }
        }
        List<Feat> misc = FULL.inCategory(Category.MISC);
        assertEquals("grand_slam", misc.get(misc.size() - 1).id());
        assertTrue(FULL.get("grand_slam").banner() && FULL.get("grandmaster").banner() && FULL.get("all_of_it").banner());
        assertFalse(FULL.get("ten_thousand").banner());
        assertEquals(StatKeys.PLATINUMS_DONE, FULL.get("grand_slam").requirements().get(0).key());
    }

    @Test
    void worldAdditionsReadTheServerTotalsAndFollowTheRightFeats() {
        int totals = 0;
        Set<String> keys = new LinkedHashSet<>();
        for (String id : WORLD) {
            Feat feat = FULL.get(id);
            assertNotNull(feat, id);
            assertEquals(Category.WORLD, feat.category());
            if (feat.trigger().kind() == Trigger.Kind.TOTAL) {
                totals++;
                keys.add(feat.trigger().key());
                assertEquals(Scope.WORLD, feat.scope());
            } else {
                assertEquals(Scope.FIRST, feat.scope());
                assertNotNull(FULL.get(feat.trigger().key()), id + " follows " + feat.trigger().key());
            }
        }
        assertEquals(9, totals);
        assertEquals(9, FULL.totalTriggered().size());
        assertEquals(Set.of(StatKeys.TOTAL_BLOCKS_BROKEN, StatKeys.TOTAL_KILLS, StatKeys.TOTAL_KILLS_BOSS, StatKeys.TOTAL_PLAYERS_KNOWN,
                StatKeys.TOTAL_PEAK_ONLINE, StatKeys.TOTAL_SERVER_DAYS, StatKeys.TOTAL_CHUNKS_GENERATED), keys);
        assertEquals(FeatRegistry.FULL_HOUSE_DEFAULT, FULL.get(FeatRegistry.FULL_HOUSE_ID).trigger().target());
        assertTrue(FULL.get("first_grand_slam").banner());
        assertEquals(List.of(FULL.get("first_grand_slam")), FULL.triggeredBy("grand_slam"));
        assertEquals(List.of(FULL.get("first_leviathan")), FULL.triggeredBy("leviathan"));
    }

    @Test
    void descriptionsStayPlain() {
        for (Feat feat : FULL.all()) {
            assertFalse(feat.description().contains("\u2014") || feat.description().contains(";"), feat.id());
            assertTrue(feat.description().endsWith("."), feat.id() + " ends with a full stop");
            assertFalse(feat.icon().isBlank(), feat.id());
        }
    }
}
