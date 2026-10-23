package dev.ryvrook.feats.integration;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Pack;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.UnlockNotifier;
import dev.ryvrook.trackerapi.api.Header;
import dev.ryvrook.trackerapi.api.event.ModMilestone;
import dev.ryvrook.trackerapi.api.event.ModTally;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ModTally rules of {@link TallyKinds} against TrackerAPI 1.3.0's CounterSink, and the keys it leaves in the player file. */
class TallyKindsTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final List<String> writes = new ArrayList<>();
    private final UUID p = UUID.randomUUID();
    private final TallyKinds tally = new TallyKinds(new ModKinds.Sink() {
        @Override
        public void add(UUID player, String key, long amount) {
            assertEquals(p, player);
            writes.add("add " + key + " " + amount);
        }

        @Override
        public void raise(UUID player, String key, long value) {
            assertEquals(p, player);
            writes.add("raise " + key + " " + value);
        }

        @Override
        public void discover(UUID player, String prefix, String id, String total) {
            writes.add("mark " + prefix + id + " " + total);
        }
    });

    @AfterAll
    static void restore() {
        new FeatRegistry(false);
    }

    private Header h(UUID who) {
        return new Header(System.currentTimeMillis(), 1, "default", who);
    }

    @Test
    void anAmountAddsTheTotalAndThePerIdSum() {
        tally.onTally(new ModTally(h(p), "fish", "caught", "Legendary_Whale", 1, 0));
        assertEquals(List.of("add fish:caught 1", "add fish:caught:Legendary_Whale 1"), writes);
        writes.clear();
        tally.onTally(new ModTally(h(p), "arcanum", "energy", null, 250, 250));
        assertEquals(List.of("add arcanum:energy 250", "raise arcanum:energy_best 250"), writes, "an amount and a best at once");
        writes.clear();
        tally.onTally(new ModTally(h(p), "melodies", "seconds", "", 30, 0));
        assertEquals(List.of("add melodies:seconds 30"), writes, "an empty id writes only the total");
    }

    @Test
    void aBestOnlyRaisesTheBestKey() {
        tally.onTally(new ModTally(h(p), "melodies", "band", null, 0, 4));
        tally.onTally(new ModTally(h(p), "fish", "perfect_streak", "ignored", 0, 5));
        assertEquals(List.of("raise melodies:band_best 4", "raise fish:perfect_streak_best 5"), writes,
                "a best never touches the per id key");
    }

    @Test
    void nothingWithoutAPlayerAModOrAStatOrANumber() {
        tally.onTally(new ModTally(h(null), "fish", "caught", "x", 1, 1));
        tally.onTally(new ModTally(h(p), null, "caught", "x", 1, 1));
        tally.onTally(new ModTally(h(p), "fish", "", "x", 1, 1));
        tally.onTally(new ModTally(h(p), "fish", "caught", "x", 0, 0));
        tally.onTally(new ModTally(h(p), "fish", "caught", "x", -3, -1));
        assertTrue(writes.isEmpty(), writes.toString());
    }

    @Test
    void theTrackerKeepsWatchedPerIdSumsAndEveryMarker() throws IOException {
        FeatRegistry registry = new FeatRegistry(EnumSet.allOf(Sibling.class), EnumSet.noneOf(Pack.class), FeatRegistry.TALLY_MINIMUM);
        ProgressStore store = new ProgressStore(Files.createTempDirectory("feats-tally"), LOGGER);
        FeatsTracker tracker = new FeatsTracker(registry, store, new UnlockNotifier(() -> null, LOGGER), LOGGER, Runnable::run);
        UUID player = UUID.randomUUID();
        var progress = tracker.load(player, "Angler");
        TallyKinds live = new TallyKinds(ModKinds.sink(tracker));
        live.onTally(new ModTally(h(player), "fish", "caught", "Legendary_Whale", 1, 0));
        live.onTally(new ModTally(h(player), "fish", "caught", "Common_Minnow", 2, 0));
        live.onTally(new ModTally(h(player), "fish", "perfect_streak", null, 0, 3));
        live.onTally(new ModTally(h(player), "fish", "perfect_streak", null, 0, 2));
        assertEquals(3, progress.get("fish:caught"));
        assertEquals(1, progress.get("fish:caught:Legendary_Whale"));
        assertEquals(3, progress.get("fish:perfect_streak_best"), "a best never goes down");
        assertTrue(tracker.isPersisted("fish:caught:Legendary_Whale"), "Ocean Legends reads it");
        assertFalse(tracker.isPersisted("fish:caught:Common_Minnow"), "no feat reads the minnow");
        for (String perId : List.of("fish:caught:x", "fish:junk:x", "fish:rarity:legendary", "fish:grade:perfect",
                "arcanum:abilities:fireball", "arcanum:failed:cast", "melodies:plays:Castle", "melodies:finished:Castle",
                "melodies:seconds:lute")) {
            assertTrue(StatKeys.isPerId(perId), perId);
        }
        for (String marker : List.of("fish:species:Common_Minnow", "fish:page:Rare_Salmon", "fish:zone:Ocean",
                "arcanum:rune:arcanum:damage", "melodies:song:Castle", "melodies:instrument:lute", "fish:species",
                "fish:pages", "fish:zones", "arcanum:runes", "melodies:songs", "melodies:instruments", "fish:grade_best",
                "arcanum:energy_best", "melodies:band_best", "melodies:band")) {
            assertFalse(StatKeys.isPerId(marker), marker);
            assertTrue(tracker.isPersisted(marker), marker + " is a total, a best or a marker");
        }
        // The discoveries arrive through ModKinds as ModMilestone, with TrackerAPI's plural for the total.
        ModKinds kinds = new ModKinds(ModKinds.sink(tracker));
        kinds.onMilestone(new ModMilestone(h(player), "fish", "species", "Common_Minnow", null));
        kinds.onMilestone(new ModMilestone(h(player), "fish", "species", "Common_Minnow", null));
        kinds.onMilestone(new ModMilestone(h(player), "fish", "page", "Rare_Salmon", null));
        assertEquals(1, progress.get("fish:species"), "species is its own plural, a sighting counts once");
        assertEquals(1, progress.get("fish:pages"));
    }
}
