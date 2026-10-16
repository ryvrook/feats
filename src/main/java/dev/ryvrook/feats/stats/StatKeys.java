package dev.ryvrook.feats.stats;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Well-known counter keys. Dynamic keys use the prefixes below plus an id. */
public final class StatKeys {
    private StatKeys() {}

    // Totals
    public static final String BLOCKS_BROKEN = "blocks_broken";
    public static final String ORE_MINED = "ore_mined";
    public static final String WOOD_CHOPPED = "wood_chopped";
    public static final String BLOCKS_PLACED = "blocks_placed";
    public static final String CRAFTED = "crafted";
    public static final String PICKED_UP = "picked_up";
    public static final String FISH_CAUGHT = "fish_caught";
    public static final String HARVESTED = "harvested";
    public static final String KILLS = "kills";
    public static final String KILLS_RANGED = "kills_ranged";
    public static final String KILLS_BOSS = "kills_boss";
    public static final String KILLS_PLAYER = "kills_player";
    public static final String DEATHS = "deaths";
    public static final String DAMAGE_DEALT = "damage_dealt";
    public static final String DAMAGE_TAKEN = "damage_taken";
    public static final String ZONES_DISCOVERED = "zones_discovered";
    public static final String DISTANCE = "distance";
    /** Seconds connected. Playtime includes idle time, so a player standing still keeps earning it. */
    public static final String PLAYTIME = "playtime";
    public static final String JOINS = "joins";
    public static final String CHAT = "chat";
    public static final String CHESTS_OPENED = "use:chest";
    public static final String BEDS_USED = "use:bed";
    public static final String BENCHES_USED = "use:bench";
    public static final String FEATS_UNLOCKED = "feats_unlocked";
    public static final String ABILITY_CASTS = "ability_casts";
    public static final String PORTALS_ENTERED = "portals_entered";
    public static final String PORTAL_TYPES = "portal_types";
    public static final String MEMORIES = "memories";
    public static final String FOOD_EATEN = "food_eaten";
    public static final String POTIONS_DRUNK = "potions_drunk";
    public static final String SPYGLASS_USES = "use_spyglass";
    public static final String OBTAINED = "obtained";
    /** Hard mode Gobliterator kills credited to a player who wore no armour at that moment. */
    public static final String GOBLITERATOR_HM_UNARMOURED = "gobliterator_hm_unarmoured";
    /** Gobliterator kills credited to a player who had not died in that Deepworks run. */
    public static final String GOBLITERATOR_DEATHLESS = "gobliterator_deathless";
    /** Gobliterator kills credited to a player within 20 minutes of entering that Deepworks run. */
    public static final String GOBLITERATOR_FAST = "gobliterator_fast";
    /** Metres travelled while swimming, sampled with {@link #DISTANCE}. */
    public static final String SWIM_DISTANCE = "swim_distance";
    /** Metres travelled while gliding, sampled with {@link #DISTANCE}. */
    public static final String GLIDE_DISTANCE = "glide_distance";
    /** Metres travelled while riding a mount or a minecart, sampled with {@link #DISTANCE}. */
    public static final String RIDE_DISTANCE = "ride_distance";
    /** 1 once the player stood at the floor of a world, near the lowest block layer. */
    public static final String BOTTOM_REACHED = "bottom_reached";
    /** 1 once the player stood at the ceiling of a world, near the highest block layer. */
    public static final String TOP_REACHED = "top_reached";
    /** Animals the player tamed by feeding them. */
    public static final String TAMED = "tamed";
    /** The most kills in a row without dying. The running streak itself is only kept in memory. */
    public static final String KILL_STREAK = "kill_streak";
    /** The largest single hit the player dealt, in damage points. */
    public static final String BIGGEST_HIT = "biggest_hit";
    /** The longest time connected in one go, in seconds. */
    public static final String LONGEST_SESSION = "longest_session";
    /** Boss kills credited to a player who took no damage between the first hit of the fight and the kill. */
    public static final String BOSS_FLAWLESS = "boss_flawless";

