package dev.ryvrook.feats.integration;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.atlas.api.AtlasApi;
import dev.ryvrook.atlas.api.MarkerEvent;
import dev.ryvrook.atlas.api.SessionApi;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.Hooks;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Counts the two {@code atlas:} counters of docs/EXPANSION-PLAN.md section 8.5. Loaded by name from
 * {@link SiblingLinks} once the plugin manager listed Atlas, so no Atlas class is touched on a server without
 * it. It follows Atlas through {@code AtlasApi.onEnable} and {@code onDisable}, registers a marker listener on
 * an Atlas of API version 3 or newer and a session listener on {@code SessionApi}, and removes both when Atlas
 * stops.
 *
 * <p>{@code atlas:markers} counts one per marker a player placed on the in game map, so an ADDED event with an
 * author on a layer other than {@code atlas:admin}. {@code atlas:linked} is set to 1 when a player redeems a
 * link code for the web map, a REDEEMED session event. Atlas publishes the marker API a moment before the
 * session API when it starts after Feats, so a missing session API is tried again a few times on the scheduler.
 * Listener threads are Atlas' and the tracker is safe from any thread. Every call into Atlas is guarded and the
 * first failure is logged once.
 */
public final class AtlasFeats implements AutoCloseable {
    static final String MARKERS = StatKeys.ATLAS_PREFIX + "markers";
    static final String LINKED = StatKeys.ATLAS_PREFIX + "linked";
    /** The layer of the server markers placed through /atlas marker, which do not count. */
    static final String ADMIN_LAYER = "atlas:admin";
    /** The first AtlasApi version with onMarker. */
    static final int MARKER_API_VERSION = 3;
    static final long SESSION_RETRY_SECONDS = 2;
    static final int SESSION_RETRIES = 15;

    private final FeatsTracker tracker;
    private final HytaleLogger logger;
    private final ScheduledExecutorService scheduler;
    private final Consumer<AtlasApi> enable = this::bind;
    private final Runnable disable = this::unbind;
    private final AtomicBoolean warned = new AtomicBoolean();
    private final AtomicBoolean oldAtlasWarned = new AtomicBoolean();

    private volatile AtlasApi api;
    private volatile Runnable markerStop;
    private volatile Runnable sessionStop;
    private volatile ScheduledFuture<?> sessionRetry;
    private volatile boolean closed;
    /** Held across bind, the session retries, unbind and close. */
    private final Object bindLock = new Object();

    AtlasFeats(FeatsTracker tracker, HytaleLogger logger, ScheduledExecutorService scheduler) {
        this.tracker = tracker;
        this.logger = logger;
        this.scheduler = scheduler;
    }

    /** Called by name from {@link SiblingLinks}. */
    public static AtlasFeats install(Hooks.Context ctx, ScheduledExecutorService scheduler) {
        return install(ctx.tracker(), ctx.logger(), scheduler);
    }

    static AtlasFeats install(FeatsTracker tracker, HytaleLogger logger, ScheduledExecutorService scheduler) {
        AtlasFeats feats = new AtlasFeats(tracker, logger, scheduler);
        AtlasApi.onDisable(feats.disable);
        AtlasApi.onEnable(feats.enable);
        return feats;
    }

    /** True while Atlas is up and linked. */
    public boolean bound() {
        return api != null;
    }

    /** True while the marker listener is registered. */
    boolean countingMarkers() {
        return markerStop != null;
    }

    /** True while the session listener is registered. */
    boolean countingLinks() {
        return sessionStop != null;
    }

    /** True for a marker a player placed on the in game map, the only kind Surveyor's Mark counts. */
    static boolean countsMarker(MarkerEvent e) {
        return e != null && e.kind() == MarkerEvent.Kind.ADDED && e.player() != null && !ADMIN_LAYER.equals(e.layer());
    }

