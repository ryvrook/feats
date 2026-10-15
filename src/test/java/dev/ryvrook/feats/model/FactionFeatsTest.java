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

    /** Dominion's integration StatKeys.ALL in 0.5.1. A rename on either side shows up here. */
    private static final Set<String> DOMINION_KEYS = Set.of("dominion:claims", "dominion:unclaims",
            "dominion:enemy_kills", "dominion:enemy_land_deaths", "dominion:sieges_won", "dominion:sieges_lost",
            "dominion:missions", "dominion:bank_deposited", "dominion:factions_founded", "dominion:invites");

    @Test
    void theFactionsTabOnlyExistsWithDominion() {
        FeatRegistry without = new FeatRegistry(false);
        FeatRegistry with = new FeatRegistry(true);
        assertTrue(without.inCategory(Category.FACTIONS).isEmpty());
        assertEquals(15, with.inCategory(Category.FACTIONS).size());
        assertEquals(without.count() + 15, with.count());
        assertEquals(without.totalPoints() + 460, with.totalPoints());
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
        assertFalse(tracker.isWatched("dominion:unclaims"));
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
}