    // Counters Dominion Factions keeps and hands to Feats through FeatsApi.addStat. Only the Factions
    // feats read them, see FeatRegistry. The names match Dominion's integration StatKeys.
    public static final String DOMINION_PREFIX = "dominion:";
    public static final String DOMINION_CLAIMS = DOMINION_PREFIX + "claims";
    public static final String DOMINION_ENEMY_KILLS = DOMINION_PREFIX + "enemy_kills";
    public static final String DOMINION_ENEMY_LAND_DEATHS = DOMINION_PREFIX + "enemy_land_deaths";
    public static final String DOMINION_SIEGES_WON = DOMINION_PREFIX + "sieges_won";
    public static final String DOMINION_SIEGES_LOST = DOMINION_PREFIX + "sieges_lost";
    public static final String DOMINION_MISSIONS = DOMINION_PREFIX + "missions";
    public static final String DOMINION_BANK_DEPOSITED = DOMINION_PREFIX + "bank_deposited";
    public static final String DOMINION_FACTIONS_FOUNDED = DOMINION_PREFIX + "factions_founded";
    public static final String DOMINION_INVITES = DOMINION_PREFIX + "invites";


    // ---- 1.7.2 counters. The own path hook and the TrackerAPI kind behind each one are listed in
    // docs/EXPANSION-PLAN.md section 3.4. Per id keys use the prefixes further down.

    // Exploration and world
    /** Distinct biomes stood in, raised by {@code biome:<name>} discoveries. */
    public static final String BIOMES_VISITED = "biomes_visited";
    /** Distinct weather ids experienced for at least 10 seconds, raised by {@code weather:<id>}. */
    public static final String WEATHERS_SEEN = "weathers_seen";
    /** Distinct moon phases seen while online at night, raised by {@code moon:<phase>}. */
    public static final String MOONS_SEEN = "moons_seen";
    /** Instances discovered by their title key, raised by {@code instance:<key>}. */
    public static final String INSTANCES_DISCOVERED = "instances_discovered";
    /** The most respawn points held at once in one world. */
    public static final String RESPAWN_POINTS = "respawn_points";
    /** Seconds spent inside portal worlds. */
    public static final String PORTAL_SECONDS = "portal_seconds";
    /** The highest Void event stage reached inside a portal world. */
    public static final String VOID_STAGE_MAX = "void_stage_max";
    /** Exits from a timed portal world with under 60 seconds left. */
    public static final String PORTAL_CLOSE_EXITS = "portal_close_exits";
    /** World events that ended in success with this player taking part, raised with {@code world_event:<id>}. */
    public static final String WORLD_EVENTS_WON = "world_events_won";
    /** Memories recorded that only a kill unlocks. */
    public static final String MEMORY_BY_KILL = "memory_by_kill";
    /** Distinct merchants traded with, raised by {@code merchant:<id>}. */
    public static final String MERCHANTS = "merchants";

    // Items, blocks and crafting
    /** Items that reached zero durability in hand. */
    public static final String TOOLS_BROKEN = "tools_broken";
    /** Items whose durability rose on the same slot without a swap. */
    public static final String REPAIRS = "repairs";
    /** Distinct trunk species chopped, raised by {@code wood:<species>}. */
    public static final String WOOD_SPECIES = "wood_species";
    /** Distinct block ids placed, raised by {@code placed_kind:<id>}. */
    public static final String PLACED_KINDS = "placed_kinds";
    /** Knowledge recipes learned, raised by {@code recipe:<id>}. */
    public static final String RECIPES_KNOWN = "recipes_known";
    /** Distinct full armour sets worn at once, raised by {@code armour_set:<material>}. */
    public static final String ARMOUR_SETS = "armour_sets";
    /** Seeds placed, with {@code seed:<crop>} per crop. */
    public static final String SEEDS_PLANTED = "seeds_planted";
    public static final String WATERINGS = "waterings";
    public static final String FERTILIZINGS = "fertilizings";
    public static final String COOP_USES = "coop_uses";
    public static final String PRODUCE_COLLECTED = "produce_collected";
    public static final String CRATES_USED = "crates_used";
    public static final String TRAPS_PLACED = "traps_placed";
    public static final String TRAPS_BAITED = "traps_baited";
    /** Fish from the rarest drop tier landed. */
    public static final String FISH_RARE = "fish_rare";

