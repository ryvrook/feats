package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.PlacedBlocks;
import dev.ryvrook.feats.track.RecentPlacements;
import org.joml.Vector3i;

import java.util.UUID;

public final class PlaceBlockSystem extends PlayerEventSystem<PlaceBlockEvent> {
    private final RecentPlacements placements;
    private final PlacedBlocks placed;

    public PlaceBlockSystem(FeatsTracker tracker, RecentPlacements placements, PlacedBlocks placed) {
        super(PlaceBlockEvent.class, tracker);
        this.placements = placements;
        this.placed = placed;
    }

    @Override
    protected void handlePlayer(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, PlaceBlockEvent event) {
        if (event.isCancelled()) {
            return;
        }
        ItemStack item = event.getItemInHand();
        String itemId = item != null ? item.getItemId() : null;
        placements.record(uuid, itemId);
        Vector3i pos = event.getTargetBlock();
        if (pos != null && !placed.place(uuid, worldName(store), pos.x, pos.y, pos.z)) {
            return; // placing again on a spot the player just built on and broke counts once
        }
        tracker.incrementAll(uuid, Classify.blockPlaced(itemId), 1);
    }
}
