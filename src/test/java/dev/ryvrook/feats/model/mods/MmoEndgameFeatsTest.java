package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.Category;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Pack;
import dev.ryvrook.feats.model.Requirement;
import dev.ryvrook.feats.model.Scope;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.model.Tier;
import dev.ryvrook.feats.model.Trigger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The MMO Skill Tree and Endgame sections of Feats 1.9.0 against docs/MODS-PLAN.md section 2. */
class MmoEndgameFeatsTest {
    /** Every sibling and every pack with TrackerAPI at the minimum, so both sections are in. */
    private static final FeatRegistry FULL = new FeatRegistry(EnumSet.allOf(Sibling.class), EnumSet.allOf(Pack.class),
            FeatRegistry.MODS_MINIMUM);

    /** The mmo: counters integration/ModKinds mirrors, totals and bests in full, per id families by prefix. */
    private static final Set<String> MMO_KEYS = Set.of("mmo:skill_level_best", "mmo:skill_level_ups", "mmo:xp",
            "mmo:abilities", "mmo:classes", "mmo:quests", "mmo:bounties", "mmo:achievements");
    private static final Set<String> MMO_PREFIXES = Set.of("mmo:skill_level:", "mmo:xp:", "mmo:abilities:", "mmo:class:",
            "mmo:quest:", "mmo:bounty:", "mmo:achievement:");

    /** The endgame: counters, including the two bests TrackerAPI keeps and the mirror reads back. */
    private static final Set<String> ENDGAME_KEYS = Set.of("endgame:bosses", "endgame:boss_types", "endgame:achievements",
            "endgame:bestiaries", "endgame:runes", "endgame:waves", "endgame:runs", "endgame:cleared", "endgame:failed",
            "endgame:dungeon_types", "endgame:fast_trial", "endgame:wave_best", "endgame:combo_best");
    private static final Set<String> ENDGAME_PREFIXES = Set.of("endgame:bosses:", "endgame:boss_type:", "endgame:achievement:",
            "endgame:bestiary:", "endgame:rune:", "endgame:wave:", "endgame:runs:", "endgame:cleared:", "endgame:failed:",
            "endgame:dungeon_type:");

    @AfterAll
    static void restore() {
        new FeatRegistry(false); // put the shared feat predicates back to the plain list for the other tests
    }

    private static boolean allowed(String key, Set<String> keys, Set<String> prefixes) {
        if (keys.contains(key)) {
            return true;
        }
        for (String p : prefixes) {
            if (key.startsWith(p) && key.length() > p.length()) {
                return true;
            }
        }
        return false;
    }

    @Test
    void twentyPersonalFeatsPerMod() {
        assertEquals(20, FULL.inCategory(Category.MMO).size());
        assertEquals(20, FULL.inCategory(Category.ENDGAME).size());
        for (Feat feat : FULL.inCategory(Category.MMO)) {
            assertTrue(feat.isPersonal(), feat.id());
            assertTrue(feat.id().startsWith("mmo_"), feat.id());
            assertEquals(Set.of(Sibling.MMO_SKILL_TREE, Sibling.TRACKER_API), feat.requires(), feat.id());
        }
        for (Feat feat : FULL.inCategory(Category.ENDGAME)) {
            assertTrue(feat.isPersonal(), feat.id());
            assertTrue(feat.id().startsWith("endgame_"), feat.id());
            assertEquals(Set.of(Sibling.ENDGAME, Sibling.TRACKER_API), feat.requires(), feat.id());
        }
    }

    @Test
    void everyRequirementReadsAMirroredCounter() {
        for (Feat feat : FULL.inCategory(Category.MMO)) {
            assertFalse(feat.requirements().isEmpty(), feat.id());
            for (Requirement r : feat.requirements()) {
                assertTrue(allowed(r.key(), MMO_KEYS, MMO_PREFIXES), feat.id() + " reads " + r.key());
                assertTrue(r.target() > 0, feat.id());
            }
        }
        for (Feat feat : FULL.inCategory(Category.ENDGAME)) {
            assertFalse(feat.requirements().isEmpty(), feat.id());
            for (Requirement r : feat.requirements()) {
                assertTrue(allowed(r.key(), ENDGAME_KEYS, ENDGAME_PREFIXES), feat.id() + " reads " + r.key());
                assertTrue(r.target() > 0, feat.id());
            }
        }
        // The two signature bosses are keyed by the raw NPC type id the Endgame binding publishes.
        assertEquals(List.of(new Requirement("endgame:bosses:Endgame_Dragon_Frost", 1)), FULL.get("endgame_frost_dragon").requirements());
        assertEquals(List.of(new Requirement("endgame:bosses:Endgame_Dragon_Void", 1)), FULL.get("endgame_void_dragon").requirements());
    }