    // Combat
    /** Distinct damage causes taken, raised by {@code damage_type:<cause>}. */
    public static final String DAMAGE_TYPES = "damage_types";
    /** Hits blocked with a shield. */
    public static final String BLOCKS = "blocks";
    /** Projectiles launched by a weapon. */
    public static final String SHOTS_FIRED = "shots_fired";
    /** Kills from 50 metres or more. */
    public static final String KILLS_FAR = "kills_far";
    public static final String KILLS_UNDERWATER = "kills_underwater";
    public static final String KILLS_GLIDING = "kills_gliding";
    public static final String KILLS_MOUNTED = "kills_mounted";
    /** Kills made with under 10 percent health. */
    public static final String KILLS_LOW_HEALTH = "kills_low_health";
    public static final String KILLS_NIGHT = "kills_night";
    public static final String KILLS_BLOOD_MOON = "kills_blood_moon";
    /** Windows of 2 kills in 3 s, 3 in 5 s and 10 in 15 s. */
    public static final String MULTIKILL_2 = "multikill_2";
    public static final String MULTIKILL_3 = "multikill_3";
    public static final String MULTIKILL_10 = "multikill_10";
    /** Health under 10 percent then back over 25 percent without dying. */
    public static final String CLOSE_CALLS = "close_calls";
    /** The largest fall damage survived. */
    public static final String FALL_SURVIVED_MAX = "fall_survived_max";
    /** Falls of 30 metres or more that ended in water. */
    public static final String WATER_LANDINGS = "water_landings";
    /** Drowning damage taken and survived. */
    public static final String DROWNING_SURVIVED = "drowning_survived";
    /** Boss kills the ledger credited to 3 or 5 or more players. */
    public static final String BOSS_COOP_3 = "boss_coop_3";
    public static final String BOSS_COOP_5 = "boss_coop_5";
    /** Distinct creature kinds defeated, raised by {@code kill_kind:<id>}. */
    public static final String KILL_KINDS = "kill_kinds";
    /** Player kills in a row without dying, the running streak kept in memory. */
    public static final String PLAYER_KILL_STREAK = "player_kill_streak";
    /** Kills of the player who last killed you. */
    public static final String VENDETTAS = "vendettas";
    /** Player kills from 30 metres or more. */
    public static final String PLAYER_KILLS_FAR = "player_kills_far";
    /** Hard mode Gobliterator kills without a death in that run, and within 20 minutes. */
    public static final String GOBLITERATOR_HARD_DEATHLESS = "gobliterator_hard_deathless";
    public static final String GOBLITERATOR_HARD_FAST = "gobliterator_hard_fast";

    // Survival, vitals, sessions
    /** Surfaced with under 10 percent oxygen. */
    public static final String OXYGEN_LOW = "oxygen_low";
    /** Mana reached zero. */
    public static final String MANA_EMPTIED = "mana_emptied";
    /** Whole nights slept through, not bed uses. */
    public static final String NIGHTS_SLEPT = "nights_slept";
    /** Game day rollovers seen while online. */
    public static final String GAME_DAYS = "game_days";
    /** Game days of the current life and the longest life. */
    public static final String LIFE_DAYS = "life_days";
    public static final String LONGEST_LIFE_DAYS = "longest_life_days";
    /** Real days joined in a row, in the server's local date, and the best run. */
    public static final String JOIN_STREAK = "join_streak";
    public static final String BEST_JOIN_STREAK = "best_join_streak";
    /** 1 once the player joined a year or more after their first visit. */
    public static final String SERVER_YEAR = "server_year";
    /** 1 once the player joined between 00:00 and 04:00, and between 05:00 and 07:00, server time. */
    public static final String JOIN_NIGHT = "join_night";
    public static final String JOIN_EARLY = "join_early";
    /** Hardcore game days survived, the lowest lives seen (stored as 1 when it was the last life) and clean days. */
    public static final String HARDCORE_DAYS = "hardcore_days";
    public static final String LIVES_LEFT_MIN = "lives_left_min";
    public static final String HARDCORE_CLEAN_DAYS = "hardcore_clean_days";

