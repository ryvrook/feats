package dev.ryvrook.feats.track;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the last few hundred block positions each player placed, per world. Breaking one of
 * those blocks again, or placing a block on one of those spots again, does not count, so a single
 * block placed and broken over and over cannot farm the block counters.
 */
public final class PlacedBlocks {
    private static final int PER_PLAYER = 256;

    private record Spot(String world, int x, int y, int z) {}

    private final Map<UUID, Map<Spot, Boolean>> recent = new ConcurrentHashMap<>();

    /**
     * Notes a placement and returns true when the spot was not already one of the player's recent
     * placements, which is when the placement should count.
     */
    public boolean place(UUID uuid, String world, int x, int y, int z) {
        if (uuid == null) {
            return true;
        }
        Map<Spot, Boolean> spots = recent.computeIfAbsent(uuid, id -> ring());
        synchronized (spots) {
            Spot spot = new Spot(world, x, y, z);
            boolean fresh = spots.remove(spot) == null;
            spots.put(spot, Boolean.TRUE); // newest last, the oldest falls off the front
            return fresh;
        }
    }

    /** True when the player placed a block on this spot recently. */
    public boolean placedHere(UUID uuid, String world, int x, int y, int z) {
        if (uuid == null) {
            return false;
        }
        Map<Spot, Boolean> spots = recent.get(uuid);
        if (spots == null) {
            return false;
        }
        synchronized (spots) {
            return spots.containsKey(new Spot(world, x, y, z));
        }
    }

    public void forget(UUID uuid) {
        if (uuid != null) {
            recent.remove(uuid);
        }
    }

    private static Map<Spot, Boolean> ring() {
        return new LinkedHashMap<>(PER_PLAYER * 2) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Spot, Boolean> eldest) {
                return size() > PER_PLAYER;
            }
        };
    }
}
