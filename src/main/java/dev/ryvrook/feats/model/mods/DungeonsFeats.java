package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;

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

    /** Adds the Major Dungeons feats, 12 planned in docs/MODS-PLAN.md. Empty until the definitions land. */
    public static void define(FeatRegistry.Definer d) {
        // The Major Dungeons feats go here.
    }
}
