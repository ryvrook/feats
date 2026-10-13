package dev.ryvrook.feats.track;

import dev.ryvrook.feats.stats.StatKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns raw game identifiers (block ids, item ids, NPC type ids) into the stat keys
 * that feats are defined against. Pure functions, no game state.
 */
public final class Classify {
    private Classify() {}

    private static final Set<String> LIVESTOCK = Set.of(
            "Cow", "Pig", "Sheep", "Chicken", "Goat", "Bison", "Boar", "Camel", "Horse",
            "Mouflon", "Rabbit", "Bunny", "Ram", "Turkey", "Warthog", "Skrill", "Duck", "Mosshorn");

    private static final Set<String> PREDATORS = Set.of(
            "Bear_Grizzly", "Bear_Polar", "Wolf_Black", "Wolf_White", "Tiger_Sabertooth", "Leopard_Snow",
            "Hyena", "Crocodile", "Raptor_Cave", "Rex_Cave", "Fox", "Werewolf",
            // Guttering Hollows hunt predators (Server/NPC/Groups/Hunt_Predators.json)
            "Bear_Voidtaken");

    private static final Set<String> BOSSES = Set.of(
            "Dragon_Fire", "Dragon_Frost", "Goblin_Duke", "Scarak_Broodmother",
            "Dungeon_Scarak_Broodmother", "Goblin_Ogre", "Shadow_Knight", "Golem_Guardian_Void",
            // Chapter I. Multi phase bosses swap roles instead of dying, so only the last phase dies.
            "Goblin_Duke_Phase_2", "Goblin_Duke_Phase_3_Fast", "Goblin_Duke_Phase_3_Slow",
            "Dungeon_Feastmaster_Boss", "Bear_Voidtaken", "Skeleton_Elite", "Skeleton_Elite_Phase_2",
            // The Dread Wade, behind the Ancient Gateway
            "Hedera");

    private static final Set<String> REVENANT = Set.of("Skeleton_Elite", "Skeleton_Elite_Phase_2");

    /** Gadgets crafted at the Goblin Lab benches. */
    private static final Set<String> GADGETS = Set.of(
            "Rocket_Boots", "Tool_Hookshot", "Scrap_Hook", "Glider_Scrap", "Tool_Drill_Scrap",
            "Tool_Spyglass", "Weapon_Bangstick");

    /** Block ids that keep their own use counter exactly as they are. */
    private static final Set<String> EXACT_USE_BLOCKS = Set.of("GoblinLab_Console");

    /**
     * Block id families that share one use counter, such as every tinkering bench variant. The
     * completion portal is placed in The Deepworks in its Chapter1_TheDeepworks state, so the state
     * variant and the default block both count as Dungeon_Portal_End.
     */
    private static final List<String> GROUPED_USE_BLOCKS = List.of("GoblinLab_Tinkering_Bench", "Dungeon_Portal_End");

    /**
     * The Gobliterator difficulty button. Pressing it moves it between its default, Active and Closed
     * states, which all count as one press. The Locked states only play a sound once the fight starts.
     */
    private static final String DIFFICULTY_BUTTON = "Goblin_Gobliterator_Difficulty_Button";

    /**
     * Voidspawn kinds and the group each one counts towards. A kind matches its own id and any variant
     * built on it, such as Crawler_Void_Surge or Void_Spawn_Entombed.
     */
    private static final List<String[]> VOID_KINDS = List.of(
            new String[] {"Crawler_Void", "void_crawler"}, new String[] {"Eye_Void", "void_eye"},
            new String[] {"Larva_Void", "void_larva"}, new String[] {"Void_Spawn", "void_spawn"},
            new String[] {"Void_Tentacle", "void_tentacle"});

    private static final String ABILITY_ROOT = "Root_Ability_";
    private static final String SPYGLASS_ROOT = "Root_Spyglass_Zoom";

