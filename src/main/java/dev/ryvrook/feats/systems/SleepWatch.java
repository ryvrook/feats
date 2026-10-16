package dev.ryvrook.feats.systems;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whole nights slept, from the world's sleep state as the samples see it. While a world is in slumber
 * its {@code WorldSlumber} names the game time it is heading for. The engine ends the slumber either when
 * the real time it takes has run out, which sets the game time to that target, or when someone woke up,
 * which sets the game time only part of the way. So a night counts when the world is back awake and its
 * game time reached the target, for every player who was seen in slumber during it. Verified against
 * {@code UpdateWorldSlumberSystem.tick} in the 0.7.1 jar.
 */
public final class SleepWatch {
    private final Map<String, Instant> targets = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> sleepers = new ConcurrentHashMap<>();

    /**
     * One world sample. {@code slumberTarget} is the target game time while the world slumbers and null
     * while it is awake. Returns the players who slept through a night that just finished, or empty.
     */
    public Set<UUID> world(String world, Instant slumberTarget, Instant gameTime) {
        if (world == null) {
            return Set.of();
        }
        if (slumberTarget != null) {
            targets.put(world, slumberTarget);
            return Set.of();
        }
        Instant target = targets.remove(world);
        Set<UUID> slept = sleepers.remove(world);
        if (target == null || slept == null || slept.isEmpty() || gameTime == null) {
            return Set.of();
        }
        return gameTime.plusSeconds(1).isBefore(target) ? Set.of() : slept;
    }

    /** A player was seen in slumber in a slumbering world. */
    public void sleeping(String world, UUID player) {
        if (world != null && player != null) {
            sleepers.computeIfAbsent(world, w -> ConcurrentHashMap.newKeySet()).add(player);
        }
    }

    /** The player left, so the night in progress is not theirs. */
    public void forget(UUID player) {
        if (player != null) {
            sleepers.values().forEach(set -> set.remove(player));
        }
    }
}
