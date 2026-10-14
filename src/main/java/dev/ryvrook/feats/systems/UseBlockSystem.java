package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.List;
import java.util.UUID;

public final class UseBlockSystem extends PlayerEventSystem<UseBlockEvent.Post> {
    private final LabUses labUses;

    public UseBlockSystem(FeatsTracker tracker, LabUses labUses) {
        super(UseBlockEvent.Post.class, tracker);
        this.labUses = labUses;
    }

    @Override
    protected void handlePlayer(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, UseBlockEvent.Post event) {
        String blockId = event.getBlockType() != null ? event.getBlockType().getId() : null;
        List<String> keys = Classify.blockUsed(blockId);
        if (!keys.isEmpty()) {
            tracker.incrementAll(uuid, keys, 1);
        }
        labUses.remember(uuid, blockId, System.currentTimeMillis());
    }
}