    // Movement and mounts
    public static final String SEATS = "seats";
    public static final String MOUNTS = "mounts";
    public static final String MINECART_RIDES = "minecart_rides";
    /** The longest ride in seconds. */
    public static final String LONGEST_RIDE = "longest_ride";
    public static final String JUMPS = "jumps";
    public static final String DOUBLE_JUMPS = "double_jumps";
    public static final String ROLLS = "rolls";
    public static final String MANTLES = "mantles";
    public static final String CLIMB_SECONDS = "climb_seconds";
    public static final String SPRINT_SECONDS = "sprint_seconds";
    public static final String CROUCH_SECONDS = "crouch_seconds";
    public static final String GLIDES = "glides";
    public static final String LONGEST_GLIDE_SECONDS = "longest_glide_seconds";

    // Misc and social
    public static final String REFERRAL_JOINS = "referral_joins";
    /** Permission groups added, any, raised with {@code group_added:<name>}. */
    public static final String GROUPS_ADDED = "group_added";
    /** Distinct players online at the same time, capped by the player file. */
    public static final String PLAYERS_MET = "players_met";
    /** Hours with at least one other player online. */
    public static final String COOP_HOURS = "coop_hours";
    /** The most players online at once while this player was, themselves included. */
    public static final String PEAK_WITH_ME = "peak_with_me";
    /** 1 once the player was online within a minute of a server milestone being reached. */
    public static final String MILESTONE_WITNESSED = "milestone_witnessed";
    /** 1 once the player was online on the server's install anniversary. */
    public static final String SERVER_BIRTHDAY = "server_birthday";

    // Meta, kept by the tracker like FEATS_UNLOCKED
    /** The player's points total. */
    public static final String FEAT_POINTS = "feat_points";
    /** 1 once every Platinum feat but the closers is unlocked. */
    public static final String PLATINUMS_DONE = "platinums_done";

    // Server totals, in server.json, see WorldStore. The World tab reads them through Trigger.total.
    public static final String TOTAL_BLOCKS_BROKEN = "blocks_broken";
    public static final String TOTAL_KILLS = "kills";
    public static final String TOTAL_KILLS_BOSS = "kills_boss";
    public static final String TOTAL_DEATHS = "deaths";
    public static final String TOTAL_PLAYERS_KNOWN = "players_known";
    public static final String TOTAL_PEAK_ONLINE = "peak_online";
    public static final String TOTAL_CHUNKS_GENERATED = "chunks_generated";
    public static final String TOTAL_SERVER_DAYS = "server_days";
    /** The player counters the tracker mirrors into the server totals on every increment. */
    public static final Set<String> MIRRORED_TOTALS = Set.of(TOTAL_BLOCKS_BROKEN, TOTAL_KILLS, TOTAL_KILLS_BOSS, TOTAL_DEATHS);

