package dev.ryvrook.feats.api;

import java.util.UUID;

/**
 * One row of the points leaderboard from {@link FeatsApi#top}. Part of the stable Feats API since API version 2.
 *
 * @param rank         position counting from 1
 * @param uuid         the player
 * @param name         the player's name as Feats last saw it
 * @param points       points from every unlocked feat, world and first feats included
 * @param unlocked     personal feats unlocked
 * @param lastUnlockAt epoch milliseconds of the player's latest unlock, 0 for none
 */
public record LeaderboardEntry(int rank, UUID uuid, String name, int points, int unlocked, long lastUnlockAt) {
}
