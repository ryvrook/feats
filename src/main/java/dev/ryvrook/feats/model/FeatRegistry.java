package dev.ryvrook.feats.model;

import dev.ryvrook.feats.integration.VersionCompare;
import dev.ryvrook.feats.model.mods.DungeonsFeats;
import dev.ryvrook.feats.model.mods.EnchantFeats;
import dev.ryvrook.feats.model.mods.EndgameFeats;
import dev.ryvrook.feats.model.mods.LuckyFeats;
import dev.ryvrook.feats.model.mods.MmoFeats;
import dev.ryvrook.feats.model.mods.ParriesFeats;
import dev.ryvrook.feats.model.mods.RelicsFeats;
import dev.ryvrook.feats.model.mods.RpgFeats;
import dev.ryvrook.feats.model.mods.TameworkFeats;
import dev.ryvrook.feats.model.mods.VeinFeats;
import dev.ryvrook.feats.model.mods.ZephyrFeats;
import dev.ryvrook.feats.stats.StatKeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.EnumSet;

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

    /** The siblings and packs this registry was built for. */
    private final Set<Sibling> present;
    private final Set<Pack> packs;
    private final List<Feat> totalTriggered = new ArrayList<>();
    /** Feats skipped because a sibling or pack is missing, by id, so a grant of one can say why. */
    private final Map<String, String> skipped = new HashMap<>();

    /** The oldest TrackerAPI that publishes the mod records {@code integration/ModKinds} mirrors. */
    public static final String MODS_MINIMUM = "1.2.0";
    /** The TrackerAPI version found at setup, or null when it is not installed. */
    private final String trackerVersion;
    /** Why the feats of the mods TrackerAPI reads are out, one line for the log, or null when they are in. */
    private String modsNote;

    /** The built-in feats without the Factions tab, no sibling and no pack. */
    public FeatRegistry() {
        this(EnumSet.noneOf(Sibling.class), EnumSet.noneOf(Pack.class));
    }

    /**
     * The built-in feats. {@code factions} adds the Factions tab, read from the {@code dominion:} counters
     * Dominion Factions mirrors into Feats, so it is only worth adding when Dominion is installed. No packs.
     */
    public FeatRegistry(boolean factions) {
        this(factions ? EnumSet.of(Sibling.DOMINION) : EnumSet.noneOf(Sibling.class), EnumSet.noneOf(Pack.class));
    }

    /**
     * The built-in feats for the installed siblings and the packs that are on. A feat that requires a sibling not
     * in {@code present}, or sits in a pack not in {@code packs}, is left out, so its tab is not shown and it is
     * not counted. Its counters keep moving and a later registry with it in credits the player at their next join.
     */
    public FeatRegistry(Set<Sibling> present, Set<Pack> packs) {
        this(present, packs, null);
    }

    /**
     * As {@link #FeatRegistry(Set, Set)} with the TrackerAPI version found at setup, null when it is not installed.
     * The feats of the mods TrackerAPI reads are only defined when it is {@link #MODS_MINIMUM} or newer, see
     * {@link #modsNote()}.
     */
    public FeatRegistry(Set<Sibling> present, Set<Pack> packs, String trackerVersion) {
        this.present = present == null || present.isEmpty() ? EnumSet.noneOf(Sibling.class) : EnumSet.copyOf(present);
        this.packs = packs == null || packs.isEmpty() ? EnumSet.noneOf(Pack.class) : EnumSet.copyOf(packs);
        this.trackerVersion = trackerVersion;
        for (Category c : Category.values()) {
            byCategory.put(c, new ArrayList<>());
        }
        defineAll();
        definePacks();
        defineFactions();
        defineCoinage();
        defineChronicle();
        defineMods();
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

    /** Siblings every feat of the section being defined needs, applied to feats that name none themselves. */
    private Set<Sibling> sectionRequires = Set.of();

    private void add(Feat feat) {
        if (byId.containsKey(feat.id()) || skipped.containsKey(feat.id())) {
            throw new IllegalStateException("duplicate feat id " + feat.id());
        }
        if (!sectionRequires.isEmpty() && feat.requires().isEmpty()) {
            feat = feat.requiring(sectionRequires.toArray(new Sibling[0]));
        }
        if (!present.containsAll(feat.requires())) {
            skipped.put(feat.id(), "needs " + feat.requires());
            return;
        }
        if (feat.pack() != null && !packs.contains(feat.pack())) {
            skipped.put(feat.id(), "pack " + feat.pack() + " is off");
            return;
        }
        byId.put(feat.id(), feat);
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
        switch (t.kind()) {
            case AFTER -> byAfter.computeIfAbsent(t.key(), k -> new ArrayList<>()).add(feat);
            case FLAG -> flagged.add(feat);
            case TOTAL -> totalTriggered.add(feat);
        }
    }

    /** World feats raised by a server total in server.json. */
    public List<Feat> totalTriggered() {
        return Collections.unmodifiableList(totalTriggered);
    }

    /** The siblings this registry was built with. */
    public Set<Sibling> present() {
        return Collections.unmodifiableSet(present);
    }

    /** The packs this registry was built with. */
    public Set<Pack> packs() {
        return Collections.unmodifiableSet(packs);
    }

    /** Why a defined feat is not in this registry, such as "pack GRIND is off", or null when it is in or unknown. */
    public String whySkipped(String id) {
        return id == null ? null : skipped.get(id);
    }

    /**
     * Why the feats of the mods TrackerAPI reads were left out, one line for the log, or null when they were
     * defined. They need TrackerAPI {@link #MODS_MINIMUM}, which publishes the mod records.
     */
    public String modsNote() {
        return modsNote;
    }

    /** The TrackerAPI version this registry was built with, or null. */
    public String trackerVersion() {
        return trackerVersion;
    }

    /** Every tab that has at least one feat, in display order. */
    public List<Category> tabs() {
        List<Category> out = new ArrayList<>();
        for (Category c : Category.values()) {
            if (!byCategory.get(c).isEmpty()) {
                out.add(c);
            }
        }
        return out;
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

    /** A personal feat built with the record's {@code withBanner}, {@code requiring} and {@code inPack}. */
    private void define(Feat feat) {
        add(feat);
    }

    /** A personal feat as a value, to decorate with {@code withBanner}, {@code requiring} or {@code inPack} before {@link #define}. */
    private static Feat of(String id, String name, String desc, Category cat, Tier tier, String icon, boolean hidden, Requirement... reqs) {
        return new Feat(id, name, desc, cat, tier, icon, List.of(reqs), hidden);
    }

    private static Trigger total(String key, long target) {
        return Trigger.total(key, target);
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
    /** The Full House milestone. Its target is the FullHouse setting, the tracker reads it when it checks the total. */
    public static final String FULL_HOUSE_ID = "world_full_house";
    public static final int FULL_HOUSE_DEFAULT = 20;

    private void defineAll() {
        // ---------------- Exploration ----------------
        feat("first_steps", "First Steps", "Travel 100 metres on foot.", EXPLORATION, BRONZE, "Tool_Map", StatKeys.DISTANCE, 100);
        feat("wanderer", "Wanderer", "Travel 1 kilometre.", EXPLORATION, BRONZE, "Tool_Map", StatKeys.DISTANCE, 1_000);
        feat("traveller", "Traveller", "Travel 10 kilometres.", EXPLORATION, SILVER, "Glider_Scrap", StatKeys.DISTANCE, 10_000);
        feat("globetrotter", "Globetrotter", "Travel 50 kilometres.", EXPLORATION, GOLD, "Glider_Scrap", StatKeys.DISTANCE, 50_000);
        feat("around_orbis", "Around Orbis", "Travel 200 kilometres.", EXPLORATION, PLATINUM, "Glider_Scrap", StatKeys.DISTANCE, 200_000);
        feat("circumnavigator", "Circumnavigator", "Travel 500 kilometres.", EXPLORATION, PLATINUM, "Vehicle_Wood_Boat", StatKeys.DISTANCE, 500_000);
        // Movement trackers. The engine keeps these counters in metres.
        feat("sea_legs", "Sea Legs", "Swim 1 kilometre.", EXPLORATION, SILVER, "Armor_Diving_Crude_Chest", "swim_distance", 1_000); // engine key swim_distance
        feat("wingspan", "Wingspan", "Glide 5 kilometres on a Scrap Glider.", EXPLORATION, GOLD, "Glider_Scrap", "glide_distance", 5_000); // engine key glide_distance
        // Set to 1 the first time the player stands at the floor or the top of the world.
        feat("bedrock_bound", "Bedrock Bound", "Reach the bottom of the world.", EXPLORATION, SILVER, "Rock_Bedrock", "bottom_reached", 1); // engine key bottom_reached
        feat("head_in_the_clouds", "Head in the Clouds", "Stand at the top of the world.", EXPLORATION, SILVER, "Ingredient_Feathers_Light", "top_reached", 1); // engine key top_reached
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
        feat("vault_breaker", "Vault Breaker", "Open 2,500 chests.", EXPLORATION, PLATINUM, "Furniture_Ancient_Chest_Large", StatKeys.CHESTS_OPENED, 2_500);
        feat("gatekeeper", "Gatekeeper", "Build an Ancient Gateway.", EXPLORATION, GOLD, "Portal_Device", StatKeys.CRAFTED_PREFIX + "Portal_Device", 1);
        hidden("take_flight", "Take Flight", "Get your hands on a Scrap Glider.", EXPLORATION, GOLD, "Glider_Scrap", StatKeys.OBTAINED_PREFIX + "Glider_Scrap", 1);
        // 1.7.2, biomes, weather, the moon, instances, respawn points and dungeon volumes
        feat("biome_10", "Local Flora", "Stand in 10 different biomes.", EXPLORATION, BRONZE, "Plant_Sapling_Oak", StatKeys.BIOMES_VISITED, 10);
        feat("biome_25", "Field Guide", "Stand in 25 different biomes.", EXPLORATION, SILVER, "Deco_Map", StatKeys.BIOMES_VISITED, 25);
        feat("biome_50", "Naturalist", "Stand in 50 different biomes.", EXPLORATION, GOLD, "Deco_Map", StatKeys.BIOMES_VISITED, 50);
        feat("biome_100", "Every Horizon", "Stand in 100 of the 144 biomes.", EXPLORATION, PLATINUM, "Glider_Scrap", StatKeys.BIOMES_VISITED, 100);
        multi("storm_chaser", "Storm Chaser", "Weather a sandstorm, a thunderstorm, a snowstorm and a Devastated Lands storm.", EXPLORATION, GOLD, "Rock_Sandstone",
                req(StatKeys.WEATHER_PREFIX + "Zone2_Sand_Storm", 1), req(StatKeys.WEATHER_PREFIX + "Zone2_Thunder_Storm", 1),
                req(StatKeys.WEATHER_PREFIX + "Zone3_Snow_Storm", 1), req(StatKeys.WEATHER_PREFIX + "Zone4_Storm", 1));
        hidden("aurora", "Northern Lights", "See the northern lights.", EXPLORATION, SILVER, "Ingredient_Ice_Essence", StatKeys.WEATHER_PREFIX + "Zone3_Northern_Lights", 1);
        hidden("red_sky", "Red Sky", "Be outside during a Blood Moon.", EXPLORATION, SILVER, "Ingredient_Fire_Essence", StatKeys.WEATHER_PREFIX + "Blood_Moon", 1);
        multi("lunar_calendar", "Lunar Calendar", "See every phase of the moon.", EXPLORATION, GOLD, "Deco_Scroll",
                req(StatKeys.MOON_PREFIX + "0", 1), req(StatKeys.MOON_PREFIX + "1", 1), req(StatKeys.MOON_PREFIX + "2", 1), req(StatKeys.MOON_PREFIX + "3", 1),
                req(StatKeys.MOON_PREFIX + "4", 1), req(StatKeys.MOON_PREFIX + "5", 1), req(StatKeys.MOON_PREFIX + "6", 1), req(StatKeys.MOON_PREFIX + "7", 1));
        feat("temple_raider", "Temple Raider", "Discover the Forgotten Temple.", EXPLORATION, SILVER, "Furniture_Ancient_Chest_Small", StatKeys.INSTANCE_PREFIX + "Forgotten_Temple", 1);
        feat("home_base", "Home Base", "Set a respawn point.", EXPLORATION, BRONZE, "Furniture_Crude_Bed", StatKeys.RESPAWN_POINTS, 1);
        feat("waypoints", "Waypoints", "Hold three respawn points in one world.", EXPLORATION, SILVER, "Furniture_Tavern_Bed", StatKeys.RESPAWN_POINTS, 3);
        feat("checkpoint", "Checkpoint", "Reach a dungeon checkpoint.", EXPLORATION, BRONZE, "Furniture_Flag_Small_Red", StatKeys.VOLUME_PREFIX + "Dungeon_Checkpoint", 1);
        feat("patched_up", "Patched Up", "Pick up a health pack in a dungeon.", EXPLORATION, BRONZE, "Potion_Health_Small", StatKeys.VOLUME_PREFIX + "Health_Pack_Volume", 1);
        // Atlas, only while the web map is installed
        define(of("on_the_map", "On the Map", "Link your account to the web map.", EXPLORATION, BRONZE, "Tool_Map", true,
                req(StatKeys.ATLAS_PREFIX + "linked", 1)).requiring(Sibling.ATLAS));
        define(of("surveyors_mark", "Surveyor's Mark", "Place 10 shared map markers.", EXPLORATION, SILVER, "Deco_Map", false,
                req(StatKeys.ATLAS_PREFIX + "markers", 10)).requiring(Sibling.ATLAS));
        closer(EXPLORATION, "exploration_complete", "Exploration, Complete", "Unlock every Exploration feat.");

        // ---------------- Mining ----------------
        feat("rock_bottom", "Rock Bottom", "Break your first block.", MINING, BRONZE, "Tool_Pickaxe_Crude", StatKeys.BLOCKS_BROKEN, 1);
        feat("digger", "Digger", "Break 100 blocks.", MINING, BRONZE, "Tool_Pickaxe_Copper", StatKeys.BLOCKS_BROKEN, 100);
        feat("excavator", "Excavator", "Break 1,000 blocks.", MINING, SILVER, "Tool_Pickaxe_Iron", StatKeys.BLOCKS_BROKEN, 1_000);
        feat("tunneler", "Tunneler", "Break 10,000 blocks.", MINING, GOLD, "Tool_Pickaxe_Cobalt", StatKeys.BLOCKS_BROKEN, 10_000);
        feat("terraformer", "Terraformer", "Break 100,000 blocks.", MINING, PLATINUM, "Tool_Pickaxe_Adamantite", StatKeys.BLOCKS_BROKEN, 100_000);
        hidden("million_mark", "Million Mark", "Break 1,000,000 blocks.", MINING, PLATINUM, "Tool_Drill_Scrap", StatKeys.BLOCKS_BROKEN, 1_000_000);
        feat("prospector", "Prospector", "Mine 10 ore blocks.", MINING, BRONZE, "Ore_Copper", StatKeys.ORE_MINED, 10);
        feat("miner", "Miner", "Mine 100 ore blocks.", MINING, SILVER, "Ore_Iron", StatKeys.ORE_MINED, 100);
        feat("ore_baron", "Ore Baron", "Mine 1,000 ore blocks.", MINING, GOLD, "Ore_Gold", StatKeys.ORE_MINED, 1_000);
        feat("motherlode", "Motherlode", "Mine 5,000 ore blocks.", MINING, PLATINUM, "Ore_Mithril", StatKeys.ORE_MINED, 5_000);
        feat("deep_vein", "Deep Vein", "Mine 25,000 ore blocks.", MINING, PLATINUM, "Ore_Adamantite_Magma", StatKeys.ORE_MINED, 25_000);
        feat("copper_rush", "Copper Rush", "Mine 50 copper ore.", MINING, BRONZE, "Ore_Copper", StatKeys.ORE_PREFIX + "Copper", 50);
        feat("copper_magnate", "Copper Magnate", "Mine 500 copper ore.", MINING, SILVER, "Ingredient_Bar_Copper", StatKeys.ORE_PREFIX + "Copper", 500);
        feat("iron_will", "Iron Will", "Mine 100 iron ore.", MINING, SILVER, "Ore_Iron", StatKeys.ORE_PREFIX + "Iron", 100);
        feat("ironbound", "Ironbound", "Mine 1,000 iron ore.", MINING, GOLD, "Ingredient_Bar_Iron", StatKeys.ORE_PREFIX + "Iron", 1_000);
        feat("gold_digger", "Gold Digger", "Mine 25 gold ore.", MINING, SILVER, "Ore_Gold", StatKeys.ORE_PREFIX + "Gold", 25);
        feat("gold_standard", "Gold Standard", "Mine 250 gold ore.", MINING, GOLD, "Ingredient_Bar_Gold", StatKeys.ORE_PREFIX + "Gold", 250);
        feat("silver_lining", "Silver Lining", "Mine 25 silver ore.", MINING, SILVER, "Ore_Silver", StatKeys.ORE_PREFIX + "Silver", 25);
        feat("silver_service", "Silver Service", "Mine 250 silver ore.", MINING, GOLD, "Ingredient_Bar_Silver", StatKeys.ORE_PREFIX + "Silver", 250);
        feat("cobalt_core", "Cobalt Core", "Mine 50 cobalt ore.", MINING, GOLD, "Ore_Cobalt", StatKeys.ORE_PREFIX + "Cobalt", 50);
        feat("cobalt_crown", "Cobalt Crown", "Mine 500 cobalt ore.", MINING, PLATINUM, "Ingredient_Bar_Cobalt", StatKeys.ORE_PREFIX + "Cobalt", 500);
        feat("thorium_touch", "Thorium Touch", "Mine 50 thorium ore.", MINING, GOLD, "Ore_Thorium", StatKeys.ORE_PREFIX + "Thorium", 50);
        feat("thorium_reactor", "Thorium Reactor", "Mine 500 thorium ore.", MINING, PLATINUM, "Ingredient_Bar_Thorium", StatKeys.ORE_PREFIX + "Thorium", 500);
        feat("adamant", "Adamant", "Mine 10 adamantite ore.", MINING, PLATINUM, "Ore_Adamantite", StatKeys.ORE_PREFIX + "Adamantite", 10);
        feat("adamant_heart", "Adamant Heart", "Mine 100 adamantite ore.", MINING, PLATINUM, "Ingredient_Bar_Adamantite", StatKeys.ORE_PREFIX + "Adamantite", 100);
        // Every metal that world generation places in 0.7.0. Mithril, onyxium and prisma are never placed.
        multi("full_spectrum", "Full Spectrum", "Mine every metal, from copper all the way to adamantite.",
                MINING, GOLD, "Ore_Adamantite_Magma",
                req(StatKeys.ORE_PREFIX + "Copper", 1), req(StatKeys.ORE_PREFIX + "Iron", 1),
                req(StatKeys.ORE_PREFIX + "Silver", 1), req(StatKeys.ORE_PREFIX + "Gold", 1),
                req(StatKeys.ORE_PREFIX + "Cobalt", 1), req(StatKeys.ORE_PREFIX + "Thorium", 1),
                req(StatKeys.ORE_PREFIX + "Adamantite", 1));
        multi("pick_collector", "Pick Collector", "Craft a pickaxe of every material, from wood to adamantite.", MINING, GOLD, "Tool_Pickaxe_Thorium",
                req(StatKeys.CRAFTED_PREFIX + "Tool_Pickaxe_Wood", 1), req(StatKeys.CRAFTED_PREFIX + "Tool_Pickaxe_Copper", 1),
                req(StatKeys.CRAFTED_PREFIX + "Tool_Pickaxe_Iron", 1), req(StatKeys.CRAFTED_PREFIX + "Tool_Pickaxe_Cobalt", 1),
                req(StatKeys.CRAFTED_PREFIX + "Tool_Pickaxe_Thorium", 1), req(StatKeys.CRAFTED_PREFIX + "Tool_Pickaxe_Adamantite", 1));
        // 1.7.2, tools wearing out, repairs and tree species
        feat("worn_out", "Worn Out", "Wear a tool out.", MINING, BRONZE, "Tool_Pickaxe_Crude", StatKeys.TOOLS_BROKEN, 1);
        feat("scrap_pile", "Scrap Pile", "Wear out 25 tools.", MINING, SILVER, "Ingredient_Scraps", StatKeys.TOOLS_BROKEN, 25);
        feat("hard_use", "Hard Use", "Wear out 100 tools.", MINING, GOLD, "Deco_Scrap_Pile_Shiny", StatKeys.TOOLS_BROKEN, 100);
        feat("good_as_new", "Good as New", "Repair an item.", MINING, SILVER, "Tool_Repair_Kit_Goblin", StatKeys.REPAIRS, 1);
        feat("timber_survey", "Timber Survey", "Chop 10 kinds of tree.", MINING, SILVER, "Wood_Birch_Trunk", StatKeys.WOOD_SPECIES, 10);
        feat("every_grain", "Every Grain", "Chop 25 of the 34 kinds of tree.", MINING, PLATINUM, "Wood_Oak_Trunk", StatKeys.WOOD_SPECIES, 25);
        closer(MINING, "mining_complete", "Mining, Complete", "Unlock every Mining feat.");

        // ---------------- Building ----------------
        feat("cornerstone", "Cornerstone", "Place your first block.", BUILDING, BRONZE, "Wood_Ash_Trunk", StatKeys.BLOCKS_PLACED, 1);
        feat("builder", "Builder", "Place 100 blocks.", BUILDING, BRONZE, "Wood_Ash_Trunk", StatKeys.BLOCKS_PLACED, 100);
        feat("architect", "Architect", "Place 1,000 blocks.", BUILDING, SILVER, "Rock_Aqua_Brick", StatKeys.BLOCKS_PLACED, 1_000);
        feat("master_builder", "Master Builder", "Place 10,000 blocks.", BUILDING, GOLD, "Rock_Aqua_Brick", StatKeys.BLOCKS_PLACED, 10_000);
        feat("metropolis", "Metropolis", "Place 100,000 blocks.", BUILDING, PLATINUM, "Rock_Aqua_Brick_Ornate", StatKeys.BLOCKS_PLACED, 100_000);
        hidden("megastructure", "Megastructure", "Place 1,000,000 blocks.", BUILDING, PLATINUM, "Rock_Stone_Brick_Ornate", StatKeys.BLOCKS_PLACED, 1_000_000);
        feat("light_it_up", "Light It Up", "Place 10 torches.", BUILDING, BRONZE, "Furniture_Crude_Torch", StatKeys.PLACED_PREFIX + "torch", 10);
        feat("beacon_of_hope", "Beacon of Hope", "Place 100 torches.", BUILDING, SILVER, "Furniture_Crude_Torch", StatKeys.PLACED_PREFIX + "torch", 100);
        feat("light_of_orbis", "Light of Orbis", "Place 1,000 torches.", BUILDING, GOLD, "Furniture_Crude_Torch", StatKeys.PLACED_PREFIX + "torch", 1_000);
        feat("lantern_festival", "Lantern Festival", "Place 25 lanterns.", BUILDING, SILVER, "Deco_Lantern", StatKeys.PLACED_PREFIX + "lantern", 25);
        feat("festival_of_lights", "Festival of Lights", "Place 250 lanterns.", BUILDING, GOLD, "Furniture_Kweebec_Lantern", StatKeys.PLACED_PREFIX + "lantern", 250);
        feat("home_sweet_home", "Home Sweet Home", "Place a bed.", BUILDING, BRONZE, "Furniture_Crude_Bed", StatKeys.PLACED_PREFIX + "bed", 1);
        feat("bed_and_breakfast", "Bed and Breakfast", "Place 10 beds.", BUILDING, SILVER, "Furniture_Tavern_Bed", StatKeys.PLACED_PREFIX + "bed", 10);
        feat("well_furnished", "Well Furnished", "Place 50 pieces of furniture.", BUILDING, SILVER, "Furniture_Crude_Table", StatKeys.PLACED_PREFIX + "furniture", 50);
        feat("interior_designer", "Interior Designer", "Place 250 pieces of furniture.", BUILDING, GOLD, "Furniture_Ancient_Table", StatKeys.PLACED_PREFIX + "furniture", 250);
        feat("show_home", "Show Home", "Place 1,000 pieces of furniture.", BUILDING, PLATINUM, "Furniture_Ancient_Statue", StatKeys.PLACED_PREFIX + "furniture", 1_000);
        feat("workshop", "Workshop", "Place 5 crafting benches.", BUILDING, SILVER, "Bench_WorkBench", StatKeys.PLACED_PREFIX + "bench", 5);
        feat("industrial_park", "Industrial Park", "Place 25 crafting benches.", BUILDING, GOLD, "Bench_WorkBench", StatKeys.PLACED_PREFIX + "bench", 25);
        feat("hoarder", "Hoarder", "Place 10 chests.", BUILDING, SILVER, "Furniture_Crude_Chest_Small", StatKeys.PLACED_PREFIX + "chest", 10);
        feat("warehouse", "Warehouse", "Place 50 chests.", BUILDING, GOLD, "Furniture_Crude_Chest_Large", StatKeys.PLACED_PREFIX + "chest", 50);
        feat("grand_vault", "Grand Vault", "Place 250 chests.", BUILDING, PLATINUM, "Furniture_Ancient_Chest_Large", StatKeys.PLACED_PREFIX + "chest", 250);
        // Material groups, sorted by Classify.blockPlaced.
        feat("glazier", "Glazier", "Place 250 glass or window blocks.", BUILDING, SILVER, "Furniture_Crude_Window", StatKeys.PLACED_PREFIX + "glass", 250);
        feat("stonemason", "Stonemason", "Place 2,500 stone or brick blocks.", BUILDING, GOLD, "Rock_Stone_Brick", StatKeys.PLACED_PREFIX + "stone", 2_500);
        feat("timber_frame", "Timber Frame", "Place 2,500 wood blocks.", BUILDING, GOLD, "Wood_Hardwood_Planks", StatKeys.PLACED_PREFIX + "wood", 2_500);
        feat("arborist", "Arborist", "Plant 50 saplings.", BUILDING, SILVER, "Plant_Sapling_Oak", StatKeys.PLACED_PREFIX + "sapling", 50);
        // 1.7.2, distinct block kinds placed
        feat("palette", "Palette", "Place 50 different kinds of block.", BUILDING, SILVER, "Rock_Aqua_Brick", StatKeys.PLACED_KINDS, 50);
        feat("mosaic", "Mosaic", "Place 200 different kinds of block.", BUILDING, GOLD, "Rock_Stone_Brick", StatKeys.PLACED_KINDS, 200);
        closer(BUILDING, "building_complete", "Building, Complete", "Unlock every Building feat.");

        // ---------------- Crafting ----------------
        feat("handy", "Handy", "Craft your first item.", CRAFTING, BRONZE, "Bench_WorkBench", StatKeys.CRAFTED, 1);
        feat("apprentice", "Apprentice", "Craft 50 items.", CRAFTING, BRONZE, "Ingredient_Stick", StatKeys.CRAFTED, 50);
        feat("artisan", "Artisan", "Craft 500 items.", CRAFTING, SILVER, "Ingredient_Bar_Copper", StatKeys.CRAFTED, 500);
        feat("master_crafter", "Master Crafter", "Craft 2,500 items.", CRAFTING, GOLD, "Ingredient_Bar_Iron", StatKeys.CRAFTED, 2_500);
        feat("industrialist", "Industrialist", "Craft 10,000 items.", CRAFTING, PLATINUM, "Ingredient_Bar_Thorium", StatKeys.CRAFTED, 10_000);
        feat("assembly_line", "Assembly Line", "Craft 50,000 items.", CRAFTING, PLATINUM, "Ingredient_Gadget_Assembly", StatKeys.CRAFTED, 50_000);
        feat("first_pickaxe", "Pick of the Litter", "Craft a pickaxe.", CRAFTING, BRONZE, "Tool_Pickaxe_Crude", StatKeys.CRAFT_GROUP_PREFIX + "pickaxe", 1);
        feat("toolmaker", "Toolmaker", "Craft 25 tools.", CRAFTING, SILVER, "Tool_Hatchet_Iron", StatKeys.CRAFT_GROUP_PREFIX + "tool", 25);
        feat("master_toolmaker", "Master Toolmaker", "Craft 250 tools.", CRAFTING, GOLD, "Tool_Hammer_Iron", StatKeys.CRAFT_GROUP_PREFIX + "tool", 250);
        feat("armed", "Armed", "Craft a weapon.", CRAFTING, BRONZE, "Weapon_Axe_Crude", StatKeys.CRAFT_GROUP_PREFIX + "weapon", 1);
        feat("weaponsmith", "Weaponsmith", "Craft 25 weapons.", CRAFTING, SILVER, "Weapon_Sword_Bronze", StatKeys.CRAFT_GROUP_PREFIX + "weapon", 25);
        feat("arms_dealer", "Arms Dealer", "Craft 250 weapons.", CRAFTING, GOLD, "Weapon_Sword_Cobalt", StatKeys.CRAFT_GROUP_PREFIX + "weapon", 250);
        feat("armorer", "Armourer", "Craft 4 pieces of armour.", CRAFTING, SILVER, "Armor_Iron_Chest", StatKeys.CRAFT_GROUP_PREFIX + "armor", 4);
        feat("quartermaster", "Quartermaster", "Craft 40 pieces of armour.", CRAFTING, GOLD, "Armor_Steel_Chest", StatKeys.CRAFT_GROUP_PREFIX + "armor", 40);
        multi("copper_kit", "Copper Kit", "Craft a full set of copper armour.", CRAFTING, BRONZE, "Armor_Copper_Chest",
                req(StatKeys.CRAFTED_PREFIX + "Armor_Copper_Head", 1), req(StatKeys.CRAFTED_PREFIX + "Armor_Copper_Chest", 1),
                req(StatKeys.CRAFTED_PREFIX + "Armor_Copper_Hands", 1), req(StatKeys.CRAFTED_PREFIX + "Armor_Copper_Legs", 1));
        multi("ironclad", "Ironclad", "Craft a full set of iron armour.", CRAFTING, SILVER, "Armor_Iron_Head",
                req(StatKeys.CRAFTED_PREFIX + "Armor_Iron_Head", 1), req(StatKeys.CRAFTED_PREFIX + "Armor_Iron_Chest", 1),
                req(StatKeys.CRAFTED_PREFIX + "Armor_Iron_Hands", 1), req(StatKeys.CRAFTED_PREFIX + "Armor_Iron_Legs", 1));
        multi("cobalt_clad", "Cobalt Clad", "Craft a full set of cobalt armour.", CRAFTING, GOLD, "Armor_Cobalt_Chest",
                req(StatKeys.CRAFTED_PREFIX + "Armor_Cobalt_Head", 1), req(StatKeys.CRAFTED_PREFIX + "Armor_Cobalt_Chest", 1),
                req(StatKeys.CRAFTED_PREFIX + "Armor_Cobalt_Hands", 1), req(StatKeys.CRAFTED_PREFIX + "Armor_Cobalt_Legs", 1));
        multi("thorium_plate", "Thorium Plate", "Craft a full set of thorium armour.", CRAFTING, GOLD, "Armor_Thorium_Chest",
                req(StatKeys.CRAFTED_PREFIX + "Armor_Thorium_Head", 1), req(StatKeys.CRAFTED_PREFIX + "Armor_Thorium_Chest", 1),
                req(StatKeys.CRAFTED_PREFIX + "Armor_Thorium_Hands", 1), req(StatKeys.CRAFTED_PREFIX + "Armor_Thorium_Legs", 1));
        multi("adamant_wall", "Adamant Wall", "Craft a full set of adamantite armour.", CRAFTING, PLATINUM, "Armor_Adamantite_Chest",
                req(StatKeys.CRAFTED_PREFIX + "Armor_Adamantite_Head", 1), req(StatKeys.CRAFTED_PREFIX + "Armor_Adamantite_Chest", 1),
                req(StatKeys.CRAFTED_PREFIX + "Armor_Adamantite_Hands", 1), req(StatKeys.CRAFTED_PREFIX + "Armor_Adamantite_Legs", 1));
        feat("adamant_edge", "Adamant Edge", "Forge an adamantite weapon.", CRAFTING, GOLD, "Weapon_Sword_Adamantite", StatKeys.CRAFT_GROUP_PREFIX + "weapon_adamantite", 1);
        multi("sword_collector", "Sword Collector", "Craft a sword of every metal, from copper to adamantite.", CRAFTING, GOLD, "Weapon_Sword_Thorium",
                req(StatKeys.CRAFTED_PREFIX + "Weapon_Sword_Copper", 1), req(StatKeys.CRAFTED_PREFIX + "Weapon_Sword_Iron", 1),
                req(StatKeys.CRAFTED_PREFIX + "Weapon_Sword_Cobalt", 1), req(StatKeys.CRAFTED_PREFIX + "Weapon_Sword_Thorium", 1),
                req(StatKeys.CRAFTED_PREFIX + "Weapon_Sword_Adamantite", 1));
        feat("blacksmith", "Blacksmith", "Smelt 50 metal bars.", CRAFTING, SILVER, "Ingredient_Bar_Iron", StatKeys.CRAFT_GROUP_PREFIX + "bar", 50);
        feat("metallurgist", "Metallurgist", "Smelt 500 metal bars.", CRAFTING, GOLD, "Ingredient_Bar_Cobalt", StatKeys.CRAFT_GROUP_PREFIX + "bar", 500);
        feat("foundry", "Foundry", "Smelt 5,000 metal bars.", CRAFTING, PLATINUM, "Bench_Furnace", StatKeys.CRAFT_GROUP_PREFIX + "bar", 5_000);
        feat("adamantite_smith", "Adamantite Smith", "Smelt an adamantite bar.", CRAFTING, PLATINUM, "Ingredient_Bar_Adamantite", StatKeys.CRAFTED_PREFIX + "Ingredient_Bar_Adamantite", 1);
        feat("chef", "Chef", "Cook 25 meals.", CRAFTING, SILVER, "Food_Bread", StatKeys.CRAFT_GROUP_PREFIX + "food", 25);
        feat("head_chef", "Head Chef", "Cook 250 meals.", CRAFTING, GOLD, "Food_Pie_Meat", StatKeys.CRAFT_GROUP_PREFIX + "food", 250);
        feat("banquet", "Banquet", "Cook 2,500 meals.", CRAFTING, PLATINUM, "Food_Kebab_Meat", StatKeys.CRAFT_GROUP_PREFIX + "food", 2_500);
        feat("alchemist", "Alchemist", "Brew 10 potions.", CRAFTING, SILVER, "Potion_Health", StatKeys.CRAFT_GROUP_PREFIX + "potion", 10);
        feat("potion_master", "Potion Master", "Brew 100 potions.", CRAFTING, GOLD, "Potion_Health_Greater", StatKeys.CRAFT_GROUP_PREFIX + "potion", 100);
        feat("apothecary", "Apothecary", "Brew 1,000 potions.", CRAFTING, PLATINUM, "Potion_Signature_Greater", StatKeys.CRAFT_GROUP_PREFIX + "potion", 1_000);
        feat("carpenter", "Carpenter", "Craft 25 pieces of furniture.", CRAFTING, SILVER, "Furniture_Crude_Stool", StatKeys.CRAFT_GROUP_PREFIX + "furniture", 25);
        feat("master_carpenter", "Master Carpenter", "Craft 250 pieces of furniture.", CRAFTING, GOLD, "Furniture_Crude_Table", StatKeys.CRAFT_GROUP_PREFIX + "furniture", 250);
        feat("movie_night", "Movie Night", "Cook 10 popcorn.", CRAFTING, BRONZE, "Food_Popcorn", StatKeys.CRAFTED_PREFIX + "Food_Popcorn", 10);
        feat("bench_press", "Bench Press", "Craft 5 benches.", CRAFTING, SILVER, "Bench_Furnace", StatKeys.CRAFT_GROUP_PREFIX + "bench", 5);
        // 1.7.2, knowledge recipes and full armour sets worn
        feat("recipe_book", "Recipe Book", "Learn 5 recipes that need knowledge.", CRAFTING, SILVER, "Deco_Scroll", StatKeys.RECIPES_KNOWN, 5);
        feat("cookbook", "Every Recipe", "Learn all 25 recipes that need knowledge.", CRAFTING, PLATINUM, "Deco_Book_Pile_Large", StatKeys.RECIPES_KNOWN, 25);
        feat("suited_up", "Suited Up", "Wear a full set of armour.", CRAFTING, SILVER, "Armor_Copper_Chest", StatKeys.ARMOUR_SETS, 1);
        feat("wardrobe", "Wardrobe", "Wear 5 different full sets of armour.", CRAFTING, GOLD, "Armor_Cobalt_Chest", StatKeys.ARMOUR_SETS, 5);
        feat("armoury_complete", "Every Armour", "Wear 10 different full sets of armour.", CRAFTING, PLATINUM, "Armor_Adamantite_Chest", StatKeys.ARMOUR_SETS, 10);
        closer(CRAFTING, "crafting_complete", "Crafting, Complete", "Unlock every Crafting feat.");

        // ---------------- Combat ----------------
        feat("first_blood", "First Blood", "Defeat your first creature.", COMBAT, BRONZE, "Weapon_Sword_Bronze", StatKeys.KILLS, 1);
        feat("hunter", "Hunter", "Defeat 50 creatures.", COMBAT, BRONZE, "Weapon_Sword_Bronze", StatKeys.KILLS, 50);
        feat("slayer", "Slayer", "Defeat 250 creatures.", COMBAT, SILVER, "Weapon_Axe_Iron", StatKeys.KILLS, 250);
        feat("warlord", "Warlord", "Defeat 1,000 creatures.", COMBAT, GOLD, "Weapon_Battleaxe_Iron", StatKeys.KILLS, 1_000);
        feat("legend", "Legend", "Defeat 5,000 creatures.", COMBAT, PLATINUM, "Weapon_Battleaxe_Mithril", StatKeys.KILLS, 5_000);
        feat("mythic", "Mythic", "Defeat 25,000 creatures.", COMBAT, PLATINUM, "Weapon_Battleaxe_Adamantite", StatKeys.KILLS, 25_000);
        // Best streak of kills between deaths, raised by the engine.
        feat("rampage", "Rampage", "Defeat 50 creatures without dying.", COMBAT, GOLD, "Weapon_Axe_Bone", "kill_streak", 50); // engine key kill_streak
        feat("sharpshooter", "Sharpshooter", "Defeat 50 creatures with projectiles.", COMBAT, SILVER, "Weapon_Arrow_Iron", StatKeys.KILLS_RANGED, 50);
        feat("deadeye", "Deadeye", "Defeat 250 creatures with projectiles.", COMBAT, GOLD, "Weapon_Arrow_Deadeye", StatKeys.KILLS_RANGED, 250);
        // Ids master_marksman and berserker_rage because marksman and berserker are already taken.
        feat("master_marksman", "Marksman", "Defeat 1,000 creatures with projectiles.", COMBAT, PLATINUM, "Weapon_Shortbow_Thorium", StatKeys.KILLS_RANGED, 1_000);
        feat("undead_bane", "Bane of the Undead", "Defeat 100 undead.", COMBAT, SILVER, "Weapon_Club_Zombie_Arm", StatKeys.KILL_GROUP_PREFIX + "undead", 100);
        feat("grave_keeper", "Grave Keeper", "Defeat 500 undead.", COMBAT, GOLD, "Weapon_Club_Zombie_Arm", StatKeys.KILL_GROUP_PREFIX + "undead", 500);
        feat("lich_bane", "Lich Bane", "Defeat 2,500 undead.", COMBAT, PLATINUM, "Weapon_Sword_Bone", StatKeys.KILL_GROUP_PREFIX + "undead", 2_500);
        feat("trork_tamer", "Trork Tamer", "Defeat 50 Trorks.", COMBAT, SILVER, "Weapon_Axe_Stone_Trork", StatKeys.KILL_GROUP_PREFIX + "trork", 50);
        feat("trork_scourge", "Trork Scourge", "Defeat 250 Trorks.", COMBAT, GOLD, "Weapon_Axe_Stone_Trork", StatKeys.KILL_GROUP_PREFIX + "trork", 250);
        feat("outlander_nemesis", "Outlander Nemesis", "Defeat 50 Outlanders.", COMBAT, SILVER, "Weapon_Club_Tribal", StatKeys.KILL_GROUP_PREFIX + "outlander", 50);
        feat("outlander_outlaw", "Outlander Outlaw", "Defeat 250 Outlanders.", COMBAT, GOLD, "Weapon_Club_Tribal", StatKeys.KILL_GROUP_PREFIX + "outlander", 250);
        feat("exterminator", "Exterminator", "Defeat 50 Scaraks.", COMBAT, SILVER, "Weapon_Battleaxe_Scarab", StatKeys.KILL_GROUP_PREFIX + "scarak", 50);
        feat("hive_breaker", "Hive Breaker", "Defeat 250 Scaraks.", COMBAT, GOLD, "Weapon_Battleaxe_Scarab", StatKeys.KILL_GROUP_PREFIX + "scarak", 250);
        feat("feran_foe", "Feran Foe", "Defeat 25 Feran.", COMBAT, SILVER, "Weapon_Claws_Tribal", StatKeys.KILL_GROUP_PREFIX + "feran", 25);
        feat("feran_hunter", "Feran Hunter", "Defeat 100 Feran.", COMBAT, GOLD, "Weapon_Claws_Tribal", StatKeys.KILL_GROUP_PREFIX + "feran", 100);
        feat("elemental_ender", "Elemental Ender", "Defeat 25 golems or spirits.", COMBAT, GOLD, "Weapon_Battleaxe_Steel_Rusty", StatKeys.KILL_GROUP_PREFIX + "elemental", 25);
        feat("elemental_master", "Elemental Master", "Defeat 100 golems or spirits.", COMBAT, PLATINUM, "Weapon_Battleaxe_Steel_Rusty", StatKeys.KILL_GROUP_PREFIX + "elemental", 100);
        feat("void_walker", "Void Walker", "Defeat 25 creatures of the Void.", COMBAT, SILVER, "Weapon_Battleaxe_Scythe_Void", StatKeys.KILL_GROUP_PREFIX + "void", 25);
        feat("duke_dethroned", "Duke Dethroned", "Defeat the Goblin Duke.", COMBAT, SILVER, "Weapon_Club_Scrap_Washboard", StatKeys.KILL_GROUP_PREFIX + "goblin_duke", 1);
        feat("giant_killer", "Giant Killer", "Defeat a boss.", COMBAT, GOLD, "Weapon_Club_Doomed", StatKeys.KILLS_BOSS, 1);
        feat("boss_rush", "Boss Rush", "Defeat 10 bosses.", COMBAT, PLATINUM, "Weapon_Battleaxe_Doomed", StatKeys.KILLS_BOSS, 10);
        feat("boss_hunter", "Boss Hunter", "Defeat 50 bosses.", COMBAT, PLATINUM, "Weapon_Shortbow_Doomed", StatKeys.KILLS_BOSS, 50);
        hidden("untouchable", "Untouchable", "Defeat a boss without taking any damage.", COMBAT, PLATINUM, "Weapon_Shield_Scrap", "boss_flawless", 1); // engine key boss_flawless
        hidden("dragon_slayer", "Dragon Slayer", "Defeat a dragon.", COMBAT, PLATINUM, "Weapon_Axe_Doomed", StatKeys.KILL_GROUP_PREFIX + "dragon", 1);
        feat("heavy_hitter", "Heavy Hitter", "Deal 1,000 damage.", COMBAT, BRONZE, "Weapon_Club_Iron", StatKeys.DAMAGE_DEALT, 1_000);
        feat("bruiser", "Bruiser", "Deal 10,000 damage.", COMBAT, SILVER, "Weapon_Club_Iron", StatKeys.DAMAGE_DEALT, 10_000);
        feat("destroyer", "Destroyer", "Deal 100,000 damage.", COMBAT, GOLD, "Weapon_Battleaxe_Cobalt", StatKeys.DAMAGE_DEALT, 100_000);
        feat("apocalypse", "Apocalypse", "Deal 1,000,000 damage.", COMBAT, PLATINUM, "Weapon_Bomb_Large_Fire", StatKeys.DAMAGE_DEALT, 1_000_000);
        // Largest single hit, raised by the engine.
        hidden("overkill", "Overkill", "Deal 250 damage in one hit.", COMBAT, SILVER, "Weapon_Club_Iron", "biggest_hit", 250); // engine key biggest_hit
        feat("tough", "Tough", "Take 1,000 damage.", COMBAT, BRONZE, "Armor_Iron_Chest", StatKeys.DAMAGE_TAKEN, 1_000);
        feat("punching_bag", "Punching Bag", "Take 10,000 damage.", COMBAT, SILVER, "Armor_Iron_Chest", StatKeys.DAMAGE_TAKEN, 10_000);
        feat("unbreakable", "Unbreakable", "Take 100,000 damage.", COMBAT, GOLD, "Armor_Cobalt_Chest", StatKeys.DAMAGE_TAKEN, 100_000);
        feat("immortal", "Immortal", "Take 1,000,000 damage.", COMBAT, PLATINUM, "Armor_Adamantite_Chest", StatKeys.DAMAGE_TAKEN, 1_000_000);
        hidden("duelist", "Duelist", "Defeat another player.", COMBAT, GOLD, "Weapon_Daggers_Bronze", StatKeys.KILLS_PLAYER, 1);
        hidden("gladiator", "Gladiator", "Defeat 10 players.", COMBAT, GOLD, "Armor_Bronze_Ornate_Chest", StatKeys.KILLS_PLAYER, 10);
        hidden("warmonger", "Warmonger", "Defeat 100 players.", COMBAT, PLATINUM, "Weapon_Daggers_Claw_Bone", StatKeys.KILLS_PLAYER, 100);
        // 1.7.2, damage kinds, blocking, projectiles, kill context, windows, close calls and co-op bosses
        feat("hazard_course", "Hazard Course", "Take 10 different kinds of damage.", COMBAT, GOLD, "Armor_Iron_Chest", StatKeys.DAMAGE_TYPES, 10);
        feat("shield_up", "Shield Up", "Block 10 hits.", COMBAT, BRONZE, "Weapon_Shield_Wood", StatKeys.BLOCKS, 10);
        feat("bulwark", "Bulwark", "Block 500 hits.", COMBAT, GOLD, "Weapon_Shield_Cobalt", StatKeys.BLOCKS, 500);
        feat("quiver", "Quiver", "Fire 1,000 projectiles.", COMBAT, SILVER, "Weapon_Arrow_Iron", StatKeys.SHOTS_FIRED, 1_000);
        hidden("longshot", "Longshot", "Defeat a creature from 50 metres away.", COMBAT, GOLD, "Weapon_Shortbow_Iron", StatKeys.KILLS_FAR, 1);
        hidden("deep_strike", "Deep Strike", "Defeat a creature while underwater.", COMBAT, SILVER, "Armor_Diving_Crude_Chest", StatKeys.KILLS_UNDERWATER, 1);
        hidden("death_from_above", "From Above", "Defeat a creature while gliding.", COMBAT, GOLD, "Glider_Scrap", StatKeys.KILLS_GLIDING, 1);
        feat("cavalry", "Cavalry", "Defeat 25 creatures while mounted.", COMBAT, SILVER, "Tool_Feedbag", StatKeys.KILLS_MOUNTED, 25);
        hidden("last_stand", "Last Stand", "Defeat a creature with under 10 percent health.", COMBAT, GOLD, "Potion_Health_Small", StatKeys.KILLS_LOW_HEALTH, 1);
        feat("night_watch", "Night Watch", "Defeat 100 creatures at night.", COMBAT, SILVER, "Furniture_Crude_Torch", StatKeys.KILLS_NIGHT, 100);
        feat("blood_moon_hunter", "Blood Moon Hunter", "Defeat 25 creatures during a Blood Moon.", COMBAT, GOLD, "Weapon_Sword_Bone", StatKeys.KILLS_BLOOD_MOON, 25);
        feat("double_kill", "Double", "Defeat 2 creatures within 3 seconds.", COMBAT, BRONZE, "Weapon_Daggers_Bronze", StatKeys.MULTIKILL_2, 1);
        feat("triple_kill", "Triple", "Defeat 3 creatures within 5 seconds.", COMBAT, SILVER, "Weapon_Daggers_Iron", StatKeys.MULTIKILL_3, 1);
        feat("massacre", "Ten in Fifteen", "Defeat 10 creatures within 15 seconds.", COMBAT, GOLD, "Weapon_Battleaxe_Iron", StatKeys.MULTIKILL_10, 1);
        feat("close_call", "Close Call", "Drop under 10 percent health and recover.", COMBAT, SILVER, "Potion_Health", StatKeys.CLOSE_CALLS, 1);
        feat("hard_to_kill", "Hard to Kill", "Recover from under 10 percent health 25 times.", COMBAT, GOLD, "Potion_Health_Greater", StatKeys.CLOSE_CALLS, 25);
        hidden("hard_landing", "Hard Landing", "Survive a fall worth 30 damage.", COMBAT, SILVER, "Deco_Bone_Ribs_Feran", StatKeys.FALL_SURVIVED_MAX, 30);
        hidden("water_landing", "Water Landing", "Fall 30 metres into water.", COMBAT, GOLD, "Vehicle_Wood_Boat", StatKeys.WATER_LANDINGS, 1);
        hidden("second_wind", "Second Wind", "Take drowning damage and live.", COMBAT, BRONZE, "Fish_Minnow_Item", StatKeys.DROWNING_SURVIVED, 1);
        feat("raid_party", "Raid Party", "Defeat a boss alongside two other players.", COMBAT, SILVER, "Weapon_Club_Doomed", StatKeys.BOSS_COOP_3, 1);
        feat("war_band", "War Band", "Defeat a boss alongside four other players.", COMBAT, GOLD, "Weapon_Battleaxe_Doomed", StatKeys.BOSS_COOP_5, 1);
        closer(COMBAT, "combat_complete", "Combat, Complete", "Unlock every Combat feat.");

        // ---------------- Survival ----------------
        feat("welcome", "Welcome to Orbis", "Join the world.", SURVIVAL, BRONZE, "Deco_Kweebec_Plush", StatKeys.JOINS, 1);
        feat("regular", "Regular", "Play 10 sessions.", SURVIVAL, BRONZE, "Deco_Kweebec_Plush", StatKeys.JOINS, 10);
        feat("local", "Local", "Play 50 sessions.", SURVIVAL, SILVER, "Deco_Kweebec_Plush", StatKeys.JOINS, 50);
        feat("fixture", "Fixture", "Play 250 sessions.", SURVIVAL, GOLD, "Deco_Kweebec_Plush", StatKeys.JOINS, 250);
        feat("part_of_the_furniture", "Part of the Furniture", "Play 1,000 sessions.", SURVIVAL, PLATINUM, "Furniture_Crude_Stool", StatKeys.JOINS, 1_000);
        feat("dedicated", "Dedicated", "Play for 1 hour.", SURVIVAL, BRONZE, "Deco_Trophy_Harvest", StatKeys.PLAYTIME, HOUR);
        feat("devoted", "Devoted", "Play for 10 hours.", SURVIVAL, SILVER, "Deco_Trophy_Harvest", StatKeys.PLAYTIME, 10 * HOUR);
        feat("veteran", "Veteran", "Play for 50 hours.", SURVIVAL, GOLD, "Deco_Treasure", StatKeys.PLAYTIME, 50 * HOUR);
        hidden("touch_grass", "Touch Grass", "Play for 200 hours.", SURVIVAL, PLATINUM, "Deco_Treasure_Pile_Large", StatKeys.PLAYTIME, 200 * HOUR);
        hidden("old_timer", "Old Timer", "Play for 500 hours.", SURVIVAL, PLATINUM, "Deco_Treasure_Pile_Large", StatKeys.PLAYTIME, 500 * HOUR);
        // Longest single session in seconds, raised by the engine when a player leaves.
        feat("marathon", "Marathon", "Stay online for 8 hours in one session.", SURVIVAL, GOLD, "Potion_Stamina_Greater", "longest_session", 8 * HOUR); // engine key longest_session
        feat("first_death", "Everybody Dies", "Die for the first time.", SURVIVAL, BRONZE, "Deco_Bone_Skulls_Wall", StatKeys.DEATHS, 1);
        feat("nine_lives", "Nine Lives", "Die 9 times.", SURVIVAL, SILVER, "Deco_Bone_Skulls_Wall", StatKeys.DEATHS, 9);
        feat("death_wish", "Death Wish", "Die 50 times.", SURVIVAL, GOLD, "Deco_Bone_Skulls", StatKeys.DEATHS, 50);
        hidden("respawn_point", "Respawn Point", "Die 250 times.", SURVIVAL, PLATINUM, "Deco_Bone_Pile", StatKeys.DEATHS, 250);
        hidden("gravity_check", "Gravity Check", "Die from a fall.", SURVIVAL, BRONZE, "Deco_Bone_Ribs_Feran", StatKeys.DEATH_CAUSE_PREFIX + "fall", 1);
        hidden("sleeping_with_fishes", "Sleeping with the Fishes", "Drown.", SURVIVAL, BRONZE, "Fish_Minnow_Item", StatKeys.DEATH_CAUSE_PREFIX + "drowning", 1);
        hidden("hot_stuff", "Hot Stuff", "Burn to death.", SURVIVAL, BRONZE, "Ingredient_Fire_Essence", StatKeys.DEATH_CAUSE_PREFIX + "fire", 1);
        hidden("lava_bath", "Lava Bath", "Die in lava.", SURVIVAL, BRONZE, "Rock_Volcanic_Cracked_Lava", StatKeys.DEATH_CAUSE_PREFIX + "lava", 1);
        feat("well_rested", "Well Rested", "Sleep in a bed.", SURVIVAL, BRONZE, "Furniture_Crude_Bed", StatKeys.BEDS_USED, 1);
        feat("sleepyhead", "Sleepyhead", "Sleep 25 nights.", SURVIVAL, SILVER, "Furniture_Ancient_Bed", StatKeys.BEDS_USED, 25);
        feat("hibernation", "Hibernation", "Sleep 100 nights.", SURVIVAL, GOLD, "Furniture_Ancient_Bed", StatKeys.BEDS_USED, 100);
        feat("long_winter", "Long Winter", "Sleep 500 nights.", SURVIVAL, PLATINUM, "Furniture_Frozen_Castle_Bed", StatKeys.BEDS_USED, 500);
        feat("bench_warmer", "Bench Warmer", "Use a crafting bench 50 times.", SURVIVAL, SILVER, "Bench_WorkBench", StatKeys.BENCHES_USED, 50);
        feat("bench_veteran", "Bench Veteran", "Use a crafting bench 500 times.", SURVIVAL, GOLD, "Bench_Furnace", StatKeys.BENCHES_USED, 500);
        feat("well_fed", "Well Fed", "Eat 50 meals.", SURVIVAL, BRONZE, "Food_Bread", StatKeys.FOOD_EATEN, 50);
        feat("glutton", "Glutton", "Eat 500 meals.", SURVIVAL, SILVER, "Food_Cheese", StatKeys.FOOD_EATEN, 500);
        feat("bottomless", "Bottomless", "Eat 5,000 meals.", SURVIVAL, GOLD, "Food_Pie_Pumpkin", StatKeys.FOOD_EATEN, 5_000);
        feat("potion_addict", "Potion Addict", "Drink 50 potions.", SURVIVAL, SILVER, "Potion_Health", StatKeys.POTIONS_DRUNK, 50);
        feat("potion_problem", "Potion Problem", "Drink 500 potions.", SURVIVAL, GOLD, "Potion_Health_Large", StatKeys.POTIONS_DRUNK, 500);
        feat("potion_seller", "Potion Seller", "Pick up 25 potions.", SURVIVAL, SILVER, "Potion_Health_Small", StatKeys.PICKED_PREFIX + "Potion_Health", 25);
        // 1.7.2, nights, lives, join streaks, join hours, vitals and hardcore
        feat("deep_sleep", "Deep Sleep", "Sleep through a whole night.", SURVIVAL, BRONZE, "Furniture_Crude_Bed", StatKeys.NIGHTS_SLEPT, 1);
        feat("sound_sleeper", "Sound Sleeper", "Sleep through 50 nights.", SURVIVAL, GOLD, "Furniture_Ancient_Bed", StatKeys.NIGHTS_SLEPT, 50);
        feat("survivor", "Survivor", "Live 7 game days without dying.", SURVIVAL, SILVER, "Potion_Stamina", StatKeys.LONGEST_LIFE_DAYS, 7);
        feat("hardened", "Hardened", "Live 30 game days without dying.", SURVIVAL, GOLD, "Potion_Stamina_Greater", StatKeys.LONGEST_LIFE_DAYS, 30);
        hidden("ageless", "Ageless", "Live 100 game days without dying.", SURVIVAL, PLATINUM, "Furniture_Frozen_Castle_Bed", StatKeys.LONGEST_LIFE_DAYS, 100);
        feat("habit", "Habit", "Join 3 days in a row.", SURVIVAL, BRONZE, "Deco_Kweebec_Plush", StatKeys.BEST_JOIN_STREAK, 3);
        feat("routine", "Routine", "Join 7 days in a row.", SURVIVAL, SILVER, "Deco_Kweebec_Plush", StatKeys.BEST_JOIN_STREAK, 7);
        feat("clockwork", "Clockwork", "Join 30 days in a row.", SURVIVAL, GOLD, "Deco_Trophy_Harvest", StatKeys.BEST_JOIN_STREAK, 30);
        hidden("year_round", "Year Round", "Join 365 days in a row.", SURVIVAL, PLATINUM, "Deco_Treasure_Pile_Large", StatKeys.BEST_JOIN_STREAK, 365);
        hidden("anniversary", "Anniversary", "Play one year after your first visit.", SURVIVAL, PLATINUM, "Deco_Treasure_Pile_Large", StatKeys.SERVER_YEAR, 1);
        hidden("night_owl", "Night Owl", "Join between midnight and 4 in the morning, server time.", SURVIVAL, BRONZE, "Furniture_Crude_Torch", StatKeys.JOIN_NIGHT, 1);
        hidden("early_bird", "Early Bird", "Join between 5 and 7 in the morning, server time.", SURVIVAL, BRONZE, "Food_Egg", StatKeys.JOIN_EARLY, 1);
        hidden("out_of_breath", "Out of Breath", "Surface with under 10 percent oxygen.", SURVIVAL, SILVER, "Armor_Diving_Crude_Chest", StatKeys.OXYGEN_LOW, 1);
        feat("drained", "Drained", "Empty your Mana 50 times.", SURVIVAL, BRONZE, "Potion_Mana", StatKeys.MANA_EMPTIED, 50);
        // Hardcore. These two stay in Survival whatever the Hardcore pack says, see the plan's section 6.2.
        hidden("last_life", "Last Life", "Reach your last life with hardcore on.", SURVIVAL, GOLD, "Deco_Bone_Skulls", StatKeys.LIVES_LEFT_MIN, 1);
        hidden("ten_days_hardcore", "Ten Days", "Survive 10 game days with hardcore on.", SURVIVAL, PLATINUM, "Deco_Bone_Pile", StatKeys.HARDCORE_DAYS, 10);
        closer(SURVIVAL, "survival_complete", "Survival, Complete", "Unlock every Survival feat.");

        // ---------------- Nature ----------------
        feat("lumberjack", "Lumberjack", "Chop 10 logs.", NATURE, BRONZE, "Tool_Hatchet_Crude", StatKeys.WOOD_CHOPPED, 10);
        feat("logger", "Logger", "Chop 250 logs.", NATURE, SILVER, "Tool_Hatchet_Iron", StatKeys.WOOD_CHOPPED, 250);
        feat("timber", "Timber", "Chop 1,000 logs.", NATURE, GOLD, "Wood_Oak_Trunk", StatKeys.WOOD_CHOPPED, 1_000);
        feat("deforestation", "Deforestation", "Chop 2,500 logs.", NATURE, GOLD, "Tool_Hatchet_Cobalt", StatKeys.WOOD_CHOPPED, 2_500);
        feat("clear_cut", "Clear Cut", "Chop 5,000 logs.", NATURE, PLATINUM, "Tool_Hatchet_Thorium", StatKeys.WOOD_CHOPPED, 5_000);
        hidden("old_growth", "Old Growth", "Chop 25,000 logs.", NATURE, PLATINUM, "Tool_Hatchet_Adamantite", StatKeys.WOOD_CHOPPED, 25_000);
        feat("green_thumb", "Green Thumb", "Plant 10 crops.", NATURE, BRONZE, "Plant_Crop_Carrot_Item", StatKeys.PLACED_PREFIX + "crop", 10);
        feat("farmer", "Farmer", "Plant 100 crops.", NATURE, SILVER, "Plant_Crop_Corn_Item", StatKeys.PLACED_PREFIX + "crop", 100);
        feat("agriculturist", "Agriculturist", "Plant 1,000 crops.", NATURE, GOLD, "Plant_Crop_Cauliflower_Item", StatKeys.PLACED_PREFIX + "crop", 1_000);
        feat("breadbasket", "Breadbasket", "Plant 5,000 crops.", NATURE, PLATINUM, "Plant_Crop_Wheat_Item", StatKeys.PLACED_PREFIX + "crop", 5_000);
        feat("harvest", "Harvest", "Gather 50 crops or food.", NATURE, BRONZE, "Plant_Crop_Carrot_Item", StatKeys.HARVESTED, 50);
        feat("bumper_crop", "Bumper Crop", "Gather 500 crops or food.", NATURE, SILVER, "Food_Pie_Apple", StatKeys.HARVESTED, 500);
        feat("granary", "Granary", "Gather 2,500 crops or food.", NATURE, GOLD, "Plant_Crop_Pumpkin_Item", StatKeys.HARVESTED, 2_500);
        feat("harvest_moon", "Harvest Moon", "Gather 10,000 crops or food.", NATURE, PLATINUM, "Deco_Trophy_Harvest", StatKeys.HARVESTED, 10_000);
        multi("crop_rotation", "Crop Rotation", "Harvest every kind of crop.", NATURE, GOLD, "Plant_Crop_Aubergine_Item",
                req(StatKeys.PICKED_PREFIX + "Plant_Crop_Wheat_Item", 1), req(StatKeys.PICKED_PREFIX + "Plant_Crop_Carrot_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Plant_Crop_Potato_Item", 1), req(StatKeys.PICKED_PREFIX + "Plant_Crop_Onion_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Plant_Crop_Tomato_Item", 1), req(StatKeys.PICKED_PREFIX + "Plant_Crop_Corn_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Plant_Crop_Rice_Item", 1), req(StatKeys.PICKED_PREFIX + "Plant_Crop_Pumpkin_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Plant_Crop_Lettuce_Item", 1), req(StatKeys.PICKED_PREFIX + "Plant_Crop_Cauliflower_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Plant_Crop_Turnip_Item", 1), req(StatKeys.PICKED_PREFIX + "Plant_Crop_Aubergine_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Plant_Crop_Chilli_Item", 1), req(StatKeys.PICKED_PREFIX + "Plant_Crop_Cotton_Item", 1));
        feat("forager", "Forager", "Pick up 100 items.", NATURE, BRONZE, "Ingredient_Stick", StatKeys.PICKED_UP, 100);
        feat("collector", "Collector", "Pick up 1,000 items.", NATURE, SILVER, "Ingredient_Stick", StatKeys.PICKED_UP, 1_000);
        feat("magpie", "Magpie", "Pick up 10,000 items.", NATURE, GOLD, "Deco_Treasure_Pile_Small", StatKeys.PICKED_UP, 10_000);
        feat("dragons_hoard", "Dragon's Hoard", "Pick up 100,000 items.", NATURE, PLATINUM, "Deco_Treasure_Pile_Large", StatKeys.PICKED_UP, 100_000);
        feat("fisher", "Gone Fishing", "Catch a fish.", NATURE, BRONZE, "Fish_Trout_Rainbow_Item", StatKeys.FISH_CAUGHT, 1);
        feat("angler", "Angler", "Catch 25 fish.", NATURE, SILVER, "Fish_Salmon_Item", StatKeys.FISH_CAUGHT, 25);
        feat("master_angler", "Master Angler", "Catch 100 fish.", NATURE, GOLD, "Fish_Pike_Item", StatKeys.FISH_CAUGHT, 100);
        feat("sea_legend", "Sea Legend", "Catch 500 fish.", NATURE, PLATINUM, "Fish_Catfish_Item", StatKeys.FISH_CAUGHT, 500);
        hidden("big_catch", "The Big One", "Land a hammerhead shark.", NATURE, GOLD, "Fish_Shark_Hammerhead_Item", StatKeys.PICKED_PREFIX + "Fish_Shark_Hammerhead_Item", 1);
        multi("full_net", "Full Net", "Catch 15 different kinds of fish.", NATURE, PLATINUM, "Fish_Clownfish_Item",
                req(StatKeys.PICKED_PREFIX + "Fish_Minnow_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Bluegill_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Trout_Rainbow_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Salmon_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Pike_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Catfish_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Piranha_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Clownfish_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Pufferfish_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Frostgill_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Snapjaw_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Eel_Moray_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Crab_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Lobster_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Shark_Hammerhead_Item", 1));
        feat("butcher", "Butcher", "Slaughter 25 livestock.", NATURE, SILVER, "Food_Beef_Raw", StatKeys.KILL_GROUP_PREFIX + "livestock", 25);
        feat("abattoir", "Abattoir", "Slaughter 250 livestock.", NATURE, GOLD, "Food_Pork_Raw", StatKeys.KILL_GROUP_PREFIX + "livestock", 250);
        feat("big_game", "Big Game Hunter", "Defeat 10 predators.", NATURE, SILVER, "Food_Wildmeat_Raw", StatKeys.KILL_GROUP_PREFIX + "predator", 10);
        feat("apex_predator", "Apex Predator", "Defeat 100 predators.", NATURE, GOLD, "Food_Wildmeat_Cooked", StatKeys.KILL_GROUP_PREFIX + "predator", 100);
        hidden("kweebec_grudge", "Kweebec Grudge", "Defeat 10 Kweebecs.", NATURE, SILVER, "Armor_Kweebec_Head", StatKeys.KILL_GROUP_PREFIX + "kweebec", 10);
        // Tamed creatures counted by the engine, and mounted travel in metres.
        feat("tamer", "Tamer", "Tame an animal.", NATURE, SILVER, "Tool_Trap_Bait", "tamed", 1); // engine key tamed
        feat("saddle_up", "Saddle Up", "Ride a mount for 1 kilometre.", NATURE, SILVER, "Tool_Feedbag", "ride_distance", 1_000); // engine key ride_distance
        // 1.7.2, farming, fishing, mounts, taming and creature kinds
        // The 14 field crops plus the Health, Mana and Stamina herb crops. The seed key is the crop name
        // after Plant_Seeds_, with the herb tier digit dropped, so Plant_Seeds_Health2 counts as Health.
        multi("sower", "Sower", "Plant seeds of every crop.", NATURE, GOLD, "Plant_Seeds_Wheat",
                req(StatKeys.SEED_PREFIX + "Wheat", 1), req(StatKeys.SEED_PREFIX + "Carrot", 1), req(StatKeys.SEED_PREFIX + "Potato", 1),
                req(StatKeys.SEED_PREFIX + "Onion", 1), req(StatKeys.SEED_PREFIX + "Tomato", 1), req(StatKeys.SEED_PREFIX + "Corn", 1),
                req(StatKeys.SEED_PREFIX + "Rice", 1), req(StatKeys.SEED_PREFIX + "Pumpkin", 1), req(StatKeys.SEED_PREFIX + "Lettuce", 1),
                req(StatKeys.SEED_PREFIX + "Cauliflower", 1), req(StatKeys.SEED_PREFIX + "Turnip", 1), req(StatKeys.SEED_PREFIX + "Aubergine", 1),
                req(StatKeys.SEED_PREFIX + "Chilli", 1), req(StatKeys.SEED_PREFIX + "Cotton", 1), req(StatKeys.SEED_PREFIX + "Health", 1),
                req(StatKeys.SEED_PREFIX + "Mana", 1), req(StatKeys.SEED_PREFIX + "Stamina", 1));
        feat("sprinkler", "Sprinkler", "Water 100 plots.", NATURE, SILVER, "Tool_Watering_Can", StatKeys.WATERINGS, 100);
        feat("fertile", "Fertile", "Fertilise 50 plots.", NATURE, SILVER, "Tool_Fertilizer", StatKeys.FERTILIZINGS, 50);
        feat("henhouse", "Henhouse", "House an animal in a coop.", NATURE, SILVER, "Coop_Chicken", StatKeys.COOP_USES, 1);
        feat("egg_run", "Produce", "Collect produce from a coop 100 times.", NATURE, GOLD, "Food_Egg", StatKeys.PRODUCE_COLLECTED, 100);
        feat("catch_and_carry", "Catch and Carry", "Catch an animal in a capture crate.", NATURE, SILVER, "Tool_Capture_Crate", StatKeys.CRATES_USED, 1);
        feat("trapper", "Trapper", "Place a fishing trap.", NATURE, BRONZE, "Tool_Fishing_Trap", StatKeys.TRAPS_PLACED, 1);
        feat("bait_shop", "Bait Shop", "Bait 50 traps.", NATURE, SILVER, "Tool_Trap_Bait", StatKeys.TRAPS_BAITED, 50);
        hidden("leviathan", "Leviathan", "Land a humpback whale.", NATURE, PLATINUM, "Fish_Whale_Humpback_Item", StatKeys.PICKED_PREFIX + "Fish_Whale_Humpback_Item", 1);
        hidden("deep_catch", "Deep Catch", "Land a fish from the rarest tier.", NATURE, GOLD, "Fish_Jellyfish_Man_Of_War_Item", StatKeys.FISH_RARE, 1);
        // Every Fish_*_Item in the 0.7.1 assets, 30 of them.
        multi("aquarium", "Aquarium", "Catch all 30 kinds of fish.", NATURE, PLATINUM, "Fish_Clownfish_Item",
                req(StatKeys.PICKED_PREFIX + "Fish_Bluegill_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Catfish_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Clownfish_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Crab_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Eel_Moray_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Frostgill_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Jellyfish_Blue_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Jellyfish_Cyan_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Jellyfish_Green_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Jellyfish_Man_Of_War_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Jellyfish_Red_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Jellyfish_Yellow_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Lobster_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Minnow_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Pike_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Piranha_Black_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Piranha_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Pufferfish_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Salmon_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Shark_Hammerhead_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Shellfish_Lava_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Snapjaw_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Tang_Blue_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Tang_Chevron_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Tang_Lemon_Peel_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Tang_Sailfin_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Trilobite_Black_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Trilobite_Item", 1),
                req(StatKeys.PICKED_PREFIX + "Fish_Trout_Rainbow_Item", 1), req(StatKeys.PICKED_PREFIX + "Fish_Whale_Humpback_Item", 1));
        feat("first_ride", "First Ride", "Ride a tamed mount.", NATURE, BRONZE, "Tool_Feedbag", StatKeys.MOUNTS, 1);
        multi("stable", "Stable", "Ride a horse, a camel and a ram.", NATURE, GOLD, "Tool_Feedbag",
                req(StatKeys.MOUNT_PREFIX + "Tamed_Horse", 1), req(StatKeys.MOUNT_PREFIX + "Tamed_Camel", 1), req(StatKeys.MOUNT_PREFIX + "Tamed_Ram", 1));
        feat("rail_rider", "Rail Rider", "Ride a minecart.", NATURE, SILVER, "Rail_Kart", StatKeys.MINECART_RIDES, 1);
        feat("long_haul", "Long Haul", "Ride a mount for 10 kilometres.", NATURE, GOLD, "Tool_Map", StatKeys.RIDE_DISTANCE, 10_000);
        feat("rancher", "Rancher", "Tame 10 animals.", NATURE, GOLD, "Tool_Trap_Bait", StatKeys.TAMED, 10);
        // Every adult Tamed_ role in the 0.7.1 assets, 20 of them. Calves, chicks, lambs and piglets grow into these.
        multi("menagerie", "Menagerie", "Tame every kind of animal that can be tamed.", NATURE, PLATINUM, "Deco_Kweebec_Plush",
                req(StatKeys.TAMED_PREFIX + "Tamed_Bison", 1), req(StatKeys.TAMED_PREFIX + "Tamed_Boar", 1),
                req(StatKeys.TAMED_PREFIX + "Tamed_Bunny", 1), req(StatKeys.TAMED_PREFIX + "Tamed_Camel", 1),
                req(StatKeys.TAMED_PREFIX + "Tamed_Chicken", 1), req(StatKeys.TAMED_PREFIX + "Tamed_Chicken_Desert", 1),
                req(StatKeys.TAMED_PREFIX + "Tamed_Cow", 1), req(StatKeys.TAMED_PREFIX + "Tamed_Goat", 1),
                req(StatKeys.TAMED_PREFIX + "Tamed_Horse", 1), req(StatKeys.TAMED_PREFIX + "Tamed_Mosshorn", 1),
                req(StatKeys.TAMED_PREFIX + "Tamed_Mosshorn_Plain", 1), req(StatKeys.TAMED_PREFIX + "Tamed_Mouflon", 1),
                req(StatKeys.TAMED_PREFIX + "Tamed_Pig", 1), req(StatKeys.TAMED_PREFIX + "Tamed_Pig_Wild", 1),
                req(StatKeys.TAMED_PREFIX + "Tamed_Rabbit", 1), req(StatKeys.TAMED_PREFIX + "Tamed_Ram", 1),
                req(StatKeys.TAMED_PREFIX + "Tamed_Sheep", 1), req(StatKeys.TAMED_PREFIX + "Tamed_Skrill", 1),
                req(StatKeys.TAMED_PREFIX + "Tamed_Turkey", 1), req(StatKeys.TAMED_PREFIX + "Tamed_Warthog", 1));
        feat("zoologist", "Zoologist", "Defeat 50 different kinds of creature.", NATURE, GOLD, "Food_Wildmeat_Raw", StatKeys.KILL_KINDS, 50);
        feat("bestiary", "Bestiary", "Defeat 100 different kinds of creature.", NATURE, PLATINUM, "Deco_Bone_Skulls", StatKeys.KILL_KINDS, 100);
        closer(NATURE, "nature_complete", "Nature, Complete", "Unlock every Nature feat.");

        // ---------------- Chapter I ----------------
        // Goblins, the Void and the new bosses
        feat("goblin_slayer", "Goblin Slayer", "Defeat 50 goblins.", CHAPTER, SILVER, "Weapon_Club_Scrap", StatKeys.KILL_GROUP_PREFIX + "goblin", 50);
        feat("scrap_heap", "Scrap Heap", "Defeat 100 goblins.", CHAPTER, GOLD, "Weapon_Club_Scrap", StatKeys.KILL_GROUP_PREFIX + "goblin", 100);
        feat("goblin_warlord", "Goblin Warlord", "Defeat 500 goblins.", CHAPTER, PLATINUM, "Weapon_Mace_Scrap", StatKeys.KILL_GROUP_PREFIX + "goblin", 500);
        feat("goblin_bane", "Goblin Bane", "Defeat 2,500 goblins.", CHAPTER, PLATINUM, "Weapon_Mace_Scrap", StatKeys.KILL_GROUP_PREFIX + "goblin", 2_500);
        multi("goblinologist", "Goblinologist", "Defeat one of every kind of goblin.", CHAPTER, GOLD, "Deco_Goblin_Plush",
                req(StatKeys.KILL_GROUP_PREFIX + "goblin_scrapper", 1), req(StatKeys.KILL_GROUP_PREFIX + "goblin_miner", 1),
                req(StatKeys.KILL_GROUP_PREFIX + "goblin_burner", 1), req(StatKeys.KILL_GROUP_PREFIX + "goblin_lobber", 1),
                req(StatKeys.KILL_GROUP_PREFIX + "goblin_guardian", 1), req(StatKeys.KILL_GROUP_PREFIX + "goblin_thief", 1),
                req(StatKeys.KILL_GROUP_PREFIX + "goblin_glutton", 1), req(StatKeys.KILL_PREFIX + "Dungeon_Feastmaster_Boss", 1),
                req(StatKeys.KILL_GROUP_PREFIX + "goblin_duke", 1));
        hidden("second_helping", "Seconds", "Defeat a Goblin Glutton.", CHAPTER, SILVER, "Food_Pie_Meat", StatKeys.KILL_GROUP_PREFIX + "goblin_glutton", 1);
        feat("arachnophobia", "Arachnophobia", "Defeat 25 spiders.", CHAPTER, SILVER, "Ingredient_Spider_Silk", StatKeys.KILL_GROUP_PREFIX + "spider", 25);
        feat("web_warden", "Web Warden", "Defeat 100 spiders.", CHAPTER, GOLD, "Ingredient_Spider_Silk", StatKeys.KILL_GROUP_PREFIX + "spider", 100);
        feat("arachnid_apex", "Arachnid Apex", "Defeat 500 spiders.", CHAPTER, PLATINUM, "Ingredient_Spider_Silk", StatKeys.KILL_GROUP_PREFIX + "spider", 500);
        feat("void_purge", "Void Purge", "Defeat 100 creatures of the Void.", CHAPTER, GOLD, "Weapon_Daggers_Void_Crystal", StatKeys.KILL_GROUP_PREFIX + "void", 100);
        feat("void_reaver", "Void Reaver", "Defeat 500 creatures of the Void.", CHAPTER, PLATINUM, "Weapon_Battleaxe_Scythe_Void", StatKeys.KILL_GROUP_PREFIX + "void", 500);
        feat("anomaly_closed", "Anomaly Closed", "Destroy 5 Void anomaly crystals.", CHAPTER, SILVER, "Rock_Crystal_Void_Block", StatKeys.KILL_GROUP_PREFIX + "void_anomaly", 5);
        feat("keystone", "Keystone", "Shatter a Void anomaly keystone.", CHAPTER, SILVER, "Rock_Crystal_Void_Block", StatKeys.KILL_PREFIX + "Void_Anomaly_Crystal_Keystone", 1);
        feat("spectre_hunter", "Spectre Hunter", "Defeat 10 Void Spectres.", CHAPTER, SILVER, "Weapon_Staff_Voidcrystal", StatKeys.KILL_GROUP_PREFIX + "void_spectre", 10);
        multi("void_scholar", "Know the Void", "Defeat every kind of Voidspawn.", CHAPTER, GOLD, "Ingredient_Crystal_Void",
                req(StatKeys.KILL_GROUP_PREFIX + "void_crawler", 1), req(StatKeys.KILL_GROUP_PREFIX + "void_eye", 1),
                req(StatKeys.KILL_GROUP_PREFIX + "void_larva", 1), req(StatKeys.KILL_GROUP_PREFIX + "void_spawn", 1),
                req(StatKeys.KILL_GROUP_PREFIX + "void_spectre", 1), req(StatKeys.KILL_GROUP_PREFIX + "void_tentacle", 1));
        feat("revenant_slayer", "Revenant Slayer", "Defeat a Revenant Guardian.", CHAPTER, GOLD, "Weapon_Longsword_Ruined_Giant", StatKeys.KILL_GROUP_PREFIX + "revenant", 1);
        feat("feastmaster", "Feast Interrupted", "Defeat the Goblin Feastmaster.", CHAPTER, GOLD, "Food_Popcorn", StatKeys.KILL_PREFIX + "Dungeon_Feastmaster_Boss", 1);
        feat("gobliterated", "Gobliterated", "Defeat the Gobliterator.", CHAPTER, PLATINUM, "Ingredient_Gobliterator_Core", StatKeys.KILL_GROUP_PREFIX + "gobliterator", 1);
        hidden("gobliterated_hard", "Overclocked", "Defeat the Gobliterator on hard mode.", CHAPTER, PLATINUM, "Ingredient_Gobliterator_Core", StatKeys.KILL_GROUP_PREFIX + "gobliterator_hm", 1);
        hidden("hard_habit", "Hard Habit", "Defeat the Gobliterator on hard mode 5 times.", CHAPTER, PLATINUM, "Goblin_Gobliterator_Difficulty_Button", StatKeys.KILL_GROUP_PREFIX + "gobliterator_hm", 5);
        feat("scrap_metal", "Scrap Metal", "Defeat the Gobliterator 5 times.", CHAPTER, GOLD, "Ingredient_Gadget_Assembly", StatKeys.KILL_GROUP_PREFIX + "gobliterator", 5);
        feat("scrap_dealer", "Scrap Dealer", "Defeat the Gobliterator 25 times.", CHAPTER, PLATINUM, "Ingredient_Gadget_Assembly", StatKeys.KILL_GROUP_PREFIX + "gobliterator", 25);
        hidden("nothing_to_lose", "Nothing to Lose", "Defeat the Gobliterator on hard mode wearing no armour.", CHAPTER, PLATINUM, "Weapon_Bangstick", StatKeys.GOBLITERATOR_HM_UNARMOURED, 1);
        hidden("not_a_scratch", "Not a Scratch", "Defeat the Gobliterator without dying in The Deepworks.", CHAPTER, PLATINUM, "Potion_Health_Greater", StatKeys.GOBLITERATOR_DEATHLESS, 1);
        // Gobliterator kills within 20 minutes of entering that Deepworks run, counted by the engine.
        hidden("speedrunner", "Speedrunner", "Defeat the Gobliterator within 20 minutes of entering The Deepworks.", CHAPTER, PLATINUM, "Rocket_Boots", "gobliterator_fast", 1); // engine key gobliterator_fast
        feat("chapter_closed", "Chapter Closed", "Step through the Dungeon Completion Portal in The Deepworks.", CHAPTER, GOLD, "Dungeon_Portal_End", StatKeys.USE_PREFIX + "Dungeon_Portal_End", 1);
        feat("bigger_pockets", "Bigger Pockets", "Trade two Gobliterator Cores for the largest backpack.", CHAPTER, PLATINUM, "Upgrade_Backpack_4", StatKeys.OBTAINED_PREFIX + "Upgrade_Backpack_4", 1);
        feat("voidtaken", "Bear Necessities", "Defeat the Voidtaken bear.", CHAPTER, GOLD, "Ingredient_Voidtaken_Grizzly_Hide", StatKeys.KILL_PREFIX + "Bear_Voidtaken", 1);
        feat("dread_wade", "Weed Killer", "Defeat The Hedera in The Dread Wade.", CHAPTER, GOLD, "PortalKey_Hederas_Lair", StatKeys.KILL_PREFIX + "Hedera", 1);

        // Runestones and abilities
        feat("first_rune", "Rune Awakening", "Craft your first Runestone.", CHAPTER, BRONZE, "Rune_Empty", StatKeys.CRAFT_GROUP_PREFIX + "rune", 1);
        feat("runesmith", "Runesmith", "Craft 10 Runestones.", CHAPTER, SILVER, "Ingredient_Rune_Shard", StatKeys.CRAFT_GROUP_PREFIX + "rune", 10);
        feat("rune_lord", "Rune Lord", "Craft 100 Runestones.", CHAPTER, GOLD, "Rune_Empty", StatKeys.CRAFT_GROUP_PREFIX + "rune", 100);
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
        feat("grand_archmage", "Grand Archmage", "Cast 25,000 abilities.", CHAPTER, PLATINUM, "Bench_Abilities", StatKeys.ABILITY_CASTS, 25_000);
        feat("pyromancer", "Pyromancer", "Cast Fireball 100 times.", CHAPTER, BRONZE, "Rune_Fireball", StatKeys.CAST_PREFIX + "Fireball", 100);
        feat("inferno", "Inferno", "Cast Fireball 1,000 times.", CHAPTER, SILVER, "Rune_Fireball", StatKeys.CAST_PREFIX + "Fireball", 1_000);
        feat("earthshaker", "Earthshaker", "Cast Ground Slam 100 times.", CHAPTER, BRONZE, "Rune_GroundSlam", StatKeys.CAST_PREFIX + "GroundSlam", 100);
        feat("tremor", "Tremor", "Cast Ground Slam 1,000 times.", CHAPTER, SILVER, "Rune_GroundSlam", StatKeys.CAST_PREFIX + "GroundSlam", 1_000);
        feat("berserker", "Seeing Red", "Cast Enrage 50 times.", CHAPTER, BRONZE, "Rune_Enrage", StatKeys.CAST_PREFIX + "Enrage", 50);
        feat("berserker_rage", "Berserker", "Cast Enrage 500 times.", CHAPTER, SILVER, "Rune_Enrage", StatKeys.CAST_PREFIX + "Enrage", 500);
        feat("venomancer", "Venomancer", "Cast Imbue Poison 50 times.", CHAPTER, BRONZE, "Rune_PoisonImbue", StatKeys.CAST_PREFIX + "PoisonImbue", 50);
        feat("plaguebringer", "Plaguebringer", "Cast Imbue Poison 500 times.", CHAPTER, SILVER, "Rune_PoisonImbue", StatKeys.CAST_PREFIX + "PoisonImbue", 500);
        feat("gale_force", "Gale Force", "Cast Wind Strike 100 times.", CHAPTER, BRONZE, "Rune_WindStrike", StatKeys.CAST_PREFIX + "WindStrike", 100);
        feat("tempest", "Tempest", "Cast Wind Strike 1,000 times.", CHAPTER, SILVER, "Rune_WindStrike", StatKeys.CAST_PREFIX + "WindStrike", 1_000);
        feat("get_over_here", "Get Over Here", "Cast Chain Hook 50 times.", CHAPTER, BRONZE, "Rune_ChainHook", StatKeys.CAST_PREFIX + "ChainHook", 50);
        feat("hooked", "Hooked", "Cast Chain Hook 500 times.", CHAPTER, SILVER, "Rune_ChainHook", StatKeys.CAST_PREFIX + "ChainHook", 500);
        feat("marksman", "Charged Up", "Cast Charged Shot 100 times.", CHAPTER, BRONZE, "Rune_ChargedShot", StatKeys.CAST_PREFIX + "ChargedShot", 100);
        feat("overcharged", "Overcharged", "Cast Charged Shot 1,000 times.", CHAPTER, SILVER, "Rune_ChargedShot", StatKeys.CAST_PREFIX + "ChargedShot", 1_000);
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
        feat("big_bang", "Big Bang", "Craft 25 Bangsticks.", CHAPTER, SILVER, "Weapon_Bangstick", StatKeys.CRAFTED_PREFIX + "Weapon_Bangstick", 25);

        // The Breach, The Deepworks and the Hollows
        feat("into_the_breach", "Into the Breach", "Enter a Goblin Breach.", CHAPTER, BRONZE, "PortalKey_Goblin_Breach", StatKeys.PORTAL_PREFIX + "Goblin_Breach", 1);
        feat("breach_regular", "Breach Regular", "Enter 10 Goblin Breaches.", CHAPTER, GOLD, "PortalKey_Goblin_Breach", StatKeys.PORTAL_PREFIX + "Goblin_Breach", 10);
        feat("breach_veteran", "Breach Veteran", "Enter 50 Goblin Breaches.", CHAPTER, PLATINUM, "PortalKey_Goblin_Breach", StatKeys.PORTAL_PREFIX + "Goblin_Breach", 50);
        feat("deep_dive", "Deep Dive", "Enter The Deepworks.", CHAPTER, GOLD, "PortalKey_The_Deepworks", StatKeys.PORTAL_PREFIX + "The_Deepworks", 1);
        feat("deepworks_regular", "Deepworks Regular", "Enter The Deepworks 10 times.", CHAPTER, GOLD, "PortalKey_The_Deepworks", StatKeys.PORTAL_PREFIX + "The_Deepworks", 10);
        feat("portal_hopper", "Portal Hopper", "Enter 3 different kinds of portal world.", CHAPTER, SILVER, "PortalKey_Jungles", StatKeys.PORTAL_TYPES, 3);
        multi("fragments_of_orbis", "Fragments of Orbis", "Enter all five Fragments the Ancient Gateway can reach.", CHAPTER, PLATINUM, "PortalKey_Taiga",
                req(StatKeys.PORTAL_TYPE_PREFIX + "Goblin_Breach", 1), req(StatKeys.PORTAL_TYPE_PREFIX + "The_Deepworks", 1),
                req(StatKeys.PORTAL_TYPE_PREFIX + "Hederas_Lair", 1), req(StatKeys.PORTAL_TYPE_PREFIX + "Taiga", 1),
                req(StatKeys.PORTAL_TYPE_PREFIX + "Windsurf_Valley", 1));
        feat("compressed", "Under Pressure", "Craft the key to The Deepworks.", CHAPTER, GOLD, "PortalKey_The_Deepworks", StatKeys.CRAFTED_PREFIX + "PortalKey_The_Deepworks", 1);
        feat("fragment_explorer", "Fragment Explorer", "Enter 25 portal worlds.", CHAPTER, GOLD, "PortalKey_Henges", StatKeys.PORTALS_ENTERED, 25);
        feat("frequent_flyer", "Frequent Flyer", "Enter 100 portal worlds.", CHAPTER, PLATINUM, "PortalKey_Henges", StatKeys.PORTALS_ENTERED, 100);
        hidden("hard_mode_switch", "Flip the Switch", "Press the hard mode button before facing the Gobliterator.", CHAPTER, SILVER, "Goblin_Gobliterator_Difficulty_Button", StatKeys.USE_PREFIX + "Goblin_Gobliterator_Difficulty_Button", 1);
        hidden("cold_feet", "Cold Feet", "Leave The Deepworks through the Escape Portal.", CHAPTER, BRONZE, "Dungeon_Portal_Early_Exit", StatKeys.USE_PREFIX + "Dungeon_Portal_Early_Exit", 1);
        feat("overtime", "Overtime", "Repair a Breach Console to buy more time.", CHAPTER, BRONZE, "GoblinLab_Console", StatKeys.USE_PREFIX + "GoblinLab_Console", 1);
        feat("lab_assistant", "Lab Assistant", "Use the Goblin Lab tinkering bench.", CHAPTER, BRONZE, "Ingredient_Scraps", StatKeys.USE_PREFIX + "GoblinLab_Tinkering_Bench", 1);
        feat("heart_of_the_void", "Heart of the Void", "Get your hands on a Voidheart.", CHAPTER, SILVER, "Ingredient_Voidheart", StatKeys.OBTAINED_PREFIX + "Ingredient_Voidheart", 1);
        feat("shard_collector", "Shard Collector", "Collect 20 Runestone Shards.", CHAPTER, SILVER, "Ingredient_Rune_Shard", StatKeys.OBTAINED_PREFIX + "Ingredient_Rune_Shard", 20);
        feat("rune_hoard", "Rune Hoard", "Collect 200 Runestone Shards.", CHAPTER, GOLD, "Ingredient_Rune_Shard", StatKeys.OBTAINED_PREFIX + "Ingredient_Rune_Shard", 200);
        feat("scrap_rat", "Scrap Rat", "Collect 250 Scraps.", CHAPTER, SILVER, "Ingredient_Scraps", StatKeys.OBTAINED_PREFIX + "Ingredient_Scraps", 250);
        feat("scrap_baron", "Scrap Baron", "Collect 2,500 Scraps.", CHAPTER, GOLD, "Ingredient_Scraps", StatKeys.OBTAINED_PREFIX + "Ingredient_Scraps", 2_500);
        feat("key_master", "Key Master", "Collect 5 Coffer Keys.", CHAPTER, SILVER, "Coffer_Key", StatKeys.OBTAINED_PREFIX + "Coffer_Key", 5);
        feat("key_ring", "Key Ring", "Collect 25 Coffer Keys.", CHAPTER, GOLD, "Coffer_Key", StatKeys.OBTAINED_PREFIX + "Coffer_Key", 25);
        feat("bookworm", "Bookworm", "Pick up 5 lore notes.", CHAPTER, SILVER, "Deco_Scroll", StatKeys.PICKED_PREFIX + "lore", 5);
        multi("loremaster", "Loremaster", "Collect all twelve lore notes.", CHAPTER, GOLD, "Lore_Note_Goblin_Poem",
                req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Died_Improperly", 1), req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Enkindled_Journal_Rite", 1),
                req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Enkindled_Journal_Vermin", 1), req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Enkindled_Ledger_Trade", 1),
                req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Enkindled_Letter_Workshops", 1), req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Enkindled_Research_Gateways", 1),
                req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Goblin_Commission_Abandoned_Parts", 1), req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Goblin_Feastmaster_Menu", 1),
                req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Goblin_Musings_Too_Heavy", 1), req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Goblin_Musings_Translocation", 1),
                req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Goblin_Poem", 1), req(StatKeys.OBTAINED_PREFIX + "Lore_Note_Goblin_Why_Purple", 1));
        feat("silk_road", "Silk Road", "Collect 50 Spider Silk.", CHAPTER, SILVER, "Ingredient_Spider_Silk", StatKeys.OBTAINED_PREFIX + "Ingredient_Spider_Silk", 50);
        feat("silk_merchant", "Silk Merchant", "Collect 500 Spider Silk.", CHAPTER, GOLD, "Ingredient_Spider_Silk", StatKeys.OBTAINED_PREFIX + "Ingredient_Spider_Silk", 500);
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
        feat("kenolite_quarry", "Kenolite Quarry", "Mine 1,000 Kenolite.", CHAPTER, GOLD, "Rock_Kenolite", StatKeys.BROKEN_PREFIX + "Rock_Kenolite", 1_000);
        feat("crystal_clear", "Crystal Clear", "Break 25 Void crystals.", CHAPTER, SILVER, "Rock_Crystal_Void_Block", StatKeys.BROKEN_PREFIX + "void_crystal", 25);
        feat("crystal_cavern", "Crystal Cavern", "Break 250 Void crystals.", CHAPTER, GOLD, "Rock_Crystal_Void_Block", StatKeys.BROKEN_PREFIX + "void_crystal", 250);
        feat("scrap_scavenger", "Scrap Scavenger", "Mine 25 shiny scrap piles.", CHAPTER, SILVER, "Deco_Scrap_Pile_Shiny", StatKeys.BROKEN_PREFIX + "Deco_Scrap_Pile_Shiny", 25);
        feat("goblin_chic", "Goblin Chic", "Place 25 pieces of goblin furniture.", CHAPTER, SILVER, "Furniture_Goblin_Bed", StatKeys.PLACED_PREFIX + "goblin_furniture", 25);
        feat("art_critic", "Art Critic", "Place 6 goblin paintings.", CHAPTER, SILVER, "Goblin_Painting_Breach", StatKeys.PLACED_PREFIX + "goblin_painting", 6);
        // Only the originals count, a crafted _Replica is a different item id.
        multi("gallery_opening", "Gallery Opening", "Hang one of each goblin painting.", CHAPTER, SILVER, "Goblin_Painting_Orbis",
                req(StatKeys.PLACED_PREFIX + "Goblin_Painting_Breach", 1), req(StatKeys.PLACED_PREFIX + "Goblin_Painting_Fail", 1),
                req(StatKeys.PLACED_PREFIX + "Goblin_Painting_ForgottenTemple", 1), req(StatKeys.PLACED_PREFIX + "Goblin_Painting_Orbis", 1),
                req(StatKeys.PLACED_PREFIX + "Goblin_Painting_Portal", 1), req(StatKeys.PLACED_PREFIX + "Goblin_Painting_Repair", 1));
        feat("forger", "Forger", "Craft 3 painting replicas.", CHAPTER, SILVER, "Goblin_Painting_Breach", StatKeys.CRAFT_GROUP_PREFIX + "painting", 3);
        feat("pipe_dream", "Pipe Dream", "Place 100 metal blocks.", CHAPTER, SILVER, "Metal_Goblin_Iron", StatKeys.PLACED_PREFIX + "metal", 100);
        feat("ironworks", "Ironworks", "Place 1,000 metal blocks.", CHAPTER, GOLD, "Metal_Orbis_Copper", StatKeys.PLACED_PREFIX + "metal", 1_000);
        feat("steelworks", "Steelworks", "Place 10,000 metal blocks.", CHAPTER, PLATINUM, "Metal_Goblin_Iron", StatKeys.PLACED_PREFIX + "metal", 10_000);

        // 1.7.2, world events, portal worlds, memories, merchants, NPCs and the hard mode Gobliterator
        feat("breach_closed", "Breach Closed", "Help end a Goblin Breach event.", CHAPTER, GOLD, "PortalKey_Goblin_Breach", StatKeys.WORLD_EVENT_PREFIX + "Goblin_Breach_First_Event", 1);
        feat("hunt_master", "Hunt Master", "Help end 10 Hunt events.", CHAPTER, GOLD, "Weapon_Shortbow_Iron", StatKeys.WORLD_EVENT_PREFIX + "Hunt_Breach", 10);
        feat("coffer_cracker", "Coffer Cracker", "Help end a Coffer Goblin event.", CHAPTER, SILVER, "Coffer_Key", StatKeys.WORLD_EVENT_PREFIX + "Coffer_Goblin", 1);
        hidden("eviction", "Eviction", "Survive the third stage of a Void event in a portal world.", CHAPTER, GOLD, "Ingredient_Crystal_Void", StatKeys.VOID_STAGE_MAX, 3);
        hidden("last_second", "Last Second", "Leave a timed portal world with under a minute left.", CHAPTER, SILVER, "Dungeon_Portal_Early_Exit", StatKeys.PORTAL_CLOSE_EXITS, 1);
        feat("portal_hour", "Portal Hour", "Spend an hour inside portal worlds.", CHAPTER, SILVER, "PortalKey_Henges", StatKeys.PORTAL_SECONDS, HOUR);
        feat("hard_won", "Hard Won", "Record a Memory that only a kill unlocks.", CHAPTER, SILVER, "Deco_Goblin_Plush", StatKeys.MEMORY_BY_KILL, 1);
        feat("haggler", "Haggler", "Trade with a merchant.", CHAPTER, SILVER, "Deco_Treasure_Pile_Small", StatKeys.MERCHANTS, 1);
        feat("trade_route", "Trade Route", "Trade with all three merchants.", CHAPTER, GOLD, "Deco_Treasure", StatKeys.MERCHANTS, 3);
        feat("elders_counsel", "Elder's Counsel", "Talk to a Kweebec Elder.", CHAPTER, BRONZE, "Armor_Kweebec_Head", StatKeys.TALK_PREFIX + "Kweebec_Elder", 1);
        hidden("overclocked_clean", "Overclocked, Clean", "Defeat the Gobliterator on hard mode without dying in The Deepworks.", CHAPTER, PLATINUM, "Goblin_Gobliterator_Difficulty_Button", StatKeys.GOBLITERATOR_HARD_DEATHLESS, 1);
        hidden("overclocked_fast", "Overclocked, Fast", "Defeat the Gobliterator on hard mode within 20 minutes.", CHAPTER, PLATINUM, "Rocket_Boots", StatKeys.GOBLITERATOR_HARD_FAST, 1);
        closer(CHAPTER, "chapter_complete", "Chapter I, Complete", "Unlock every Chapter I feat.");


        // ---------------- Misc ----------------
        feat("chatterbox", "Chatterbox", "Send 100 chat messages.", MISC, BRONZE, "Deco_Scroll", StatKeys.CHAT, 100);
        feat("town_crier", "Town Crier", "Send 1,000 chat messages.", MISC, SILVER, "Deco_Scroll", StatKeys.CHAT, 1_000);
        feat("loudspeaker", "Loudspeaker", "Send 10,000 chat messages.", MISC, GOLD, "Deco_Scroll", StatKeys.CHAT, 10_000);
        feat("getting_started", "Getting Started", "Unlock 10 feats.", MISC, BRONZE, "Deco_Treasure_Pile_Small", StatKeys.FEATS_UNLOCKED, 10);
        feat("achiever", "Achiever", "Unlock 25 feats.", MISC, SILVER, "Deco_Treasure", StatKeys.FEATS_UNLOCKED, 25);
        feat("overachiever", "Overachiever", "Unlock 50 feats.", MISC, GOLD, "Deco_Treasure_Pile_Large", StatKeys.FEATS_UNLOCKED, 50);
        feat("legend_of_orbis", "Legend of Orbis", "Unlock 100 feats.", MISC, PLATINUM, "Deco_Treasure_Pile_Large", StatKeys.FEATS_UNLOCKED, 100);
        feat("icon", "Icon", "Unlock 150 feats.", MISC, PLATINUM, "Deco_Treasure", StatKeys.FEATS_UNLOCKED, 150);
        hidden("completionist", "Completionist", "Unlock 250 feats.", MISC, PLATINUM, "Deco_Treasure_Pile_Large", StatKeys.FEATS_UNLOCKED, 250);
        // 1.7.2, seats, movement, referrals and permission groups
        feat("take_a_seat", "Take a Seat", "Sit on 20 different seats.", MISC, BRONZE, "Furniture_Crude_Stool", StatKeys.SEATS, 20);
        feat("hop", "Hop", "Jump 1,000 times.", MISC, BRONZE, "Rocket_Boots", StatKeys.JUMPS, 1_000);
        feat("bounce", "Bounce", "Jump 10,000 times.", MISC, SILVER, "Rocket_Boots", StatKeys.JUMPS, 10_000);
        hidden("double_jump", "Double Jump", "Use a double jump.", MISC, BRONZE, "Rocket_Boots", StatKeys.DOUBLE_JUMPS, 1);
        feat("tumbler", "Tumbler", "Roll 100 times.", MISC, BRONZE, "Ingredient_Feathers_Light", StatKeys.ROLLS, 100);
        feat("scaler", "Scaler", "Spend 10 minutes climbing.", MISC, SILVER, "Deco_Rope", StatKeys.CLIMB_SECONDS, 600);
        feat("mantle", "Over the Top", "Mantle a ledge 100 times.", MISC, BRONZE, "Tool_Hookshot", StatKeys.MANTLES, 100);
        feat("sprinter", "Sprinter", "Sprint for an hour.", MISC, SILVER, "Potion_Stamina", StatKeys.SPRINT_SECONDS, HOUR);
        hidden("low_profile", "Low Profile", "Spend an hour crouched.", MISC, SILVER, "Armor_Leather_Raven_Head", StatKeys.CROUCH_SECONDS, HOUR);
        hidden("through_the_hub", "Through the Hub", "Arrive through a hub referral.", MISC, BRONZE, "Portal_Device", StatKeys.REFERRAL_JOINS, 1);
        hidden("promoted", "Promoted", "Be added to a permission group.", MISC, SILVER, "Furniture_Flag_Orange", StatKeys.GROUPS_ADDED, 1);
        // Endgame. Points and unlock ladders kept by the tracker, see FeatsTracker afterUnlocks.
        feat("ten_thousand", "Ten Thousand", "Reach 10,000 points.", MISC, GOLD, "Deco_Treasure", StatKeys.FEAT_POINTS, 10_000);
        hidden("twenty_thousand", "Twenty Thousand", "Reach 20,000 points.", MISC, PLATINUM, "Deco_Treasure_Pile_Large", StatKeys.FEAT_POINTS, 20_000);
        define(of("grandmaster", "Grandmaster", "Unlock 350 feats.", MISC, PLATINUM, "Deco_Treasure_Pile_Large", true,
                req(StatKeys.FEATS_UNLOCKED, 350)).withBanner());
        define(of("all_of_it", "All of It", "Unlock 425 feats.", MISC, PLATINUM, "Deco_Treasure_Pile_Large", true,
                req(StatKeys.FEATS_UNLOCKED, 425)).withBanner());
        define(of("grand_slam", "Grand Slam", "Unlock every Platinum feat.", MISC, PLATINUM, "Deco_Treasure_Pile_Large", true,
                req(StatKeys.PLATINUMS_DONE, 1)).withBanner());
    }

    /**
     * The Factions tab. Dominion Factions counts these and hands them to Feats with FeatsApi.addStat, so
     * they only move while Dominion is installed. Credits for offline members are written into their file.
     */
    private void defineFactions() {
        sectionRequires = Set.of(Sibling.DOMINION);
        feat("faction_founder", "Founding Charter", "Found a faction.", FACTIONS, BRONZE, "Furniture_Human_Ruins_Banner", StatKeys.DOMINION_FACTIONS_FOUNDED, 1);
        feat("staked_claim", "Staked Claim", "Claim a chunk for your faction.", FACTIONS, BRONZE, "Furniture_Flag_Small_Red", StatKeys.DOMINION_CLAIMS, 1);
        feat("landholder", "Landholder", "Claim 50 chunks for your faction.", FACTIONS, SILVER, "Furniture_Flag_Orange", StatKeys.DOMINION_CLAIMS, 50);
        feat("sprawl", "Sprawl", "Claim 250 chunks for your faction.", FACTIONS, GOLD, "Tool_Map", StatKeys.DOMINION_CLAIMS, 250);
        feat("recruiter", "Recruiter", "Invite 10 players to your faction.", FACTIONS, SILVER, "Deco_Scroll", StatKeys.DOMINION_INVITES, 10);
        feat("blood_feud", "Blood Feud", "Defeat a player from a faction you are at war with.", FACTIONS, BRONZE, "Weapon_Sword_Iron", StatKeys.DOMINION_ENEMY_KILLS, 1);
        feat("war_hero", "War Hero", "Defeat 25 players from factions you are at war with.", FACTIONS, GOLD, "Weapon_Battleaxe_Iron", StatKeys.DOMINION_ENEMY_KILLS, 25);
        hidden("behind_enemy_lines", "Behind Enemy Lines", "Die on land held by a faction you are at war with.", FACTIONS, BRONZE, "Deco_Bone_Skulls", StatKeys.DOMINION_ENEMY_LAND_DEATHS, 1);
        feat("victory_banner", "Victory Banner", "Win a siege with your faction.", FACTIONS, SILVER, "Furniture_Outlander_Banner", StatKeys.DOMINION_SIEGES_WON, 1);
        feat("conqueror", "Conqueror", "Win 10 sieges with your faction.", FACTIONS, PLATINUM, "Furniture_Ancient_Statue", StatKeys.DOMINION_SIEGES_WON, 10);
        hidden("sacked", "Sacked", "Lose a siege with your faction.", FACTIONS, BRONZE, "Deco_Bone_Pile", StatKeys.DOMINION_SIEGES_LOST, 1);
        feat("orders_received", "Orders Received", "Complete a faction mission.", FACTIONS, BRONZE, "Deco_Map", StatKeys.DOMINION_MISSIONS, 1);
        feat("mission_veteran", "Old Hand", "Complete 25 faction missions.", FACTIONS, GOLD, "Furniture_Ancient_Table", StatKeys.DOMINION_MISSIONS, 25);
        feat("dues_paid", "Dues Paid", "Put 1,000 coins into your faction bank.", FACTIONS, SILVER, "Deco_Treasure_Pile_Small", StatKeys.DOMINION_BANK_DEPOSITED, 1_000);
        feat("patron", "Patron", "Put 25,000 coins into your faction bank.", FACTIONS, GOLD, "Deco_Treasure_Pile_Large", StatKeys.DOMINION_BANK_DEPOSITED, 25_000);
        // 1.7.2, the keys Dominion 0.6.3 sends on top of the first ten
        feat("enlisted", "Enlisted", "Join a faction.", FACTIONS, BRONZE, "Furniture_Human_Ruins_Banner", StatKeys.DOMINION_JOINED, 1);
        hidden("exiled", "Exiled", "Be kicked from a faction.", FACTIONS, BRONZE, "Deco_Bone_Pile", StatKeys.DOMINION_KICKED, 1);
        feat("declaration", "Declaration", "Declare war on another faction.", FACTIONS, SILVER, "Weapon_Sword_Iron", StatKeys.DOMINION_WARS_DECLARED, 1);
        feat("pact", "Pact", "Form an alliance.", FACTIONS, SILVER, "Deco_Scroll", StatKeys.DOMINION_ALLIANCES, 1);
        feat("upgraded", "Upgraded", "Buy a faction upgrade.", FACTIONS, SILVER, "Bench_WorkBench", StatKeys.DOMINION_UPGRADES, 1);
        feat("fortified", "Fortified", "Hold a faction upgrade at level 5.", FACTIONS, GOLD, "Rock_Stone_Brick", StatKeys.DOMINION_UPGRADE_LEVEL_MAX, 5);
        feat("faction_market", "Faction Market", "Trade on the faction market.", FACTIONS, SILVER, "Deco_Treasure_Pile_Small", StatKeys.DOMINION_MARKET_TRADES, 1);
        hidden("trespasser", "Trespasser", "Enter the land of 25 other factions.", FACTIONS, SILVER, "Tool_Map", StatKeys.DOMINION_FOREIGN_ENTRIES, 25);
        feat("held_the_line", "Held the Line", "Defend your land through a siege.", FACTIONS, GOLD, "Weapon_Shield_Iron", StatKeys.DOMINION_SIEGES_DEFENDED, 1);
        feat("breacher", "Breacher", "Breach another faction's wall.", FACTIONS, GOLD, "Weapon_Bomb_Large_Fire", StatKeys.DOMINION_BREACHES, 1);
        feat("king_of_the_hill", "King of the Hill", "Win a hill event for your faction.", FACTIONS, GOLD, "Furniture_Flag_Small_Red", StatKeys.DOMINION_HILL_WINS, 1);
        feat("outpost", "Outpost", "Hold an outpost for your faction.", FACTIONS, SILVER, "Furniture_Flag_Orange", StatKeys.DOMINION_OUTPOSTS_HELD, 1);
        feat("powerhouse", "Powerhouse", "Reach full power.", FACTIONS, GOLD, "Potion_Stamina_Greater", StatKeys.DOMINION_POWER_MAX, 1);
        hidden("downsizing", "Downsizing", "Release a claim.", FACTIONS, BRONZE, "Deco_Map", StatKeys.DOMINION_UNCLAIMS, 1);
        // 1.7.3, Dominion 0.7.2 credits one rank change to the member for each promotion or demotion
        feat("faction_rank", "New Rank", "Be promoted or demoted in your faction.", FACTIONS, BRONZE, "Deco_Scroll", StatKeys.DOMINION_RANK_CHANGES, 1);
        // Cross mod, Dominion and Coinage together. Keys written by the Coinage binding and Dominion's bridge.
        define(of("war_chest", "War Chest", "Hold a net worth of 50,000 coins while your faction is at war.", FACTIONS, GOLD, "Deco_Treasure_Pile_Large", true,
                req(StatKeys.COINAGE_PREFIX + "war_chest", 1)).requiring(Sibling.DOMINION, Sibling.COINAGE));
        define(of("treasurer", "Treasurer", "Put 100,000 coins into your faction bank.", FACTIONS, PLATINUM, "Deco_Treasure_Pile_Large", false,
                req(StatKeys.DOMINION_BANK_DEPOSITED, 100_000)).requiring(Sibling.DOMINION, Sibling.COINAGE));
        sectionRequires = Set.of();
    }

    /**
     * A category closer, the hidden Platinum last row of a tab that unlocks once every other personal feat of
     * the tab is unlocked. The tracker raises category_done:<name> after each unlock, counting the tab without
     * the closers themselves, so a closer never waits on itself.
     */
    private void closer(Category cat, String id, String name, String desc) {
        define(of(id, name, desc, cat, PLATINUM, cat.icon(), true, req(StatKeys.CATEGORY_DONE_PREFIX + cat.name(), 1)).withBanner());
    }

    /**
     * The admin packs, each a tab of its own that exists only while its switch in Feats.json is on. Last Life
     * and Ten Days stay in Survival, the Hardcore pack adds the two feats past them.
     */
    private void definePacks() {
        // ---------------- Social ----------------
        define(of("company", "Company", "Spend an hour online with at least one other player.", SOCIAL, BRONZE, "Deco_Kweebec_Plush", false,
                req(StatKeys.COOP_HOURS, 1)).inPack(Pack.SOCIAL));
        define(of("regular_company", "Good Company", "Spend 50 hours online with other players.", SOCIAL, GOLD, "Deco_Goblin_Plush", false,
                req(StatKeys.COOP_HOURS, 50)).inPack(Pack.SOCIAL));
        define(of("crowd", "Crowd", "Be online with 9 other players at once.", SOCIAL, SILVER, "Furniture_Tavern_Chair", false,
                req(StatKeys.PEAK_WITH_ME, 10)).inPack(Pack.SOCIAL));
        define(of("familiar_faces", "Familiar Faces", "Play alongside 25 different players.", SOCIAL, SILVER, "Deco_Kweebec_Plush", false,
                req(StatKeys.PLAYERS_MET, 25)).inPack(Pack.SOCIAL));
        define(of("hundred_faces", "A Hundred Faces", "Play alongside 100 different players.", SOCIAL, PLATINUM, "Deco_Treasure_Pile_Large", false,
                req(StatKeys.PLAYERS_MET, 100)).inPack(Pack.SOCIAL));
        define(of("shared_first", "Shared First", "Share in a server milestone within a minute of it being reached.", SOCIAL, SILVER, "Deco_Trophy_Harvest", true,
                req(StatKeys.MILESTONE_WITNESSED, 1)).inPack(Pack.SOCIAL));

        // ---------------- Hardcore ----------------
        define(of("thirty_days_hardcore", "Thirty Days", "Survive 30 game days with hardcore on.", HARDCORE, PLATINUM, "Deco_Bone_Skulls", true,
                req(StatKeys.HARDCORE_DAYS, 30)).inPack(Pack.HARDCORE));
        define(of("full_pool", "Full Pool", "Reach 10 game days without losing a life.", HARDCORE, GOLD, "Potion_Health_Greater", false,
                req(StatKeys.HARDCORE_CLEAN_DAYS, 10)).inPack(Pack.HARDCORE));

        // ---------------- PvP ----------------
        define(of("vendetta", "Vendetta", "Defeat the player who last defeated you.", PVP, GOLD, "Weapon_Daggers_Claw_Bone", true,
                req(StatKeys.VENDETTAS, 1)).inPack(Pack.PVP));
        define(of("spree", "Spree", "Defeat 5 players without dying.", PVP, GOLD, "Weapon_Sword_Iron", false,
                req(StatKeys.PLAYER_KILL_STREAK, 5)).inPack(Pack.PVP));
        define(of("long_duel", "Long Duel", "Defeat a player from 30 metres away.", PVP, SILVER, "Weapon_Crossbow_Iron", false,
                req(StatKeys.PLAYER_KILLS_FAR, 1)).inPack(Pack.PVP));

        // ---------------- Calendar ----------------
        // Real dates in the server's zone or CalendarZone, as date:MMDD, checked at join and at midnight.
        define(of("new_year", "New Year", "Be online on the first of January.", CALENDAR, BRONZE, "Ingredient_Ice_Essence", true,
                req(StatKeys.DATE_PREFIX + "0101", 1)).inPack(Pack.CALENDAR));
        define(of("midsummer", "Midsummer", "Be online on the 21st of June.", CALENDAR, BRONZE, "Plant_Crop_Wheat_Item", true,
                req(StatKeys.DATE_PREFIX + "0621", 1)).inPack(Pack.CALENDAR));
        define(of("midwinter", "Midwinter", "Be online on the 21st of December.", CALENDAR, BRONZE, "Rock_Ice", true,
                req(StatKeys.DATE_PREFIX + "1221", 1)).inPack(Pack.CALENDAR));
        define(of("server_birthday", "Server Birthday", "Be online on the anniversary of the day Feats was installed.", CALENDAR, SILVER, "Deco_Treasure", true,
                req(StatKeys.SERVER_BIRTHDAY, 1)).inPack(Pack.CALENDAR));

        // ---------------- Grind ----------------
        define(of("world_eater", "World Eater", "Break 10,000,000 blocks.", GRIND, PLATINUM, "Tool_Drill_Scrap", true,
                req(StatKeys.BLOCKS_BROKEN, 10_000_000)).inPack(Pack.GRIND));
        define(of("extinction", "Extinction", "Defeat 100,000 creatures.", GRIND, PLATINUM, "Weapon_Battleaxe_Adamantite", true,
                req(StatKeys.KILLS, 100_000)).inPack(Pack.GRIND));
        define(of("thousand_hours", "Thousand Hours", "Play for 1,000 hours.", GRIND, PLATINUM, "Deco_Treasure_Pile_Large", true,
                req(StatKeys.PLAYTIME, 1_000 * HOUR)).inPack(Pack.GRIND));
    }

    /**
     * The Coinage tab. The Coinage binding writes these counters from Coinage's economy listener, so they only
     * move while Coinage is installed. Balance and net worth are records of the highest value seen.
     */
    private void defineCoinage() {
        sectionRequires = Set.of(Sibling.COINAGE);
        feat("first_coin", "First Coin", "Receive a payment.", COINAGE, BRONZE, "Deco_Treasure_Pile_Small", StatKeys.COINAGE_PREFIX + "received", 1);
        feat("payday", "Payday", "Earn 1,000 coins.", COINAGE, SILVER, "Ingredient_Bar_Copper", StatKeys.COINAGE_PREFIX + "received", 1_000);
        feat("well_off", "Well Off", "Hold 10,000 coins at once.", COINAGE, GOLD, "Ingredient_Bar_Silver", StatKeys.COINAGE_PREFIX + "balance_max", 10_000);
        feat("tycoon", "Tycoon", "Reach a net worth of 100,000 coins.", COINAGE, PLATINUM, "Deco_Treasure_Pile_Large", StatKeys.COINAGE_PREFIX + "net_worth_max", 100_000);
        feat("generous", "Generous", "Send 10 payments.", COINAGE, BRONZE, "Deco_Scroll", StatKeys.COINAGE_PREFIX + "payments_sent", 10);
        feat("benefactor", "Benefactor", "Send 10,000 coins to other players.", COINAGE, GOLD, "Ingredient_Bar_Gold", StatKeys.COINAGE_PREFIX + "paid", 10_000);
        feat("customer", "Customer", "Buy 100 items from shops.", COINAGE, SILVER, "Furniture_Ancient_Chest_Small", StatKeys.COINAGE_PREFIX + "shop_bought", 100);
        feat("shopkeeper", "Shopkeeper", "Sell 100 items through shops.", COINAGE, SILVER, "Furniture_Ancient_Chest_Large", StatKeys.COINAGE_PREFIX + "shop_sold", 100);
        feat("market_maker", "Market Maker", "Sell 25 market listings.", COINAGE, GOLD, "Deco_Treasure", StatKeys.COINAGE_PREFIX + "listings_sold", 25);
        hidden("winning_bid", "Winning Bid", "Win an auction.", COINAGE, SILVER, "Deco_Scroll", StatKeys.COINAGE_PREFIX + "auctions_won", 1);
        feat("trader", "Trader", "Settle 10 trades with other players.", COINAGE, SILVER, "Deco_Kweebec_Plush", StatKeys.COINAGE_PREFIX + "trades", 10);
        feat("saver", "Saver", "Put coins into savings.", COINAGE, BRONZE, "Furniture_Crude_Chest_Small", StatKeys.COINAGE_PREFIX + "savings_deposits", 1);
        feat("compound", "Compound", "Earn 100 coins of interest.", COINAGE, SILVER, "Ingredient_Bar_Copper", StatKeys.COINAGE_PREFIX + "interest", 100);
        hidden("borrower", "Borrower", "Take a loan.", COINAGE, BRONZE, "Deco_Scroll", StatKeys.COINAGE_PREFIX + "loans", 1);
        feat("clean_slate", "Clean Slate", "Repay a loan in full.", COINAGE, SILVER, "Deco_Scroll", StatKeys.COINAGE_PREFIX + "loans_repaid", 1);
        hidden("defaulted", "Defaulted", "Default on a loan.", COINAGE, BRONZE, "Deco_Bone_Pile", StatKeys.COINAGE_PREFIX + "loans_defaulted", 1);
        feat("gambler", "Gambler", "Place 10 bets.", COINAGE, BRONZE, "Deco_Goblin_Plush", StatKeys.COINAGE_PREFIX + "bets", 10);
        feat("lucky", "Lucky", "Win 50 bets.", COINAGE, SILVER, "Deco_Trophy_Harvest", StatKeys.COINAGE_PREFIX + "bets_won", 50);
        feat("high_roller", "High Roller", "Stake 10,000 coins in total.", COINAGE, GOLD, "Ingredient_Bar_Gold", StatKeys.COINAGE_PREFIX + "staked", 10_000);
        hidden("heavy_losses", "Heavy Losses", "Lose 10,000 coins at the tables.", COINAGE, SILVER, "Deco_Bone_Skulls", StatKeys.COINAGE_PREFIX + "lost", 10_000);
        hidden("jackpot", "Jackpot", "Win the lottery.", COINAGE, GOLD, "Deco_Treasure_Pile_Large", StatKeys.COINAGE_PREFIX + "lottery_wins", 1);
        feat("daily_week", "Daily, a Week", "Claim your daily reward 7 days in a row.", COINAGE, SILVER, "Deco_Treasure_Pile_Small", StatKeys.COINAGE_PREFIX + "daily_best_streak", 7);
        feat("daily_month", "Daily, a Month", "Claim your daily reward 30 days in a row.", COINAGE, GOLD, "Deco_Treasure", StatKeys.COINAGE_PREFIX + "daily_best_streak", 30);
        feat("paper_trail", "Paper Trail", "Turn coins into a note.", COINAGE, BRONZE, "Deco_Scroll", StatKeys.COINAGE_PREFIX + "notes_issued", 1);
        // Cross mod, Coinage and Dominion together. Written by the Coinage binding when both parties have allied factions.
        define(of("allied_trade", "Allied Trade", "Settle a trade with a member of an allied faction.", COINAGE, SILVER, "Deco_Scroll", false,
                req(StatKeys.COINAGE_PREFIX + "allied_trades", 1)).requiring(Sibling.COINAGE, Sibling.DOMINION));
        sectionRequires = Set.of();
    }

    /**
     * The Chronicle tab. The Chronicle binding reads Chronicle Stats' time windows and the RPG Leveling bridge
     * and raises these as records. The three level feats only move while Chronicle reports an rpg_level stat.
     */
    private void defineChronicle() {
        sectionRequires = Set.of(Sibling.CHRONICLE);
        feat("busy_day", "Busy Day", "Break 2,500 blocks in one day.", CHRONICLE, SILVER, "Tool_Pickaxe_Iron", StatKeys.CHRONICLE_PREFIX + "day_blocks_max", 2_500);
        feat("big_week", "Big Week", "Defeat 1,000 creatures in one week.", CHRONICLE, GOLD, "Weapon_Battleaxe_Iron", StatKeys.CHRONICLE_PREFIX + "week_kills_max", 1_000);
        feat("full_month", "Full Month", "Be active every day of a month.", CHRONICLE, GOLD, "Deco_Scroll", StatKeys.CHRONICLE_PREFIX + "month_days_max", 28);
        feat("level_10", "Level 10", "Reach level 10 in RPG Leveling.", CHRONICLE, SILVER, "Rune_Empty", StatKeys.CHRONICLE_PREFIX + "rpg_level_max", 10);
        feat("level_25", "Level 25", "Reach level 25 in RPG Leveling.", CHRONICLE, GOLD, "Rune_Fireball", StatKeys.CHRONICLE_PREFIX + "rpg_level_max", 25);
        feat("level_50", "Level 50", "Reach level 50 in RPG Leveling.", CHRONICLE, PLATINUM, "Bench_Abilities", StatKeys.CHRONICLE_PREFIX + "rpg_level_max", 50);
        sectionRequires = Set.of();
    }

    /**
     * World milestones and server firsts. The Goblin Lab flags are raised for the whole server by the
     * Generator and the three repairable devices, see {@code AugmentBlock.ProgressionKey} in the assets.
     */
    // ---- the mods TrackerAPI reads, 1.9.0 ---------------------------------------------------------------------

    /**
     * What a definition class under {@code model/mods} adds feats with. The registry hands one to each
     * {@code <Mod>Feats.define}, with the mod's {@link Sibling} and {@link Sibling#TRACKER_API} as the section
     * requirement, so a feat that names no sibling itself gets both. Personal feats go in the mod's tab from
     * {@link Category#ofMod}, world feats in {@link Category#WORLD} through {@link #first} and {@link #milestone}.
     * The counters are the {@code <mod>:<stat>} keys {@code integration/ModKinds} mirrors, see docs/DEVELOPMENT.md.
     */
    public interface Definer {
        /** A personal feat on one counter reaching a target. */
        void feat(String id, String name, String desc, Category cat, Tier tier, String icon, String key, long target);

        /** A hidden personal feat on one counter, shown as ??? until unlocked. */
        void hidden(String id, String name, String desc, Category cat, Tier tier, String icon, String key, long target);

        /** A personal feat that needs every requirement at once. */
        void multi(String id, String name, String desc, Category cat, Tier tier, String icon, Requirement... reqs);

        /** A feat built with {@link #of} and decorated with {@code withBanner} or {@code requiring}. */
        void define(Feat feat);

        /** A server first in the World tab, for the one player who gets there before anyone else. */
        void first(String id, String name, String desc, Tier tier, String icon, Trigger trigger);

        /** A server milestone in the World tab, reached once for the whole server. */
        void milestone(String id, String name, String desc, Tier tier, String icon, Trigger trigger);

        /** A personal feat as a value, to decorate before {@link #define}. */
        static Feat of(String id, String name, String desc, Category cat, Tier tier, String icon, boolean hidden, Requirement... reqs) {
            return FeatRegistry.of(id, name, desc, cat, tier, icon, hidden, reqs);
        }

        static Requirement req(String key, long target) {
            return FeatRegistry.req(key, target);
        }

        /** Raised the first time anyone unlocks the personal feat, for {@link #first} and {@link #milestone}. */
        static Trigger after(String featId) {
            return Trigger.after(featId);
        }

        /** Raised once a server total in server.json reaches the target, for {@link #milestone}. */
        static Trigger total(String key, long target) {
            return Trigger.total(key, target);
        }
    }

    /** The registry's own {@link Definer}, every call lands in {@link #add} under the current section. */
    private final class RegistryDefiner implements Definer {
        @Override
        public void feat(String id, String name, String desc, Category cat, Tier tier, String icon, String key, long target) {
            FeatRegistry.this.feat(id, name, desc, cat, tier, icon, key, target);
        }

        @Override
        public void hidden(String id, String name, String desc, Category cat, Tier tier, String icon, String key, long target) {
            FeatRegistry.this.hidden(id, name, desc, cat, tier, icon, key, target);
        }

        @Override
        public void multi(String id, String name, String desc, Category cat, Tier tier, String icon, Requirement... reqs) {
            FeatRegistry.this.multi(id, name, desc, cat, tier, icon, reqs);
        }

        @Override
        public void define(Feat feat) {
            FeatRegistry.this.define(feat);
        }

        @Override
        public void first(String id, String name, String desc, Tier tier, String icon, Trigger trigger) {
            FeatRegistry.this.first(id, name, desc, tier, icon, trigger);
        }

        @Override
        public void milestone(String id, String name, String desc, Tier tier, String icon, Trigger trigger) {
            FeatRegistry.this.world(id, name, desc, tier, icon, trigger);
        }
    }

    /**
     * The feats of the eleven mods TrackerAPI 1.2.0 reads, one definition class per mod under {@code model/mods}.
     * Each section requires the mod's sibling and TrackerAPI, and the whole set is left out, with {@link #modsNote()}
     * set, unless TrackerAPI is present and at least {@link #MODS_MINIMUM}, since an older TrackerAPI publishes
     * none of the records {@code ModKinds} mirrors and the counters could never move.
     */
    private void defineMods() {
        if (!present.contains(Sibling.TRACKER_API)) {
            modsNote = "Feats leaves out the feats of the mods TrackerAPI reads, TrackerAPI is not installed or"
                    + " its Tracker setting is never";
            return;
        }
        if (trackerVersion == null || VersionCompare.below(trackerVersion, MODS_MINIMUM)) {
            modsNote = "Feats leaves out the feats of the mods TrackerAPI reads, they need TrackerAPI " + MODS_MINIMUM
                    + " and this server has " + (trackerVersion == null ? "an unknown version" : trackerVersion);
            return;
        }
        Definer d = new RegistryDefiner();
        section(Sibling.RPG_LEVELING);
        RpgFeats.define(d);
        section(Sibling.MAJOR_DUNGEONS);
        DungeonsFeats.define(d);
        section(Sibling.PERFECT_PARRIES);
        ParriesFeats.define(d);
        section(Sibling.WONDER_WEAPONS);
        RelicsFeats.define(d);
        section(Sibling.ZEPHYR);
        ZephyrFeats.define(d);
        section(Sibling.SIMPLE_ENCHANTMENTS);
        EnchantFeats.define(d);
        section(Sibling.VEIN_MINING);
        VeinFeats.define(d);
        section(Sibling.LUCKY_MINING);
        LuckyFeats.define(d);
        section(Sibling.MMO_SKILL_TREE);
        MmoFeats.define(d);
        section(Sibling.ENDGAME);
        EndgameFeats.define(d);
        section(Sibling.TAMEWORK);
        TameworkFeats.define(d);
        sectionRequires = Set.of();
    }

    /** Every feat defined next needs this mod and TrackerAPI. */
    private void section(Sibling mod) {
        sectionRequires = EnumSet.of(mod, Sibling.TRACKER_API);
    }

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
        first("first_legend_of_orbis", "Founder", "Be the first on this server to unlock 100 feats.", PLATINUM, "Deco_Treasure_Pile_Large", after("legend_of_orbis"));
        first("first_legend", "Warpath", "Be the first on this server to defeat 5,000 creatures.", PLATINUM, "Weapon_Battleaxe_Mithril", after("legend"));
        first("first_master_explorer", "Surveyor", "Be the first on this server to discover all 5 zones.", PLATINUM, "Deco_Map", after("master_explorer"));

        // ---------------- 1.7.2 server totals and firsts ----------------
        // Totals live in server.json, raised by the tracker and the hooks, see WorldStore. The Full House
        // target here is the default, the tracker reads the real FullHouse setting when it checks.
        world("world_million_blocks", "A Million Blocks", "Break a million blocks between everyone.", SILVER, "Tool_Pickaxe_Iron", total(StatKeys.TOTAL_BLOCKS_BROKEN, 1_000_000));
        world("world_ten_million_blocks", "Ten Million Blocks", "Break ten million blocks between everyone.", GOLD, "Tool_Drill_Scrap", total(StatKeys.TOTAL_BLOCKS_BROKEN, 10_000_000));
        world("world_hundred_thousand_kills", "A Hundred Thousand", "Defeat a hundred thousand creatures between everyone.", GOLD, "Weapon_Battleaxe_Iron", total(StatKeys.TOTAL_KILLS, 100_000));
        world("world_thousand_bosses", "A Thousand Bosses", "Defeat a thousand bosses between everyone.", GOLD, "Weapon_Club_Doomed", total(StatKeys.TOTAL_KILLS_BOSS, 1_000));
        world("world_hundred_players", "A Hundred Players", "Have a hundred players join the server.", SILVER, "Deco_Kweebec_Plush", total(StatKeys.TOTAL_PLAYERS_KNOWN, 100));
        world("world_thousand_players", "A Thousand Players", "Have a thousand players join the server.", GOLD, "Deco_Goblin_Plush", total(StatKeys.TOTAL_PLAYERS_KNOWN, 1_000));
        world(FULL_HOUSE_ID, "Full House", "Have 20 players online at once.", SILVER, "Furniture_Tavern_Chair", total(StatKeys.TOTAL_PEAK_ONLINE, FULL_HOUSE_DEFAULT));
        world("world_first_year", "First Year", "Run with Feats for a year.", GOLD, "Deco_Trophy_Harvest", total(StatKeys.TOTAL_SERVER_DAYS, 365));
        world("world_chunk_factory", "Chunk Factory", "Generate 100,000 chunks.", SILVER, "Tool_Map", total(StatKeys.TOTAL_CHUNKS_GENERATED, 100_000));
        first("first_leviathan", "Whaler", "Be the first on this server to land a humpback whale.", PLATINUM, "Fish_Whale_Humpback_Item", after("leviathan"));
        first("first_bestiary", "Taxonomist", "Be the first on this server to defeat 100 kinds of creature.", PLATINUM, "Deco_Bone_Skulls", after("bestiary"));
        add(new Feat("first_grand_slam", "Peerless", "Be the first on this server to unlock every Platinum feat.", WORLD, PLATINUM,
                "Deco_Treasure_Pile_Large", List.of(), false, Scope.FIRST, after("grand_slam")).withBanner());
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