    // Sibling counters. Coinage and Chronicle keys are written by the Feats bindings, Dominion keys arrive
    // through FeatsApi.addStat.
    public static final String COINAGE_PREFIX = "coinage:";
    public static final String CHRONICLE_PREFIX = "chronicle:";
    public static final String ATLAS_PREFIX = "atlas:";
    public static final String DOMINION_UNCLAIMS = DOMINION_PREFIX + "unclaims";
    public static final String DOMINION_JOINED = DOMINION_PREFIX + "joined";
    public static final String DOMINION_KICKED = DOMINION_PREFIX + "kicked";
    public static final String DOMINION_WARS_DECLARED = DOMINION_PREFIX + "wars_declared";
    public static final String DOMINION_ALLIANCES = DOMINION_PREFIX + "alliances";
    public static final String DOMINION_UPGRADES = DOMINION_PREFIX + "upgrades";
    public static final String DOMINION_UPGRADE_LEVEL_MAX = DOMINION_PREFIX + "upgrade_level_max";
    public static final String DOMINION_MARKET_TRADES = DOMINION_PREFIX + "market_trades";
    public static final String DOMINION_FOREIGN_ENTRIES = DOMINION_PREFIX + "foreign_entries";
    public static final String DOMINION_SIEGES_DEFENDED = DOMINION_PREFIX + "sieges_defended";
    public static final String DOMINION_BREACHES = DOMINION_PREFIX + "breaches";
    public static final String DOMINION_HILL_WINS = DOMINION_PREFIX + "hill_wins";
    public static final String DOMINION_OUTPOSTS_HELD = DOMINION_PREFIX + "outposts_held";
    public static final String DOMINION_POWER_MAX = DOMINION_PREFIX + "power_max";
    public static final String DOMINION_RANK_CHANGES = DOMINION_PREFIX + "rank_changes";
    public static final String DOMINION_TUTORIAL_DONE = DOMINION_PREFIX + "tutorial_done";

    // Dynamic prefixes
    public static final String BROKEN_PREFIX = "broken:";
    public static final String ORE_PREFIX = "ore:";
    public static final String PLACED_PREFIX = "placed:";
    public static final String CRAFTED_PREFIX = "crafted:";
    public static final String CRAFT_GROUP_PREFIX = "craft:";
    public static final String PICKED_PREFIX = "picked:";
    public static final String KILL_PREFIX = "kill:";
    public static final String KILL_GROUP_PREFIX = "kills_";
    public static final String DEATH_CAUSE_PREFIX = "death:";
    public static final String ZONE_PREFIX = "zone:";
    public static final String USE_PREFIX = "use:";
    public static final String CAST_PREFIX = "cast:";
    public static final String PORTAL_PREFIX = "portal:";
    public static final String PORTAL_TYPE_PREFIX = "portaltype:";
    public static final String MEMORY_PREFIX = "memory:";
    public static final String OBTAINED_PREFIX = "obtained:";
    /**
     * Items a player put back into the world (a chest, the ground) and may take again. Only kept for
     * items that a feat counts, so taking your own stash back out does not count twice.
     */
    public static final String STASH_PREFIX = "stash:";
    // 1.7.2 prefixes. The discovery ones are in DISCOVERY_PREFIXES, the rest are per id.
    public static final String BIOME_PREFIX = "biome:";
    public static final String WEATHER_PREFIX = "weather:";
    public static final String MOON_PREFIX = "moon:";
    public static final String INSTANCE_PREFIX = "instance:";
    public static final String VOLUME_PREFIX = "volume:";
    public static final String TOOL_BROKEN_PREFIX = "tool_broken:";
    public static final String WOOD_PREFIX = "wood:";
    public static final String PLACED_KIND_PREFIX = "placed_kind:";
    public static final String RECIPE_PREFIX = "recipe:";
    public static final String ARMOUR_SET_PREFIX = "armour_set:";
    public static final String DAMAGE_TYPE_PREFIX = "damage_type:";
    public static final String SEAT_PREFIX = "seat:";
    public static final String MOUNT_PREFIX = "mount:";
    public static final String TAMED_PREFIX = "tamed:";
    public static final String KILL_KIND_PREFIX = "kill_kind:";
    public static final String SEED_PREFIX = "seed:";
    public static final String WORLD_EVENT_PREFIX = "world_event:";
    public static final String MERCHANT_PREFIX = "merchant:";
    public static final String TALK_PREFIX = "talk:";
    public static final String GROUP_ADDED_PREFIX = "group_added:";
    public static final String DATE_PREFIX = "date:";
    /** 1 when every personal feat of a tab but the closer is unlocked, keyed by the Category name. */
    public static final String CATEGORY_DONE_PREFIX = "category_done:";

