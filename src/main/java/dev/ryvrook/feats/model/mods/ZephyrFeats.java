package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;

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

    /** Adds the Zephyr feats, 8 planned in docs/MODS-PLAN.md. Empty until the definitions land. */
    public static void define(FeatRegistry.Definer d) {
        // The Zephyr feats go here.
    }
}
