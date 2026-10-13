package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.PlacedBlocks;
import org.joml.Vector3i;

import java.util.UUID;

public final class BreakBlockSystem extends PlayerEventSystem<BreakBlockEvent> {
    private final PlacedBlocks placed;

    public BreakBlockSystem(FeatsTracker tracker, PlacedBlocks placed) {
        super(BreakBlockEvent.class, tracker);
        this.placed = placed;
    }

    @Override
    protected void handlePlayer(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, BreakBlockEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Vector3i pos = event.getTargetBlock();
        if (pos != null && placed.placedHere(uuid, worldName(store), pos.x, pos.y, pos.z)) {
            return; // the player's own recent placement, breaking it again earns nothing
        }
        String blockId = event.getBlockType() != null ? event.getBlockType().getId() : null;
        tracker.incrementAll(uuid, Classify.blockBroken(blockId), 1);
    }
}