    /** Keys to bump when a player breaks a block with the given block type id. */
    public static List<String> blockBroken(String blockId) {
        List<String> keys = new ArrayList<>(4);
        keys.add(StatKeys.BLOCKS_BROKEN);
        if (blockId == null || blockId.isEmpty()) {
            return keys;
        }
        keys.add(StatKeys.BROKEN_PREFIX + blockId);
        if (blockId.startsWith("Ore_")) {
            keys.add(StatKeys.ORE_MINED);
            String metal = segment(blockId, 1);
            if (!metal.isEmpty()) {
                keys.add(StatKeys.ORE_PREFIX + metal);
            }
        } else if (blockId.startsWith("Wood_") && blockId.contains("Trunk")) {
            keys.add(StatKeys.WOOD_CHOPPED);
        } else if (blockId.startsWith("Rock_Crystal_Void")) {
            keys.add(StatKeys.BROKEN_PREFIX + "void_crystal");
        }
        return keys;
    }

    /** Keys to bump when a player places a block from the given item id. */
    public static List<String> blockPlaced(String itemId) {
        List<String> keys = new ArrayList<>(4);
        keys.add(StatKeys.BLOCKS_PLACED);
        if (itemId == null || itemId.isEmpty()) {
            return keys;
        }
        keys.add(StatKeys.PLACED_PREFIX + itemId);
        if (itemId.contains("Torch")) {
            keys.add(StatKeys.PLACED_PREFIX + "torch");
        }
        if (itemId.contains("Lantern")) {
            keys.add(StatKeys.PLACED_PREFIX + "lantern");
        }
        if (itemId.endsWith("_Bed")) {
            keys.add(StatKeys.PLACED_PREFIX + "bed");
        }
        if (itemId.contains("Chest")) {
            keys.add(StatKeys.PLACED_PREFIX + "chest");
        }
        if (itemId.startsWith("Furniture_")) {
            keys.add(StatKeys.PLACED_PREFIX + "furniture");
        }
        if (itemId.startsWith("Bench_")) {
            keys.add(StatKeys.PLACED_PREFIX + "bench");
        }
        if (itemId.startsWith("Plant_Crop_")) {
            keys.add(StatKeys.PLACED_PREFIX + "crop");
        }
        if (itemId.startsWith("Furniture_Goblin_") || itemId.startsWith("Deco_Goblin_Plush")) {
            keys.add(StatKeys.PLACED_PREFIX + "goblin_furniture");
        }
        if (itemId.startsWith("Goblin_Painting_")) {
            // Originals and replicas alike. The per id key above tells the originals apart.
            keys.add(StatKeys.PLACED_PREFIX + "goblin_painting");
        }
        if (itemId.startsWith("Metal_")) {
            keys.add(StatKeys.PLACED_PREFIX + "metal");
        }
        return keys;
    }

