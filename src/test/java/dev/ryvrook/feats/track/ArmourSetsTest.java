package dev.ryvrook.feats.track;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ArmourSetsTest {
    @Test
    void fourPieceSetsNeedAllFour() {
        assertEquals("Iron", ArmourSets.completeSet(List.of("Armor_Iron_Head", "Armor_Iron_Chest", "Armor_Iron_Hands",
                "Armor_Iron_Legs")));
        assertNull(ArmourSets.completeSet(List.of("Armor_Iron_Head", "Armor_Iron_Chest", "Armor_Iron_Hands")));
        assertEquals("Leather_Raven", ArmourSets.completeSet(List.of("Armor_Leather_Raven_Legs", "Armor_Leather_Raven_Hands",
                "Armor_Leather_Raven_Chest", "Armor_Leather_Raven_Head")));
        assertEquals("Cloth_Cindercloth", ArmourSets.completeSet(List.of("Armor_Cloth_Cindercloth_Head",
                "Armor_Cloth_Cindercloth_Chest", "Armor_Cloth_Cindercloth_Hands", "Armor_Cloth_Cindercloth_Legs")));
    }

    @Test
    void partialSetsCompleteWithThePiecesTheyShip() {
        assertEquals("Bronze_Ornate", ArmourSets.completeSet(List.of("Armor_Bronze_Ornate_Head", "Armor_Bronze_Ornate_Chest",
                "Armor_Bronze_Ornate_Hands")));
        assertEquals("Kweebec", ArmourSets.completeSet(List.of("Armor_Kweebec_Head", "Armor_Kweebec_Chest")));
        assertEquals("Trooper", ArmourSets.completeSet(List.of("Armor_Trooper_Head", "Armor_Trooper_Chest", "Armor_Trooper_Legs")));
        assertNull(ArmourSets.completeSet(List.of("Armor_Kweebec_Head")));
        // Bronze and Bronze Ornate are different sets even though one id starts with the other
        assertNull(ArmourSets.completeSet(List.of("Armor_Bronze_Head", "Armor_Bronze_Chest", "Armor_Bronze_Hands",
                "Armor_Bronze_Ornate_Legs")));
    }

    @Test
    void mixedSetsNullsAndOddItemsDoNotComplete() {
        assertNull(ArmourSets.completeSet(List.of("Armor_Iron_Head", "Armor_Steel_Chest", "Armor_Iron_Hands", "Armor_Iron_Legs")));
        assertNull(ArmourSets.completeSet(Arrays.asList(null, null, null, null)));
        assertNull(ArmourSets.completeSet(List.of()));
        assertNull(ArmourSets.completeSet(null));
        assertNull(ArmourSets.completeSet(List.of("Tool_Pickaxe_Iron", "Weapon_Sword_Iron")));
        assertNull(ArmourSets.completeSet(List.of("Armor_QA_Chest", "Armor_QA_Hands", "Armor_QA_Legs")));
        assertNull(ArmourSets.completeSet(List.of("Debug_Armor_D_Chest_Of_Damage_Reduction")));
        // A full set with a fifth slot of something else still completes
        assertEquals("Copper", ArmourSets.completeSet(Arrays.asList("Armor_Copper_Head", "Armor_Copper_Chest",
                "Armor_Copper_Hands", "Armor_Copper_Legs", null)));
    }

    @Test
    void materialParsing() {
        assertEquals("Steel_Ancient", ArmourSets.material("Armor_Steel_Ancient_Legs"));
        assertNull(ArmourSets.material("Armor_Iron_Boots"));
        assertNull(ArmourSets.material("Armor_Head"));
        assertNull(ArmourSets.material(null));
        assertEquals(4, ArmourSets.piecesOf("Iron").size());
        assertEquals(2, ArmourSets.piecesOf("Kweebec").size());
    }
}
