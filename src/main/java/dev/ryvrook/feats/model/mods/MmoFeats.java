package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.FeatRegistry;

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
 */
public final class MmoFeats {
    private MmoFeats() {
    }

    /** Adds the MMO Skill Tree feats, 20 planned in docs/MODS-PLAN.md. Empty until the definitions land. */
    public static void define(FeatRegistry.Definer d) {
        // The MMO Skill Tree feats go here.
    }
}
