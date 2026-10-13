package dev.ryvrook.feats.track;

import com.hypixel.hytale.builtin.portals.resources.PortalWorld;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.portalworld.PortalType;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.systems.DeepworksRuns;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Counts entries into portal worlds such as the Goblin Breach and The Deepworks. Every world entry
 * fires {@link AddPlayerToWorldEvent}, and portal worlds carry a {@link PortalWorld} resource that
 * names their portal type. If the portal classes ever change shape the tracker switches itself off
 * with one warning instead of failing on every world change.
 */
public final class PortalTracker {
    private final FeatsTracker tracker;
    private final HytaleLogger logger;
    private static final long QUIET_NANOS = TimeUnit.MINUTES.toNanos(1);

    private volatile boolean disabled;
    private final AtomicLong lastSkipLog = new AtomicLong(System.nanoTime() - QUIET_NANOS);

    public PortalTracker(FeatsTracker tracker, HytaleLogger logger) {
        this.tracker = tracker;
        this.logger = logger;
    }

    public void onAddPlayerToWorld(AddPlayerToWorldEvent event) {
        if (disabled) {
            return;
        }
        try {
            World world = event.getWorld();
            Holder<EntityStore> holder = event.getHolder();
            if (world == null || holder == null) {
                return;
            }
            PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
            if (playerRef == null) {
                return;
            }
            UUID uuid = playerRef.getUuid();
            if (!tracker.isLoaded(uuid)) {
                return; // first join of the session, PlayerReady has not loaded the player yet
            }
            PortalWorld portal = world.getEntityStore().getStore().getResource(PortalWorld.getResourceType());
            PortalType type = portal != null && portal.exists() ? portal.getPortalType() : null;
            String typeId = type != null ? type.getId() : null;
            // Entering a Deepworks instance tells the deathless run tracker which run the player is on.
            DeepworksRuns.of(tracker).entered(uuid, typeId, world.getName());
            if (portal == null || !portal.exists()) {
                return;
            }
            tracker.incrementAll(uuid, Classify.portal(typeId), 1);
            tracker.discover(uuid, StatKeys.PORTAL_TYPE_PREFIX, typeId, StatKeys.PORTAL_TYPES);
        } catch (LinkageError e) {
            disable(e); // the API this tracker reads is missing on this server
        } catch (RuntimeException e) {
            skip(e); // one player or one world misbehaved, carry on with the rest
        }
    }

    /**
     * A single failed read, for example a world that stopped taking tasks while it closed. Skips this
     * player this time and logs at most once a minute at FINE.
     */
    private void skip(RuntimeException cause) {
        long now = System.nanoTime();
        long last = lastSkipLog.get();
        if (now - last >= QUIET_NANOS && lastSkipLog.compareAndSet(last, now)) {
            logger.at(Level.FINE).withCause(cause).log("Feats skipped a portal read");
        }
    }

    private void disable(Throwable cause) {
        if (!disabled) {
            disabled = true;
            logger.at(Level.WARNING).withCause(cause).log("Feats portal tracking is not supported on this server and has been turned off");
        }
    }
}