    @Test
    void idsAreUniqueAndTheScaleHolds() {
        Set<String> ids = new HashSet<>();
        for (Feat feat : FULL.all()) {
            assertTrue(ids.add(feat.id()), "duplicate " + feat.id());
        }
        for (Category cat : List.of(Category.MMO, Category.ENDGAME)) {
            int platinum = 0;
            int hidden = 0;
            for (Feat feat : FULL.inCategory(cat)) {
                if (feat.tier() == Tier.PLATINUM) {
                    platinum++;
                }
                if (feat.hidden()) {
                    hidden++;
                }
                assertFalse(feat.name().contains("—") || feat.description().contains("—"), feat.id() + " uses an em dash");
                assertFalse(feat.name().contains(";") || feat.description().contains(";"), feat.id() + " uses a semicolon");
                assertTrue(feat.description().endsWith("."), feat.id());
            }
            assertTrue(platinum <= 2, cat + " has " + platinum + " platinum feats");
            assertTrue(hidden <= 2, cat + " has " + hidden + " hidden feats");
        }
    }

    @Test
    void theWorldTabFollowsThePersonalFeats() {
        Feat prodigy = FULL.get("first_mmo_skill_100");
        Feat skillCap = FULL.get("world_mmo_skill_100");
        Feat dragonslayer = FULL.get("first_endgame_frost_dragon");
        Feat voidDragon = FULL.get("world_endgame_void_dragon");
        for (Feat feat : List.of(prodigy, skillCap, dragonslayer, voidDragon)) {
            assertNotNull(feat);
            assertSame(Category.WORLD, feat.category(), feat.id());
            assertFalse(feat.isPersonal(), feat.id());
            assertSame(Trigger.Kind.AFTER, feat.trigger().kind(), feat.id());
        }
        assertSame(Scope.FIRST, prodigy.scope());
        assertSame(Scope.WORLD, skillCap.scope());
        assertSame(Scope.FIRST, dragonslayer.scope());
        assertSame(Scope.WORLD, voidDragon.scope());
        assertEquals(Set.of(Sibling.MMO_SKILL_TREE, Sibling.TRACKER_API), prodigy.requires());
        assertEquals(Set.of(Sibling.ENDGAME, Sibling.TRACKER_API), voidDragon.requires());
        assertTrue(FULL.triggeredBy("mmo_skill_100").containsAll(List.of(prodigy, skillCap)));
        assertTrue(FULL.triggeredBy("endgame_frost_dragon").contains(dragonslayer));
        assertTrue(FULL.triggeredBy("endgame_void_dragon").contains(voidDragon));
    }

    @Test
    void withoutTheModNothingIsDefined() {
        FeatRegistry noMods = new FeatRegistry(EnumSet.of(Sibling.TRACKER_API, Sibling.DOMINION, Sibling.COINAGE),
                EnumSet.allOf(Pack.class), FeatRegistry.MODS_MINIMUM);
        assertTrue(noMods.inCategory(Category.MMO).isEmpty());
        assertTrue(noMods.inCategory(Category.ENDGAME).isEmpty());
        for (String id : List.of("mmo_skill_100", "first_mmo_skill_100", "world_mmo_skill_100", "endgame_frost_dragon",
                "first_endgame_frost_dragon", "world_endgame_void_dragon")) {
            assertNull(noMods.get(id), id);
            assertNotNull(noMods.whySkipped(id), id);
        }
        FeatRegistry onlyMmo = new FeatRegistry(EnumSet.of(Sibling.TRACKER_API, Sibling.MMO_SKILL_TREE),
                EnumSet.noneOf(Pack.class), FeatRegistry.MODS_MINIMUM);
        assertEquals(20, onlyMmo.inCategory(Category.MMO).size());
        assertTrue(onlyMmo.inCategory(Category.ENDGAME).isEmpty());
        assertNotNull(onlyMmo.get("first_mmo_skill_100"));
        assertNull(onlyMmo.get("first_endgame_frost_dragon"));
        FeatRegistry oldTracker = new FeatRegistry(EnumSet.allOf(Sibling.class), EnumSet.noneOf(Pack.class), "1.1.2");
        assertTrue(oldTracker.inCategory(Category.MMO).isEmpty());
        assertTrue(oldTracker.inCategory(Category.ENDGAME).isEmpty());
    }
}
