package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.StatKeys;

import static dev.ryvrook.feats.model.Category.ZEPHYR;
import static dev.ryvrook.feats.model.FeatRegistry.Definer.req;
import static dev.ryvrook.feats.model.Tier.BRONZE;
import static dev.ryvrook.feats.model.Tier.GOLD;
import static dev.ryvrook.feats.model.Tier.PLATINUM;
import static dev.ryvrook.feats.model.Tier.SILVER;

/**
 * The Zephyr feats, read from the {@code zephyr:} counters {@code integration/ModKinds} mirrors from the TrackerAPI
 * records. The registry calls {@link #define} with {@code Sibling.ZEPHYR} and {@code Sibling.TRACKER_API} as the
 * section requirement, so every feat here is only in while both are installed. Personal feats sit in
 * {@code Category.ZEPHYR}, world feats go through {@code first} and {@code milestone}.
 *
 * <p>The counters available: zephyr:dodge, zephyr:perfect_dodge and zephyr:perfect_dodge:pvp, zephyr:double_jump, zephyr:wall_jump,
     * zephyr:grapple, zephyr:vanish, zephyr:zipline, and zephyr:move_kind:<action> markers with zephyr:move_kinds.
 */
public final class ZephyrFeats {
    private ZephyrFeats() {
    }

    /** The counter prefix, {@code zephyr:}. */
    static final String P = StatKeys.ZEPHYR_PREFIX;

    /**
     * Adds the 8 Zephyr feats planned in docs/MODS-PLAN.md. The full set names the five moves the binding publishes
     * by default, the zipline is a poll the operator turns on, so it stays out of Every Move.
     */
    public static void define(FeatRegistry.Definer d) {
        d.feat("zephyr_dodges_100", "Hundred Dodges", "Dodge 100 times.", ZEPHYR, BRONZE, "Ingredient_Feathers_Light", P + "dodge", 100);
        d.feat("zephyr_dodges_5000", "Five Thousand Dodges", "Dodge 5,000 times.", ZEPHYR, GOLD, "Ingredient_Feathers_Blue", P + "dodge", 5_000);
        d.feat("zephyr_perfect", "Perfect Dodge", "Dodge an attack at the last moment.", ZEPHYR, BRONZE, "Potion_Stamina", P + "perfect_dodge", 1);
        d.feat("zephyr_perfect_250", "Perfect Dodges", "Land 250 perfect dodges.", ZEPHYR, GOLD, "Potion_Stamina_Greater", P + "perfect_dodge", 250);
        d.feat("zephyr_double_jump", "Double Jumper", "Double jump 500 times.", ZEPHYR, SILVER, "Rocket_Boots", P + "double_jump", 500);
        d.feat("zephyr_grapple", "Grappler", "Fire the grapple 100 times.", ZEPHYR, SILVER, "Tool_Hookshot", P + "grapple", 100);
        d.hidden("zephyr_vanish", "Vanished", "Vanish 25 times.", ZEPHYR, SILVER, "Deco_Lantern", P + "vanish", 25);
        d.multi("zephyr_every_move", "Every Move", "Use the dodge, the double jump, the wall jump, the grapple and the vanish.", ZEPHYR, PLATINUM, "Glider_Scrap",
                req(P + "dodge", 1), req(P + "double_jump", 1), req(P + "wall_jump", 1), req(P + "grapple", 1), req(P + "vanish", 1));
    }
}
