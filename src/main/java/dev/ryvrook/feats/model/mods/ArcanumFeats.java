package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import java.util.List;

import static dev.ryvrook.feats.model.Category.ARCANUM;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

/**
 * The Arcanum feats, read from the {@code arcanum:} counters TrackerAPI 1.3.0 publishes for the binding
 * {@code arcanum}, mirrored by {@code integration/ModKinds} (casts, failed casts, runes) and
 * {@code integration/TallyKinds} (energy and the bests). The registry calls {@link #define} with
 * {@code Sibling.ARCANUM} and {@code Sibling.TRACKER_API} as the section requirement, and only when TrackerAPI is at
 * least 1.3.0. Personal feats sit in {@code Category.ARCANUM}.
 *
 * <p>The numbers follow Arcanum 0.6.3. It has 89 runes and every player knows the 10 starter runes from the first
 * read, so the rune ladder starts above 10. A spell grid holds at most 81 runes and the energy pool caps at 5,000.
 */
public final class ArcanumFeats {
    private ArcanumFeats() {
    }

    // ---- every key the binding writes, in one place so a change of the binding is one edit here ----
    /** Spells cast, with {@code arcanum:abilities:<spell>} per normalised spell name. */
    static final String CASTS = StatKeys.ARCANUM_PREFIX + "abilities";
    static final String CASTS_ID = StatKeys.ARCANUM_PREFIX + "abilities:";
    /** Runes known, marks {@code arcanum:rune:<id>}. */
    static final String RUNES = StatKeys.ARCANUM_PREFIX + "runes";
    /** Casts that failed, with {@code arcanum:failed:<stage>} for the stage prepare or cast. */
    static final String FAILED = StatKeys.ARCANUM_PREFIX + "failed";
    static final String FAILED_ID = StatKeys.ARCANUM_PREFIX + "failed:";
    /** Energy spent, a sum. */
    static final String ENERGY = StatKeys.ARCANUM_PREFIX + "energy";
    /** The most energy one spell cost. */
    static final String ENERGY_BEST = StatKeys.ARCANUM_PREFIX + "energy_best";
    static final String COMPLEXITY_BEST = StatKeys.ARCANUM_PREFIX + "complexity_best";
    /** The most runes in one spell. */
    static final String SIZE_BEST = StatKeys.ARCANUM_PREFIX + "size_best";

    /** The keys above. One ending in a colon is a per id prefix. */
    public static final List<String> KEYS = List.of(CASTS, CASTS_ID, RUNES, FAILED, FAILED_ID, ENERGY, ENERGY_BEST,
            COMPLEXITY_BEST, SIZE_BEST);

    /** Adds the 10 Arcanum feats. */
    public static void define(FeatRegistry.Definer d) {
        // ---- casting ----
        d.feat("arcanum_first_spell", "First Spell", "Cast a spell.", ARCANUM, BRONZE, "Weapon_Wand_Wood", CASTS, 1);
        d.feat("arcanum_spells_100", "Hundred Spells", "Cast 100 spells.", ARCANUM, SILVER, "Weapon_Staff_Wizard", CASTS, 100);
        d.feat("arcanum_spells_1000", "Thousand Spells", "Cast 1,000 spells.", ARCANUM, GOLD, "Weapon_Staff_Crystal_Purple", CASTS, 1_000);
        // ---- runes, the 10 starters count ----
        d.feat("arcanum_runes_20", "Rune Student", "Know 20 runes.", ARCANUM, BRONZE, "Rune_Empty", RUNES, 20);
        d.feat("arcanum_runes_50", "Rune Scholar", "Know 50 runes.", ARCANUM, GOLD, "Ingredient_Rune_Shard", RUNES, 50);
        d.feat("arcanum_runes_89", "Every Rune", "Know all 89 runes.", ARCANUM, PLATINUM, "Weapon_Spellbook_Grimoire_Purple", RUNES, 89);
        // ---- energy and size ----
        d.feat("arcanum_energy", "Energy Spent", "Spend 100,000 energy on spells.", ARCANUM, GOLD, "Ingredient_Crystal_Cyan", ENERGY, 100_000);
        d.feat("arcanum_costly_spell", "Costly Spell", "Cast a spell that costs 1,000 energy.", ARCANUM, SILVER, "Ingredient_Crystal_Blue", ENERGY_BEST, 1_000);
        d.feat("arcanum_large_spell", "Large Spell", "Cast a spell built from 15 runes.", ARCANUM, SILVER, "Weapon_Spellbook_Fire", SIZE_BEST, 15);
        d.hidden("arcanum_fizzled", "Fizzled Spells", "Fail 10 spell casts.", ARCANUM, BRONZE, "Deco_Book_Pile_Small", FAILED, 10);
    }
}
