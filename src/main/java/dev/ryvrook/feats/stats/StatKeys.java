package dev.ryvrook.feats.stats;

import java.util.List;
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

    /**
     * Prefixes that keep one counter per block, item, creature or cause id. These keys are only kept
     * when a feat watches them or a stats row shows them, so save files stay small.
     */
    private static final List<String> PER_ID_PREFIXES = List.of(
            BROKEN_PREFIX, ORE_PREFIX, PLACED_PREFIX, CRAFTED_PREFIX, PICKED_PREFIX, USE_PREFIX,
            OBTAINED_PREFIX, KILL_PREFIX, DEATH_CAUSE_PREFIX, CAST_PREFIX, PORTAL_PREFIX);

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
            new StatRow(FEATS_UNLOCKED, "Feats unlocked", Format.COUNT)
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
