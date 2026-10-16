package dev.ryvrook.feats.integration;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.Hooks;
import dev.ryvrook.feats.track.UnlockNotifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which bindings SiblingLinks starts for which siblings, and that close lets go of them. */
class SiblingLinksTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private FeatsTracker tracker;
    private ScheduledThreadPoolExecutor scheduler;
    private CoinageFeatsTest.StandIn coinage;

    @BeforeEach
    void setUp() throws IOException {
        ProgressStore store = new ProgressStore(Files.createTempDirectory("feats-links"), LOGGER);
        FeatRegistry registry = new FeatRegistry(EnumSet.allOf(Sibling.class), Set.of());
        tracker = new FeatsTracker(registry, store, new UnlockNotifier(() -> null, LOGGER), LOGGER, Runnable::run);
        scheduler = new ScheduledThreadPoolExecutor(1);
        coinage = new CoinageFeatsTest.StandIn();
    }

    @AfterEach
    void tearDown() {
        coinage.stop();
        scheduler.shutdownNow();
    }

    private Hooks.Context ctx(Set<Sibling> siblings) {
        return new Hooks.Context(null, tracker, null, null, LOGGER, null, siblings, false, null);
    }

    @Test
    void noSiblingsNoLinks() {
        try (SiblingLinks links = SiblingLinks.start(ctx(Set.of()), scheduler)) {
            assertTrue(links.linked().isEmpty());
        }
        try (SiblingLinks links = SiblingLinks.start(null, scheduler)) {
            assertTrue(links.linked().isEmpty(), "no context starts nothing and never throws");
        }
        try (SiblingLinks links = SiblingLinks.start(ctx(Set.of(Sibling.DOMINION, Sibling.TRACKER_API)), scheduler)) {
            assertTrue(links.linked().isEmpty(), "Dominion and TrackerAPI have bindings of their own");
        }
    }

    @Test
    void eachInstalledSiblingGetsItsBindingInOrder() {
        try (SiblingLinks links = SiblingLinks.start(ctx(EnumSet.of(Sibling.ATLAS, Sibling.CHRONICLE, Sibling.COINAGE)), scheduler)) {
            assertEquals(List.of("Coinage", "Chronicle Stats", "Atlas"), links.linked());
        }
    }

    @Test
    void aBindingThatFailsToStartLeavesTheOthersRunning() {
        // Chronicle needs the scheduler for its poll, so without one only it fails
        try (SiblingLinks links = SiblingLinks.start(ctx(EnumSet.of(Sibling.CHRONICLE, Sibling.ATLAS)), (ScheduledExecutorService) null)) {
            assertEquals(List.of("Atlas"), links.linked());
        }
    }

    @Test
    void theCoinageBindingFollowsCoinageAndCloseLetsGo() {
        SiblingLinks links = SiblingLinks.start(ctx(EnumSet.of(Sibling.COINAGE)), scheduler);
        assertEquals(List.of("Coinage"), links.linked());
        coinage.start();
        assertEquals("Feats", coinage.owner, "the binding subscribed when Coinage started");
        assertTrue(coinage.subscription.isOpen());
        links.close();
        assertTrue(links.linked().isEmpty());
        assertTrue(!coinage.subscription.isOpen(), "close ended the subscription");
        coinage.stop();
        coinage.owner = null;
        coinage.start();
        assertNull(coinage.owner, "the hooks are gone, so a Coinage that starts again sees no Feats");
    }
}
