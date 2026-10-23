package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import static dev.ryvrook.feats.model.Category.ENDGAME;
import static dev.ryvrook.feats.model.FeatRegistry.Definer.after;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

/**
 * The Endgame and QoL feats, read from the {@code endgame:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.ENDGAME} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.ENDGAME}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: endgame:bosses and endgame:bosses:<boss>, endgame:boss_type:<boss> markers with endgame:boss_types,
 * endgame:achievement:<id> with endgame:achievements, endgame:bestiary:<id> with endgame:bestiaries,
 * endgame:rune:<id> with endgame:runes, endgame:wave:<arena>/<wave> with endgame:waves, endgame:runs and
 * endgame:runs:<dungeon> (dungeon entries), endgame:cleared and endgame:cleared:<arena> (trials completed),
 * endgame:failed and endgame:failed:<arena>, endgame:dungeon_type:<arena> with endgame:dungeon_types,
 * endgame:fast_trial (trials completed under 3 minutes), and the two bests TrackerAPI's binding keeps that the
 * mirror reads back on every Endgame milestone, endgame:wave_best and endgame:combo_best.
 *
 * <p>The numbers follow Endgame and QoL 6.0.4. A boss kill is keyed by the raw NPC type id, {@code Endgame_Dragon_Frost}
 * and {@code Endgame_Dragon_Void} here, the bestiary registry lists 44 creatures and the mod's own top discovery
 * achievement asks for 30, the rune collection claims tiers up to 25 found, and the mod's boss slayer ladder ends
 * at 100 kills.
 */
public final class EndgameFeats {
    private EndgameFeats() {
    }

    /** The NPC type id of the Frost Dragon, as {@code BossKillEvent.bossTypeId()} gives it. */
    static final String FROST_DRAGON = "Endgame_Dragon_Frost";
    /** The NPC type id of the Void Dragon. */
    static final String VOID_DRAGON = "Endgame_Dragon_Void";

    /** {@code endgame:<stat>}. */
    private static String k(String stat) {
        return StatKeys.ENDGAME_PREFIX + stat;
    }

    /** Adds the Endgame and QoL feats, 20 personal and 2 in the World tab, see docs/MODS-PLAN.md. */
    public static void define(FeatRegistry.Definer d) {
        // ---- the mod's own achievements ----
        d.feat("endgame_achievements_5", "Endgame Starter", "Unlock 5 Endgame achievements.", ENDGAME, BRONZE, "Deco_Trophy_Harvest", k("achievements"), 5);
        d.feat("endgame_achievements_15", "Endgame Regular", "Unlock 15 Endgame achievements.", ENDGAME, SILVER, "Deco_Trophy_Harvest", k("achievements"), 15);
        d.feat("endgame_achievements_30", "Endgame Veteran", "Unlock 30 Endgame achievements.", ENDGAME, GOLD, "Furniture_Ancient_Statue", k("achievements"), 30);
        // ---- bestiary and runes, one mark per id ----
        d.feat("endgame_bestiary_10", "Field Notes", "Discover 10 creatures in the Endgame bestiary.", ENDGAME, BRONZE, "Deco_Scroll", k("bestiaries"), 10);
        d.feat("endgame_bestiary_30", "Thirty Creatures", "Discover 30 creatures in the Endgame bestiary.", ENDGAME, GOLD, "Deco_Book_Pile_Large", k("bestiaries"), 30);
        d.feat("endgame_runes_5", "Rune Finder", "Find 5 different runes.", ENDGAME, BRONZE, "Rune_Empty", k("runes"), 5);
        d.feat("endgame_runes_25", "Rune Collection", "Find 25 different runes.", ENDGAME, GOLD, "Ingredient_Rune_Shard", k("runes"), 25);
        // ---- bosses, every credited player counts the kill ----
        d.feat("endgame_boss_first", "First Boss", "Defeat an Endgame boss.", ENDGAME, BRONZE, "Weapon_Sword_Adamantite", k("bosses"), 1);
        d.feat("endgame_bosses_50", "Boss Slayer", "Defeat 50 Endgame bosses.", ENDGAME, GOLD, "Weapon_Battleaxe_Scythe_Void", k("bosses"), 50);
        d.feat("endgame_bosses_100", "Hundred Bosses", "Defeat 100 Endgame bosses.", ENDGAME, PLATINUM, "Weapon_Longsword_Ruined_Giant", k("bosses"), 100);
        d.feat("endgame_boss_types_3", "Three Boss Kinds", "Defeat 3 different Endgame bosses.", ENDGAME, SILVER, "Deco_Bone_Skulls", k("boss_types"), 3);
        d.feat("endgame_frost_dragon", "Frost Dragon", "Defeat the Frost Dragon.", ENDGAME, SILVER, "Ingredient_Ice_Essence", k("bosses:" + FROST_DRAGON), 1);
        d.feat("endgame_void_dragon", "Void Dragon", "Defeat the Void Dragon.", ENDGAME, GOLD, "Ingredient_Crystal_Void", k("bosses:" + VOID_DRAGON), 1);
        // ---- dungeons and trials ----
        d.feat("endgame_dungeon_first", "Dungeon Door", "Enter an Endgame dungeon.", ENDGAME, BRONZE, "Coffer_Key", k("runs"), 1);
        d.feat("endgame_trials_10", "Ten Trials", "Complete 10 trials.", ENDGAME, GOLD, "Weapon_Shield_Cobalt", k("cleared"), 10);
        d.hidden("endgame_fast_trial", "Quick Trial", "Complete a trial in under 3 minutes.", ENDGAME, GOLD, "Potion_Stamina_Greater", k("fast_trial"), 1);
        d.feat("endgame_every_trial", "Every Trial", "Complete 4 different trials.", ENDGAME, GOLD, "Weapon_Shield_Iron", k("dungeon_types"), 4);
        d.feat("endgame_trials_25", "Trial Veteran", "Complete 25 trials.", ENDGAME, PLATINUM, "Armor_Adamantite_Head", k("cleared"), 25);
        // ---- the combo meter ----
        d.feat("endgame_combo_25", "Combo 25", "Reach a 25 hit combo.", ENDGAME, BRONZE, "Weapon_Daggers_Void_Crystal", k("combo_best"), 25);
        d.feat("endgame_combo_100", "Combo 100", "Reach a 100 hit combo.", ENDGAME, GOLD, "Weapon_Daggers_Void_Crystal", k("combo_best"), 100);

        // ---- World tab ----
        d.milestone("world_endgame_void_dragon", "Void Dragon Slain", "Defeat the Void Dragon on this server.", GOLD, "Ingredient_Crystal_Void", after("endgame_void_dragon"));
        d.first("first_endgame_frost_dragon", "Dragonslayer", "Be the first on this server to defeat the Frost Dragon.", PLATINUM, "Ingredient_Ice_Essence", after("endgame_frost_dragon"));
    }
}
