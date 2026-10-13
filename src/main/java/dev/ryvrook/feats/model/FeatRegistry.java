package dev.ryvrook.feats.model;

import dev.ryvrook.feats.stats.StatKeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.ryvrook.feats.model.Category.*;
import static dev.ryvrook.feats.model.Tier.*;

/** The built-in feat list. Order within a category is display order. */
public final class FeatRegistry {
    private final Map<String, Feat> byId = new LinkedHashMap<>();
    private final Map<String, List<Feat>> byKey = new HashMap<>();
    private final Map<Category, List<Feat>> byCategory = new EnumMap<>(Category.class);

    /** Personal feat id to the world and first feats its first unlock raises. */
    private final Map<String, List<Feat>> byAfter = new HashMap<>();
    private final List<Feat> flagged = new ArrayList<>();
    private final List<Feat> personal = new ArrayList<>();
    private final List<Feat> server = new ArrayList<>();

    public FeatRegistry() {
        for (Category c : Category.values()) {
            byCategory.put(c, new ArrayList<>());
        }
        defineAll();
        defineWorld();
        for (Map.Entry<String, List<Feat>> e : byAfter.entrySet()) {
            Feat source = byId.get(e.getKey());
            if (source == null || !source.isPersonal()) {
                throw new IllegalStateException("world feat follows unknown personal feat " + e.getKey());
            }
        }
        byCategory.replaceAll((c, list) -> Collections.unmodifiableList(list));
        byKey.replaceAll((k, list) -> Collections.unmodifiableList(list));
        byAfter.replaceAll((k, list) -> Collections.unmodifiableList(list));
        // Counts and the feats unlocked counter only see personal feats, so 100 percent stays reachable.
        dev.ryvrook.feats.stats.PlayerProgress.knownFeats(id -> {
            Feat f = byId.get(id);
            return f != null && f.isPersonal();
        });
        dev.ryvrook.feats.stats.PlayerProgress.serverFeats(id -> {
            Feat f = byId.get(id);
            return f != null && !f.isPersonal();
        });
    }

    public Feat get(String id) {
        return byId.get(id);
    }

    /** Every feat, personal, world and first, in display order. See {@link #personal()} for the counted ones. */
    public List<Feat> all() {
        return List.copyOf(byId.values());
    }

    /** The personal feats in display order, the ones totals, points and completion are made of. */
    public List<Feat> personal() {
        return Collections.unmodifiableList(personal);
    }

    /** World milestones and server firsts in display order. */
    public List<Feat> server() {
        return Collections.unmodifiableList(server);
    }

    /** World and first feats raised the first time anyone unlocks the given personal feat. */
    public List<Feat> triggeredBy(String personalFeatId) {
        return personalFeatId == null ? List.of() : byAfter.getOrDefault(personalFeatId, List.of());
    }

    /** World feats raised by a game flag. */
    public List<Feat> flagTriggered() {
        return Collections.unmodifiableList(flagged);
    }

    public List<Feat> inCategory(Category category) {
        return byCategory.get(category);
    }

    /** Feats that reference the given stat key in any requirement. */
    public List<Feat> dependingOn(String key) {
        return byKey.getOrDefault(key, List.of());
    }

    /** Number of personal feats. World and first feats are left out so completion can reach 100 percent. */
    public int count() {
        return personal.size();
    }

    /** Points of every personal feat. */
    public int totalPoints() {
        int sum = 0;
        for (Feat f : personal) {
            sum += f.tier().points();
        }
        return sum;
    }

    /** Number of every feat including world and first feats. */
    public int countAll() {
        return byId.size();
    }

    /** Number of world and first feats. */
    public int countServer() {
        return server.size();
    }

    // ------------------------------------------------------------------

    private void add(Feat feat) {
        if (byId.putIfAbsent(feat.id(), feat) != null) {
            throw new IllegalStateException("duplicate feat id " + feat.id());
        }
        byCategory.get(feat.category()).add(feat);
        for (Requirement r : feat.requirements()) {
            byKey.computeIfAbsent(r.key(), k -> new ArrayList<>()).add(feat);
        }
        if (feat.isPersonal()) {
            if (feat.category() == WORLD) {
                throw new IllegalStateException("personal feat " + feat.id() + " cannot sit in the World tab");
            }
            personal.add(feat);
            return;
        }
        if (feat.category() != WORLD) {
            throw new IllegalStateException(feat.scope() + " feat " + feat.id() + " belongs in the World tab");
        }
        server.add(feat);
        Trigger t = feat.trigger();
        if (t.kind() == Trigger.Kind.AFTER) {
            byAfter.computeIfAbsent(t.key(), k -> new ArrayList<>()).add(feat);
        } else {
            flagged.add(feat);
        }
    }

    /** A server milestone, reached once for the whole server. */
    private void world(String id, String name, String desc, Tier tier, String icon, Trigger trigger) {
        add(new Feat(id, name, desc, WORLD, tier, icon, List.of(), false, Scope.WORLD, trigger));
    }

    /** A server first, unlocked only by the first player to get there. */
    private void first(String id, String name, String desc, Tier tier, String icon, Trigger trigger) {
        add(new Feat(id, name, desc, WORLD, tier, icon, List.of(), false, Scope.FIRST, trigger));
    }

    private static Trigger flag(String gameFlag, int minLevel) {
        return Trigger.flag(gameFlag, minLevel);
    }

    private static Trigger after(String featId) {
        return Trigger.after(featId);
    }

    private void feat(String id, String name, String desc, Category cat, Tier tier, String icon, String key, long target) {
        add(new Feat(id, name, desc, cat, tier, icon, List.of(new Requirement(key, target)), false));
    }

    private void hidden(String id, String name, String desc, Category cat, Tier tier, String icon, String key, long target) {
        add(new Feat(id, name, desc, cat, tier, icon, List.of(new Requirement(key, target)), true));
    }

    private void multi(String id, String name, String desc, Category cat, Tier tier, String icon, Requirement... reqs) {
        add(new Feat(id, name, desc, cat, tier, icon, List.of(reqs), false));
    }

    private static Requirement req(String key, long target) {
        return new Requirement(key, target);
    }

    private static final long HOUR = 3600;

