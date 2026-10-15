package dev.ryvrook.feats.api;

import java.util.UUID;

/**
 * A reached server milestone or a claimed server first. Part of the stable Feats API.
 *
 * @param featId the world or first feat id
 * @param at     when it was reached or claimed, in epoch milliseconds
 * @param by     the player who raised the milestone or holds the first, null for a milestone reached
 *               with nobody named
 * @param byName that player's name when it was recorded, null together with {@code by}
 * @param hidden true when the feat shows as ??? in the browser until someone earns it, so a page can hide it too
 */
public record WorldRecord(String featId, long at, UUID by, String byName, boolean hidden) {
    /** The record as Feats 1.3.0 to 1.5.1 built it, not hidden. */
    public WorldRecord(String featId, long at, UUID by, String byName) {
        this(featId, at, by, byName, false);
    }
}