    /**
     * The Atlas enable hook. Bind, the session retries, unbind and close share one lock, so a retry cannot set
     * a session listener after an unbind ran. The close sets {@code closed} before it waits for the lock, so a
     * bind that was past the first check sees it after registering and drops what it registered.
     */
    private void bind(AtlasApi live) {
        Runnable late;
        synchronized (bindLock) {
            if (closed) {
                return;
            }
            api = live;
            try {
                int version = live.version();
                if (version >= MARKER_API_VERSION) {
                    Runnable old = markerStop;
                    markerStop = null;
                    stop(old);
                    markerStop = live.onMarker(this::marker);
                } else if (oldAtlasWarned.compareAndSet(false, true)) {
                    logger.at(Level.WARNING).log("Atlas API version %d has no marker events, so Surveyor's Mark cannot count. Atlas 1.9.0 or newer has them", version);
                }
            } catch (RuntimeException | LinkageError e) {
                warn(e);
            }
            bindSessions(live, 0);
            late = closed ? drop() : null;
        }
        if (late != null) {
            late.run();
        }
    }

    /**
     * Registers the session listener, or tries again shortly when Atlas has not published the session API yet.
     * A retry from an earlier bind finds another api, or none, and does nothing.
     */
    private void bindSessions(AtlasApi live, int attempt) {
        Runnable late;
        synchronized (bindLock) {
            if (closed || api == null || api != live) {
                return;
            }
            try {
                SessionApi sessions = SessionApi.get();
                if (sessions == null) {
                    if (attempt < SESSION_RETRIES && scheduler != null) {
                        sessionRetry = scheduler.schedule(() -> bindSessions(live, attempt + 1), SESSION_RETRY_SECONDS, TimeUnit.SECONDS);
                    } else if (warned.compareAndSet(false, true)) {
                        logger.at(Level.WARNING).log("Atlas published no session API, so On the Map cannot count until Atlas is reloaded");
                    }
                    return;
                }
                Runnable old = sessionStop;
                sessionStop = null;
                stop(old);
                sessionStop = sessions.onChange(this::session);
            } catch (RuntimeException | LinkageError e) {
                warn(e);
            }
            late = closed ? drop() : null;
        }
        if (late != null) {
            late.run();
        }
    }

    /** The Atlas disable hook. Drops the api, the retry and both listeners. */
    private void unbind() {
        Runnable handles;
        synchronized (bindLock) {
            handles = drop();
        }
        handles.run();
    }

    /**
     * Clears the binding under the lock and hands back what is left to stop, which the caller runs after the
     * lock is released, so no Atlas call runs while close holds it.
     */
    private Runnable drop() {
        api = null;
        ScheduledFuture<?> retry = sessionRetry;
        sessionRetry = null;
        Runnable markers = markerStop;
        markerStop = null;
        Runnable sessions = sessionStop;
        sessionStop = null;
        return () -> {
            if (retry != null) {
                retry.cancel(false);
            }
            stop(markers);
            stop(sessions);
        };
    }

    private void stop(Runnable handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.run(); // Atlas drops its listeners when it stops, so this is usually a no op
        } catch (RuntimeException | LinkageError ignored) {
            // Atlas is already gone
        }
    }

    private void marker(MarkerEvent e) {
        try {
            if (countsMarker(e)) {
                tracker.addOffline(e.player(), MARKERS, 1);
            }
        } catch (RuntimeException | LinkageError ex) {
            warn(ex);
        }
    }

    private void session(SessionApi.SessionEvent e) {
        try {
            if (e == null || e.kind() != SessionApi.SessionEvent.Kind.REDEEMED || e.uuid() == null) {
                return;
            }
            UUID uuid = e.uuid();
            if (tracker.loadedProgress(uuid) != null) {
                tracker.raiseTo(uuid, LINKED, 1);
            } else {
                tracker.addOffline(uuid, LINKED, 1); // a code can be redeemed in a browser while the player is offline
            }
        } catch (RuntimeException | LinkageError ex) {
            warn(ex);
        }
    }

    private void warn(Throwable e) {
        if (warned.compareAndSet(false, true)) {
            logger.at(Level.WARNING).withCause(e).log("Feats could not read an Atlas event, so an Atlas feat may miss it");
        }
    }

    /** Removes the hooks, for the Feats shutdown. */
    @Override
    public void close() {
        closed = true; // before the lock, so a bind or retry holding it sees the close once it registered
        unbind();
        try {
            AtlasApi.removeHooks(enable, disable);
        } catch (RuntimeException | LinkageError ignored) {
            // Atlas is already gone
        }
    }
}
