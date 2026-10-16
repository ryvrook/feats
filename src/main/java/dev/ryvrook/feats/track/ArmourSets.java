package dev.ryvrook.feats.track;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out which full armour set a player wears from the item ids in their armour slots. Armour item ids
 * are {@code Armor_<material>_<piece>} with the piece one of Head, Chest, Hands and Legs. Most sets ship
 * all four pieces. Bronze Ornate has no legs, Kweebec has only a head and a chest and Trooper has no
 * hands, so those count as complete with the pieces they ship. Pure functions, no game state.
 */
public final class ArmourSets {
    private ArmourSets() {}

    private static final String PREFIX = "Armor_";
    static final Set<String> PIECES = Set.of("Head", "Chest", "Hands", "Legs");

    /** Sets that ship fewer than four pieces, material to the pieces it has. */
    static final Map<String, Set<String>> PARTIAL_SETS = Map.of(
            "Bronze_Ornate", Set.of("Head", "Chest", "Hands"),
            "Kweebec", Set.of("Head", "Chest"),
            "Trooper", Set.of("Head", "Chest", "Legs"));

    /** Developer sets that are not armour a player earns. */
    private static final Set<String> IGNORED = Set.of("QA");

    /**
     * The material of the complete set worn, such as {@code Iron} or {@code Leather_Raven}, or null when no
     * set is complete. Nulls and non armour ids in the list are skipped. Mixed sets never complete.
     */
    public static String completeSet(List<String> wornItemIds) {
        if (wornItemIds == null || wornItemIds.isEmpty()) {
            return null;
        }
        Map<String, Set<String>> byMaterial = null;
        for (String itemId : wornItemIds) {
            String material = material(itemId);
            if (material == null) {
                continue;
            }
            String piece = piece(itemId);
            if (byMaterial == null) {
                byMaterial = new HashMap<>(4);
            }
            byMaterial.computeIfAbsent(material, m -> new java.util.HashSet<>(4)).add(piece);
        }
        if (byMaterial == null) {
            return null;
        }
        for (Map.Entry<String, Set<String>> e : byMaterial.entrySet()) {
            Set<String> required = PARTIAL_SETS.getOrDefault(e.getKey(), PIECES);
            if (e.getValue().containsAll(required)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** The material of an armour item id, or null when the id is not a wearable armour piece. */
    static String material(String itemId) {
        if (itemId == null || !itemId.startsWith(PREFIX)) {
            return null;
        }
        int cut = itemId.lastIndexOf('_');
        if (cut <= PREFIX.length()) {
            return null;
        }
        String piece = itemId.substring(cut + 1);
        if (!PIECES.contains(piece)) {
            return null;
        }
        String material = itemId.substring(PREFIX.length(), cut);
        return material.isEmpty() || IGNORED.contains(material) ? null : material;
    }

    private static String piece(String itemId) {
        return itemId.substring(itemId.lastIndexOf('_') + 1);
    }

    /** The pieces a set of the given material ships, for tests and the browser text. */
    public static List<String> piecesOf(String material) {
        return new ArrayList<>(PARTIAL_SETS.getOrDefault(material, PIECES));
    }
}
