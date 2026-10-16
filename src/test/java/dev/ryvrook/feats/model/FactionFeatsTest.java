package dev.ryvrook.feats.model;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.UnlockNotifier;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionFeatsTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Dominion's integration StatKeys.ALL, the ten from 0.5.1 and the fifteen 0.7.1 adds for the Factions tab
     * additions in docs/EXPANSION-PLAN.md section 8.3. A rename on either side shows up here.
     */
    private static final Set<String> DOMINION_KEYS = Set.of("dominion:claims", "dominion:unclaims",
            "dominion:enemy_kills", "dominion:enemy_land_deaths", "dominion:sieges_won", "dominion:sieges_lost",
            "dominion:missions", "dominion:bank_deposited", "dominion:factions_founded", "dominion:invites",
            "dominion:joined", "dominion:kicked", "dominion:wars_declared", "dominion:alliances", "dominion:upgrades",
            "dominion:upgrade_level_max", "dominion:market_trades", "dominion:foreign_entries", "dominion:sieges_defended",
            "dominion:breaches", "dominion:hill_wins", "dominion:outposts_held", "dominion:power_max",
            "dominion:rank_changes", "dominion:tutorial_done");

    @Test
    void theFactionsTabOnlyExistsWithDominion() {
        FeatRegistry without = new FeatRegistry(false);
        FeatRegistry with = new FeatRegistry(true);
        assertTrue(without.inCategory(Category.FACTIONS).isEmpty());
        assertEquals(30, with.inCategory(Category.FACTIONS).size(), "15 from 1.6.0, 14 from 1.7.2 and 1 from 1.7.3");
        assertEquals(without.count() + 30, with.count());
        assertEquals(without.totalPoints() + 460 + 430 + 10, with.totalPoints());
    }

    @Test
    void everyFactionFeatReadsACounterDominionSends() {
        for (Feat feat : new FeatRegistry(true).inCategory(Category.FACTIONS)) {
            assertTrue(feat.isPersonal(), feat.id());
            for (Requirement r : feat.requirements()) {
                assertTrue(DOMINION_KEYS.contains(r.key()), feat.id() + " reads " + r.key());
            }
        }
    }

    @Test
    void aMirroredClaimUnlocksStakedClaim() throws Exception {
        FeatRegistry registry = new FeatRegistry(true);
        ProgressStore store = new ProgressStore(Files.createTempDirectory("feats-factions"), LOGGER);
        FeatsTracker tracker = new FeatsTracker(registry, store, new UnlockNotifier(() -> null, LOGGER), LOGGER,
                Runnable::run);
        UUID uuid = UUID.randomUUID();
        PlayerProgress progress = tracker.load(uuid, "Warden");
        assertTrue(tracker.isPersisted(StatKeys.DOMINION_CLAIMS));
        assertTrue(tracker.isWatched(StatKeys.DOMINION_CLAIMS));
        assertTrue(tracker.isWatched("dominion:unclaims"), "Downsizing reads it since 1.7.2");
        tracker.increment(uuid, StatKeys.DOMINION_CLAIMS, 1);
        assertTrue(progress.isUnlocked("staked_claim"));
        assertFalse(progress.isUnlocked("landholder"));
        tracker.increment(uuid, StatKeys.DOMINION_CLAIMS, 49);
        assertTrue(progress.isUnlocked("landholder"));
        for (String id : List.of("staked_claim", "landholder")) {
            assertEquals(Category.FACTIONS, registry.get(id).category());
        }
        new FeatRegistry(false); // put the shared feat predicates back to the plain list for the other tests
    }

    @Test
    void aRankChangeFromDominionUnlocksNewRank() throws Exception {
        FeatRegistry registry = new FeatRegistry(true);
        ProgressStore store = new ProgressStore(Files.createTempDirectory("feats-rank"), LOGGER);
        FeatsTracker tracker = new FeatsTracker(registry, store, new UnlockNotifier(() -> null, LOGGER), LOGGER,
                Runnable::run);
        UUID uuid = UUID.randomUUID();
        PlayerProgress progress = tracker.load(uuid, "Warden");
        assertTrue(tracker.isPersisted(StatKeys.DOMINION_RANK_CHANGES));
        assertTrue(tracker.isWatched(StatKeys.DOMINION_RANK_CHANGES));
        assertFalse(progress.isUnlocked("faction_rank"));
        tracker.increment(uuid, StatKeys.DOMINION_RANK_CHANGES, 1);
        assertTrue(progress.isUnlocked("faction_rank"));
        assertEquals(Category.FACTIONS, registry.get("faction_rank").category());
        assertFalse(progress.isUnlocked("promoted"), "the permission group feat in Misc is a different one");
        new FeatRegistry(false);
    }

    @Test
    void removingDominionParksTheFactionFeatsAndAddingItBackRestoresThem() throws Exception {
        java.nio.file.Path dir = Files.createTempDirectory("feats-factions-gone");
        UUID uuid = UUID.randomUUID();
        FeatRegistry with = new FeatRegistry(true);
        FeatsTracker before = new FeatsTracker(with, new ProgressStore(dir, LOGGER),
                new UnlockNotifier(() -> null, LOGGER), LOGGER, Runnable::run);
        PlayerProgress progress = before.load(uuid, "Warden");
        before.increment(uuid, StatKeys.DOMINION_CLAIMS, 1);
        before.increment(uuid, "deaths", 1);
        int withPoints = before.points(progress);
        int withCount = progress.unlockedCount();
        before.unload(uuid);

        FeatRegistry without = new FeatRegistry(false); // the next start, Dominion removed
        FeatsTracker after = new FeatsTracker(without, new ProgressStore(dir, LOGGER),
                new UnlockNotifier(() -> null, LOGGER), LOGGER, Runnable::run);
        PlayerProgress back = after.load(uuid, "Warden");
        assertTrue(back.isUnlocked("staked_claim"), "the unlock stays in the file");
        assertEquals(1, back.get(StatKeys.DOMINION_CLAIMS), "and so does the counter");
        assertEquals(withCount - 1, back.unlockedCount(), "but it is not counted");
        assertEquals(withPoints - with.get("staked_claim").tier().points(), after.points(back));
        after.unload(uuid);

        FeatRegistry again = new FeatRegistry(true);
        FeatsTracker readded = new FeatsTracker(again, new ProgressStore(dir, LOGGER),
                new UnlockNotifier(() -> null, LOGGER), LOGGER, Runnable::run);
        PlayerProgress restored = readded.load(uuid, "Warden");
        assertEquals(withCount, restored.unlockedCount());
        assertEquals(withPoints, readded.points(restored));
        new FeatRegistry(false);
    }
}
