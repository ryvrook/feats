package dev.ryvrook.feats.integration;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.atlas.api.AtlasApi;
import dev.ryvrook.atlas.api.MarkerEvent;
import dev.ryvrook.atlas.api.MarkerLayer;
import dev.ryvrook.atlas.api.RenderStatus;
import dev.ryvrook.atlas.api.SessionApi;
import dev.ryvrook.atlas.api.WorldStatus;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.UnlockNotifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives AtlasFeats through stand in marker and session APIs and checks atlas:markers and atlas:linked. */
class AtlasFeatsTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static FeatRegistry registry;

    private FeatsTracker tracker;
    private Markers atlas;
    private Sessions sessions;
    private AtlasFeats feats;
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
        ProgressStore store = new ProgressStore(Files.createTempDirectory("feats-atlas"), LOGGER);
        tracker = new FeatsTracker(registry(), store, new UnlockNotifier(() -> null, LOGGER), LOGGER, Runnable::run);
        p = UUID.randomUUID();
        progress = tracker.load(p, "Mapper");
        atlas = new Markers(3);
        sessions = new Sessions();
    }

    @AfterEach
    void tearDown() {
        if (feats != null) {
            feats.close();
        }
        sessions.stop();
        atlas.stop();
    }

    /** Atlas start order, the session API is published right after the marker API's enable hooks ran. */
    private void startAtlas() {
        sessions.start();
        atlas.start();
    }

    private static MarkerEvent marker(MarkerEvent.Kind kind, UUID player, String layer) {
        return new MarkerEvent(kind, player, "default", layer, "m1", 10, 20, 0);
    }

    @Test
    void countsSharedMarkersPlayersPlaced() {
        startAtlas();
        feats = AtlasFeats.install(tracker, LOGGER, null);
        assertTrue(feats.countingMarkers());
        atlas.fire(marker(MarkerEvent.Kind.ADDED, p, "shared"));
        atlas.fire(marker(MarkerEvent.Kind.ADDED, p, "shared"));
        atlas.fire(marker(MarkerEvent.Kind.REMOVED, p, "shared"));
        atlas.fire(marker(MarkerEvent.Kind.ADDED, null, "shared"));
        atlas.fire(marker(MarkerEvent.Kind.ADDED, p, AtlasFeats.ADMIN_LAYER));
        assertEquals(2, progress.get(AtlasFeats.MARKERS), "removals, server markers and the admin layer do not count");
    }

    @Test
    void anOldAtlasLinksSessionsButNotMarkers() {
        atlas = new Markers(2);
        startAtlas();
        feats = AtlasFeats.install(tracker, LOGGER, null);
        assertTrue(feats.bound());
        assertFalse(feats.countingMarkers(), "onMarker is not called below API version 3");
        assertEquals(0, atlas.listeners.size());
        assertTrue(feats.countingLinks());
    }

    @Test
    void aRedeemedCodeSetsLinked() {
        startAtlas();
        feats = AtlasFeats.install(tracker, LOGGER, null);
        sessions.fire(new SessionApi.SessionEvent(SessionApi.SessionEvent.Kind.ADOPTED, "h", p));
        sessions.fire(new SessionApi.SessionEvent(SessionApi.SessionEvent.Kind.REVOKED, "h", p));
        assertEquals(0, progress.get(AtlasFeats.LINKED));
        sessions.fire(new SessionApi.SessionEvent(SessionApi.SessionEvent.Kind.REDEEMED, "h", p));
        sessions.fire(new SessionApi.SessionEvent(SessionApi.SessionEvent.Kind.REDEEMED, "h2", p));
        assertEquals(1, progress.get(AtlasFeats.LINKED), "linked is a flag, a second browser keeps it at 1");
    }

    @Test
    void followsAtlasThroughAReloadAndLetsGoOnClose() {
        feats = AtlasFeats.install(tracker, LOGGER, null);
        assertFalse(feats.bound());
        startAtlas();
        assertTrue(feats.bound(), "an Atlas that starts after Feats is picked up");
        assertEquals(1, atlas.listeners.size());
        assertEquals(1, sessions.listeners.size());

        sessions.stop();
        atlas.stop();
        assertFalse(feats.bound());
        assertEquals(0, atlas.listeners.size(), "both listeners are removed on disable");
        assertEquals(0, sessions.listeners.size());

        startAtlas();
        assertTrue(feats.countingMarkers());
        assertTrue(feats.countingLinks());
        atlas.fire(marker(MarkerEvent.Kind.ADDED, p, "shared"));
        assertEquals(1, progress.get(AtlasFeats.MARKERS));

        feats.close();
        assertEquals(0, atlas.listeners.size());
        assertEquals(0, sessions.listeners.size());
        sessions.stop();
        atlas.stop();
        startAtlas();
        assertFalse(feats.bound(), "a closed binding stays away");
    }

    @Test
    void aCloseWhileTheSessionListenerRegistersLeavesNothingBehind() {
        feats = AtlasFeats.install(tracker, LOGGER, null);
        sessions.onRegister = () -> feats.close(); // the shutdown arrives while the enable hook registers
        startAtlas();
        assertFalse(feats.bound());
        assertFalse(feats.countingLinks(), "the session listener set after the close is dropped again");
        assertEquals(0, sessions.listeners.size());
        assertEquals(0, atlas.listeners.size());
    }

    @Test
    void aThrowingListenerCallIsSwallowed() {
        startAtlas();
        feats = AtlasFeats.install(tracker, LOGGER, null);
        atlas.fire(null);
        sessions.fire(null);
        atlas.fire(marker(MarkerEvent.Kind.ADDED, p, "shared"));
        assertEquals(1, progress.get(AtlasFeats.MARKERS));
    }

    /** A marker API of the given version that hands events to its listeners. */
    private static final class Markers extends AtlasApi {
        final List<Consumer<MarkerEvent>> listeners = new CopyOnWriteArrayList<>();
        private final int version;
        private boolean running;

        Markers(int version) {
            this.version = version;
        }

        void start() {
            running = true;
            enable(this);
        }

        void stop() {
            if (running) {
                running = false;
                disable(this);
                listeners.clear();
            }
        }

        void fire(MarkerEvent e) {
            for (Consumer<MarkerEvent> l : listeners) {
                l.accept(e);
            }
        }

        @Override
        public int version() {
            return version;
        }

        @Override
        public Runnable onMarker(Consumer<MarkerEvent> listener) {
            if (version < 3) {
                throw new AbstractMethodError("onMarker");
            }
            listeners.add(listener);
            return () -> listeners.remove(listener);
        }

        @Override
        public MarkerLayer layer(String pluginId, String layerId) {
            return null;
        }

        @Override
        public List<MarkerLayer> layers() {
            return List.of();
        }

        @Override
        public List<MarkerLayer> layers(String world) {
            return List.of();
        }

        @Override
        public boolean removeLayer(String pluginId, String layerId) {
            return false;
        }

        @Override
        public void setHidden(UUID player, boolean hidden, String reason) {
        }

        @Override
        public boolean isHidden(UUID player) {
            return false;
        }

        @Override
        public String publicUrl() {
            return "http://localhost/";
        }

        @Override
        public boolean publicUrlSet() {
            return false;
        }

        @Override
        public RenderStatus renderStatus() {
            return null;
        }

        @Override
        public List<WorldStatus> worlds() {
            return List.of();
        }
    }

    /** A session API that hands events to its listeners. */
    private static final class Sessions extends SessionApi {
        final List<Consumer<SessionEvent>> listeners = new CopyOnWriteArrayList<>();
        private boolean running;
        /** Runs inside onChange, so a test can close Feats in the middle of a bind. */
        Runnable onRegister;

        void start() {
            running = true;
            enable(this);
        }

        void stop() {
            if (running) {
                running = false;
                disable(this);
                listeners.clear();
            }
        }

        void fire(SessionEvent e) {
            for (Consumer<SessionEvent> l : listeners) {
                l.accept(e);
            }
        }

        @Override
        public Runnable onChange(Consumer<SessionEvent> listener) {
            assertNotNull(listener);
            listeners.add(listener);
            Runnable hook = onRegister;
            onRegister = null;
            if (hook != null) {
                hook.run();
            }
            return () -> listeners.remove(listener);
        }

        @Override
        public String issueCode(UUID uuid, String name) {
            return "ABC123";
        }

        @Override
        public Linked redeem(String code) {
            return null;
        }

        @Override
        public Session resolve(String tokenHash) {
            return null;
        }

        @Override
        public boolean revoke(String tokenHash) {
            return false;
        }

        @Override
        public void adopt(Session session, String tokenHash) {
        }

        @Override
        public Set<UUID> hidden() {
            return Set.of();
        }
    }
}
