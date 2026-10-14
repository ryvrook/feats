package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.UUID;

public final class CraftSystem extends PlayerEventSystem<CraftRecipeEvent.Post> {
    public CraftSystem(FeatsTracker tracker) {
        super(CraftRecipeEvent.Post.class, tracker);
    }

    @Override
    protected void handlePlayer(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, CraftRecipeEvent.Post event) {
        if (event.isCancelled()) {
            return;
        }
        CraftingRecipe recipe = event.getCraftedRecipe();
        if (recipe == null) {
            return;
        }
        MaterialQuantity output = recipe.getPrimaryOutput();
        String itemId = output != null ? output.getItemId() : null;
        long count = CraftCounts.of(store, ref, recipe, event.getQuantity());
        tracker.incrementAll(uuid, Classify.crafted(itemId), count);
    }
}
