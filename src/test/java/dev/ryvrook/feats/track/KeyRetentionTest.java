package dev.ryvrook.feats.track;

import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per id key rule, and that the batched incrementAll ends in the same state as calling increment
 * once per key on the current tracker. It does not compare against 1.3.0 itself.
 */
class KeyRetentionTest {
    @Test
    void perIdKeysOnlyWhenWatchedOrShown() {
        FeatsTracker tracker = TestTrackers.tracker();
        assertTrue(tracker.isPersisted(StatKeys.BLOCKS_BROKEN));
        assertTrue(tracker.isPersisted(StatKeys.SWIM_DISTANCE));
        assertTrue(tracker.isPersisted(StatKeys.KILL_STREAK));
        assertTrue(tracker.isPersisted("kills_goblin"), "kill groups are totals, not per id keys");
        assertFalse(tracker.isPersisted("broken:Soil_Dirt_Nobody_Watches_This"));
        assertFalse(tracker.isPersisted("placed:not_a_group"));
        for (String group : List.of("placed:glass", "placed:stone", "placed:wood", "placed:sapling", "death:fire",
                "death:lava")) {
            assertEquals(!TestTrackers.registry().dependingOn(group).isEmpty(), tracker.isPersisted(group), group);
        }
    }

    @Test
    void unwatchedPerIdKeysNeverReachTheFile() {
        FeatsTracker tracker = TestTrackers.tracker();
        UUID uuid = UUID.randomUUID();
        PlayerProgress progress = tracker.load(uuid, "Keeper");
        tracker.incrementAll(uuid, Classify.blockBroken("Soil_Dirt_Nobody_Watches_This"), 1);
        assertEquals(1, progress.get(StatKeys.BLOCKS_BROKEN));
        assertFalse(progress.statsSnapshot().containsKey("broken:Soil_Dirt_Nobody_Watches_This"));
        tracker.increment(uuid, "placed:not_a_group", 5);
        assertFalse(progress.statsSnapshot().containsKey("placed:not_a_group"));
    }

    @Test
    void pausedPlayersCountNothingButPlaytimeWhenAllowed() {
        FeatsTracker tracker = TestTrackers.tracker();
        UUID uuid = UUID.randomUUID();
        PlayerProgress progress = tracker.load(uuid, "Paused");
        tracker.setPaused(uuid, true);
        tracker.setPausePlaytime(false);
        tracker.incrementAll(uuid, List.of(StatKeys.BLOCKS_BROKEN, StatKeys.PLAYTIME), 1);
        assertEquals(0, progress.get(StatKeys.BLOCKS_BROKEN));
        assertEquals(1, progress.get(StatKeys.PLAYTIME));
        tracker.setPaused(uuid, false);
        tracker.incrementAll(uuid, List.of(StatKeys.BLOCKS_BROKEN), 1);
        assertEquals(1, progress.get(StatKeys.BLOCKS_BROKEN));
    }

    @Test
    void batchedIncrementMatchesOneIncrementPerKey() {
        FeatsTracker batched = TestTrackers.tracker();
        FeatsTracker single = TestTrackers.tracker();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        PlayerProgress pa = batched.load(a, "Batched");
        PlayerProgress pb = single.load(b, "Single");
        String[] blocks = {"Ore_Copper_Stone", "Ore_Iron_Basalt", "Wood_Oak_Trunk", "Rock_Stone", "Rock_Crystal_Void_Large"};
        String[] victims = {"Goblin_Scrapper", "Skeleton_Soldier", "Cow", "Trork_Warrior", "Goblin_Duke_Phase_3_Slow",
                "Crawler_Void"};
        String[] items = {"Plant_Sapling_Oak", "Rock_Stone_Brick", "Furniture_Crude_Torch", "Wood_Hardwood_Planks",
                "Furniture_Crude_Window", "Bench_WorkBench"};
        Random random = new Random(42);
        for (int i = 0; i < 20_000; i++) {
            List<String> keys = switch (random.nextInt(3)) {
                case 0 -> Classify.blockBroken(blocks[random.nextInt(blocks.length)]);
                case 1 -> Classify.kill(victims[random.nextInt(victims.length)], random.nextBoolean());
                default -> Classify.blockPlaced(items[random.nextInt(items.length)]);
            };
            long amount = 1 + random.nextInt(3);
            batched.incrementAll(a, keys, amount);
            for (String key : keys) {
                single.increment(b, key, amount);
            }
        }
        Map<String, Long> statsA = pa.statsSnapshot();
        Map<String, Long> statsB = pb.statsSnapshot();
        assertEquals(statsB, statsA);
        assertEquals(pb.unlockedSnapshot().keySet(), pa.unlockedSnapshot().keySet());
        assertTrue(pa.unlockedCount() > 5, "the run should unlock a good few feats");
    }
}
