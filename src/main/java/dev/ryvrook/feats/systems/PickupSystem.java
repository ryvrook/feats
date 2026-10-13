package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.RecentDrops;

import java.util.UUID;

public final class PickupSystem extends PlayerEventSystem<InteractivelyPickupItemEvent> {
    private final RecentDrops drops;

    public PickupSystem(FeatsTracker tracker, RecentDrops drops) {
        super(InteractivelyPickupItemEvent.class, tracker);
        this.drops = drops;
    }

    @Override
    protected void handlePlayer(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, InteractivelyPickupItemEvent event) {
        if (event.isCancelled()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        long amount = Math.max(1, stack.getQuantity());
        // Items this player dropped a few minutes ago are only being taken back.
        long fresh = amount - drops.take(uuid, stack.getItemId(), amount);
        if (fresh > 0) {
            tracker.incrementAll(uuid, Classify.pickedUp(stack.getItemId()), fresh);
        }
    }
}
