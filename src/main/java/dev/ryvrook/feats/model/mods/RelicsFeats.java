package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import static dev.ryvrook.feats.model.Category.RELICS;
import static dev.ryvrook.feats.model.FeatRegistry.Definer.after;
import static dev.ryvrook.feats.model.FeatRegistry.Definer.req;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

/**
 * The Wan's Wonder Weapons feats, read from the {@code relics:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.WONDER_WEAPONS} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.RELICS}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: relics:kills and relics:kills:<relic item id>, the kill counts TrackerAPI's binding writes and
     * the mirror reads back on each kill, relics:abilities and relics:abilities:<ability>, relics:relic_kind:<relic> markers
     * with relics:relic_kinds (distinct relics whose signature ability was used), relics:form_switch.
 */
public final class RelicsFeats {
    private RelicsFeats() {
    }

    /** The counter prefix, {@code relics:}. */
    static final String P = StatKeys.RELICS_PREFIX;

    /** The 16 relic item ids of Wan's Wonder Weapons 1.1.1, as TrackerAPI's kill table spells them. */
    static final String[] RELICS_ALL = {
            "WanMine_Ashthorn_Dagger", "WanMine_Frostburn_Dagger", "WanMine_Nightshade_Dagger", "WanMine_Heartroot_Dagger",
            "Wanmine_Weapon_Eclipsebound_Dagger", "WanMine_Chromatic_Cleaver_Dagger", "WanMine_Lethal_Leftovers_Sword",
            "WanMine_Gaias_Wrath_Sword", "WanMine_Quasar_Cosmic_Sword", "WanMine_Soulblight_Longsword",
            "WanMine_Soulblight_Ashen_Longsword", "WanMine_Maelstrom_Mace", "WanMine_Helioram_Mace", "WanMine_Mjollnir_Mace",
            "WanMine_God_Slayer_Greatsword", "WanMine_Void_Requiem_Scythe"
    };

    /**
     * Adds the 8 Wan's Wonder Weapons feats planned in docs/MODS-PLAN.md and the every relic milestone. Kills are
     * counted per relic by TrackerAPI from the item in hand, so the full set reads the 16 per relic kill counts rather
     * than the ability kinds, which only move for relics with a signature of their own.
     */
    public static void define(FeatRegistry.Definer d) {
        d.feat("relics_first_kill", "Relic Kill", "Defeat a creature with a relic weapon.", RELICS, BRONZE, "Weapon_Sword_Runic", P + "kills", 1);
        d.feat("relics_hundred_kills", "Hundred Relic Kills", "Defeat 100 creatures with relic weapons.", RELICS, SILVER, "Weapon_Daggers_Void_Crystal", P + "kills", 100);
        d.feat("relics_thousand_kills", "Thousand Relic Kills", "Defeat 1,000 creatures with relic weapons.", RELICS, GOLD, "Weapon_Battleaxe_Scythe_Void", P + "kills", 1_000);
        d.feat("relics_signature", "Signature Cast", "Use a relic ability.", RELICS, BRONZE, "Weapon_Staff_Wizard", P + "abilities", 1);
        d.feat("relics_five_kinds", "Five Relics", "Use the abilities of 5 different relics.", RELICS, SILVER, "Deco_Treasure", P + "relic_kinds", 5);
        var every = new dev.ryvrook.feats.model.Requirement[RELICS_ALL.length];
        for (int i = 0; i < RELICS_ALL.length; i++) {
            every[i] = req(P + "kills:" + RELICS_ALL[i], 1);
        }
        d.multi("relics_every_relic", "Every Relic", "Defeat a creature with each of the 16 relics.", RELICS, PLATINUM, "Deco_Treasure_Pile_Large", every);
        d.hidden("relics_form_switch", "Form Switch", "Switch the form of the Eclipsebound dagger or the stance of the Chromatic Cleaver.", RELICS, BRONZE, "Weapon_Daggers_Iron", P + "form_switch", 1);
        d.hidden("relics_god_slayer", "Mortal Verdict", "Defeat 100 creatures with the God Slayer greatsword.", RELICS, GOLD, "Weapon_Longsword_Ruined_Giant", P + "kills:WanMine_God_Slayer_Greatsword", 100);

        d.milestone("world_relics_every_relic", "Full Armoury", "Have a player defeat a creature with each of the 16 relics on this server.",
                GOLD, "Deco_Treasure_Pile_Large", after("relics_every_relic"));
    }
}