    /** Keys to bump when a player crafts the given item id. */
    public static List<String> crafted(String itemId) {
        List<String> keys = new ArrayList<>(4);
        keys.add(StatKeys.CRAFTED);
        if (itemId == null || itemId.isEmpty()) {
            return keys;
        }
        keys.add(StatKeys.CRAFTED_PREFIX + itemId);
        if (itemId.startsWith("Tool_Pickaxe")) {
            keys.add(StatKeys.CRAFT_GROUP_PREFIX + "pickaxe");
        }
        if (itemId.startsWith("Tool_")) {
            keys.add(StatKeys.CRAFT_GROUP_PREFIX + "tool");
        } else if (itemId.startsWith("Weapon_")) {
            keys.add(StatKeys.CRAFT_GROUP_PREFIX + "weapon");
        } else if (itemId.startsWith("Armor_")) {
            keys.add(StatKeys.CRAFT_GROUP_PREFIX + "armor");
        } else if (itemId.startsWith("Ingredient_Bar_")) {
            keys.add(StatKeys.CRAFT_GROUP_PREFIX + "bar");
        } else if (itemId.startsWith("Food_")) {
            keys.add(StatKeys.CRAFT_GROUP_PREFIX + "food");
        } else if (itemId.startsWith("Potion_")) {
            keys.add(StatKeys.CRAFT_GROUP_PREFIX + "potion");
        } else if (itemId.startsWith("Furniture_")) {
            keys.add(StatKeys.CRAFT_GROUP_PREFIX + "furniture");
        } else if (itemId.startsWith("Bench_")) {
            keys.add(StatKeys.CRAFT_GROUP_PREFIX + "bench");
        }
        if (itemId.startsWith("Weapon_")) {
            String family = segment(itemId, 1).toLowerCase(Locale.ROOT);
            if (!family.isEmpty()) {
                keys.add(StatKeys.CRAFT_GROUP_PREFIX + "weapon_" + family);
            }
            // Every adamantite weapon, such as Weapon_Sword_Adamantite or Weapon_Spear_Adamantite_Saurian
            if (itemId.contains("_Adamantite")) {
                keys.add(StatKeys.CRAFT_GROUP_PREFIX + "weapon_adamantite");
            }
        }
        if (itemId.startsWith("Rune_")) {
            keys.add(StatKeys.CRAFT_GROUP_PREFIX + "rune");
        }
        if (GADGETS.contains(itemId)) {
            keys.add(StatKeys.CRAFT_GROUP_PREFIX + "gadget");
        }
        if (itemId.startsWith("Tool_Repair_Kit")) {
            keys.add(StatKeys.CRAFT_GROUP_PREFIX + "repair_kit");
        }
        if (itemId.startsWith("Goblin_Painting_") && itemId.endsWith("_Replica")) {
            keys.add(StatKeys.CRAFT_GROUP_PREFIX + "painting");
        }
        return keys;
    }

