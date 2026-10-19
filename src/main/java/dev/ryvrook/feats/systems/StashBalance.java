package dev.ryvrook.feats.systems;

import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.Map;
import java.util.UUID;

/**
 * Per player and per item, how much the player put into containers that are not their own or dropped, less
 * what they took back. Kept as the silent counter {@code stash:<item id>} in the player's file, so it has no
 * clock and survives a leave and a restart. Only items some feat counts as obtained are kept, the same as
 * before 1.8.1. Taking items out of a container takes from here first, so moving a stack out and in again is
 * never obtained twice. Used on the own tracking path only, TrackerAPI keeps its own balance.
 */
final class StashBalance {
    private final FeatsTracker tracker;

    StashBalance(FeatsTracker tracker) {
        this.tracker = tracker;
    }

    /** The player put this many of an item into a container that is not theirs, or dropped them. */
    void put(UUID uuid, String itemId, long amount) {
        if (tracker.isWatched(Classify.obtainedKey(itemId))) {
            tracker.addSilently(uuid, Classify.stashKey(itemId), amount);
        }
    }

    /** Takes up to {@code amount} off the balance and returns how much was taken. */
    long take(UUID uuid, String itemId, long amount) {
        if (!tracker.isWatched(Classify.obtainedKey(itemId))) {
            return 0;
        }
        return tracker.takeSilently(uuid, Classify.stashKey(itemId), amount);
    }

    /**
     * The part of a gain that counts as obtained. Whatever the balance holds of the item is taken back first.
     * Items out of a container a player placed never count, the balance is still taken down by them.
     */
    long fresh(UUID uuid, String itemId, long amount, boolean placedContainer) {
        long taken = take(uuid, itemId, amount);
        return placedContainer ? 0 : Math.max(0, amount - taken);
    }

    /**
     * Moves made in Creative. What went into another container adds to the balance and what came out of one
     * takes from it, and nothing is obtained. Either map may be null.
     */
    void creative(UUID uuid, Map<String, Long> putAway, Map<String, Long> takenOut) {
        if (putAway != null) {
            putAway.forEach((id, amount) -> put(uuid, id, amount));
        }
        if (takenOut != null) {
            takenOut.forEach((id, amount) -> take(uuid, id, amount));
        }
    }

    /** The balance of one item. */
    long balance(UUID uuid, String itemId) {
        var progress = tracker.loadedProgress(uuid);
        return progress == null ? 0 : progress.get(Classify.stashKey(itemId));
    }
}
