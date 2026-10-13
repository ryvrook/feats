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
 */
public record WorldRecord(String featId, long at, UUID by, String byName) {
}
