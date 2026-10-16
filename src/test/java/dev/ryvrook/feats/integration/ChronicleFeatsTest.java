package dev.ryvrook.feats.integration;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.UnlockNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The mapping from Chronicle changes to the {@code chronicle:} max counters, over a fake set of window reads, and
 * the reflective handles resolved against a Chronicle Stats jar when the sibling checkout has one.
 */
class ChronicleFeatsTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Path CHRONICLE_JAR = Path.of("..", "chronicle-stats", "build", "libs", "ChronicleStats-1.5.0.jar");
    private static FeatRegistry registry;

    private FeatsTracker tracker;
    private final AtomicLong now = new AtomicLong(1_000_000);
    private FakeWindows windows;
    private ChronicleFeats feats;
    private UUID p;
    private PlayerProgress progress;

    private static synchronized FeatRegistry registry() {
        if (registry == null) {
            registry = new FeatRegistry(EnumSet.allOf(Sibling.class), Set.of());
        }
        return registry;
    }

    @BeforeEach
    void setUp() throws IOException {
        ProgressStore store = new ProgressStore(Files.createTempDirectory("feats-chronicle"), LOGGER);
        tracker = new FeatsTracker(registry(), store, new UnlockNotifier(() -> null, LOGGER), LOGGER, Runnable::run);
        p = UUID.randomUUID();
        progress = tracker.load(p, "Chron");
        windows = new FakeWindows();
        feats = ChronicleFeats.over(tracker, LOGGER, now::get, windows);
    }

    @Test
    void eachKeyRaisesItsWindowMax() {
        windows.set("blocks_broken", "TODAY", 2_600);
        windows.set("kills", "WEEK", 120);
        windows.set("days_active", "MONTH", 28);
        feats.changed(p, "blocks_broken", 50_000);
        feats.changed(p, "kills", 9_000);
        feats.changed(p, "days_active", 400);
        assertEquals(2_600, progress.get(ChronicleFeats.DAY_BLOCKS_MAX));
        assertEquals(120, progress.get(ChronicleFeats.WEEK_KILLS_MAX));
        assertEquals(28, progress.get(ChronicleFeats.MONTH_DAYS_MAX));
        assertEquals(List.of("blocks_broken/TODAY", "kills/WEEK", "days_active/MONTH"), windows.reads);
    }

    @Test
    void aLowerWindowKeepsTheRecord() {
        windows.set("blocks_broken", "TODAY", 3_000);
        feats.changed(p, "blocks_broken", 1);
        now.addAndGet(ChronicleFeats.READ_GAP_MILLIS);
        windows.set("blocks_broken", "TODAY", 10); // a new day
        feats.changed(p, "blocks_broken", 2);
        assertEquals(3_000, progress.get(ChronicleFeats.DAY_BLOCKS_MAX));
    }

    @Test
    void rpgLevelCountsOnlyWhileChronicleDefinesTheStat() {
        windows.rpg = false;
        feats.changed(p, "rpg_level", 12);
        assertEquals(0, progress.get(ChronicleFeats.RPG_LEVEL_MAX), "no RPG Leveling bridge, no level");
        windows.rpg = true;
        now.addAndGet(ChronicleFeats.READ_GAP_MILLIS);
        feats.changed(p, "rpg_level", 12);
        assertEquals(12, progress.get(ChronicleFeats.RPG_LEVEL_MAX), "the value of the change is the level");
    }

    @Test
    void windowReadsAreLimitedToOnePerPlayerPerKeyPer10Seconds() {
        windows.set("blocks_broken", "TODAY", 100);
        feats.changed(p, "blocks_broken", 1);
        windows.set("blocks_broken", "TODAY", 200);
        feats.changed(p, "blocks_broken", 2);
        feats.changed(p, "blocks_broken", 3);
        assertEquals(1, windows.reads.size(), "the burst reads once");
        assertEquals(100, progress.get(ChronicleFeats.DAY_BLOCKS_MAX));
        assertEquals(1, feats.pendingCount(), "the last change waits");

        feats.changed(p, "kills", 1);
        assertEquals(2, windows.reads.size(), "another key has its own gap");
        UUID other = UUID.randomUUID();
        tracker.load(other, "Other");
        feats.changed(other, "blocks_broken", 1);
        assertEquals(3, windows.reads.size(), "another player has their own gap");

        now.addAndGet(ChronicleFeats.READ_GAP_MILLIS - 1);
        feats.flushPending();
        assertEquals(3, windows.reads.size(), "not yet");
        now.addAndGet(1);
        feats.flushPending();
        assertEquals(4, windows.reads.size(), "the held change is read once the gap passed");
        assertEquals(200, progress.get(ChronicleFeats.DAY_BLOCKS_MAX), "so the end of a burst is not missed");
        assertEquals(0, feats.pendingCount());
    }

    @Test
    void thePollReadsTheMonthWindowOncePerMinuteForEveryLoadedPlayer() {
        // Chronicle raises days_active when it closes a day and sends no change, so only the poll sees it
        UUID other = UUID.randomUUID();
        PlayerProgress otherProgress = tracker.load(other, "Other");
        windows.set("days_active", "MONTH", 30);
        feats.tick();
        assertEquals(30, progress.get(ChronicleFeats.MONTH_DAYS_MAX), "Full Month moves without a change");
        assertEquals(30, otherProgress.get(ChronicleFeats.MONTH_DAYS_MAX));
        assertEquals(2, windows.reads.size());

        windows.set("days_active", "MONTH", 31);
        now.addAndGet(ChronicleFeats.MONTH_GAP_MILLIS - 1);
        feats.tick();
        assertEquals(2, windows.reads.size(), "one read per player per minute");
        now.addAndGet(1);
        feats.tick();
        assertEquals(4, windows.reads.size());
        assertEquals(31, progress.get(ChronicleFeats.MONTH_DAYS_MAX));
    }

    @Test
    void aFailingReadIsSwallowedAndOtherKeysStillCount() {
        windows.fail = true;
        feats.changed(p, "blocks_broken", 1);
        windows.fail = false;
        windows.set("kills", "WEEK", 7);
        feats.changed(p, "kills", 1);
        assertEquals(7, progress.get(ChronicleFeats.WEEK_KILLS_MAX));
    }

    @Test
    void handlesResolveAgainstTheChronicleJar() throws Throwable {
        assumeTrue(Files.isRegularFile(CHRONICLE_JAR), "no Chronicle Stats jar next to this checkout");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{CHRONICLE_JAR.toUri().toURL()}, getClass().getClassLoader())) {
            ChronicleFeats.Handles h = ChronicleFeats.handles(null, loader);
            assertNotNull(h.window());
            assertNotNull(h.onChange());
            assertEquals(Set.of("TODAY", "WEEK", "MONTH"), h.windows().keySet());
            Class<?> change = Class.forName(ChronicleFeats.STAT_CHANGE, true, loader);
            UUID u = UUID.randomUUID();
            Object record = change.getConstructors()[0].newInstance(u, "kills", 1L, 42L, null, 0L);
            assertEquals(u, (UUID) h.changeUuid().invoke(record));
            assertEquals("kills", (String) h.changeKey().invoke(record));
            assertEquals(42L, (long) h.changeValue().invoke(record));
        }
    }

    /** Window values set by the test, and a log of the reads. */
    private static final class FakeWindows implements ChronicleFeats.Windows {
        final Map<String, Long> values = new HashMap<>();
        final List<String> reads = new ArrayList<>();
        boolean rpg = true;
        boolean fail;

        void set(String key, String window, long value) {
            values.put(key + "/" + window, value);
        }

        @Override
        public long window(UUID uuid, String key, String window) {
            if (fail) {
                throw new IllegalStateException("Chronicle stopped");
            }
            reads.add(key + "/" + window);
            return values.getOrDefault(key + "/" + window, 0L);
        }

        @Override
        public boolean hasStat(String key) {
            return rpg && "rpg_level".equals(key);
        }
    }
}
