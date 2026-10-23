package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import static dev.ryvrook.feats.model.Category.MMO;
import static dev.ryvrook.feats.model.FeatRegistry.Definer.after;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

/**
 * The MMO Skill Tree feats, read from the {@code mmo:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.MMO_SKILL_TREE} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.MMO}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: mmo:skill_level:<skill> (best per skill), mmo:skill_level_best (best over every skill), mmo:skill_level_ups,
 * mmo:xp and mmo:xp:<skill>, mmo:abilities and mmo:abilities:<ability>, mmo:class:<id> markers with mmo:classes,
 * mmo:quest:<id> with mmo:quests, mmo:bounty:<id> with mmo:bounties, mmo:achievement:<id> with mmo:achievements.
 * The mod publishes no main level, so there is no mmo:level.
 *
 * <p>The numbers follow MMO Skill Tree 1.7.3. Every skill caps at level 100 (its own ladder is 10, 25, 50, 75, 100),
 * it ships 213 achievements, its XP ladder runs from 10,000 to 100,000,000 and its casting ladder tops out at
 * 1,000 casts. Quests, bounties and achievements are one time marks per id, so the totals count different ones.
 * Classes are server content, the jar ships no fixed set, so the class feats stop at three picked.
 */
public final class MmoFeats {
    private MmoFeats() {
    }

    /** {@code mmo:<stat>}. */
    private static String k(String stat) {
        return StatKeys.MMO_PREFIX + stat;
    }

    /** Adds the MMO Skill Tree feats, 20 personal and 2 in the World tab, see docs/MODS-PLAN.md. */
    public static void define(FeatRegistry.Definer d) {
        // ---- the mod's own achievements, 213 in all ----
        d.feat("mmo_achievements_5", "Achievement Hunter", "Unlock 5 MMO Skill Tree achievements.", MMO, BRONZE, "Deco_Scroll", k("achievements"), 5);
        d.feat("mmo_achievements_25", "Achievement Collector", "Unlock 25 MMO Skill Tree achievements.", MMO, SILVER, "Deco_Book_Pile_Small", k("achievements"), 25);
        d.feat("mmo_achievements_75", "Achievement Scholar", "Unlock 75 MMO Skill Tree achievements.", MMO, GOLD, "Deco_Book_Pile_Large", k("achievements"), 75);
        d.feat("mmo_achievements_150", "Achievement Master", "Unlock 150 MMO Skill Tree achievements.", MMO, PLATINUM, "Deco_Treasure_Pile_Large", k("achievements"), 150);
        // ---- quests and bounties, counted once per quest id ----
        d.feat("mmo_quest_first", "First Quest", "Complete a quest.", MMO, BRONZE, "Deco_Scroll", k("quests"), 1);
        d.feat("mmo_quests_10", "Quest Log", "Complete 10 different quests.", MMO, SILVER, "Deco_Map", k("quests"), 10);
        d.feat("mmo_quests_50", "Quest Veteran", "Complete 50 different quests.", MMO, GOLD, "Deco_Book_Pile_Large", k("quests"), 50);
        d.feat("mmo_bounties_5", "Bounty Hunter", "Complete 5 different bounties.", MMO, SILVER, "Weapon_Crossbow_Iron", k("bounties"), 5);
        d.feat("mmo_bounties_25", "Bounty Board", "Complete 25 different bounties.", MMO, GOLD, "Weapon_Shortbow_Thorium", k("bounties"), 25);
        // ---- classes, one mark per class id ever picked ----
        d.feat("mmo_class_pick", "Class Chosen", "Pick a class.", MMO, BRONZE, "Bench_Abilities", k("classes"), 1);
        d.feat("mmo_class_three", "Three Classes", "Pick 3 different classes.", MMO, SILVER, "Weapon_Sword_Runic", k("classes"), 3);
        // ---- skill levels, the best over every skill, cap 100 ----
        d.feat("mmo_skill_25", "Skill Level 25", "Reach level 25 in any skill.", MMO, BRONZE, "Rune_Empty", k("skill_level_best"), 25);
        d.feat("mmo_skill_50", "Skill Level 50", "Reach level 50 in any skill.", MMO, SILVER, "Rune_Fireball", k("skill_level_best"), 50);
        d.feat("mmo_skill_75", "Skill Level 75", "Reach level 75 in any skill.", MMO, GOLD, "Rune_Damage_Up", k("skill_level_best"), 75);
        d.feat("mmo_skill_100", "Skill Level 100", "Reach level 100 in a skill.", MMO, PLATINUM, "Weapon_Sword_Runic", k("skill_level_best"), 100);
        d.feat("mmo_level_ups_100", "Hundred Level Ups", "Gain 100 skill levels across every skill.", MMO, SILVER, "Ingredient_Rune_Shard", k("skill_level_ups"), 100);
        // ---- experience, whole points over every skill ----
        d.feat("mmo_xp_100k", "Hundred Thousand XP", "Earn 100,000 skill experience.", MMO, SILVER, "Potion_Stamina_Greater", k("xp"), 100_000);
        d.feat("mmo_xp_million", "Million XP", "Earn 1,000,000 skill experience.", MMO, GOLD, "Ingredient_Bar_Adamantite", k("xp"), 1_000_000);
        // ---- abilities ----
        d.feat("mmo_abilities_100", "Spellcaster", "Cast 100 abilities.", MMO, BRONZE, "Potion_Mana", k("abilities"), 100);
        d.feat("mmo_abilities_1000", "Thousand Casts", "Cast 1,000 abilities.", MMO, GOLD, "Weapon_Staff_Wizard", k("abilities"), 1_000);

        // ---- World tab, both follow Skill Level 100 ----
        d.milestone("world_mmo_skill_100", "Skill Cap", "Reach level 100 in a skill on this server.", GOLD, "Rune_Damage_Up", after("mmo_skill_100"));
        d.first("first_mmo_skill_100", "Prodigy", "Be the first on this server to reach level 100 in a skill.", PLATINUM, "Weapon_Sword_Runic", after("mmo_skill_100"));
    }
}
