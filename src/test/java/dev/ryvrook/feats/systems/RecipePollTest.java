package dev.ryvrook.feats.systems;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipePollTest {
    @Test
    void twentyFiveDistinctKnowledgeRecipes() {
        assertEquals(25, RecipePoll.KNOWLEDGE_RECIPES.size());
        assertEquals(25, new HashSet<>(RecipePoll.KNOWLEDGE_RECIPES).size());
        long armour = RecipePoll.KNOWLEDGE_RECIPES.stream().filter(id -> id.startsWith("Armor_")).count();
        long food = RecipePoll.KNOWLEDGE_RECIPES.stream().filter(id -> id.startsWith("Food_")).count();
        long paintings = RecipePoll.KNOWLEDGE_RECIPES.stream().filter(id -> id.endsWith("_Replica")).count();
        assertEquals(15, armour);
        assertEquals(4, food);
        assertEquals(6, paintings);
        assertTrue(RecipePoll.KNOWLEDGE_RECIPES.stream().noneMatch(id -> id.equals("Armor_Bronze_Ornate_Legs")),
                "Bronze Ornate ships no legs");
    }

    @Test
    void knownPicksOnlyTheKnowledgeRecipesInOrder() {
        Set<String> known = Set.of("Food_Pie_Meat", "Tool_Pickaxe_Iron", "Armor_Bronze_Head", "Goblin_Painting_Orbis_Replica");
        assertEquals(List.of("Armor_Bronze_Head", "Food_Pie_Meat", "Goblin_Painting_Orbis_Replica"), RecipePoll.known(known));
        assertEquals(List.of(), RecipePoll.known(Set.of("Tool_Pickaxe_Iron")));
        assertEquals(List.of(), RecipePoll.known(Set.of()));
        assertEquals(List.of(), RecipePoll.known(null));
        assertEquals(25, RecipePoll.known(new HashSet<>(RecipePoll.KNOWLEDGE_RECIPES)).size());
    }
}
