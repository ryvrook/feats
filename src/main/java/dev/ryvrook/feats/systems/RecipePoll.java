package dev.ryvrook.feats.systems;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The recipes a player has to learn from a recipe item before a bench offers them, and the check of a
 * player's known recipe set against them. In 0.7.1 every Recipe_ item runs a LearnRecipe interaction whose
 * ItemId is the crafted item, and that id is what PlayerConfigData.getKnownRecipes holds. The 25 ids are
 * the Bronze, Bronze Ornate, Leather Raven and Steel Ancient armour pieces, four foods and the six goblin
 * painting replicas, from the Server/Item/Items/Recipe folder of the game assets. Pure, no game state.
 */
public final class RecipePoll {
    private RecipePoll() {}

    /** A sample every {@link #EVERY} world samples reads the known recipes, so once in five seconds. */
    public static final int EVERY = 5;

    public static final List<String> KNOWLEDGE_RECIPES = List.of(
            "Armor_Bronze_Head", "Armor_Bronze_Chest", "Armor_Bronze_Hands", "Armor_Bronze_Legs",
            "Armor_Bronze_Ornate_Head", "Armor_Bronze_Ornate_Chest", "Armor_Bronze_Ornate_Hands",
            "Armor_Leather_Raven_Head", "Armor_Leather_Raven_Chest", "Armor_Leather_Raven_Hands", "Armor_Leather_Raven_Legs",
            "Armor_Steel_Ancient_Head", "Armor_Steel_Ancient_Chest", "Armor_Steel_Ancient_Hands", "Armor_Steel_Ancient_Legs",
            "Food_Pie_Apple", "Food_Pie_Meat", "Food_Pie_Pumpkin", "Food_Salad_Caesar",
            "Goblin_Painting_Breach_Replica", "Goblin_Painting_Fail_Replica", "Goblin_Painting_ForgottenTemple_Replica",
            "Goblin_Painting_Orbis_Replica", "Goblin_Painting_Portal_Replica", "Goblin_Painting_Repair_Replica");

    /** The knowledge recipes in the player's known set, in the order of {@link #KNOWLEDGE_RECIPES}. */
    public static List<String> known(Set<String> knownRecipes) {
        if (knownRecipes == null || knownRecipes.isEmpty()) {
            return List.of();
        }
        List<String> hits = null;
        for (String id : KNOWLEDGE_RECIPES) {
            if (knownRecipes.contains(id)) {
                if (hits == null) {
                    hits = new ArrayList<>(4);
                }
                hits.add(id);
            }
        }
        return hits == null ? List.of() : hits;
    }
}
