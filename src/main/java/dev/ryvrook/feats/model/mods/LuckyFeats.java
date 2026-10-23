package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import static dev.ryvrook.feats.model.Category.LUCKY_MINING;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

/**
 * The Lucky Mining feats, read from the {@code lucky:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.LUCKY_MINING} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.LUCKY_MINING}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: lucky:drops and lucky:drops:<ore>, lucky:streak_best (best).
 */
public final class LuckyFeats {
    private LuckyFeats() {
    }

    /** The counter prefix, {@code lucky:}. */
    static final String P = StatKeys.LUCKY_PREFIX;

    /**
     * Adds the 5 Lucky Mining feats planned in docs/MODS-PLAN.md. With the default chances, 0.4 to start and 0.02 more
     * per ore, a streak of 31 is where every further ore is a certain bonus.
     */
    public static void define(FeatRegistry.Definer d) {
        d.feat("lucky_first", "First Bonus", "Get a bonus ore from Lucky Mining.", LUCKY_MINING, BRONZE, "Ore_Copper", P + "drops", 1);
        d.feat("lucky_hundred", "Hundred Bonuses", "Get 100 bonus ores.", LUCKY_MINING, SILVER, "Ore_Silver", P + "drops", 100);
        d.feat("lucky_thousand", "Thousand Bonuses", "Get 1,000 bonus ores.", LUCKY_MINING, GOLD, "Ore_Gold", P + "drops", 1_000);
        d.feat("lucky_streak_10", "Ten Streak", "Reach a lucky streak of 10 ores.", LUCKY_MINING, SILVER, "Ingredient_Bar_Silver", P + "streak_best", 10);
        d.feat("lucky_streak_31", "Guaranteed", "Reach a lucky streak of 31 ores, where every bonus is certain.", LUCKY_MINING, PLATINUM, "Ingredient_Bar_Gold", P + "streak_best", 31);
    }
}
