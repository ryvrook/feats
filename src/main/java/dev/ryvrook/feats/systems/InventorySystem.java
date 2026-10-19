package dev.ryvrook.feats.systems;

import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.BlockWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.entity.entities.player.windows.WindowManager;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ResourceTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.TagTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.components.PlacedByInteractionComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.ArmourSets;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.RecentDrops;
import dev.ryvrook.feats.track.RecentPlacements;
import org.bson.BsonDocument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads every change to the inventory a player carries (hotbar, storage, backpack and utility belt).
 *
 * <p>Items obtained. Loot, mob drops, ground pickups and crafted results arrive as add transactions.
 * Any move whose other side is not one of the player's own containers is read on the player's side, slot
 * before against slot after, whichever way the move ran, see {@link MoveSides}. What left is put away and
 * what arrived is taken out of a container. A swap of two stacks that do not stack together is both at once.
 * Moves between two of the player's own containers and sorting are skipped, so rearranging never counts.
 * Items put away or dropped go onto the player's {@link StashBalance}, and a take or pickup counts only what
 * the balance does not cover. Items out of a container block some player placed, which the engine marks with
 * {@code PlacedByInteractionComponent}, never count. The output slots of a processing bench are the exception,
 * they hold new items. Dropped items are also handed to {@link RecentDrops} so the pickup counters skip them
 * when they come back.
 *
 * <p>Meals and potions. A finished eat or drink takes exactly one Food_ or Potion_ item out of the
 * held slot through a removal that names the held stack. Dropping with the drop key removes without
 * naming a stack, death drops and crafting use other transaction kinds, and placing a potion as a
 * block is told apart by the placement that comes just before it.
 *
 * <p>Wear and repair. A slot whose stack keeps its item id but goes from whole to {@code isBroken()} is a
 * tool worn out, and one whose durability rose is a repair [inferred, the repair kit opens the ItemRepair
 * page and the page edits the slot in place]. Both only look at tools, weapons and armour, not at the
 * charge tools such as the watering can, see {@link Classify#isWearable}. A capture crate that gains its
 * CapturedEntity metadata on the same slot caught an animal [inferred from UseCaptureCrateInteraction].
 *
 * <p>Armour. A change to the armour section reads the four slots and credits a full set, see
 * {@link ArmourSets}. Merchants. Items gained while a barter page is open are a trade with that merchant,
 * see {@link MerchantPage}.
 *
 * <p>Creative mode is ignored, its items are free. Its moves to and from other containers still change the
 * balance, so items put into a chest in Creative do not count when taken out later in Adventure.
 */
public final class InventorySystem extends PlayerEventSystem<InventoryChangeEvent> {
    private final RecentPlacements placements;
    private final RecentDrops drops;
    private final StashBalance stash;
    /** The player's own inventory sections, read once since the engine registers them at start. */
    private final List<ComponentType<EntityStore, ? extends InventoryComponent>> sections;
    /** The armour item id last seen per slot, per player, so a durability tick skips the set check. */
    private final Map<UUID, String[]> lastArmour = new ConcurrentHashMap<>();
    /** Past this many players noted, the notes of players no longer loaded are dropped. */
    private static final int ARMOUR_PRUNE_AT = 256;

    public InventorySystem(FeatsTracker tracker, RecentPlacements placements, RecentDrops drops) {
        super(InventoryChangeEvent.class, tracker);
        this.placements = placements;
        this.drops = drops;
        this.stash = new StashBalance(tracker);
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
    static final class Totals {
        Map<String, Long> gained;
        /** Out of a world generated or unknown container, or a processing bench's output. */
        Map<String, Long> fromContainer;
        /** Out of a container block a player placed, never obtained. */
        Map<String, Long> fromPlaced;
        Map<String, Long> putAway;
        Map<String, Long> dropped;
        /** Item ids that reached zero durability on their slot. */
        List<String> broken;
        int repairs;
        int captures;

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
        boolean armour = section == InventoryComponent.Armor.getComponentType();
        if (!carried && !armour) {
            return;
        }
        Transaction transaction = event.getTransaction();
        if (transaction == null || !transaction.succeeded()) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (player.getGameMode() == GameMode.Creative) {
            if (carried) {
                Totals moved = new Totals();
                walk(transaction, true, true, ref, store, moved);
                stash.creative(uuid, moved.putAway, moved.fromContainer);
            }
            return;
        }
        if (armour) {
            wornSet(uuid, event);
            Totals wear = new Totals();
            scanWear(transaction, wear, false); // armour wearing through is not a tool worn out, repairs still count
            countWear(uuid, wear);
            return;
        }

        if (hand && transaction instanceof ItemStackSlotTransaction slot) {
            countConsumed(uuid, slot);
        }

        Totals totals = new Totals();
        walk(transaction, true, false, ref, store, totals);
        if (hand) {
            scanWear(transaction, totals, true);
            countWear(uuid, totals);
        }
        if (totals.putAway != null) {
            totals.putAway.forEach((itemId, amount) -> stash.put(uuid, itemId, amount));
        }
        if (totals.dropped != null) {
            totals.dropped.forEach((itemId, amount) -> drops.record(uuid, itemId, amount));
        }
        if (totals.fromPlaced != null) {
            totals.fromPlaced.forEach((itemId, amount) -> obtain(uuid, itemId, amount, true));
        }
        if (totals.fromContainer != null) {
            totals.fromContainer.forEach((itemId, amount) -> obtain(uuid, itemId, amount, false));
        }
        if (totals.gained != null) {
            totals.gained.forEach((itemId, amount) -> obtain(uuid, itemId, amount, false));
        }
        if (totals.gained != null || totals.fromContainer != null || totals.fromPlaced != null) {
            String merchant = MerchantPage.merchantOf(player);
            if (merchant != null) {
                tracker.discover(uuid, StatKeys.MERCHANT_PREFIX, merchant, StatKeys.MERCHANTS);
            }
        }
    }

    // ---- wear, repairs and captures --------------------------------------------

    /**
     * Visits every slot change in the transaction and notes the stacks that broke, were repaired or caught
     * an animal. Only slots whose item id stayed the same are looked at, a swap is never wear. Moves and
     * swaps arrive as move transactions and are skipped whole. A durability rise also does not count when
     * another slot of the same transaction lost that item or saw its durability fall, since that is two
     * copies of one tool changing places through plain slot sets and not a repair.
     */
    static void scanWear(Transaction transaction, Totals totals, boolean breaks) {
        List<SlotTransaction> slots = new ArrayList<>(4);
        slots(transaction, slots);
        for (SlotTransaction slot : slots) {
            ItemStack before = slot.getSlotBefore();
            ItemStack after = slot.getSlotAfter();
            if (isEmpty(before) || isEmpty(after) || !before.getItemId().equals(after.getItemId())) {
                continue;
            }
            String itemId = after.getItemId();
            if (Classify.isWearable(itemId)) {
                if (after.isBroken() && !before.isBroken()) {
                    if (!breaks) {
                        continue;
                    }
                    if (totals.broken == null) {
                        totals.broken = new ArrayList<>(2);
                    }
                    totals.broken.add(itemId);
                } else if (after.getDurability() > before.getDurability() && !after.isBroken()
                        && !pairedLoss(slots, slot, itemId)) {
                    totals.repairs++;
                }
            } else if (Classify.CAPTURE_CRATE.equals(itemId) && hasCapture(after) && !hasCapture(before)) {
                totals.captures++;
            }
        }
    }

    /** Collects the plain slot changes of a transaction, leaving out every move and swap. */
    private static void slots(Transaction transaction, List<SlotTransaction> out) {
        if (transaction == null || !transaction.succeeded() || transaction instanceof MoveTransaction<?>) {
            return; // a move or swap carries a tool to another slot, its durability rising there is no repair
        }
        if (transaction instanceof ListTransaction<?> list) {
            for (Transaction part : list.getList()) {
                slots(part, out);
            }
        } else if (transaction instanceof ItemStackTransaction stack) {
            for (ItemStackSlotTransaction part : stack.getSlotTransactions()) {
                slots(part, out);
            }
        } else if (transaction instanceof SlotTransaction slot) {
            out.add(slot);
        }
    }

    /** True when another slot in the same transaction lost this item or saw its durability fall. */
    private static boolean pairedLoss(List<SlotTransaction> slots, SlotTransaction rise, String itemId) {
        for (SlotTransaction other : slots) {
            if (other == rise) {
                continue;
            }
            ItemStack before = other.getSlotBefore();
            if (isEmpty(before) || !itemId.equals(before.getItemId())) {
                continue;
            }
            ItemStack after = other.getSlotAfter();
            if (isEmpty(after) || !itemId.equals(after.getItemId()) || after.getDurability() < before.getDurability()) {
                return true;
            }
        }
        return false;
    }

    private void countWear(UUID uuid, Totals totals) {
        if (totals.broken != null) {
            for (String itemId : totals.broken) {
                tracker.incrementAll(uuid, Classify.toolBroken(itemId), 1);
            }
        }
        if (totals.repairs > 0) {
            tracker.increment(uuid, StatKeys.REPAIRS, totals.repairs);
        }
        if (totals.captures > 0) {
            tracker.increment(uuid, StatKeys.CRATES_USED, totals.captures);
        }
    }

    /** The metadata key the capture crate interaction writes when an animal is inside. */
    private static final String CAPTURED_ENTITY = "CapturedEntity";

    private static boolean hasCapture(ItemStack stack) {
        BsonDocument metadata = stack.getMetadata();
        return metadata != null && metadata.containsKey(CAPTURED_ENTITY);
    }

    // ---- armour sets --------------------------------------------------------------

    /**
     * Reads every armour slot after the change and credits a full set once per material. The armour section
     * changes on every durability tick, so the item id last seen per slot is kept per player and the set
     * check only runs when one of them changed, which is a real equip or unequip.
     */
    private void wornSet(UUID uuid, InventoryChangeEvent event) {
        ItemContainer container = event.getItemContainer();
        if (container == null && event.getInventory() != null) {
            container = event.getInventory().getInventory();
        }
        if (container == null) {
            return;
        }
        short capacity = container.getCapacity();
        String[] seen = lastArmour.get(uuid);
        if (seen == null || seen.length != capacity) {
            seen = new String[capacity];
            pruneArmour();
            lastArmour.put(uuid, seen);
        } else if (sameArmour(container, seen)) {
            return; // a durability tick or a stack of the same piece, nothing was put on or taken off
        }
        List<String> worn = new ArrayList<>(capacity);
        for (short i = 0; i < capacity; i++) {
            ItemStack stack = container.getItemStack(i);
            String itemId = isEmpty(stack) ? null : stack.getItemId();
            seen[i] = itemId;
            if (itemId != null) {
                worn.add(itemId);
            }
        }
        String material = ArmourSets.completeSet(worn);
        if (material != null) {
            tracker.discover(uuid, StatKeys.ARMOUR_SET_PREFIX, material, StatKeys.ARMOUR_SETS);
        }
    }

    /** True when every armour slot holds the item id it held last time. Allocates nothing. */
    private static boolean sameArmour(ItemContainer container, String[] seen) {
        for (short i = 0; i < seen.length; i++) {
            ItemStack stack = container.getItemStack(i);
            String itemId = isEmpty(stack) ? null : stack.getItemId();
            if (!Objects.equals(itemId, seen[i])) {
                return false;
            }
        }
        return true;
    }

    /** Drops the armour notes of players who left, checked only when a new player is noted. */
    private void pruneArmour() {
        if (lastArmour.size() >= ARMOUR_PRUNE_AT) {
            lastArmour.keySet().removeIf(id -> !tracker.isLoaded(id));
        }
    }

    // ---- obtained ---------------------------------------------------------

    /**
     * Totals one change. In Creative only the moves to and from other containers are read, so the balance
     * follows deposits, and the walk does not look up where items came from.
     */
    private void walk(Transaction transaction, boolean topLevel, boolean creative, Ref<EntityStore> ref,
                      Store<EntityStore> store, Totals totals) {
        if (transaction == null || !transaction.succeeded()) {
            return;
        }
        if (transaction instanceof MoveTransaction<?> move) {
            ItemContainer other = move.getOtherContainer();
            if (ownedByPlayer(other, ref, store, sections)) {
                return; // a rearrangement inside the player's own inventory
            }
            MoveSides sides = MoveSides.of(move);
            if (sides == null) {
                return;
            }
            if (sides.left != null) {
                totals.putAway = merge(totals.putAway, sides.left);
            }
            if (sides.arrived != null) {
                if (!creative && placedSource(other, MoveSides.otherSlot(move), ref, store)) {
                    totals.fromPlaced = merge(totals.fromPlaced, sides.arrived);
                } else {
                    totals.fromContainer = merge(totals.fromContainer, sides.arrived);
                }
            }
            return;
        }
        if (transaction instanceof MaterialTransaction || transaction instanceof ResourceTransaction
                || transaction instanceof TagTransaction) {
            return; // crafting and repairs taking ingredients
        }
        if (transaction instanceof ListTransaction<?> list) {
            for (Transaction part : list.getList()) {
                walk(part, false, creative, ref, store, totals);
            }
            return;
        }
        if (creative) {
            return; // Creative gains and drops are free and never touch the balance
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

    private static Map<String, Long> merge(Map<String, Long> totals, Map<String, Long> more) {
        if (totals == null) {
            return more;
        }
        more.forEach((id, amount) -> totals.merge(id, amount, Long::sum));
        return totals;
    }

    // ---- where a container move came from ---------------------------------------

    /**
     * True when the items came out of a container block some player placed, and not out of a processing
     * bench's output slots. The block is found through the player's open windows, a container block window or
     * a processing bench window whose container is the move's other side. The engine puts
     * {@code PlacedByInteractionComponent} on every block with block data a player places and saves it with
     * the chunk [javap 0.7.2 BlockPlaceUtils], so a chest placed before Feats 1.8.1 is known too. Anything
     * else, crafting windows, item containers, other plugins' windows and every failure, answers false, which
     * leaves the take to the balance.
     */
    private static boolean placedSource(ItemContainer other, int otherSlot, Ref<EntityStore> ref,
                                        Store<EntityStore> store) {
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            WindowManager manager = player != null ? player.getWindowManager() : null;
            if (manager == null) {
                return false;
            }
            for (Window window : manager.getWindows()) {
                if (!(window instanceof BlockWindow block) || !(window instanceof ItemContainerWindow holder)) {
                    continue;
                }
                ItemContainer mine = holder.getItemContainer();
                if (mine == null || !(mine == other || mine.containsContainer(other) || other.containsContainer(mine))) {
                    continue;
                }
                EntityStore entities = store.getExternalData();
                World world = entities != null ? entities.getWorld() : null;
                if (world == null) {
                    return false;
                }
                int x = block.getX();
                int y = block.getY();
                int z = block.getZ();
                boolean chest = window instanceof ContainerBlockWindow;
                ProcessingBenchBlock bench = chest ? null : bench(world, x, y, z);
                boolean output = bench != null && benchOutput(bench, other, otherSlot);
                PlacedByInteractionComponent placed = BlockModule.getComponent(
                        PlacedByInteractionComponent.getComponentType(), world, x, y, z);
                return neverObtained(chest, bench != null, output, placed != null ? placed.getWhoPlacedUuid() : null);
            }
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
        return false;
    }

    /**
     * The rule once the block is known. A plain container a player placed never gives obtained items, nor do
     * the input and fuel slots of a placed processing bench. Its output does, and so does any block nobody
     * placed or that is neither.
     */
    static boolean neverObtained(boolean containerWindow, boolean bench, boolean benchOutput, UUID placedBy) {
        if (placedBy == null) {
            return false;
        }
        return containerWindow || (bench && !benchOutput);
    }

    /** The processing bench at a spot, or null when there is none or the crafting module is missing. */
    private static ProcessingBenchBlock bench(World world, int x, int y, int z) {
        try {
            return BlockModule.getComponent(ProcessingBenchBlock.getComponentType(), world, x, y, z);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static boolean benchOutput(ProcessingBenchBlock bench, ItemContainer other, int otherSlot) {
        return benchOutput(bench.getOutputContainer(), bench.getInputContainer(), bench.getFuelContainer(),
                bench.getItemContainer(), other, otherSlot);
    }

    /**
     * True when a move out of a processing bench came from its output slots, or when that cannot be told,
     * since an output is counted against the balance, which is never more than the player put in. The window
     * moves through the bench's combined container, input, fuel and output in that order, and names the
     * combined slot.
     */
    static boolean benchOutput(ItemContainer output, ItemContainer input, ItemContainer fuel,
                               CombinedItemContainer combined, ItemContainer other, int otherSlot) {
        if (output == null || other == output) {
            return true;
        }
        if (other == input || other == fuel) {
            return false;
        }
        if (combined != null && other == combined && otherSlot >= 0 && otherSlot < combined.getCapacity()) {
            return combined.getContainerForSlot((short) otherSlot) == output;
        }
        return true;
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

    private void obtain(UUID uuid, String itemId, long amount, boolean placedContainer) {
        long fresh = stash.fresh(uuid, itemId, amount, placedContainer);
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
