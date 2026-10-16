package dev.ryvrook.feats.systems;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Role ids are NPC roles of 0.7.1, Server/NPC/Roles. */
class CombatKindsTest {
    @Test
    void patrolAndWanderVariantsAreOneKind() {
        assertEquals("Skeleton_Scout", CombatKinds.baseRole("Skeleton_Scout_Patrol"));
        assertEquals("Skeleton_Scout", CombatKinds.baseRole("Skeleton_Scout_Wander"));
        assertEquals("Skeleton_Scout", CombatKinds.baseRole("Skeleton_Scout"));
        assertEquals("Trork_Sentry", CombatKinds.baseRole("Trork_Sentry_Patrol"));
    }

    @Test
    void numberedPatrolsAreStripped() {
        assertEquals("Temple_Kweebec_Razorleaf", CombatKinds.baseRole("Temple_Kweebec_Razorleaf_Patrol5"));
        assertEquals("Temple_Kweebec_Razorleaf", CombatKinds.baseRole("Temple_Kweebec_Razorleaf_Patrol"));
    }

    @Test
    void bossPhasesAreOneKind() {
        assertEquals("Goblin_Duke", CombatKinds.baseRole("Goblin_Duke_Phase_2"));
        assertEquals("Goblin_Duke", CombatKinds.baseRole("Goblin_Duke_Phase_3_Fast"));
        assertEquals("Goblin_Gobliterator", CombatKinds.baseRole("Goblin_Gobliterator_Phase3_HM"));
        assertEquals("Skeleton_Elite", CombatKinds.baseRole("Skeleton_Elite_Phase_2"));
    }

    @Test
    void wordsThatOnlyStartLikeAVariantStay() {
        assertEquals("Goblin_Patroller", CombatKinds.baseRole("Goblin_Patroller"));
        assertEquals("Wanderer", CombatKinds.baseRole("Wanderer"));
        assertEquals("Patrol", CombatKinds.baseRole("Patrol"), "a bare variant word is the whole id");
    }

    @Test
    void kindIsMemoised() {
        assertNull(CombatKinds.kind(null));
        assertNull(CombatKinds.kind(""));
        String first = CombatKinds.kind("Skeleton_Sand_Archer_Wander");
        assertEquals("Skeleton_Sand_Archer", first);
        assertSame(first, CombatKinds.kind("Skeleton_Sand_Archer_Wander"));
    }
}
