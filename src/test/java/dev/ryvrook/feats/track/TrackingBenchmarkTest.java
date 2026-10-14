package dev.ryvrook.feats.track;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.stats.StatKeys;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the block break, kill and pickup paths through Classify and the tracker a million times each and
 * prints the time and the bytes allocated per event. The numbers also land in build/benchmark.txt. Run
 * with -Pbench to see them in the console.
 */
class TrackingBenchmarkTest {
    private static final int RUNS = 1_000_000;
    private static final int WARMUP = 200_000;

    private static final String[] BLOCKS = {
            "Rock_Stone", "Soil_Dirt", "Ore_Copper_Stone", "Wood_Oak_Trunk", "Rock_Crystal_Void_Large",
            "Soil_Grass", "Ore_Iron_Basalt", "Rock_Basalt"};
    private static final String[] VICTIMS = {
            "Goblin_Scrapper", "Skeleton_Soldier", "Cow", "Trork_Warrior", "Crawler_Void_Surge", "Spider",
            "Bear_Grizzly", "Kweebec_Seedling"};
    private static final String[] PICKUPS = {
            "Plant_Crop_Wheat_Item", "Fish_Trout_Rainbow_Item", "Ingredient_Stick", "Lore_Note_Goblin_1",
            "Food_Apple", "Rock_Stone_Cobble", "Plant_Fruit_Berries_Red", "Ingredient_Fibre"};

    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    @Test
    void hotPaths() throws IOException {
        HytaleLogger logger = HytaleLogger.forEnclosingClass();
        Path dir = Files.createTempDirectory("feats-bench");
        FeatRegistry registry = new FeatRegistry();
        ProgressStore store = new ProgressStore(dir, logger);
        FeatsTracker tracker = new FeatsTracker(registry, store, new UnlockNotifier(() -> null, logger), logger,
                Runnable::run);
        UUID uuid = UUID.randomUUID();
        tracker.load(uuid, "Bench");
        PlayerProgress progress = tracker.loadedProgress(uuid);

        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "Feats tracking benchmark, %,d events per path%n", RUNS));
        out.append(measure("block break", () -> {
            for (int i = 0; i < RUNS; i++) {
                tracker.incrementAll(uuid, Classify.blockBroken(BLOCKS[i & 7]), 1);
            }
        }, () -> {
            for (int i = 0; i < WARMUP; i++) {
                tracker.incrementAll(uuid, Classify.blockBroken(BLOCKS[i & 7]), 1);
            }
        }));
        out.append(measure("kill", () -> {
            for (int i = 0; i < RUNS; i++) {
                tracker.incrementAll(uuid, Classify.kill(VICTIMS[i & 7], (i & 1) == 0), 1);
            }
        }, () -> {
            for (int i = 0; i < WARMUP; i++) {
                tracker.incrementAll(uuid, Classify.kill(VICTIMS[i & 7], (i & 1) == 0), 1);
            }
        }));
        out.append(measure("pickup", () -> {
            for (int i = 0; i < RUNS; i++) {
                tracker.incrementAll(uuid, Classify.pickedUp(PICKUPS[i & 7]), 1);
            }
        }, () -> {
            for (int i = 0; i < WARMUP; i++) {
                tracker.incrementAll(uuid, Classify.pickedUp(PICKUPS[i & 7]), 1);
            }
        }));
        int saves = 10_000;
        out.append(measure("save snapshot", saves, () -> {
            for (int i = 0; i < saves; i++) {
                progress.snapshotForSave().toJson();
            }
        }, () -> {
            for (int i = 0; i < 2_000; i++) {
                progress.snapshotForSave().toJson();
            }
        }));
        System.out.print(out);
        Path report = Path.of("build", "benchmark.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report, out.toString(), StandardCharsets.UTF_8);

        long warm = WARMUP * 3L;
        assertEquals(RUNS + warm / 3, progress.get(StatKeys.KILLS));
        assertEquals(RUNS + warm / 3, progress.get(StatKeys.BLOCKS_BROKEN));
        assertEquals(RUNS + warm / 3, progress.get(StatKeys.PICKED_UP));
        assertTrue(progress.get(StatKeys.ORE_MINED) > 0);
    }

    private static String measure(String name, Runnable body, Runnable warmup) {
        return measure(name, RUNS, body, warmup);
    }

    private static String measure(String name, int runs, Runnable body, Runnable warmup) {
        warmup.run();
        long thread = Thread.currentThread().threadId();
        long bytesBefore = THREADS.getThreadAllocatedBytes(thread);
        long start = System.nanoTime();
        body.run();
        long nanos = System.nanoTime() - start;
        long bytes = THREADS.getThreadAllocatedBytes(thread) - bytesBefore;
        return String.format(Locale.ROOT, "  %-14s %8.1f ms  %7.1f ns/event  %8.1f bytes/event%n", name,
                nanos / 1e6, (double) nanos / runs, (double) bytes / runs);
    }
}
