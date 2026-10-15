package dev.ryvrook.feats.track;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.stats.StatKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** API counter adds for offline players, the way Dominion credits a siege to members who logged off. */
class OfflineAddsTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** The IO thread, run by hand so each test decides when the queued work happens. */
    private final Deque<Runnable> io = new ArrayDeque<>();
    private ProgressStore store;
    private FeatsTracker tracker;

    @BeforeEach
    void setUp() throws Exception {
        store = new ProgressStore(Files.createTempDirectory("feats-offline"), LOGGER);
        tracker = new FeatsTracker(new FeatRegistry(true), store, new UnlockNotifier(() -> null, LOGGER), LOGGER,
                io::add);
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

    private UUID knownOfflinePlayer() {
        UUID uuid = UUID.randomUUID();
        assertTrue(store.createIfAbsent(uuid));
        return uuid;
    }

    @Test
    void addsForAnOfflinePlayerAreMergedAndWrittenIntoTheirFile() {
        UUID uuid = knownOfflinePlayer();
        assertTrue(tracker.addOffline(uuid, StatKeys.DOMINION_SIEGES_WON, 1));
        assertTrue(tracker.addOffline(uuid, StatKeys.DOMINION_SIEGES_WON, 2));
        assertTrue(tracker.addOffline(uuid, StatKeys.DOMINION_MISSIONS, 1));
        assertEquals(1, io.size(), "one write per player, however many adds wait");
        assertEquals(1, tracker.offlineAddsWaiting());
        drainIo();
        assertEquals(0, tracker.offlineAddsWaiting());
        PlayerProgress onDisk = store.load(uuid, null);
        assertEquals(3, onDisk.get(StatKeys.DOMINION_SIEGES_WON));
        assertEquals(1, onDisk.get(StatKeys.DOMINION_MISSIONS));
        assertTrue(onDisk.isUnlocked("victory_banner"));
        assertTrue(onDisk.isUnlocked("orders_received"));
        assertFalse(tracker.isLoaded(uuid), "the player stays offline");
    }

    @Test
    void aPlayerWithoutAFileGetsNothing() {
        UUID stranger = UUID.randomUUID();
        assertTrue(tracker.addOffline(stranger, StatKeys.DOMINION_MISSIONS, 1));
        drainIo();
        assertFalse(store.exists(stranger));
        assertEquals(0, tracker.offlineAddsWaiting());
    }

    @Test
    void aPlayerWhoJoinsBeforeTheWriteGetsTheAddsOnTheLiveCopy() {
        UUID uuid = knownOfflinePlayer();
        tracker.addOffline(uuid, StatKeys.DOMINION_CLAIMS, 1);
        PlayerProgress live = tracker.load(uuid, "Late");
        tracker.increment(uuid, StatKeys.DOMINION_CLAIMS, 1);
        drainIo();
        assertEquals(2, live.get(StatKeys.DOMINION_CLAIMS), "the queued add and the live one both count, once each");
        assertTrue(live.isUnlocked("staked_claim"));
        assertTrue(tracker.isLoaded(uuid));
    }

    @Test
    void anOnlinePlayerIsCountedAtOnce() {
        UUID uuid = knownOfflinePlayer();
        PlayerProgress live = tracker.load(uuid, "Here");
        assertTrue(tracker.addOffline(uuid, StatKeys.DOMINION_CLAIMS, 1));
        assertTrue(io.isEmpty());
        assertEquals(1, live.get(StatKeys.DOMINION_CLAIMS));
    }

    @Test
    void anAddArrivingAfterTheWriteStartsANewBatch() {
        UUID uuid = knownOfflinePlayer();
        tracker.addOffline(uuid, StatKeys.DOMINION_MISSIONS, 1);
        drainIo();
        tracker.addOffline(uuid, StatKeys.DOMINION_MISSIONS, 4);
        assertEquals(1, io.size());
        drainIo();
        assertEquals(5, store.load(uuid, null).get(StatKeys.DOMINION_MISSIONS));
    }
}
