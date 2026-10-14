package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveType;
import com.hypixel.hytale.server.core.inventory.transaction.ResourceTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.TagTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.RecentDrops;
import dev.ryvrook.feats.track.RecentPlacements;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads every change to the inventory a player carries (hotbar, storage, backpack and utility belt).
 *
 * <p>Items obtained. Loot, mob drops, ground pickups and crafted results arrive as add transactions,
 * and taking items out of a chest or bench arrives as a move whose other side is not one of the
 * player's own containers. Moves between two of the player's own containers, sorting and swapping
 * are skipped, so rearranging never counts. Items a player puts into a chest or drops on the ground
 * are noted, and taking those same items back cancels out instead of counting twice. Dropped items
 * are also handed to {@link RecentDrops} so the pickup counters skip them when they come back.
 *
 * <p>Meals and potions. A finished eat or drink takes exactly one Food_ or Potion_ item out of the
 * held slot through a removal that names the held stack. Dropping with the drop key removes without
 * naming a stack, death drops and crafting use other transaction kinds, and placing a potion as a
 * block is told apart by the placement that comes just before it.
 *
 * <p>Creative mode is ignored, its items are free.
 */
public final class InventorySystem extends PlayerEventSystem<InventoryChangeEvent> {
    private final RecentPlacements placements;
    private final RecentDrops drops;
    /** The player's own inventory sections, read once since the engine registers them at start. */
    private final List<ComponentType<EntityStore, ? extends InventoryComponent>> sections;

    public InventorySystem(FeatsTracker tracker, RecentPlacements placements, RecentDrops drops) {
        super(InventoryChangeEvent.class, tracker);
        this.placements = placements;
        this.drops = drops;
        this.sections = List.of(
                InventoryComponent.Hotbar.getComponentType(), InventoryComponent.Storage.getComponentType(),
                InventoryComponent.Backpack.getComponentType(), InventoryComponent.Utility.getComponentType(),
                InventoryComponent.Armor.getComponentType(), InventoryComponent.Tool.getComponentType(),
                InventoryComponent.AbilitySlots.getComponentType(), InventoryComponent.RuneBag.getComponentType());
    }

    /**
     * What one inventory change moved, item id to amount. Each map is only created once something lands
     * in it, so a change that moves nothing countable allocates no maps at all.
     */
    private static final class Totals {
        Map<String, Long> gained;
        Map<String, Long> putAway;
        Map<String, Long> dropped;

        void gain(Counted counted) {
            gained = note(gained, counted);
        }

        void putAway(Counted counted) {
            putAway = note(putAway, counted);
        }

        void drop(Counted counted) {
            dropped = note(dropped, counted);
        }
    }

    @Override
    protected void handlePlayer(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, InventoryChangeEvent event) {
        if (!tracker.isLoaded(uuid)) {
            return;
        }
        Object section = event.getComponentType();
        boolean hand = section == InventoryComponent.Hotbar.getComponentType()
                || section == InventoryComponent.Utility.getComponentType();
        boolean carried = hand
                || section == InventoryComponent.Storage.getComponentType()
                || section == InventoryComponent.Backpack.getComponentType();
        if (!carried) {
            return;
        }
        Transaction transaction = event.getTransaction();
        if (transaction == null || !transaction.succeeded()) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || player.getGameMode() == GameMode.Creative) {
            return;
        }

        if (hand && transaction instanceof ItemStackSlotTransaction slot) {
            countConsumed(uuid, slot);
        }

