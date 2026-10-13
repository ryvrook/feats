package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.ecs.DiscoverZoneEvent;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.UUID;

public final class ZoneDiscoverySystem extends PlayerEventSystem<DiscoverZoneEvent.Display> {
    public ZoneDiscoverySystem(FeatsTracker tracker) {
        super(DiscoverZoneEvent.Display.class, tracker);
    }

    @Override
    protected void handlePlayer(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, DiscoverZoneEvent.Display event) {
        WorldMapTracker.ZoneDiscoveryInfo info = event.getDiscoveryInfo();
        if (info == null) {
            return;
        }
        String name = info.zoneName();
        if (name == null || name.isBlank()) {
            name = info.regionName();
        }
        if (name == null || name.isBlank()) {
            return;
        }
        tracker.discover(uuid, StatKeys.ZONE_PREFIX, name, StatKeys.ZONES_DISCOVERED);
    }
}