    /**
     * Keys to bump when the given item lands in a player's inventory from outside it, however it got
     * there (loot, drops, crafting, a chest).
     */
    public static List<String> obtained(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return List.of(StatKeys.OBTAINED);
        }
        return List.of(StatKeys.OBTAINED, StatKeys.OBTAINED_PREFIX + itemId);
    }

    /** Keys to bump when one of the given item is eaten or drunk from a player's hand. */
    public static List<String> consumed(String itemId) {
        if (itemId == null) {
            return List.of();
        }
        if (itemId.startsWith("Food_")) {
            return List.of(StatKeys.FOOD_EATEN);
        }
        if (itemId.startsWith("Potion_") && !itemId.startsWith("Potion_Empty")) {
            return List.of(StatKeys.POTIONS_DRUNK);
        }
        return List.of();
    }

    /** Keys to bump when a player picks up the given item id. */
    public static List<String> pickedUp(String itemId) {
        List<String> keys = new ArrayList<>(4);
        keys.add(StatKeys.PICKED_UP);
        if (itemId == null || itemId.isEmpty()) {
            return keys;
        }
        keys.add(StatKeys.PICKED_PREFIX + itemId);
        if (itemId.startsWith("Fish_")) {
            keys.add(StatKeys.FISH_CAUGHT);
        } else if (itemId.startsWith("Plant_Crop_") || itemId.startsWith("Food_")) {
            keys.add(StatKeys.HARVESTED);
        } else if (itemId.startsWith("Lore_Note_")) {
            keys.add(StatKeys.PICKED_PREFIX + "lore");
        }
        return keys;
    }

    /** Keys to bump when a player interacts with (uses) a block. */
    public static List<String> blockUsed(String blockId) {
        List<String> keys = new ArrayList<>(3);
        if (blockId == null || blockId.isEmpty()) {
            return keys;
        }
        // Block state variants are registered as "*<BaseId>_<State>", so drop the marker first.
        if (blockId.startsWith("*")) {
            blockId = blockId.substring(1);
        }
        if (blockId.startsWith("Dungeon_") || blockId.startsWith("GoblinLab_")
                || blockId.startsWith("Goblin_Gobliterator_")) {
            keys.add(StatKeys.USE_PREFIX + trackedUseBlock(blockId));
        }
        if (blockId.contains("Chest")) {
            keys.add(StatKeys.CHESTS_OPENED);
        } else if (blockId.endsWith("_Bed")) {
            keys.add(StatKeys.BEDS_USED);
        } else if (blockId.startsWith("Bench_")) {
            keys.add(StatKeys.BENCHES_USED);
            keys.add(StatKeys.USE_PREFIX + blockId);
        }
        return keys;
    }

    /**
     * Keys to bump when a player kills an entity.
     *
     * @param npcTypeId NPC type id, or "Player" for a player victim
     * @param ranged    true when the killing blow came from a projectile
     */
    public static List<String> kill(String npcTypeId, boolean ranged) {
        List<String> keys = new ArrayList<>(6);
        keys.add(StatKeys.KILLS);
        if (ranged) {
            keys.add(StatKeys.KILLS_RANGED);
        }
        if (npcTypeId == null || npcTypeId.isEmpty()) {
            return keys;
        }
        keys.add(StatKeys.KILL_PREFIX + npcTypeId);
        if (npcTypeId.equals("Player")) {
            keys.add(StatKeys.KILLS_PLAYER);
            return keys;
        }
        String id = npcTypeId;
        if (isUndead(id)) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "undead");
        }
        if (isGoblin(id)) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "goblin");
            addGoblinKinds(id, keys);
        }
        if (id.startsWith("Trork_")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "trork");
        }
        if (id.startsWith("Outlander_")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "outlander");
        }
        if (id.contains("Scarak")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "scarak");
        }
        if (id.startsWith("Feran_")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "feran");
        }
        if (id.startsWith("Kweebec_")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "kweebec");
        }
        if (id.contains("Void")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "void");
        }
        if (id.startsWith("Dragon_")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "dragon");
        }
        if (id.startsWith("Golem_") || id.startsWith("Spirit_")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "elemental");
        }
        if (id.startsWith("Spider") || id.equals("Larva_Silk")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "spider");
        }
        if (id.startsWith("Void_Anomaly_Crystal")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "void_anomaly");
        }
        if (id.startsWith("Void_Spectre") || id.equals("Spectre_Void")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "void_spectre");
        }
        for (String[] kind : VOID_KINDS) {
            if (id.equals(kind[0]) || id.startsWith(kind[0] + "_")) {
                keys.add(StatKeys.KILL_GROUP_PREFIX + kind[1]);
            }
        }
        if (REVENANT.contains(id)) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "revenant");
        }
        boolean gobliterator = isGobliterator(id);
        if (gobliterator) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "gobliterator");
            if (id.endsWith("_HM")) {
                keys.add(StatKeys.KILL_GROUP_PREFIX + "gobliterator_hm");
            }
        }
        if (BOSSES.contains(id) || gobliterator) {
            keys.add(StatKeys.KILLS_BOSS);
        }
        if (isLivestock(id)) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "livestock");
        }
        if (PREDATORS.contains(id)) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "predator");
        }
        return keys;
    }

    /**
     * Keys to bump when a player starts an interaction chain with the given root interaction id.
     * Called for every chain a player starts, so unknown roots return an empty list without allocating.
     * Meals and potions are not counted here because the chain starts before the item is used up.
     */
    public static List<String> interaction(String rootId) {
        if (rootId == null || !rootId.startsWith("Root_")) {
            return List.of();
        }
        if (rootId.startsWith(ABILITY_ROOT)) {
            String ability = rootId.substring(ABILITY_ROOT.length());
            if (ability.isEmpty()) {
                return List.of();
            }
            return List.of(StatKeys.ABILITY_CASTS, StatKeys.CAST_PREFIX + ability);
        }
        if (rootId.equals(SPYGLASS_ROOT)) {
            return List.of(StatKeys.SPYGLASS_USES);
        }
        return List.of();
    }

    /** Keys to bump when a player enters a portal world of the given portal type id. */
    public static List<String> portal(String portalTypeId) {
        List<String> keys = new ArrayList<>(2);
        keys.add(StatKeys.PORTALS_ENTERED);
        if (portalTypeId != null && !portalTypeId.isEmpty()) {
            keys.add(StatKeys.PORTAL_PREFIX + portalTypeId);
        }
        return keys;
    }

    /** Keys to bump when a player dies. */
    public static List<String> death(String causeId) {
        List<String> keys = new ArrayList<>(2);
        keys.add(StatKeys.DEATHS);
        if (causeId != null && !causeId.isEmpty()) {
            keys.add(StatKeys.DEATH_CAUSE_PREFIX + causeId.toLowerCase(Locale.ROOT));
        }
        return keys;
    }

    private static boolean isUndead(String id) {
        return id.contains("Skeleton") || id.contains("Zombie") || id.contains("Ghoul")
                || id.contains("Wraith") || id.contains("Risen") || id.contains("Undead")
                || id.contains("Spectre") || id.equals("Hound_Bleached") || id.equals("Shadow_Knight");
    }

    /** Goblin fighters. Spectators in the arena stands and the lab scientist are not fair game. */
    private static boolean isGoblin(String id) {
        return (id.contains("Goblin") || id.startsWith("Dungeon_Feastmaster"))
                && !id.contains("Explosive_Barrel") && !id.startsWith("Test_")
                && !id.contains("Spectator") && !id.contains("Scientist");
    }

    private static void addGoblinKinds(String id, List<String> keys) {
        if (id.contains("Goblin_Burner")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "goblin_burner");
        }
        if (id.contains("Goblin_Lobber")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "goblin_lobber");
        }
        if (id.contains("Goblin_Guardian")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "goblin_guardian");
        }
        if (id.contains("Goblin_Thief")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "goblin_thief");
        }
        if (id.contains("Goblin_Scrapper")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "goblin_scrapper");
        }
        if (id.contains("Goblin_Miner")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "goblin_miner");
        }
        if (id.contains("Goblin_Glutton")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "goblin_glutton");
        }
        if (id.startsWith("Goblin_Duke")) {
            keys.add(StatKeys.KILL_GROUP_PREFIX + "goblin_duke");
        }
    }

    /**
     * True for a boss whose fight should credit every player who took part, not only the killing blow.
     * Pass an NPC type id.
     */
    public static boolean isBoss(String npcTypeId) {
        return npcTypeId != null && (BOSSES.contains(npcTypeId) || isGobliterator(npcTypeId));
    }

    /** True for the Gobliterator role that dies at the end of the fight, on either difficulty. */
    public static boolean isGobliteratorFinal(String npcTypeId) {
        return npcTypeId != null && npcTypeId.startsWith("Goblin_Gobliterator_Phase3");
    }

    /** True for the hard mode Gobliterator role that dies at the end of the fight. */
    public static boolean isGobliteratorFinalHard(String npcTypeId) {
        return "Goblin_Gobliterator_Phase3_HM".equals(npcTypeId);
    }

    /** Every Gobliterator boss role. The fight swaps roles between phases, so exactly one of them dies. */
    private static boolean isGobliterator(String id) {
        return id.startsWith("Goblin_Gobliterator_Phase") || id.startsWith("Goblin_Gobliterator_Transition")
                || id.startsWith("Goblin_Gobliterator_SawTransition");
    }

    private static String trackedUseBlock(String blockId) {
        if (EXACT_USE_BLOCKS.contains(blockId)) {
            return blockId;
        }
        if (blockId.startsWith(DIFFICULTY_BUTTON)) {
            return blockId.contains("_Locked") ? blockId : DIFFICULTY_BUTTON;
        }
        for (String base : GROUPED_USE_BLOCKS) {
            if (blockId.startsWith(base)) {
                return base;
            }
        }
        return blockId;
    }

    private static boolean isLivestock(String id) {
        String base = id.startsWith("Tamed_") ? id.substring("Tamed_".length()) : id;
        for (String animal : LIVESTOCK) {
            if (base.equals(animal) || base.startsWith(animal + "_")) {
                return true;
            }
        }
        return false;
    }

    private static String segment(String id, int index) {
        String[] parts = id.split("_");
        return parts.length > index ? parts[index] : "";
    }
}