        Totals totals = new Totals();
        walk(transaction, true, ref, store, totals);
        if (totals.putAway != null) {
            totals.putAway.forEach((itemId, amount) -> stash(uuid, itemId, amount));
        }
        if (totals.dropped != null) {
            totals.dropped.forEach((itemId, amount) -> drops.record(uuid, itemId, amount));
        }
        if (totals.gained != null) {
            totals.gained.forEach((itemId, amount) -> obtain(uuid, itemId, amount));
        }
    }

    // ---- obtained ---------------------------------------------------------

    private void walk(Transaction transaction, boolean topLevel, Ref<EntityStore> ref, Store<EntityStore> store,
                      Totals totals) {
        if (transaction == null || !transaction.succeeded()) {
            return;
        }
        if (transaction instanceof MoveTransaction<?> move) {
            if (ownedByPlayer(move.getOtherContainer(), ref, store, sections)) {
                return; // a rearrangement inside the player's own inventory
            }
            if (move.getMoveType() == MoveType.MOVE_TO_SELF) {
                walk(move.getAddTransaction(), false, ref, store, totals);
            } else {
                totals.putAway(removed(move.getRemoveTransaction()));
            }
            return;
        }
        if (transaction instanceof MaterialTransaction || transaction instanceof ResourceTransaction
                || transaction instanceof TagTransaction) {
            return; // crafting and repairs taking ingredients
        }
        if (transaction instanceof ListTransaction<?> list) {
            for (Transaction part : list.getList()) {
                walk(part, false, ref, store, totals);
            }
            return;
        }
        if (transaction instanceof ItemStackTransaction stack) {
            if (stack.getAction() != null && stack.getAction().isAdd()) {
                for (ItemStackSlotTransaction part : stack.getSlotTransactions()) {
                    totals.gain(added(part));
                }
            }
            return;
        }
        if (transaction instanceof ItemStackSlotTransaction slot) {
            if (slot.getAction() != null && slot.getAction().isAdd()) {
                totals.gain(added(slot));
            } else if (topLevel && slot.getAction() == ActionType.REMOVE && slot.getQuery() == null) {
                Counted gone = removed(slot); // the drop key
                totals.putAway(gone);
                totals.drop(gone);
            }
            return;
        }
        if (transaction.getClass() == SlotTransaction.class) {
            SlotTransaction slot = (SlotTransaction) transaction;
            if (slot.getAction() != null && slot.getAction().isAdd()) {
                totals.gain(added(slot));
            } else if (topLevel && slot.getAction() == ActionType.REMOVE) {
                Counted gone = removed(slot); // a whole stack leaving, such as death drops
                totals.putAway(gone);
                totals.drop(gone);
            }
        }
    }

    /** True when the container is one of this player's own inventory sections or a view over them. */
    private static boolean ownedByPlayer(ItemContainer other, Ref<EntityStore> ref, Store<EntityStore> store,
                                         List<ComponentType<EntityStore, ? extends InventoryComponent>> sections) {
        if (other == null) {
            return true; // nothing to tell it apart by, so never count it
        }
        for (ComponentType<EntityStore, ? extends InventoryComponent> type : sections) {
            InventoryComponent component = store.getComponent(ref, type);
            ItemContainer mine = component != null ? component.getInventory() : null;
            if (mine != null && (other == mine || other.containsContainer(mine))) {
                return true;
            }
        }
        return false;
    }

    /** What one slot gained, which is the new stack less whatever of the same item was already there. */
    private static Counted added(SlotTransaction slot) {
        if (slot == null || !slot.succeeded()) {
            return null;
        }
        ItemStack after = slot.getSlotAfter();
        if (isEmpty(after)) {
            return null;
        }
        ItemStack before = slot.getSlotBefore();
        long amount = after.getQuantity();
        if (!isEmpty(before) && after.getItemId().equals(before.getItemId())) {
            amount -= before.getQuantity();
        }
        return amount > 0 ? new Counted(after.getItemId(), amount) : null;
    }

    /** What one slot lost, which is the old stack less whatever of the same item is still there. */
    private static Counted removed(SlotTransaction slot) {
        if (slot == null || !slot.succeeded()) {
            return null;
        }
        ItemStack before = slot.getSlotBefore();
        if (isEmpty(before)) {
            return null;
        }
        ItemStack after = slot.getSlotAfter();
        long amount = before.getQuantity();
        if (!isEmpty(after) && before.getItemId().equals(after.getItemId())) {
            amount -= after.getQuantity();
        }
        return amount > 0 ? new Counted(before.getItemId(), amount) : null;
    }

    /** Adds to a total, creating the map on first use. Returns the map to keep. */
    private static Map<String, Long> note(Map<String, Long> totals, Counted counted) {
        if (counted == null || counted.itemId() == null) {
            return totals;
        }
        if (totals == null) {
            totals = new HashMap<>(4);
        }
        totals.merge(counted.itemId(), counted.amount(), Long::sum);
        return totals;
    }

    private void stash(UUID uuid, String itemId, long amount) {
        if (tracker.isWatched(Classify.obtainedKey(itemId))) {
            tracker.addSilently(uuid, Classify.stashKey(itemId), amount);
        }
    }

    private void obtain(UUID uuid, String itemId, long amount) {
        long fresh = amount;
        if (tracker.isWatched(Classify.obtainedKey(itemId))) {
            fresh -= tracker.takeSilently(uuid, Classify.stashKey(itemId), amount);
        }
        if (fresh > 0) {
            tracker.incrementAll(uuid, Classify.obtained(itemId), fresh);
        }
    }

    // ---- meals and potions ----------------------------------------------------

    private void countConsumed(UUID uuid, ItemStackSlotTransaction slot) {
        if (slot.getAction() != ActionType.REMOVE || slot.getQuery() == null) {
            return; // only a removal that names the held stack is an eat or drink
        }
        Counted gone = removed(slot);
        if (gone == null || gone.amount() != 1) {
            return;
        }
        List<String> keys = Classify.consumed(gone.itemId());
        if (keys.isEmpty() || placements.consume(uuid, gone.itemId())) {
            return;
        }
        tracker.incrementAll(uuid, keys, 1);
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty() || stack.getItemId() == null;
    }

    private record Counted(String itemId, long amount) {}
}