    /**
     * Prefixes that keep one counter per block, item, creature or cause id. These keys are only kept
     * when a feat watches them or a stats row shows them, so save files stay small.
     */
    private static final List<String> PER_ID_PREFIXES = List.of(
            BROKEN_PREFIX, ORE_PREFIX, PLACED_PREFIX, CRAFTED_PREFIX, PICKED_PREFIX, USE_PREFIX,
            OBTAINED_PREFIX, KILL_PREFIX, DEATH_CAUSE_PREFIX, CAST_PREFIX, PORTAL_PREFIX,
            VOLUME_PREFIX, TOOL_BROKEN_PREFIX, MOUNT_PREFIX, TAMED_PREFIX, SEED_PREFIX, WORLD_EVENT_PREFIX,
            TALK_PREFIX, GROUP_ADDED_PREFIX, DATE_PREFIX, CATEGORY_DONE_PREFIX);

    /**
     * Each prefix whose markers feed a distinct kinds total through {@code FeatsTracker.discover}, with that total.
     * None of them is in {@link #PER_ID_PREFIXES}, like the older zone, memory and portal type markers. A marker
     * dropped at load would let the next sighting count as new and raise the total again at every login.
     */
    public static final Map<String, String> DISCOVERIES = Map.ofEntries(
            Map.entry(ZONE_PREFIX, ZONES_DISCOVERED), Map.entry(MEMORY_PREFIX, MEMORIES),
            Map.entry(PORTAL_TYPE_PREFIX, PORTAL_TYPES), Map.entry(BIOME_PREFIX, BIOMES_VISITED),
            Map.entry(WEATHER_PREFIX, WEATHERS_SEEN), Map.entry(MOON_PREFIX, MOONS_SEEN),
            Map.entry(INSTANCE_PREFIX, INSTANCES_DISCOVERED), Map.entry(WOOD_PREFIX, WOOD_SPECIES),
            Map.entry(PLACED_KIND_PREFIX, PLACED_KINDS), Map.entry(RECIPE_PREFIX, RECIPES_KNOWN),
            Map.entry(ARMOUR_SET_PREFIX, ARMOUR_SETS), Map.entry(DAMAGE_TYPE_PREFIX, DAMAGE_TYPES),
            Map.entry(SEAT_PREFIX, SEATS), Map.entry(KILL_KIND_PREFIX, KILL_KINDS),
            Map.entry(MERCHANT_PREFIX, MERCHANTS));

