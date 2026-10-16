package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.PlacedBlocks;
import dev.ryvrook.feats.track.RecentPlacements;
import org.joml.Vector3i;

import java.util.List;
import java.util.UUID;

/**
 * Counts the blocks a player places. Seeds and fishing traps count on every placement, since replanting a
 * harvested plot is the point of farming. The block counters skip a spot the player just built on and
 * broke, see {@link PlacedBlocks}, and the distinct kinds placed are a discovery, so they count once.
 */
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
        List<String> seeds = Classify.seedPlanted(itemId);
        if (!seeds.isEmpty()) {
            tracker.incrementAll(uuid, seeds, 1);
        } else if (Classify.FISHING_TRAP.equals(itemId)) {
            tracker.increment(uuid, StatKeys.TRAPS_PLACED, 1);
        }
        Vector3i pos = event.getTargetBlock();
        if (pos != null && !placed.place(uuid, worldName(store), pos.x, pos.y, pos.z)) {
            return; // placing again on a spot the player just built on and broke counts once
        }
        tracker.incrementAll(uuid, Classify.blockPlaced(itemId), 1);
        if (itemId != null && !itemId.isEmpty()) {
            tracker.discover(uuid, StatKeys.PLACED_KIND_PREFIX, itemId, StatKeys.PLACED_KINDS);
        }
    }
}
