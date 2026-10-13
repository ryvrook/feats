package dev.ryvrook.feats.track;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the last block each player placed for a few seconds. Placing a block takes one item out
 * of the hand the same way eating does, so the inventory tracker asks here before it counts a potion
 * or a meal and skips the removal that belongs to a placement.
 */
public final class RecentPlacements {
    private static final long WINDOW_NANOS = 5_000_000_000L;

    private record Placement(String itemId, long at) {}

    private final Map<UUID, Placement> last = new ConcurrentHashMap<>();

    public void record(UUID uuid, String itemId) {
        if (uuid != null && itemId != null) {
            last.put(uuid, new Placement(itemId, System.nanoTime()));
        }
    }

    /** True once if the player just placed this item, consuming the record. */
    public boolean consume(UUID uuid, String itemId) {
        if (uuid == null || itemId == null) {
            return false;
        }
        Placement placement = last.get(uuid);
        if (placement == null || !placement.itemId().equals(itemId)) {
            return false;
        }
        last.remove(uuid, placement);
        return System.nanoTime() - placement.at() <= WINDOW_NANOS;
    }

    public void forget(UUID uuid) {
        last.remove(uuid);
    }
}
