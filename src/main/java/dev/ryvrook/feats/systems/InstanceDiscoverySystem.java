package dev.ryvrook.feats.systems;

import com.hypixel.hytale.builtin.instances.config.InstanceDiscoveryConfig;
import com.hypixel.hytale.builtin.instances.event.DiscoverInstanceEvent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Instances discovered, keyed by the title key of the discovery config. Each new title key is logged
 * once so the feat keys can be checked against what the server really sends.
 */
public final class InstanceDiscoverySystem extends PlayerEventSystem<DiscoverInstanceEvent.Display> {
    private final HytaleLogger logger;
    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    public InstanceDiscoverySystem(FeatsTracker tracker, HytaleLogger logger) {
        super(DiscoverInstanceEvent.Display.class, tracker);
        this.logger = logger;
    }

    @Override
    protected void handlePlayer(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, DiscoverInstanceEvent.Display event) {
        InstanceDiscoveryConfig config = event.getDiscoveryConfig();
        String key = WorldKeys.instanceKey(config != null ? config.getTitleKey() : null);
        if (key == null) {
            return;
        }
        if (seen.size() < 256 && seen.add(key)) {
            logger.at(Level.INFO).log("Feats saw instance discovery %s", key);
        }
        tracker.discover(uuid, StatKeys.INSTANCE_PREFIX, key, StatKeys.INSTANCES_DISCOVERED);
    }
}