    private void defineAll() {
        // ---------------- Exploration ----------------
        feat("first_steps", "First Steps", "Travel 100 metres on foot.", EXPLORATION, BRONZE, "Tool_Map", StatKeys.DISTANCE, 100);
        feat("wanderer", "Wanderer", "Travel 1 kilometre.", EXPLORATION, BRONZE, "Tool_Map", StatKeys.DISTANCE, 1_000);
        feat("traveller", "Traveller", "Travel 10 kilometres.", EXPLORATION, SILVER, "Glider_Scrap", StatKeys.DISTANCE, 10_000);
        feat("globetrotter", "Globetrotter", "Travel 50 kilometres.", EXPLORATION, GOLD, "Glider_Scrap", StatKeys.DISTANCE, 50_000);
        feat("around_orbis", "Around Orbis", "Travel 200 kilometres. That is a very long walk.", EXPLORATION, PLATINUM, "Glider_Scrap", StatKeys.DISTANCE, 200_000);
        // The default world names 5 zones on discovery, Emerald Wilds, Howling Sands, Whisperfrost
        // Frontiers, Devastated Lands and the Oceans, so the zone ladder tops out at 5.
        feat("new_horizons", "New Horizons", "Discover your first zone.", EXPLORATION, BRONZE, "Tool_Map", StatKeys.ZONES_DISCOVERED, 1);
        feat("sightseer", "Sightseer", "Discover 2 zones.", EXPLORATION, BRONZE, "Tool_Map", StatKeys.ZONES_DISCOVERED, 2);
        feat("cartographer", "Cartographer", "Discover 3 zones.", EXPLORATION, SILVER, "Deco_Map", StatKeys.ZONES_DISCOVERED, 3);
        feat("pathfinder", "Pathfinder", "Discover 4 zones.", EXPLORATION, GOLD, "Deco_Map", StatKeys.ZONES_DISCOVERED, 4);
        feat("master_explorer", "Master Explorer", "Discover all 5 zones.", EXPLORATION, PLATINUM, "Deco_Map", StatKeys.ZONES_DISCOVERED, 5);
        feat("treasure_hunter", "Treasure Hunter", "Open 10 chests.", EXPLORATION, BRONZE, "Furniture_Ancient_Chest_Small", StatKeys.CHESTS_OPENED, 10);
        feat("tomb_raider", "Tomb Raider", "Open 100 chests.", EXPLORATION, SILVER, "Furniture_Ancient_Chest_Large", StatKeys.CHESTS_OPENED, 100);
        feat("loot_goblin", "Loot Goblin", "Open 500 chests.", EXPLORATION, GOLD, "Furniture_Ancient_Chest_Large", StatKeys.CHESTS_OPENED, 500);
        feat("gatekeeper", "Gatekeeper", "Build an Ancient Gateway.", EXPLORATION, GOLD, "Portal_Device", StatKeys.CRAFTED_PREFIX + "Portal_Device", 1);
        hidden("take_flight", "Take Flight", "Get your hands on a Scrap Glider.", EXPLORATION, GOLD, "Glider_Scrap", StatKeys.OBTAINED_PREFIX + "Glider_Scrap", 1);

        // ---------------- Mining ----------------
        feat("rock_bottom", "Rock Bottom", "Break your first block.", MINING, BRONZE, "Tool_Pickaxe_Crude", StatKeys.BLOCKS_BROKEN, 1);
        feat("digger", "Digger", "Break 100 blocks.", MINING, BRONZE, "Tool_Pickaxe_Copper", StatKeys.BLOCKS_BROKEN, 100);
        feat("excavator", "Excavator", "Break 1,000 blocks.", MINING, SILVER, "Tool_Pickaxe_Iron", StatKeys.BLOCKS_BROKEN, 1_000);
        feat("tunneler", "Tunneler", "Break 10,000 blocks.", MINING, GOLD, "Tool_Pickaxe_Cobalt", StatKeys.BLOCKS_BROKEN, 10_000);
        feat("terraformer", "Terraformer", "Break 100,000 blocks.", MINING, PLATINUM, "Tool_Pickaxe_Adamantite", StatKeys.BLOCKS_BROKEN, 100_000);
        feat("prospector", "Prospector", "Mine 10 ore blocks.", MINING, BRONZE, "Ore_Copper", StatKeys.ORE_MINED, 10);
        feat("miner", "Miner", "Mine 100 ore blocks.", MINING, SILVER, "Ore_Iron", StatKeys.ORE_MINED, 100);
        feat("ore_baron", "Ore Baron", "Mine 1,000 ore blocks.", MINING, GOLD, "Ore_Gold", StatKeys.ORE_MINED, 1_000);
        feat("motherlode", "Motherlode", "Mine 5,000 ore blocks.", MINING, PLATINUM, "Ore_Mithril", StatKeys.ORE_MINED, 5_000);
        feat("copper_rush", "Copper Rush", "Mine 50 copper ore.", MINING, BRONZE, "Ore_Copper", StatKeys.ORE_PREFIX + "Copper", 50);
        feat("iron_will", "Iron Will", "Mine 100 iron ore.", MINING, SILVER, "Ore_Iron", StatKeys.ORE_PREFIX + "Iron", 100);
        feat("gold_digger", "Gold Digger", "Mine 25 gold ore.", MINING, SILVER, "Ore_Gold", StatKeys.ORE_PREFIX + "Gold", 25);
        feat("silver_lining", "Silver Lining", "Mine 25 silver ore.", MINING, SILVER, "Ore_Silver", StatKeys.ORE_PREFIX + "Silver", 25);
        feat("cobalt_core", "Cobalt Core", "Mine 50 cobalt ore.", MINING, GOLD, "Ore_Cobalt", StatKeys.ORE_PREFIX + "Cobalt", 50);
        feat("thorium_touch", "Thorium Touch", "Mine 50 thorium ore.", MINING, GOLD, "Ore_Thorium", StatKeys.ORE_PREFIX + "Thorium", 50);
        feat("adamant", "Adamant", "Mine 10 adamantite ore.", MINING, PLATINUM, "Ore_Adamantite", StatKeys.ORE_PREFIX + "Adamantite", 10);
        // Every metal that world generation places in 0.7.0. Mithril, onyxium and prisma are never placed.
        multi("full_spectrum", "Full Spectrum", "Mine every metal, from copper all the way to adamantite.",
                MINING, GOLD, "Ore_Adamantite_Magma",
                req(StatKeys.ORE_PREFIX + "Copper", 1), req(StatKeys.ORE_PREFIX + "Iron", 1),
                req(StatKeys.ORE_PREFIX + "Silver", 1), req(StatKeys.ORE_PREFIX + "Gold", 1),
                req(StatKeys.ORE_PREFIX + "Cobalt", 1), req(StatKeys.ORE_PREFIX + "Thorium", 1),
                req(StatKeys.ORE_PREFIX + "Adamantite", 1));

        // ---------------- Building ----------------
        feat("cornerstone", "Cornerstone", "Place your first block.", BUILDING, BRONZE, "Wood_Ash_Trunk", StatKeys.BLOCKS_PLACED, 1);
        feat("builder", "Builder", "Place 100 blocks.", BUILDING, BRONZE, "Wood_Ash_Trunk", StatKeys.BLOCKS_PLACED, 100);
        feat("architect", "Architect", "Place 1,000 blocks.", BUILDING, SILVER, "Rock_Aqua_Brick", StatKeys.BLOCKS_PLACED, 1_000);
        feat("master_builder", "Master Builder", "Place 10,000 blocks.", BUILDING, GOLD, "Rock_Aqua_Brick", StatKeys.BLOCKS_PLACED, 10_000);
        feat("metropolis", "Metropolis", "Place 100,000 blocks.", BUILDING, PLATINUM, "Rock_Aqua_Brick_Ornate", StatKeys.BLOCKS_PLACED, 100_000);
        feat("light_it_up", "Light It Up", "Place 10 torches.", BUILDING, BRONZE, "Furniture_Crude_Torch", StatKeys.PLACED_PREFIX + "torch", 10);
        feat("beacon_of_hope", "Beacon of Hope", "Place 100 torches.", BUILDING, SILVER, "Furniture_Crude_Torch", StatKeys.PLACED_PREFIX + "torch", 100);
        feat("lantern_festival", "Lantern Festival", "Place 25 lanterns.", BUILDING, SILVER, "Deco_Lantern", StatKeys.PLACED_PREFIX + "lantern", 25);
        feat("home_sweet_home", "Home Sweet Home", "Place a bed.", BUILDING, BRONZE, "Furniture_Crude_Bed", StatKeys.PLACED_PREFIX + "bed", 1);
        feat("well_furnished", "Well Furnished", "Place 50 pieces of furniture.", BUILDING, SILVER, "Furniture_Crude_Table", StatKeys.PLACED_PREFIX + "furniture", 50);
        feat("interior_designer", "Interior Designer", "Place 250 pieces of furniture.", BUILDING, GOLD, "Furniture_Ancient_Table", StatKeys.PLACED_PREFIX + "furniture", 250);
        feat("workshop", "Workshop", "Place 5 crafting benches.", BUILDING, SILVER, "Bench_WorkBench", StatKeys.PLACED_PREFIX + "bench", 5);
        feat("hoarder", "Hoarder", "Place 10 chests.", BUILDING, SILVER, "Furniture_Crude_Chest_Small", StatKeys.PLACED_PREFIX + "chest", 10);
        feat("warehouse", "Warehouse", "Place 50 chests.", BUILDING, GOLD, "Furniture_Crude_Chest_Large", StatKeys.PLACED_PREFIX + "chest", 50);

        // ---------------- Crafting ----------------
        feat("handy", "Handy", "Craft your first item.", CRAFTING, BRONZE, "Bench_WorkBench", StatKeys.CRAFTED, 1);
        feat("apprentice", "Apprentice", "Craft 50 items.", CRAFTING, BRONZE, "Ingredient_Stick", StatKeys.CRAFTED, 50);
        feat("artisan", "Artisan", "Craft 500 items.", CRAFTING, SILVER, "Ingredient_Bar_Copper", StatKeys.CRAFTED, 500);
        feat("master_crafter", "Master Crafter", "Craft 2,500 items.", CRAFTING, GOLD, "Ingredient_Bar_Iron", StatKeys.CRAFTED, 2_500);
        feat("industrialist", "Industrialist", "Craft 10,000 items.", CRAFTING, PLATINUM, "Ingredient_Bar_Thorium", StatKeys.CRAFTED, 10_000);
        feat("first_pickaxe", "Pick of the Litter", "Craft a pickaxe.", CRAFTING, BRONZE, "Tool_Pickaxe_Crude", StatKeys.CRAFT_GROUP_PREFIX + "pickaxe", 1);
        feat("toolmaker", "Toolmaker", "Craft 25 tools.", CRAFTING, SILVER, "Tool_Hatchet_Iron", StatKeys.CRAFT_GROUP_PREFIX + "tool", 25);
        feat("armed", "Armed", "Craft a weapon.", CRAFTING, BRONZE, "Weapon_Axe_Crude", StatKeys.CRAFT_GROUP_PREFIX + "weapon", 1);
        feat("weaponsmith", "Weaponsmith", "Craft 25 weapons.", CRAFTING, SILVER, "Weapon_Sword_Bronze", StatKeys.CRAFT_GROUP_PREFIX + "weapon", 25);
        feat("armorer", "Armorer", "Craft 4 pieces of armour.", CRAFTING, SILVER, "Armor_Iron_Chest", StatKeys.CRAFT_GROUP_PREFIX + "armor", 4);
        multi("ironclad", "Ironclad", "Craft a full set of iron armour.", CRAFTING, SILVER, "Armor_Iron_Head",
                req(StatKeys.CRAFTED_PREFIX + "Armor_Iron_Head", 1), req(StatKeys.CRAFTED_PREFIX + "Armor_Iron_Chest", 1),
                req(StatKeys.CRAFTED_PREFIX + "Armor_Iron_Hands", 1), req(StatKeys.CRAFTED_PREFIX + "Armor_Iron_Legs", 1));
        multi("adamant_wall", "Adamant Wall", "Craft a full set of adamantite armour.", CRAFTING, PLATINUM, "Armor_Adamantite_Chest",
                req(StatKeys.CRAFTED_PREFIX + "Armor_Adamantite_Head", 1), req(StatKeys.CRAFTED_PREFIX + "Armor_Adamantite_Chest", 1),
                req(StatKeys.CRAFTED_PREFIX + "Armor_Adamantite_Hands", 1), req(StatKeys.CRAFTED_PREFIX + "Armor_Adamantite_Legs", 1));
        feat("adamant_edge", "Adamant Edge", "Forge an adamantite weapon.", CRAFTING, GOLD, "Weapon_Sword_Adamantite", StatKeys.CRAFT_GROUP_PREFIX + "weapon_adamantite", 1);
        feat("blacksmith", "Blacksmith", "Smelt 50 metal bars.", CRAFTING, SILVER, "Ingredient_Bar_Iron", StatKeys.CRAFT_GROUP_PREFIX + "bar", 50);
        feat("metallurgist", "Metallurgist", "Smelt 500 metal bars.", CRAFTING, GOLD, "Ingredient_Bar_Cobalt", StatKeys.CRAFT_GROUP_PREFIX + "bar", 500);
        feat("adamantite_smith", "Adamantite Smith", "Smelt an adamantite bar.", CRAFTING, PLATINUM, "Ingredient_Bar_Adamantite", StatKeys.CRAFTED_PREFIX + "Ingredient_Bar_Adamantite", 1);
        feat("chef", "Chef", "Cook 25 meals.", CRAFTING, SILVER, "Food_Bread", StatKeys.CRAFT_GROUP_PREFIX + "food", 25);
        feat("head_chef", "Head Chef", "Cook 250 meals.", CRAFTING, GOLD, "Food_Pie_Meat", StatKeys.CRAFT_GROUP_PREFIX + "food", 250);
        feat("alchemist", "Alchemist", "Brew 10 potions.", CRAFTING, SILVER, "Potion_Health", StatKeys.CRAFT_GROUP_PREFIX + "potion", 10);
        feat("potion_master", "Potion Master", "Brew 100 potions.", CRAFTING, GOLD, "Potion_Health_Greater", StatKeys.CRAFT_GROUP_PREFIX + "potion", 100);
        feat("carpenter", "Carpenter", "Craft 25 pieces of furniture.", CRAFTING, SILVER, "Furniture_Crude_Stool", StatKeys.CRAFT_GROUP_PREFIX + "furniture", 25);
        feat("movie_night", "Movie Night", "Cook 10 popcorn.", CRAFTING, BRONZE, "Food_Popcorn", StatKeys.CRAFTED_PREFIX + "Food_Popcorn", 10);
        feat("bench_press", "Bench Press", "Craft 5 benches.", CRAFTING, SILVER, "Bench_Furnace", StatKeys.CRAFT_GROUP_PREFIX + "bench", 5);

        // ---------------- Combat ----------------
        feat("first_blood", "First Blood", "Defeat your first creature.", COMBAT, BRONZE, "Weapon_Sword_Bronze", StatKeys.KILLS, 1);
        feat("hunter", "Hunter", "Defeat 50 creatures.", COMBAT, BRONZE, "Weapon_Sword_Bronze", StatKeys.KILLS, 50);
        feat("slayer", "Slayer", "Defeat 250 creatures.", COMBAT, SILVER, "Weapon_Axe_Iron", StatKeys.KILLS, 250);
        feat("warlord", "Warlord", "Defeat 1,000 creatures.", COMBAT, GOLD, "Weapon_Battleaxe_Iron", StatKeys.KILLS, 1_000);
        feat("legend", "Legend", "Defeat 5,000 creatures.", COMBAT, PLATINUM, "Weapon_Battleaxe_Mithril", StatKeys.KILLS, 5_000);
        feat("sharpshooter", "Sharpshooter", "Defeat 50 creatures with projectiles.", COMBAT, SILVER, "Weapon_Arrow_Iron", StatKeys.KILLS_RANGED, 50);
        feat("deadeye", "Deadeye", "Defeat 250 creatures with projectiles.", COMBAT, GOLD, "Weapon_Arrow_Deadeye", StatKeys.KILLS_RANGED, 250);
        feat("undead_bane", "Bane of the Undead", "Defeat 100 undead.", COMBAT, SILVER, "Weapon_Club_Zombie_Arm", StatKeys.KILL_GROUP_PREFIX + "undead", 100);
        feat("grave_keeper", "Grave Keeper", "Defeat 500 undead.", COMBAT, GOLD, "Weapon_Club_Zombie_Arm", StatKeys.KILL_GROUP_PREFIX + "undead", 500);
        feat("trork_tamer", "Trork Tamer", "Defeat 50 Trorks.", COMBAT, SILVER, "Weapon_Axe_Stone_Trork", StatKeys.KILL_GROUP_PREFIX + "trork", 50);
        feat("outlander_nemesis", "Outlander Nemesis", "Defeat 50 Outlanders.", COMBAT, SILVER, "Weapon_Club_Tribal", StatKeys.KILL_GROUP_PREFIX + "outlander", 50);
        feat("exterminator", "Exterminator", "Defeat 50 Scaraks.", COMBAT, SILVER, "Weapon_Battleaxe_Scarab", StatKeys.KILL_GROUP_PREFIX + "scarak", 50);
        feat("feran_foe", "Feran Foe", "Defeat 25 Feran.", COMBAT, SILVER, "Weapon_Claws_Tribal", StatKeys.KILL_GROUP_PREFIX + "feran", 25);
        feat("elemental_ender", "Elemental Ender", "Defeat 25 golems or spirits.", COMBAT, GOLD, "Weapon_Battleaxe_Steel_Rusty", StatKeys.KILL_GROUP_PREFIX + "elemental", 25);
        feat("void_walker", "Void Walker", "Defeat 25 creatures of the Void.", COMBAT, SILVER, "Weapon_Battleaxe_Scythe_Void", StatKeys.KILL_GROUP_PREFIX + "void", 25);
        feat("duke_dethroned", "Duke Dethroned", "Defeat the Goblin Duke.", COMBAT, SILVER, "Weapon_Club_Scrap_Washboard", StatKeys.KILL_GROUP_PREFIX + "goblin_duke", 1);
        feat("giant_killer", "Giant Killer", "Defeat a boss.", COMBAT, GOLD, "Weapon_Club_Doomed", StatKeys.KILLS_BOSS, 1);
        feat("boss_rush", "Boss Rush", "Defeat 10 bosses.", COMBAT, PLATINUM, "Weapon_Battleaxe_Doomed", StatKeys.KILLS_BOSS, 10);
        hidden("dragon_slayer", "Dragon Slayer", "Defeat a dragon.", COMBAT, PLATINUM, "Weapon_Axe_Doomed", StatKeys.KILL_GROUP_PREFIX + "dragon", 1);
        feat("heavy_hitter", "Heavy Hitter", "Deal 1,000 damage.", COMBAT, BRONZE, "Weapon_Club_Iron", StatKeys.DAMAGE_DEALT, 1_000);
        feat("bruiser", "Bruiser", "Deal 10,000 damage.", COMBAT, SILVER, "Weapon_Club_Iron", StatKeys.DAMAGE_DEALT, 10_000);
        feat("destroyer", "Destroyer", "Deal 100,000 damage.", COMBAT, GOLD, "Weapon_Battleaxe_Cobalt", StatKeys.DAMAGE_DEALT, 100_000);
        feat("tough", "Tough", "Take 1,000 damage and live to tell the tale.", COMBAT, BRONZE, "Armor_Iron_Chest", StatKeys.DAMAGE_TAKEN, 1_000);
        feat("punching_bag", "Punching Bag", "Take 10,000 damage.", COMBAT, SILVER, "Armor_Iron_Chest", StatKeys.DAMAGE_TAKEN, 10_000);
        feat("unbreakable", "Unbreakable", "Take 100,000 damage.", COMBAT, GOLD, "Armor_Cobalt_Chest", StatKeys.DAMAGE_TAKEN, 100_000);
        hidden("duelist", "Duelist", "Defeat another player.", COMBAT, GOLD, "Weapon_Daggers_Bronze", StatKeys.KILLS_PLAYER, 1);

        // ---------------- Survival ----------------
        feat("welcome", "Welcome to Orbis", "Join the world.", SURVIVAL, BRONZE, "Deco_Kweebec_Plush", StatKeys.JOINS, 1);
        feat("regular", "Regular", "Play 10 sessions.", SURVIVAL, BRONZE, "Deco_Kweebec_Plush", StatKeys.JOINS, 10);
        feat("local", "Local", "Play 50 sessions.", SURVIVAL, SILVER, "Deco_Kweebec_Plush", StatKeys.JOINS, 50);
        feat("dedicated", "Dedicated", "Play for 1 hour.", SURVIVAL, BRONZE, "Deco_Trophy_Harvest", StatKeys.PLAYTIME, HOUR);
        feat("devoted", "Devoted", "Play for 10 hours.", SURVIVAL, SILVER, "Deco_Trophy_Harvest", StatKeys.PLAYTIME, 10 * HOUR);
        feat("veteran", "Veteran", "Play for 50 hours.", SURVIVAL, GOLD, "Deco_Treasure", StatKeys.PLAYTIME, 50 * HOUR);
        hidden("touch_grass", "Touch Grass", "Play for 200 hours. Maybe go outside?", SURVIVAL, PLATINUM, "Deco_Treasure_Pile_Large", StatKeys.PLAYTIME, 200 * HOUR);
        feat("first_death", "Everybody Dies", "Die for the first time.", SURVIVAL, BRONZE, "Deco_Bone_Skulls_Wall", StatKeys.DEATHS, 1);
        feat("nine_lives", "Nine Lives", "Die 9 times.", SURVIVAL, SILVER, "Deco_Bone_Skulls_Wall", StatKeys.DEATHS, 9);
        hidden("gravity_check", "Gravity Check", "Die from a fall.", SURVIVAL, BRONZE, "Deco_Bone_Ribs_Feran", StatKeys.DEATH_CAUSE_PREFIX + "fall", 1);
        hidden("sleeping_with_fishes", "Sleeping with the Fishes", "Drown.", SURVIVAL, BRONZE, "Fish_Minnow_Item", StatKeys.DEATH_CAUSE_PREFIX + "drowning", 1);
        feat("well_rested", "Well Rested", "Sleep in a bed.", SURVIVAL, BRONZE, "Furniture_Crude_Bed", StatKeys.BEDS_USED, 1);
        feat("sleepyhead", "Sleepyhead", "Sleep 25 nights.", SURVIVAL, SILVER, "Furniture_Ancient_Bed", StatKeys.BEDS_USED, 25);
        feat("hibernation", "Hibernation", "Sleep 100 nights.", SURVIVAL, GOLD, "Furniture_Ancient_Bed", StatKeys.BEDS_USED, 100);
        feat("bench_warmer", "Bench Warmer", "Use a crafting bench 50 times.", SURVIVAL, SILVER, "Bench_WorkBench", StatKeys.BENCHES_USED, 50);
        feat("well_fed", "Well Fed", "Eat 50 meals.", SURVIVAL, BRONZE, "Food_Bread", StatKeys.FOOD_EATEN, 50);
        feat("glutton", "Glutton", "Eat 500 meals. The Feastmaster would be proud.", SURVIVAL, SILVER, "Food_Cheese", StatKeys.FOOD_EATEN, 500);
        feat("potion_addict", "Potion Addict", "Drink 50 potions.", SURVIVAL, SILVER, "Potion_Health", StatKeys.POTIONS_DRUNK, 50);
        feat("potion_seller", "Potion Seller", "Pick up 25 potions.", SURVIVAL, SILVER, "Potion_Health_Small", StatKeys.PICKED_PREFIX + "Potion_Health", 25);

        // ---------------- Nature ----------------
        feat("lumberjack", "Lumberjack", "Chop 10 logs.", NATURE, BRONZE, "Tool_Hatchet_Crude", StatKeys.WOOD_CHOPPED, 10);
        feat("logger", "Logger", "Chop 250 logs.", NATURE, SILVER, "Tool_Hatchet_Iron", StatKeys.WOOD_CHOPPED, 250);
        feat("deforestation", "Deforestation", "Chop 2,500 logs.", NATURE, GOLD, "Tool_Hatchet_Cobalt", StatKeys.WOOD_CHOPPED, 2_500);
        feat("green_thumb", "Green Thumb", "Plant 10 crops.", NATURE, BRONZE, "Plant_Crop_Carrot_Item", StatKeys.PLACED_PREFIX + "crop", 10);
        feat("farmer", "Farmer", "Plant 100 crops.", NATURE, SILVER, "Plant_Crop_Corn_Item", StatKeys.PLACED_PREFIX + "crop", 100);
        feat("agriculturist", "Agriculturist", "Plant 1,000 crops.", NATURE, GOLD, "Plant_Crop_Cauliflower_Item", StatKeys.PLACED_PREFIX + "crop", 1_000);
        feat("harvest", "Harvest", "Gather 50 crops or food.", NATURE, BRONZE, "Plant_Crop_Carrot_Item", StatKeys.HARVESTED, 50);
        feat("bumper_crop", "Bumper Crop", "Gather 500 crops or food.", NATURE, SILVER, "Food_Pie_Apple", StatKeys.HARVESTED, 500);
        feat("forager", "Forager", "Pick up 100 items.", NATURE, BRONZE, "Ingredient_Stick", StatKeys.PICKED_UP, 100);
        feat("collector", "Collector", "Pick up 1,000 items.", NATURE, SILVER, "Ingredient_Stick", StatKeys.PICKED_UP, 1_000);
        feat("magpie", "Magpie", "Pick up 10,000 items.", NATURE, GOLD, "Deco_Treasure_Pile_Small", StatKeys.PICKED_UP, 10_000);
        feat("fisher", "Gone Fishing", "Catch a fish.", NATURE, BRONZE, "Fish_Trout_Rainbow_Item", StatKeys.FISH_CAUGHT, 1);
        feat("angler", "Angler", "Catch 25 fish.", NATURE, SILVER, "Fish_Salmon_Item", StatKeys.FISH_CAUGHT, 25);
        feat("master_angler", "Master Angler", "Catch 100 fish.", NATURE, GOLD, "Fish_Pike_Item", StatKeys.FISH_CAUGHT, 100);
        hidden("big_catch", "The Big One", "Land a hammerhead shark.", NATURE, GOLD, "Fish_Shark_Hammerhead_Item", StatKeys.PICKED_PREFIX + "Fish_Shark_Hammerhead_Item", 1);
        feat("butcher", "Butcher", "Slaughter 25 livestock.", NATURE, SILVER, "Food_Beef_Raw", StatKeys.KILL_GROUP_PREFIX + "livestock", 25);
        feat("big_game", "Big Game Hunter", "Defeat 10 predators.", NATURE, SILVER, "Food_Wildmeat_Raw", StatKeys.KILL_GROUP_PREFIX + "predator", 10);
        hidden("kweebec_grudge", "Kweebec Grudge", "Defeat 10 Kweebecs. Why would you do that?", NATURE, SILVER, "Armor_Kweebec_Head", StatKeys.KILL_GROUP_PREFIX + "kweebec", 10);

        // ---------------- Chapter I ----------------
        // Goblins, the Void and the new bosses
        feat("goblin_slayer", "Goblin Slayer", "Defeat 50 goblins.", CHAPTER, SILVER, "Weapon_Club_Scrap", StatKeys.KILL_GROUP_PREFIX + "goblin", 50);
        feat("scrap_heap", "Scrap Heap", "Defeat 100 goblins.", CHAPTER, GOLD, "Weapon_Club_Scrap", StatKeys.KILL_GROUP_PREFIX + "goblin", 100);
        feat("goblin_warlord", "Goblin Warlord", "Defeat 500 goblins. They will write songs about you. Bad ones.", CHAPTER, PLATINUM, "Weapon_Mace_Scrap", StatKeys.KILL_GROUP_PREFIX + "goblin", 500);
        multi("goblinologist", "Goblinologist", "Defeat one of every kind of goblin.", CHAPTER, GOLD, "Deco_Goblin_Plush",
                req(StatKeys.KILL_GROUP_PREFIX + "goblin_scrapper", 1), req(StatKeys.KILL_GROUP_PREFIX + "goblin_miner", 1),
                req(StatKeys.KILL_GROUP_PREFIX + "goblin_burner", 1), req(StatKeys.KILL_GROUP_PREFIX + "goblin_lobber", 1),
                req(StatKeys.KILL_GROUP_PREFIX + "goblin_guardian", 1), req(StatKeys.KILL_GROUP_PREFIX + "goblin_thief", 1),
                req(StatKeys.KILL_GROUP_PREFIX + "goblin_glutton", 1), req(StatKeys.KILL_PREFIX + "Dungeon_Feastmaster_Boss", 1),
                req(StatKeys.KILL_GROUP_PREFIX + "goblin_duke", 1));
        hidden("second_helping", "Seconds", "Defeat a Goblin Glutton.", CHAPTER, SILVER, "Food_Pie_Meat", StatKeys.KILL_GROUP_PREFIX + "goblin_glutton", 1);
        feat("arachnophobia", "Arachnophobia", "Defeat 25 spiders.", CHAPTER, SILVER, "Ingredient_Spider_Silk", StatKeys.KILL_GROUP_PREFIX + "spider", 25);
        feat("web_warden", "Web Warden", "Defeat 100 spiders.", CHAPTER, GOLD, "Ingredient_Spider_Silk", StatKeys.KILL_GROUP_PREFIX + "spider", 100);
        feat("void_purge", "Void Purge", "Defeat 100 creatures of the Void.", CHAPTER, GOLD, "Weapon_Daggers_Void_Crystal", StatKeys.KILL_GROUP_PREFIX + "void", 100);
        feat("anomaly_closed", "Anomaly Closed", "Destroy 5 Void anomaly crystals.", CHAPTER, SILVER, "Rock_Crystal_Void_Block", StatKeys.KILL_GROUP_PREFIX + "void_anomaly", 5);
        feat("keystone", "Keystone", "Shatter a Void anomaly keystone.", CHAPTER, SILVER, "Rock_Crystal_Void_Block", StatKeys.KILL_PREFIX + "Void_Anomaly_Crystal_Keystone", 1);
        feat("spectre_hunter", "Spectre Hunter", "Defeat 10 Void Spectres.", CHAPTER, SILVER, "Weapon_Staff_Voidcrystal", StatKeys.KILL_GROUP_PREFIX + "void_spectre", 10);
        multi("void_scholar", "Know the Void", "Defeat every kind of Voidspawn.", CHAPTER, GOLD, "Ingredient_Crystal_Void",
                req(StatKeys.KILL_GROUP_PREFIX + "void_crawler", 1), req(StatKeys.KILL_GROUP_PREFIX + "void_eye", 1),
                req(StatKeys.KILL_GROUP_PREFIX + "void_larva", 1), req(StatKeys.KILL_GROUP_PREFIX + "void_spawn", 1),
                req(StatKeys.KILL_GROUP_PREFIX + "void_spectre", 1), req(StatKeys.KILL_GROUP_PREFIX + "void_tentacle", 1));
        feat("revenant_slayer", "Revenant Slayer", "Defeat a Revenant Guardian.", CHAPTER, GOLD, "Weapon_Longsword_Ruined_Giant", StatKeys.KILL_GROUP_PREFIX + "revenant", 1);
        feat("feastmaster", "Feast Interrupted", "Defeat the Goblin Feastmaster before dessert.", CHAPTER, GOLD, "Food_Popcorn", StatKeys.KILL_PREFIX + "Dungeon_Feastmaster_Boss", 1);
        feat("gobliterated", "Gobliterated", "Defeat the Gobliterator.", CHAPTER, PLATINUM, "Ingredient_Gobliterator_Core", StatKeys.KILL_GROUP_PREFIX + "gobliterator", 1);
        hidden("gobliterated_hard", "Overclocked", "Defeat the Gobliterator on hard mode.", CHAPTER, PLATINUM, "Ingredient_Gobliterator_Core", StatKeys.KILL_GROUP_PREFIX + "gobliterator_hm", 1);
        feat("scrap_metal", "Scrap Metal", "Defeat the Gobliterator 5 times.", CHAPTER, GOLD, "Ingredient_Gadget_Assembly", StatKeys.KILL_GROUP_PREFIX + "gobliterator", 5);
        hidden("nothing_to_lose", "Nothing to Lose", "Defeat the Gobliterator on hard mode wearing no armour.", CHAPTER, PLATINUM, "Weapon_Bangstick", StatKeys.GOBLITERATOR_HM_UNARMOURED, 1);
        hidden("not_a_scratch", "Not a Scratch", "Defeat the Gobliterator without dying in The Deepworks.", CHAPTER, PLATINUM, "Potion_Health_Greater", StatKeys.GOBLITERATOR_DEATHLESS, 1);
        feat("chapter_closed", "Chapter Closed", "Step through the Dungeon Completion Portal in The Deepworks.", CHAPTER, GOLD, "Dungeon_Portal_End", StatKeys.USE_PREFIX + "Dungeon_Portal_End", 1);
        feat("bigger_pockets", "Bigger Pockets", "Trade two Gobliterator Cores for the largest backpack.", CHAPTER, PLATINUM, "Upgrade_Backpack_4", StatKeys.OBTAINED_PREFIX + "Upgrade_Backpack_4", 1);
        feat("voidtaken", "Bear Necessities", "Defeat the Voidtaken bear.", CHAPTER, GOLD, "Ingredient_Voidtaken_Grizzly_Hide", StatKeys.KILL_PREFIX + "Bear_Voidtaken", 1);
        feat("dread_wade", "Weed Killer", "Defeat The Hedera in The Dread Wade.", CHAPTER, GOLD, "PortalKey_Hederas_Lair", StatKeys.KILL_PREFIX + "Hedera", 1);

        // Runestones and abilities
        feat("first_rune", "Rune Awakening", "Craft your first Runestone.", CHAPTER, BRONZE, "Rune_Empty", StatKeys.CRAFT_GROUP_PREFIX + "rune", 1);
        feat("runesmith", "Runesmith", "Craft 10 Runestones.", CHAPTER, SILVER, "Ingredient_Rune_Shard", StatKeys.CRAFT_GROUP_PREFIX + "rune", 10);
        multi("full_arsenal", "Full Arsenal", "Craft all seven Ability Runestones.", CHAPTER, GOLD, "Rune_Fireball",
                req(StatKeys.CRAFTED_PREFIX + "Rune_ChainHook", 1), req(StatKeys.CRAFTED_PREFIX + "Rune_ChargedShot", 1),
                req(StatKeys.CRAFTED_PREFIX + "Rune_Enrage", 1), req(StatKeys.CRAFTED_PREFIX + "Rune_Fireball", 1),
                req(StatKeys.CRAFTED_PREFIX + "Rune_GroundSlam", 1), req(StatKeys.CRAFTED_PREFIX + "Rune_PoisonImbue", 1),
                req(StatKeys.CRAFTED_PREFIX + "Rune_WindStrike", 1));
        multi("modifier_collector", "Fine Tuned", "Craft all eight Modifier Runestones.", CHAPTER, GOLD, "Rune_Quickening",
                req(StatKeys.CRAFTED_PREFIX + "Rune_Wither", 1), req(StatKeys.CRAFTED_PREFIX + "Rune_Rend", 1),
                req(StatKeys.CRAFTED_PREFIX + "Rune_Quickening", 1), req(StatKeys.CRAFTED_PREFIX + "Rune_Thrift", 1),
                req(StatKeys.CRAFTED_PREFIX + "Rune_Aoe_Up", 1), req(StatKeys.CRAFTED_PREFIX + "Rune_Duration_Up", 1),
                req(StatKeys.CRAFTED_PREFIX + "Rune_Fork", 1), req(StatKeys.CRAFTED_PREFIX + "Rune_Ricochet", 1));
        feat("spellcaster", "Spellcaster", "Cast 10 abilities.", CHAPTER, BRONZE, "Rune_Fireball", StatKeys.ABILITY_CASTS, 10);
        feat("battlemage", "Battlemage", "Cast 250 abilities.", CHAPTER, SILVER, "Rune_WindStrike", StatKeys.ABILITY_CASTS, 250);
        feat("archmage", "Archmage", "Cast 2,500 abilities.", CHAPTER, GOLD, "Rune_ChargedShot", StatKeys.ABILITY_CASTS, 2_500);
        feat("pyromancer", "Pyromancer", "Cast Fireball 100 times.", CHAPTER, BRONZE, "Rune_Fireball", StatKeys.CAST_PREFIX + "Fireball", 100);
        feat("earthshaker", "Earthshaker", "Cast Ground Slam 100 times.", CHAPTER, BRONZE, "Rune_GroundSlam", StatKeys.CAST_PREFIX + "GroundSlam", 100);
        feat("berserker", "Seeing Red", "Cast Enrage 50 times.", CHAPTER, BRONZE, "Rune_Enrage", StatKeys.CAST_PREFIX + "Enrage", 50);
        feat("venomancer", "Venomancer", "Cast Imbue Poison 50 times.", CHAPTER, BRONZE, "Rune_PoisonImbue", StatKeys.CAST_PREFIX + "PoisonImbue", 50);
        feat("gale_force", "Gale Force", "Cast Wind Strike 100 times.", CHAPTER, BRONZE, "Rune_WindStrike", StatKeys.CAST_PREFIX + "WindStrike", 100);
        feat("get_over_here", "Get Over Here", "Cast Chain Hook 50 times.", CHAPTER, BRONZE, "Rune_ChainHook", StatKeys.CAST_PREFIX + "ChainHook", 50);
        feat("marksman", "Charged Up", "Cast Charged Shot 100 times.", CHAPTER, BRONZE, "Rune_ChargedShot", StatKeys.CAST_PREFIX + "ChargedShot", 100);
        multi("full_repertoire", "Full Repertoire", "Cast every ability at least once.", CHAPTER, GOLD, "Bench_Abilities",
                req(StatKeys.CAST_PREFIX + "ChainHook", 1), req(StatKeys.CAST_PREFIX + "ChargedShot", 1),
                req(StatKeys.CAST_PREFIX + "Enrage", 1), req(StatKeys.CAST_PREFIX + "Fireball", 1),
                req(StatKeys.CAST_PREFIX + "GroundSlam", 1), req(StatKeys.CAST_PREFIX + "PoisonImbue", 1),
                req(StatKeys.CAST_PREFIX + "WindStrike", 1));
        feat("antidote", "Cure All", "Brew an antidote.", CHAPTER, SILVER, "Potion_Purify", StatKeys.CRAFTED_PREFIX + "Potion_Antidote", 1);

        // Gadgets and gear
        feat("tinkerer", "Tinkerer", "Craft a gadget.", CHAPTER, SILVER, "Ingredient_Gadget_Assembly", StatKeys.CRAFT_GROUP_PREFIX + "gadget", 1);
        multi("gadgeteer", "Gadgeteer", "Craft the drill, the glider, the boots, the hookshot and the spyglass.", CHAPTER, GOLD, "Rocket_Boots",
                req(StatKeys.CRAFTED_PREFIX + "Tool_Drill_Scrap", 1), req(StatKeys.CRAFTED_PREFIX + "Glider_Scrap", 1),
                req(StatKeys.CRAFTED_PREFIX + "Rocket_Boots", 1), req(StatKeys.CRAFTED_PREFIX + "Tool_Hookshot", 1),
                req(StatKeys.CRAFTED_PREFIX + "Tool_Spyglass", 1));
        feat("far_sight", "Far Sight", "Look through a spyglass 25 times.", CHAPTER, BRONZE, "Tool_Spyglass", StatKeys.SPYGLASS_USES, 25);
        feat("fixer_upper", "Fixer Upper", "Craft 10 repair kits.", CHAPTER, SILVER, "Tool_Repair_Kit_Goblin", StatKeys.CRAFT_GROUP_PREFIX + "repair_kit", 10);
        feat("shield_wall", "Shield Wall", "Craft a shield.", CHAPTER, SILVER, "Weapon_Shield_Scrap", StatKeys.CRAFT_GROUP_PREFIX + "weapon_shield", 1);
        feat("bolt_action", "Bolt Action", "Craft a crossbow.", CHAPTER, SILVER, "Weapon_Crossbow_Iron", StatKeys.CRAFT_GROUP_PREFIX + "weapon_crossbow", 1);
        multi("full_armoury", "Full Armoury", "Craft a sword, an axe, a battleaxe, a spear, a mace, daggers, a bow, a crossbow, a shield and a staff.",
                CHAPTER, PLATINUM, "Weapon_Longsword_Ruined_Giant",
                req(StatKeys.CRAFT_GROUP_PREFIX + "weapon_sword", 1), req(StatKeys.CRAFT_GROUP_PREFIX + "weapon_axe", 1),
                req(StatKeys.CRAFT_GROUP_PREFIX + "weapon_battleaxe", 1), req(StatKeys.CRAFT_GROUP_PREFIX + "weapon_spear", 1),
                req(StatKeys.CRAFT_GROUP_PREFIX + "weapon_mace", 1), req(StatKeys.CRAFT_GROUP_PREFIX + "weapon_daggers", 1),
                req(StatKeys.CRAFT_GROUP_PREFIX + "weapon_shortbow", 1), req(StatKeys.CRAFT_GROUP_PREFIX + "weapon_crossbow", 1),
                req(StatKeys.CRAFT_GROUP_PREFIX + "weapon_shield", 1), req(StatKeys.CRAFT_GROUP_PREFIX + "weapon_staff", 1));
        feat("big_bang", "Big Bang", "Craft 25 Bangsticks. Stand well back.", CHAPTER, SILVER, "Weapon_Bangstick", StatKeys.CRAFTED_PREFIX + "Weapon_Bangstick", 25);

        // The Breach, The Deepworks and the Hollows
        feat("into_the_breach", "Into the Breach", "Enter a Goblin Breach.", CHAPTER, BRONZE, "PortalKey_Goblin_Breach", StatKeys.PORTAL_PREFIX + "Goblin_Breach", 1);
        feat("breach_regular", "Breach Regular", "Enter 10 Goblin Breaches.", CHAPTER, GOLD, "PortalKey_Goblin_Breach", StatKeys.PORTAL_PREFIX + "Goblin_Breach", 10);
        feat("deep_dive", "Deep Dive", "Enter The Deepworks.", CHAPTER, GOLD, "PortalKey_The_Deepworks", StatKeys.PORTAL_PREFIX + "The_Deepworks", 1);
        feat("portal_hopper", "Portal Hopper", "Enter 3 different kinds of portal world.", CHAPTER, SILVER, "PortalKey_Jungles", StatKeys.PORTAL_TYPES, 3);
        multi("fragments_of_orbis", "Fragments of Orbis", "Enter all five Fragments the Ancient Gateway can reach.", CHAPTER, PLATINUM, "PortalKey_Taiga",
                req(StatKeys.PORTAL_TYPE_PREFIX + "Goblin_Breach", 1), req(StatKeys.PORTAL_TYPE_PREFIX + "The_Deepworks", 1),
                req(StatKeys.PORTAL_TYPE_PREFIX + "Hederas_Lair", 1), req(StatKeys.PORTAL_TYPE_PREFIX + "Taiga", 1),
                req(StatKeys.PORTAL_TYPE_PREFIX + "Windsurf_Valley", 1));
        feat("compressed", "Under Pressure", "Craft the key to The Deepworks.", CHAPTER, GOLD, "PortalKey_The_Deepworks", StatKeys.CRAFTED_PREFIX + "PortalKey_The_Deepworks", 1);
        feat("fragment_explorer", "Fragment Explorer", "Enter 25 portal worlds.", CHAPTER, GOLD, "PortalKey_Henges", StatKeys.PORTALS_ENTERED, 25);
        hidden("hard_mode_switch", "Flip the Switch", "Press the hard mode button before facing the Gobliterator.", CHAPTER, SILVER, "Goblin_Gobliterator_Difficulty_Button", StatKeys.USE_PREFIX + "Goblin_Gobliterator_Difficulty_Button", 1);
        hidden("cold_feet", "Cold Feet", "Leave The Deepworks through the Escape Portal.", CHAPTER, BRONZE, "Dungeon_Portal_Early_Exit", StatKeys.USE_PREFIX + "Dungeon_Portal_Early_Exit", 1);
        feat("overtime", "Overtime", "Repair a Breach Console to buy more time.", CHAPTER, BRONZE, "GoblinLab_Console", StatKeys.USE_PREFIX + "GoblinLab_Console", 1);
        feat("lab_assistant", "Lab Assistant", "Use the Goblin Lab tinkering bench.", CHAPTER, BRONZE, "Ingredient_Scraps", StatKeys.USE_PREFIX + "GoblinLab_Tinkering_Bench", 1);
        feat("heart_of_the_void", "Heart of the Void", "Get your hands on a Voidheart.", CHAPTER, SILVER, "Ingredient_Voidheart", StatKeys.OBTAINED_PREFIX + "Ingredient_Voidheart", 1);
        feat("shard_collector", "Shard Collector", "Collect 20 Runestone Shards.", CHAPTER, SILVER, "Ingredient_Rune_Shard", StatKeys.OBTAINED_PREFIX + "Ingredient_Rune_Shard", 20);
        feat("scrap_rat", "Scrap Rat", "Collect 250 Scraps. One goblin's junk is your treasure.", CHAPTER, SILVER, "Ingredient_Scraps", StatKeys.OBTAINED_PREFIX + "Ingredient_Scraps", 250);
        feat("key_master", "Key Master", "Collect 5 Coffer Keys.", CHAPTER, SILVER, "Coffer_Key", StatKeys.OBTAINED_PREFIX + "Coffer_Key", 5);
        feat("bookworm", "Bookworm", "Pick up 5 lore notes.", CHAPTER, SILVER, "Deco_Scroll", StatKeys.PICKED_PREFIX + "lore", 5);
        multi("loremaster", "Loremaster", "Collect all twelve lore notes.", CHAPTER, GOLD, "Lore_Note_Goblin_Poem",
                req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Died_Improperly", 1), req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Enkindled_Journal_Rite", 1),
                req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Enkindled_Journal_Vermin", 1), req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Enkindled_Ledger_Trade", 1),
                req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Enkindled_Letter_Workshops", 1), req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Enkindled_Research_Gateways", 1),
                req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Goblin_Commission_Abandoned_Parts", 1), req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Goblin_Feastmaster_Menu", 1),
                req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Goblin_Musings_Too_Heavy", 1), req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Goblin_Musings_Translocation", 1),
                req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Goblin_Poem", 1), req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Goblin_Why_Purple", 1));
        feat("silk_road", "Silk Road", "Collect 50 Spider Silk.", CHAPTER, SILVER, "Ingredient_Spider_Silk", StatKeys.OBTAINED_PREFIX + "Ingredient_Spider_Silk", 50);
        hidden("core_sample", "Core Sample", "Get your hands on a Gobliterator Core.", CHAPTER, GOLD, "Ingredient_Gobliterator_Core", StatKeys.OBTAINED_PREFIX + "Ingredient_Gobliterator_Core", 1);

        // Memories
        feat("first_memory", "First Memory", "Record a Memory.", CHAPTER, BRONZE, "Deco_Kweebec_Plush", StatKeys.MEMORIES, 1);
        feat("keepsakes", "Keepsakes", "Record 10 Memories.", CHAPTER, SILVER, "Deco_Goblin_Plush", StatKeys.MEMORIES, 10);
        feat("chronicler", "Chronicler", "Record 25 Memories.", CHAPTER, GOLD, "Deco_Goblin_Plush", StatKeys.MEMORIES, 25);
        feat("archivist", "Archivist", "Record 50 Memories.", CHAPTER, GOLD, "Deco_Scroll", StatKeys.MEMORIES, 50);
        // Memories are a server wide race in 0.7.0. Once anyone hands one in, nobody else can record it,
        // so on a busy SMP server a player may never reach 100. Fair solo and in co-op.
        feat("living_memory", "Living Memory", "Record 100 Memories.", CHAPTER, PLATINUM, "Deco_Kweebec_Plush", StatKeys.MEMORIES, 100);

        // Blocks and building
        feat("kenolite_cutter", "Kenolite Cutter", "Mine 100 Kenolite.", CHAPTER, SILVER, "Rock_Kenolite", StatKeys.BROKEN_PREFIX + "Rock_Kenolite", 100);
        feat("crystal_clear", "Crystal Clear", "Break 25 Void crystals.", CHAPTER, SILVER, "Rock_Crystal_Void_Block", StatKeys.BROKEN_PREFIX + "void_crystal", 25);
        feat("scrap_scavenger", "Scrap Scavenger", "Mine 25 shiny scrap piles.", CHAPTER, SILVER, "Deco_Scrap_Pile_Shiny", StatKeys.BROKEN_PREFIX + "Deco_Scrap_Pile_Shiny", 25);
        feat("goblin_chic", "Goblin Chic", "Place 25 pieces of goblin furniture.", CHAPTER, SILVER, "Furniture_Goblin_Bed", StatKeys.PLACED_PREFIX + "goblin_furniture", 25);
        feat("art_critic", "Art Critic", "Place 6 goblin paintings.", CHAPTER, SILVER, "Goblin_Painting_Breach", StatKeys.PLACED_PREFIX + "goblin_painting", 6);
        // Only the originals count, a crafted _Replica is a different item id.
        multi("gallery_opening", "Gallery Opening", "Hang one of each goblin painting.", CHAPTER, SILVER, "Goblin_Painting_Orbis",
                req(StatKeys.PLACED_PREFIX + "Goblin_Painting_Breach", 1), req(StatKeys.PLACED_PREFIX + "Goblin_Painting_Fail", 1),
                req(StatKeys.PLACED_PREFIX + "Goblin_Painting_ForgottenTemple", 1), req(StatKeys.PLACED_PREFIX + "Goblin_Painting_Orbis", 1),
                req(StatKeys.PLACED_PREFIX + "Goblin_Painting_Portal", 1), req(StatKeys.PLACED_PREFIX + "Goblin_Painting_Repair", 1));
        feat("forger", "Forger", "Craft 3 painting replicas. Nobody will ever know.", CHAPTER, SILVER, "Goblin_Painting_Breach", StatKeys.CRAFT_GROUP_PREFIX + "painting", 3);
        feat("pipe_dream", "Pipe Dream", "Place 100 metal blocks.", CHAPTER, SILVER, "Metal_Goblin_Iron", StatKeys.PLACED_PREFIX + "metal", 100);
        feat("ironworks", "Ironworks", "Place 1,000 metal blocks.", CHAPTER, GOLD, "Metal_Orbis_Copper", StatKeys.PLACED_PREFIX + "metal", 1_000);


        // ---------------- Misc ----------------
        feat("chatterbox", "Chatterbox", "Send 100 chat messages.", MISC, BRONZE, "Deco_Scroll", StatKeys.CHAT, 100);
        feat("town_crier", "Town Crier", "Send 1,000 chat messages.", MISC, SILVER, "Deco_Scroll", StatKeys.CHAT, 1_000);
        feat("getting_started", "Getting Started", "Unlock 10 feats.", MISC, BRONZE, "Deco_Treasure_Pile_Small", StatKeys.FEATS_UNLOCKED, 10);
        feat("achiever", "Achiever", "Unlock 25 feats.", MISC, SILVER, "Deco_Treasure", StatKeys.FEATS_UNLOCKED, 25);
        feat("overachiever", "Overachiever", "Unlock 50 feats.", MISC, GOLD, "Deco_Treasure_Pile_Large", StatKeys.FEATS_UNLOCKED, 50);
        feat("legend_of_orbis", "Legend of Orbis", "Unlock 100 feats.", MISC, PLATINUM, "Deco_Treasure_Pile_Large", StatKeys.FEATS_UNLOCKED, 100);
    }

    /**
     * World milestones and server firsts. The Goblin Lab flags are raised for the whole server by the
     * Generator and the three repairable devices, see {@code AugmentBlock.ProgressionKey} in the assets.
     */
    private void defineWorld() {
        // ---------------- World milestones ----------------
        world("world_power", "Lights On", "Restore power to the Goblin Lab.", SILVER, "GoblinLab_Generator", flag("goblin_lab_power", 1));
        world("world_boiler", "Full Steam", "Repair the Boiler in the Goblin Lab.", SILVER, "GoblinLab_Tinkering_Bench_Recipeset_A", flag("goblin_lab_recipe_a", 1));
        world("world_engine", "Engine Room", "Repair the Engine in the Goblin Lab.", SILVER, "GoblinLab_Tinkering_Bench_Recipeset_B", flag("goblin_lab_recipe_b", 1));
        world("world_compressor", "Pressure Rising", "Repair the Compressor in the Goblin Lab.", SILVER, "GoblinLab_Tinkering_Bench_Recipeset_C", flag("goblin_lab_recipe_c", 1));
        world("world_gobliterator", "Scrap Yard", "Defeat the Gobliterator on this server.", GOLD, "Ingredient_Gobliterator_Core", after("gobliterated"));
        world("world_gobliterator_hard", "Redline", "Defeat the Gobliterator on hard mode on this server.", GOLD, "Goblin_Gobliterator_Difficulty_Button", after("gobliterated_hard"));
        world("world_chapter_closed", "End of the Chapter", "Complete Chapter I on this server.", GOLD, "Dungeon_Portal_End", after("chapter_closed"));
        world("world_gateway", "Open Gates", "Build an Ancient Gateway on this server.", GOLD, "Portal_Device", after("gatekeeper"));
        world("world_hedera", "Uprooted", "Defeat The Hedera on this server.", GOLD, "PortalKey_Hederas_Lair", after("dread_wade"));

        // ---------------- Server firsts ----------------
        first("first_gobliterator", "Pioneer", "Be the first on this server to defeat the Gobliterator.", PLATINUM, "Ingredient_Gobliterator_Core", after("gobliterated"));
        first("first_completion", "Trailblazer", "Be the first on this server to step through the Dungeon Completion Portal.", PLATINUM, "Dungeon_Portal_End", after("chapter_closed"));
        first("first_living_memory", "Keeper of Memories", "Be the first on this server to record 100 Memories.", PLATINUM, "Deco_Kweebec_Plush", after("living_memory"));
        first("first_gateway", "Gatewright", "Be the first on this server to build an Ancient Gateway.", PLATINUM, "Portal_Device", after("gatekeeper"));
        first("first_feastmaster", "Party Crasher", "Be the first on this server to defeat the Goblin Feastmaster.", PLATINUM, "Food_Popcorn", after("feastmaster"));
        first("first_archivist", "Historian", "Be the first on this server to record 50 Memories.", PLATINUM, "Deco_Scroll", after("archivist"));
    }

    /**
     * Feats for content that ships in the 0.7.0 assets but is never placed in a survival world. Mithril,
     * onyxium and prisma ore are not generated and werewolves never spawn, so these cannot be earned yet.
     * Not called from {@link #defineAll()}. They return, with the same ids, once the content is placed in
     * the world. full_spectrum is live again without mithril, this is its original form.
     */
    @SuppressWarnings("unused")
    private void parked() {
        feat("mithril_miner", "Mithril Miner", "Mine 10 mithril ore.", MINING, GOLD, "Ore_Mithril", StatKeys.ORE_PREFIX + "Mithril", 10);
        hidden("onyx_eye", "Onyx Eye", "Mine a single piece of onyxium ore.", MINING, PLATINUM, "Ore_Onyxium", StatKeys.ORE_PREFIX + "Onyxium", 1);
        hidden("prismatic", "Prismatic", "Mine a single piece of prisma ore.", MINING, PLATINUM, "Ore_Prisma", StatKeys.ORE_PREFIX + "Prisma", 1);
        multi("full_spectrum", "Full Spectrum", "Mine every metal, from copper all the way to adamantite.",
                MINING, PLATINUM, "Ore_Prisma",
                req(StatKeys.ORE_PREFIX + "Copper", 1), req(StatKeys.ORE_PREFIX + "Iron", 1),
                req(StatKeys.ORE_PREFIX + "Silver", 1), req(StatKeys.ORE_PREFIX + "Gold", 1),
                req(StatKeys.ORE_PREFIX + "Cobalt", 1), req(StatKeys.ORE_PREFIX + "Thorium", 1),
                req(StatKeys.ORE_PREFIX + "Mithril", 1), req(StatKeys.ORE_PREFIX + "Adamantite", 1));
        feat("mithril_smith", "Mithril Smith", "Smelt a mithril bar.", CRAFTING, GOLD, "Ingredient_Bar_Mithril", StatKeys.CRAFTED_PREFIX + "Ingredient_Bar_Mithril", 1);
        hidden("silver_bullet", "Silver Bullet", "Defeat a werewolf.", COMBAT, GOLD, "Weapon_Daggers_Bone", StatKeys.KILL_PREFIX + "Werewolf", 1);
    }
}
