package dev.ryvrook.feats.scan;

import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns what the engine recorded about a player into counter updates Feats would have made had it
 * watched the whole time. Updates only ever raise a counter. A one time discovery that is already
 * present, or a total already at or above the engine's number, gives no update, so a second scan
 * finds nothing.
 *
 * <p>Sources and the keys they feed:
 * <ul>
 *   <li>discovered zones, mapped from region to discovery name, as {@code zone:<name>} plus
 *       {@code zones_discovered}, the same discovery the live zone event makes</li>
 *   <li>recorded Memories still held by the player as {@code memory:<id>} plus {@code memories}, the
 *       same discovery the live Memories poll makes</li>
 *   <li>death markers in every world as a floor for {@code deaths}</li>
 *   <li>a first spawn made in any world as a floor of 1 for {@code joins}</li>
 * </ul>
 */
public final class WorldScan {
    private WorldScan() {
    }

    /** One proposed update. {@code prefix} is null for a plain raise of {@code key} to {@code value}. */
    public record Change(String prefix, String id, String key, long value) {
        static Change discover(String prefix, String id, String total) {
            return new Change(prefix, id, total, 1);
        }

        static Change raise(String key, long value) {
            return new Change(null, null, key, value);
        }

        boolean isDiscovery() {
            return prefix != null;
        }

        /** The counter this change looks at to decide whether it is new. */
        String marker() {
            return isDiscovery() ? prefix + id : key;
        }
    }

    /** The updates for one player and how many saved regions had no known zone name. */
    public record Plan(List<Change> changes, int unmappedRegions) {
    }

    /** Works out the updates against {@code current}, without changing anything. */
    public static Plan plan(FeatsTracker tracker, EngineRecord record, Map<String, String> zoneNames,
                            PlayerProgress current) {
        List<Change> changes = new ArrayList<>();
        int unmapped = 0;
        if (record == null) {
            return new Plan(changes, 0);
        }
        Set<String> zonesSeen = new java.util.HashSet<>();
        for (String region : record.regions()) {
            String zone = zoneNames.get(region);
            if (zone == null) {
                unmapped++; // a region no loaded world generates, its live name is unknown
                continue;
            }
            if (zonesSeen.add(zone)) {
                discovery(tracker, current, changes, StatKeys.ZONE_PREFIX, zone, StatKeys.ZONES_DISCOVERED);
            }
        }
        for (String memory : record.memories()) {
            discovery(tracker, current, changes, StatKeys.MEMORY_PREFIX, memory, StatKeys.MEMORIES);
        }
        raise(tracker, current, changes, StatKeys.DEATHS, record.deathMarkers());
        raise(tracker, current, changes, StatKeys.JOINS, record.spawned() ? 1 : 0);
        return new Plan(changes, unmapped);
    }

    private static void discovery(FeatsTracker tracker, PlayerProgress current, List<Change> out, String prefix,
                                  String id, String total) {
        if (!tracker.isWatched(total) && !tracker.isWatched(prefix + id)) {
            return; // no feat reads this kind of discovery
        }
        if (!tracker.isPersisted(prefix + id) || current.get(prefix + id) > 0) {
            return;
        }
        out.add(Change.discover(prefix, id, total));
    }

    private static void raise(FeatsTracker tracker, PlayerProgress current, List<Change> out, String key, long value) {
        if (value <= 0 || !tracker.isWatched(key) || current.get(key) >= value) {
            return;
        }
        out.add(Change.raise(key, value));
    }

    /**
     * Applies the plan to an online player through the tracker, so unlocks, toasts, rewards, meta feats
     * and the unlock hooks run as for a live event. Call it on the player's world thread. Returns how
     * many changes moved a counter.
     */
    public static int applyLive(FeatsTracker tracker, UUID uuid, PlayerProgress live, List<Change> changes) {
        int moved = 0;
        for (Change change : changes) {
            long before = live.get(change.marker());
            if (change.isDiscovery()) {
                tracker.discover(uuid, change.prefix(), change.id(), change.key());
            } else {
                tracker.raiseTo(uuid, change.key(), change.value());
            }
            if (live.get(change.marker()) > before) {
                moved++;
            }
        }
        return moved;
    }

    /**
     * Applies the plan to progress nobody is playing with, a held offline copy or a scratch copy for a
     * dry run. Counters move exactly as the tracker's discover and raiseTo move them. Feats are not
     * unlocked here, {@code FeatsTracker.catchUpOffline} does that for a held copy afterwards. Returns
     * how many changes moved a counter.
     */
    public static int applyOffline(PlayerProgress progress, List<Change> changes) {
        int moved = 0;
        for (Change change : changes) {
            if (change.isDiscovery()) {
                if (progress.raiseTo(change.prefix() + change.id(), 1)) {
                    progress.add(change.key(), 1);
                    moved++;
                }
            } else if (progress.raiseTo(change.key(), change.value())) {
                moved++;
            }
        }
        return moved;
    }

    /**
     * The feats the tracker's catch up would unlock for this progress, meta feats included. Works on
     * a copy, so the progress itself is left alone.
     */
    public static List<Feat> pendingUnlocks(FeatsTracker tracker, PlayerProgress progress) {
        PlayerProgress copy = copy(progress);
        List<Feat> found = new ArrayList<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Feat feat : tracker.registry().all()) {
                if (!copy.isUnlocked(feat.id()) && feat.isSatisfied(copy) && copy.unlock(feat.id(), 0L)) {
                    found.add(feat);
                    copy.add(StatKeys.FEATS_UNLOCKED, 1);
                    changed = true;
                }
            }
        }
        return found;
    }

    /** A detached copy of the progress that nothing saves. */
    public static PlayerProgress copy(PlayerProgress progress) {
        return PlayerProgress.fromDocument(progress.uuid(), progress.toDocument());
    }
}
