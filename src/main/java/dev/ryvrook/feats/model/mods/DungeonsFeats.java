package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import static dev.ryvrook.feats.model.Category.DUNGEONS;
import static dev.ryvrook.feats.model.FeatRegistry.Definer.after;
import static dev.ryvrook.feats.model.FeatRegistry.Definer.req;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

/**
 * The Major Dungeons feats, read from the {@code dungeons:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.MAJOR_DUNGEONS} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.DUNGEONS}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: dungeons:runs and dungeons:runs:<dungeon>, dungeons:cleared and dungeons:cleared:<dungeon>,
     * dungeons:cleared_mode:<mode>, dungeons:failed and dungeons:failed:<dungeon>, dungeons:bosses and
     * dungeons:bosses:<boss>, dungeons:boss_type:<boss> markers with dungeons:boss_types, dungeons:dungeon_type:<dungeon>
     * markers with dungeons:dungeon_types, and the clear counts dungeons:fast_clear (under 10 minutes),
     * dungeons:hardcore_clear and dungeons:solo_clear.
 */
public final class DungeonsFeats {
    private DungeonsFeats() {
    }

    /** The counter prefix, {@code dungeons:}. */
    static final String P = StatKeys.DUNGEONS_PREFIX;

    /**
     * Adds the 12 Major Dungeons feats planned in docs/MODS-PLAN.md, the server first for a Hardcore clear and the
     * fifty clears milestone. The dungeon and boss ids are the ones Major Dungeons 0.4.14 ships, {@code Dungeon_Of_Fear_I}
     * to {@code III} and the bosses {@code Azaroth}, {@code Baron}, {@code Katherina} with the two minibosses. A server
     * total of dungeons cleared is not fed by TrackerAPI, so the milestone follows a personal feat instead.
     */
    public static void define(FeatRegistry.Definer d) {
        d.feat("dungeons_first_run", "First Descent", "Enter a dungeon.", DUNGEONS, BRONZE, "Portal_Device", P + "runs", 1);
        d.feat("dungeons_first_clear", "Dungeon Cleared", "Clear a dungeon.", DUNGEONS, BRONZE, "Coffer_Key", P + "cleared", 1);
        d.feat("dungeons_ten_clears", "Ten Clears", "Clear 10 dungeons.", DUNGEONS, SILVER, "Dungeon_Portal_End", P + "cleared", 10);
        d.feat("dungeons_fifty_clears", "Fifty Clears", "Clear 50 dungeons.", DUNGEONS, GOLD, "Deco_Treasure", P + "cleared", 50);
        d.multi("dungeons_every_dungeon", "Every Dungeon", "Clear Dungeon of Fear I, II and III.", DUNGEONS, SILVER, "Deco_Map",
                req(P + "cleared:Dungeon_Of_Fear_I", 1), req(P + "cleared:Dungeon_Of_Fear_II", 1), req(P + "cleared:Dungeon_Of_Fear_III", 1));
        d.multi("dungeons_every_boss", "Every Boss", "Defeat all 5 dungeon bosses, the two minibosses included.", DUNGEONS, GOLD, "Deco_Bone_Skulls",
                req(P + "bosses:Azaroth", 1), req(P + "bosses:Baron", 1), req(P + "bosses:Katherina", 1),
                req(P + "bosses:Cult_Knight_Miniboss", 1), req(P + "bosses:Cult_Werewolf_Miniboss", 1));
        d.feat("dungeons_hundred_bosses", "Hundred Bosses", "Defeat 100 dungeon bosses.", DUNGEONS, GOLD, "Deco_Bone_Skulls_Wall", P + "bosses", 100);
        d.feat("dungeons_hardcore", "Hardcore Clear", "Clear a dungeon in Hardcore mode.", DUNGEONS, GOLD, "Deco_Bone_Skulls_Feran", P + "hardcore_clear", 1);
        d.feat("dungeons_hardcore_ten", "Hardcore Veteran", "Clear 10 dungeons in Hardcore mode.", DUNGEONS, PLATINUM, "Weapon_Battleaxe_Doomed", P + "hardcore_clear", 10);
        d.feat("dungeons_solo", "Solo Clear", "Clear a dungeon alone.", DUNGEONS, SILVER, "Weapon_Sword_Iron", P + "solo_clear", 1);
        d.feat("dungeons_fast", "Fast Clear", "Clear a dungeon in under 10 minutes.", DUNGEONS, GOLD, "Potion_Stamina", P + "fast_clear", 1);
        d.hidden("dungeons_wiped", "Wiped", "Fail a dungeon run.", DUNGEONS, BRONZE, "Deco_Bone_Pile", P + "failed", 1);

        d.first("first_dungeons_hardcore", "Hardcore First", "Be the first on this server to clear a dungeon in Hardcore mode.",
                PLATINUM, "Weapon_Battleaxe_Doomed", after("dungeons_hardcore"));
        d.milestone("world_dungeons_fifty_clears", "Deep Delvers", "Have a player clear 50 dungeons on this server.",
                GOLD, "Deco_Treasure", after("dungeons_fifty_clears"));
    }
}
