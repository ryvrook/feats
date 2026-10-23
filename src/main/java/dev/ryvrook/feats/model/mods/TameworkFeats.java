package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import static dev.ryvrook.feats.model.Category.TAMEWORK;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

/**
 * The Alec's Tamework feats, read from the {@code tamework:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.TAMEWORK} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.TAMEWORK}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: tamework:tamed and tamework:tamed:<species>, tamework:species:<species> markers with tamework:species,
     * tamework:bred and tamework:bred:<species>, tamework:companion_level_ups, tamework:companion_level_best (best),
     * tamework:companions_lost, tamework:xp and tamework:xp:<source>, tamework:harvest and tamework:cull.
 */
public final class TameworkFeats {
    private TameworkFeats() {
    }

    /** The counter prefix, {@code tamework:}. */
    static final String P = StatKeys.TAMEWORK_PREFIX;

    /**
     * Adds the 8 Alec's Tamework feats planned in docs/MODS-PLAN.md. The species come from a content pack such as
     * Animal Husbandry, so no feat names one. Its leveling configs cap at 20 to 30, so level 25 is the rare top.
     */
    public static void define(FeatRegistry.Definer d) {
        d.feat("tamework_first", "First Companion", "Tame a creature.", TAMEWORK, BRONZE, "Tool_Capture_Crate", P + "tamed", 1);
        d.feat("tamework_ten", "Ten Companions", "Tame 10 creatures.", TAMEWORK, SILVER, "Tool_Feedbag", P + "tamed", 10);
        d.feat("tamework_ten_species", "Ten Species", "Tame 10 different species.", TAMEWORK, GOLD, "Coop_Chicken", P + "species", 10);
        d.feat("tamework_bred", "Breeder", "Breed a companion.", TAMEWORK, SILVER, "Food_Egg", P + "bred", 1);
        d.feat("tamework_bred_25", "Full Herd", "Breed 25 companions.", TAMEWORK, GOLD, "Bench_Trough", P + "bred", 25);
        d.feat("tamework_level_10", "Trained Companion", "Raise a companion to level 10.", TAMEWORK, SILVER, "Potion_Health", P + "companion_level_best", 10);
        d.feat("tamework_level_25", "Prized Companion", "Raise a companion to level 25.", TAMEWORK, PLATINUM, "Deco_Trophy_Harvest", P + "companion_level_best", 25);
        d.hidden("tamework_lost", "In Memory", "Lose a companion.", TAMEWORK, BRONZE, "Deco_Bone_Pile", P + "companions_lost", 1);
    }
}
