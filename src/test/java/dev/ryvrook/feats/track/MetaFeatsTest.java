package dev.ryvrook.feats.track;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.Category;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Pack;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.model.Tier;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.stats.WorldStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The meta counters the tracker keeps, points, the category closers, Grand Slam and the server totals. */
class MetaFeatsTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** The IO thread, run by hand so each test decides when the queued work happens. */
    private final Deque<Runnable> io = new ArrayDeque<>();
    private FeatRegistry registry;
    private ProgressStore store;
    private WorldStore world;
    private FeatsTracker tracker;

    @BeforeEach
    void setUp() throws Exception {
        Path dir = Files.createTempDirectory("feats-meta");
        registry = new FeatRegistry(EnumSet.noneOf(Sibling.class), EnumSet.of(Pack.SOCIAL)); // Shared First reads the witness flag
        store = new ProgressStore(dir, LOGGER);
        world = new WorldStore(dir, LOGGER);
        tracker = new FeatsTracker(registry, store, new UnlockNotifier(() -> null, LOGGER), LOGGER, io::add, world);
    }

    @AfterAll
    static void restore() {
        new FeatRegistry(false); // put the shared feat predicates back to the plain list for the other tests
    }

    private void drainIo() {
        Runnable task;
        while ((task = io.poll()) != null) {
            task.run();
        }
    }

    /** True for a tab's closer, the feat that reads category_done. */
    private static boolean closer(Feat feat) {
        return feat.requirements().stream().anyMatch(r -> r.key().startsWith(StatKeys.CATEGORY_DONE_PREFIX));
    }

    /** Every personal feat of the tab but the closer, the ones the closer waits for. */
    private List<Feat> pool(Category tab) {
        return registry.inCategory(tab).stream().filter(f -> !closer(f)).toList();
    }

    @Test
    void pointsFollowUnlocksAndRevokes() {
        UUID uuid = UUID.randomUUID();
        PlayerProgress progress = tracker.load(uuid, "Scorer");
        tracker.increment(uuid, StatKeys.BLOCKS_BROKEN, 1); // Rock Bottom, Bronze
        assertEquals(10, progress.get(StatKeys.FEAT_POINTS));
        tracker.increment(uuid, StatKeys.BLOCKS_BROKEN, 999); // Digger and Excavator
        assertEquals(10 + 10 + 25, progress.get(StatKeys.FEAT_POINTS));
        assertEquals(tracker.points(progress), progress.get(StatKeys.FEAT_POINTS));
        assertEquals(List.of("digger"), tracker.revoke(progress, "digger"));
        assertEquals(10 + 25, progress.get(StatKeys.FEAT_POINTS));
        assertEquals(tracker.points(progress), progress.get(StatKeys.FEAT_POINTS));
    }

    @Test
    void tenThousandPointsUnlocksTheLadder() {
        UUID uuid = UUID.randomUUID();
        PlayerProgress progress = tracker.load(uuid, "Ladder");
        for (Feat feat : registry.personal()) {
            if (feat.tier() != Tier.BRONZE && !feat.id().equals("ten_thousand") && progress.get(StatKeys.FEAT_POINTS) < 10_000) {
                tracker.adminGrant(progress, feat, false);
            }
        }
        assertTrue(progress.get(StatKeys.FEAT_POINTS) >= 10_000);
        assertTrue(progress.isUnlocked("ten_thousand"));
        assertFalse(progress.isUnlocked("twenty_thousand"));
        assertEquals(tracker.points(progress), progress.get(StatKeys.FEAT_POINTS), "the ladder's own points count too");
    }

    @Test
    void aTabCompletedUnlocksItsCloserAndARevokeTakesItBack() {
        UUID uuid = UUID.randomUUID();
        PlayerProgress progress = tracker.load(uuid, "Builder");
        List<Feat> pool = pool(Category.BUILDING);
        for (int i = 0; i < pool.size() - 1; i++) {
            tracker.adminGrant(progress, pool.get(i), false);
        }
        assertEquals(0, progress.get(StatKeys.CATEGORY_DONE_PREFIX + "BUILDING"));
        assertFalse(progress.isUnlocked("building_complete"));
        tracker.adminGrant(progress, pool.get(pool.size() - 1), false);
        assertEquals(1, progress.get(StatKeys.CATEGORY_DONE_PREFIX + "BUILDING"));
        assertTrue(progress.isUnlocked("building_complete"), "the closer follows the last feat of the tab");
        assertEquals(0, progress.get(StatKeys.CATEGORY_DONE_PREFIX + "MINING"), "other tabs are untouched");

        List<String> removed = tracker.revoke(progress, "cornerstone");
        assertTrue(removed.contains("cornerstone") && removed.contains("building_complete"), removed.toString());
        assertEquals(0, progress.get(StatKeys.CATEGORY_DONE_PREFIX + "BUILDING"));
        assertFalse(progress.isUnlocked("building_complete"));

        tracker.increment(uuid, StatKeys.BLOCKS_PLACED, 1); // Cornerstone again, through play this time
        assertTrue(progress.isUnlocked("cornerstone"));
        assertTrue(progress.isUnlocked("building_complete"), "and the closer comes back with it");
    }

    @Test
    void aFileWrittenBeforeTheCloserExistedGetsItAtTheNextJoin() {
        UUID uuid = UUID.randomUUID();
        PlayerProgress progress = tracker.load(uuid, "Old");
        for (Feat feat : pool(Category.CRAFTING)) {
            progress.unlock(feat.id(), 1L); // straight into the file, as an older Feats would have written it
        }
        progress.set(StatKeys.FEAT_POINTS, 0);
        tracker.unload(uuid);
        drainIo();
        PlayerProgress back = tracker.load(uuid, "Old");
        tracker.catchUp(uuid);
        assertTrue(back.isUnlocked("crafting_complete"));
        assertEquals(tracker.points(back), back.get(StatKeys.FEAT_POINTS), "points are rebuilt from the unlocks");
    }

    @Test
    void everyPlatinumUnlocksGrandSlam() {
        UUID uuid = UUID.randomUUID();
        PlayerProgress progress = tracker.load(uuid, "Slam");
        List<Feat> platinums = registry.personal().stream()
                .filter(f -> f.tier() == Tier.PLATINUM && !closer(f)
                        && !List.of("grand_slam", "grandmaster", "all_of_it").contains(f.id()))
                .toList();
        for (int i = 0; i < platinums.size() - 1; i++) {
            tracker.adminGrant(progress, platinums.get(i), false);
        }
        assertFalse(progress.isUnlocked("grand_slam"));
        tracker.adminGrant(progress, platinums.get(platinums.size() - 1), false);
        assertEquals(1, progress.get(StatKeys.PLATINUMS_DONE));
        assertTrue(progress.isUnlocked("grand_slam"), "Twenty Thousand is in the pool, the closers and the unlock ladders are not");
        assertNull(world.first("first_grand_slam"), "admin grants never raise a server first");

        List<String> removed = tracker.revoke(progress, platinums.get(0).id());
        assertTrue(removed.contains("grand_slam"), removed.toString());
        assertEquals(0, progress.get(StatKeys.PLATINUMS_DONE));
    }

    @Test
    void aMirroredCounterRaisesTheServerTotalAndItsMilestone() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        PlayerProgress first = tracker.load(a, "Hunter");
        PlayerProgress second = tracker.load(b, "Bystander");
        tracker.increment(a, StatKeys.KILLS, 99_999);
        assertEquals(99_999, world.total(StatKeys.TOTAL_KILLS));
        assertFalse(world.hasMilestone("world_hundred_thousand_kills"));
        tracker.increment(b, StatKeys.KILLS, 1);
        assertEquals(100_000, world.total(StatKeys.TOTAL_KILLS));
        WorldStore.Entry entry = world.milestone("world_hundred_thousand_kills");
        assertNotNull(entry);
        assertEquals(b, entry.by(), "the player whose action crossed the line is named");
        assertTrue(second.isUnlocked("world_hundred_thousand_kills"));
        assertTrue(first.isShared("world_hundred_thousand_kills"), "everyone else online shares in it");
        assertEquals(1, first.get(StatKeys.MILESTONE_WITNESSED));
        assertEquals(1, second.get(StatKeys.MILESTONE_WITNESSED));
        assertTrue(first.isUnlocked("shared_first"));
        assertTrue(world.isDirty() || !io.isEmpty(), "the server file is saved on the IO thread");
        drainIo();
        assertTrue(Files.exists(world.file()));
    }

    @Test
    void totalsStopWhenTheSettingIsOff() {
        tracker.setServerTotals(() -> false, () -> 20);
        UUID a = UUID.randomUUID();
        tracker.load(a, "Quiet");
        tracker.incrementAll(a, List.of(StatKeys.BLOCKS_BROKEN, StatKeys.ORE_MINED), 5);
        assertEquals(0, world.total(StatKeys.TOTAL_BLOCKS_BROKEN));
        world.raiseTotal(StatKeys.TOTAL_PEAK_ONLINE, 50);
        tracker.checkTotals(null);
        assertFalse(world.hasMilestone(FeatRegistry.FULL_HOUSE_ID));
    }

    @Test
    void fullHouseReadsTheSettingAndCheckTotalsNamesNobody() {
        tracker.setServerTotals(() -> true, () -> 5);
        UUID a = UUID.randomUUID();
        PlayerProgress progress = tracker.load(a, "Host");
        world.raiseTotal(StatKeys.TOTAL_PEAK_ONLINE, 4);
        tracker.checkTotals(null);
        assertFalse(world.hasMilestone(FeatRegistry.FULL_HOUSE_ID), "4 online is short of a FullHouse of 5");
        world.raiseTotal(StatKeys.TOTAL_PEAK_ONLINE, 5);
        tracker.checkTotals(null);
        WorldStore.Entry entry = world.milestone(FeatRegistry.FULL_HOUSE_ID);
        assertNotNull(entry);
        assertNull(entry.by(), "nobody is named");
        assertTrue(progress.isShared(FeatRegistry.FULL_HOUSE_ID), "and everyone online shares in it");
        assertEquals(1, progress.get(StatKeys.MILESTONE_WITNESSED));
        world.raiseTotal(StatKeys.TOTAL_SERVER_DAYS, 365);
        tracker.checkTotals(a);
        assertEquals(a, world.milestone("world_first_year").by());
        assertTrue(progress.isUnlocked("world_first_year"));
    }

    @Test
    void incrementAllMirrorsOnlyTheTotalsKeys() {
        UUID a = UUID.randomUUID();
        tracker.load(a, "Miner");
        tracker.incrementAll(a, List.of(StatKeys.BLOCKS_BROKEN, StatKeys.ORE_MINED, StatKeys.ORE_PREFIX + "Copper"), 3);
        assertEquals(3, world.total(StatKeys.TOTAL_BLOCKS_BROKEN));
        assertEquals(0, world.total(StatKeys.ORE_MINED));
        assertEquals(List.of(StatKeys.TOTAL_BLOCKS_BROKEN), List.copyOf(world.totals().keySet()));
    }

    @Test
    void aSkippedFeatSaysWhy() {
        assertNull(tracker.registry().get("world_eater"));
        assertEquals("pack GRIND is off", tracker.whySkipped("world_eater"));
        assertNull(tracker.whySkipped("company"), "the Social pack is on here");
        assertNull(tracker.whySkipped("nothing"));
    }
}
