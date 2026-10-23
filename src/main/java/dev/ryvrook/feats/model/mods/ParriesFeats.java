package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import static dev.ryvrook.feats.model.Category.PARRIES;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

/**
 * The Perfect Parries feats, read from the {@code parries:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.PERFECT_PARRIES} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.PARRIES}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: parries:parry and parries:parry:pvp, parries:counterattack and parries:counterattack:pvp, and
     * parries:streak_best, the best parry streak TrackerAPI's binding writes and the mirror reads back after each parry.
 */
public final class ParriesFeats {
    private ParriesFeats() {
    }

    /** The counter prefix, {@code parries:}. */
    static final String P = StatKeys.PARRIES_PREFIX;

    /** Adds the 8 Perfect Parries feats planned in docs/MODS-PLAN.md. */
    public static void define(FeatRegistry.Definer d) {
        d.feat("parries_first", "First Parry", "Land a perfect parry.", PARRIES, BRONZE, "Weapon_Shield_Wood", P + "parry", 1);
        d.feat("parries_hundred", "Hundred Parries", "Land 100 perfect parries.", PARRIES, SILVER, "Weapon_Shield_Iron", P + "parry", 100);
        d.feat("parries_thousand", "Thousand Parries", "Land 1,000 perfect parries.", PARRIES, GOLD, "Weapon_Shield_Cobalt", P + "parry", 1_000);
        d.feat("parries_ten_thousand", "Ten Thousand Parries", "Land 10,000 perfect parries.", PARRIES, PLATINUM, "Weapon_Shield_Adamantite", P + "parry", 10_000);
        d.feat("parries_counter", "Counterattack", "Land a counterattack after a perfect parry.", PARRIES, BRONZE, "Weapon_Sword_Bronze", P + "counterattack", 1);
        d.feat("parries_counter_250", "Counter Fighter", "Land 250 counterattacks.", PARRIES, GOLD, "Weapon_Sword_Thorium", P + "counterattack", 250);
        d.feat("parries_pvp", "Player Parried", "Parry 25 attacks from other players.", PARRIES, SILVER, "Weapon_Daggers_Bronze", P + "parry:pvp", 25);
        d.feat("parries_streak", "Ten in a Row", "Parry 10 attacks in a row without taking a hit.", PARRIES, GOLD, "Weapon_Shield_Orbis_Knight", P + "streak_best", 10);
    }
}