    /** True for a per id counter key, see {@link #PER_ID_PREFIXES}. */
    public static boolean isPerId(String key) {
        if (key.indexOf(':') < 0) {
            return false; // every per id prefix ends in a colon, so totals skip the prefix loop
        }
        for (String prefix : PER_ID_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Rows shown on the Stats tab, in order. */
    public record StatRow(String key, String label, Format format) {}

    public enum Format { COUNT, DISTANCE, DURATION }

    public static final List<StatRow> DISPLAY = List.of(
            new StatRow(PLAYTIME, "Time played", Format.DURATION),
            new StatRow(DISTANCE, "Distance travelled", Format.DISTANCE),
            new StatRow(SWIM_DISTANCE, "Distance swum", Format.DISTANCE),
            new StatRow(GLIDE_DISTANCE, "Distance glided", Format.DISTANCE),
            new StatRow(RIDE_DISTANCE, "Distance ridden", Format.DISTANCE),
            new StatRow(ZONES_DISCOVERED, "Zones discovered", Format.COUNT),
            new StatRow(BLOCKS_BROKEN, "Blocks broken", Format.COUNT),
            new StatRow(ORE_MINED, "Ore mined", Format.COUNT),
            new StatRow(WOOD_CHOPPED, "Logs chopped", Format.COUNT),
            new StatRow(BLOCKS_PLACED, "Blocks placed", Format.COUNT),
            new StatRow(CRAFTED, "Items crafted", Format.COUNT),
            new StatRow(PICKED_UP, "Items picked up", Format.COUNT),
            new StatRow(HARVESTED, "Crops harvested", Format.COUNT),
            new StatRow(FISH_CAUGHT, "Fish caught", Format.COUNT),
            new StatRow(KILLS, "Kills", Format.COUNT),
            new StatRow(KILLS_RANGED, "Ranged kills", Format.COUNT),
            new StatRow(KILLS_BOSS, "Boss kills", Format.COUNT),
            new StatRow(KILL_STREAK, "Best kill streak", Format.COUNT),
            new StatRow(DEATHS, "Deaths", Format.COUNT),
            new StatRow(DAMAGE_DEALT, "Damage dealt", Format.COUNT),
            new StatRow(DAMAGE_TAKEN, "Damage taken", Format.COUNT),
            new StatRow(BIGGEST_HIT, "Biggest hit", Format.COUNT),
            new StatRow(TAMED, "Animals tamed", Format.COUNT),
            new StatRow(CHESTS_OPENED, "Chests opened", Format.COUNT),
            new StatRow(BEDS_USED, "Nights slept", Format.COUNT),
            new StatRow(BENCHES_USED, "Benches used", Format.COUNT),
            new StatRow(ABILITY_CASTS, "Abilities cast", Format.COUNT),
            new StatRow(PORTALS_ENTERED, "Portals entered", Format.COUNT),
            new StatRow(MEMORIES, "Memories recorded", Format.COUNT),
            new StatRow(FOOD_EATEN, "Meals eaten", Format.COUNT),
            new StatRow(POTIONS_DRUNK, "Potions drunk", Format.COUNT),
            new StatRow(CHAT, "Chat messages", Format.COUNT),
            new StatRow(JOINS, "Sessions", Format.COUNT),
            new StatRow(LONGEST_SESSION, "Longest session", Format.DURATION),
            new StatRow(BEST_JOIN_STREAK, "Best join streak, days", Format.COUNT),
            new StatRow(NIGHTS_SLEPT, "Nights slept through", Format.COUNT),
            new StatRow(LONGEST_LIFE_DAYS, "Longest life, game days", Format.COUNT),
            new StatRow(BIOMES_VISITED, "Biomes visited", Format.COUNT),
            new StatRow(KILL_KINDS, "Creature kinds defeated", Format.COUNT),
            new StatRow(JUMPS, "Jumps", Format.COUNT),
            new StatRow(TOOLS_BROKEN, "Tools worn out", Format.COUNT),
            new StatRow(PLAYERS_MET, "Players met", Format.COUNT),
            new StatRow(FEATS_UNLOCKED, "Feats unlocked", Format.COUNT),
            new StatRow(FEAT_POINTS, "Points", Format.COUNT)
    );

    private static final Set<String> DISPLAY_KEYS = DISPLAY.stream().map(StatRow::key).collect(Collectors.toUnmodifiableSet());

    /** True when a row on the Stats tab shows this key. */
    public static boolean isDisplayed(String key) {
        return DISPLAY_KEYS.contains(key);
    }

    public static String format(long value, Format format) {
        return switch (format) {
            case COUNT -> String.format("%,d", value);
            case DISTANCE -> value >= 1000
                    ? String.format("%,.1f km", value / 1000.0)
                    : String.format("%,d m", value);
            case DURATION -> {
                long h = value / 3600;
                long m = (value % 3600) / 60;
                if (h > 0) {
                    yield String.format("%dh %02dm", h, m);
                }
                yield String.format("%dm %02ds", m, value % 60);
            }
        };
    }
}
