package dev.ryvrook.feats.systems;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveType;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The player's side of a move, built from real engine transactions. The stacks are a small subclass, since a
 * real ItemStack looks its item up in the asset store, which needs a running server.
 */
class MoveSidesTest {
    static final String GLIDER = "Glider_Scrap";
    static final String KEY = "Coffer_Key";

    /** A stack that needs no asset store. */
    static final class Stack extends ItemStack {
        private final String id;
        private final int quantity;

        Stack(String id, int quantity) {
            this.id = id;
            this.quantity = quantity;
        }

        @Override
        public String getItemId() {
            return id;
        }

        @Override
        public int getQuantity() {
            return quantity;
        }

        @Override
        public boolean isEmpty() {
            return id == null || quantity <= 0;
        }
    }

    static SlotTransaction slot(ActionType action, int index, ItemStack before, ItemStack after) {
        return new SlotTransaction(true, action, (short) index, before, after, null, false, false, false);
    }

    /** The swap the engine builds when a stack is dropped on a slot holding a different non stackable item. */
    static MoveTransaction<SlotTransaction> swap(MoveType type, ItemStack mineBefore, ItemStack mineAfter) {
        SlotTransaction mine = slot(ActionType.REPLACE, 3, mineBefore, mineAfter);
        SlotTransaction theirs = slot(ActionType.REPLACE, 7, mineAfter, mineBefore);
        return type == MoveType.MOVE_TO_SELF
                ? new MoveTransaction<>(true, theirs, MoveType.MOVE_TO_SELF, null, mine)
                : new MoveTransaction<>(true, mine, MoveType.MOVE_FROM_SELF, null, theirs);
    }

    @Test
    void aSwapStartedOnTheChestSideRecordsWhatWentIn() {
        // The player drops the chest's key on their own slot holding the glider. The player's side is the add side.
        MoveTransaction<SlotTransaction> move = swap(MoveType.MOVE_TO_SELF, new Stack(GLIDER, 1), new Stack(KEY, 1));
        MoveSides sides = MoveSides.of(move);
        assertEquals(Map.of(GLIDER, 1L), sides.left);
        assertEquals(Map.of(KEY, 1L), sides.arrived);
        assertEquals(7, MoveSides.otherSlot(move));
    }

    @Test
    void aSwapStartedOnThePlayerSideRecordsWhatCameBack() {
        // The player drops their glider on the chest slot holding the key. The player's side is the remove side.
        MoveTransaction<SlotTransaction> move = swap(MoveType.MOVE_FROM_SELF, new Stack(GLIDER, 1), new Stack(KEY, 1));
        MoveSides sides = MoveSides.of(move);
        assertEquals(Map.of(GLIDER, 1L), sides.left);
        assertEquals(Map.of(KEY, 1L), sides.arrived);
        assertEquals(7, MoveSides.otherSlot(move));
    }

    @Test
    void plainMovesAndPartialStacksCountOnlyTheDifference() {
        MoveSides in = MoveSides.of(new MoveTransaction<>(true, slot(ActionType.REMOVE, 0, new Stack("Ingredient_Scraps", 20), null),
                MoveType.MOVE_TO_SELF, null, slot(ActionType.ADD, 4, new Stack("Ingredient_Scraps", 5), new Stack("Ingredient_Scraps", 25))));
        assertEquals(Map.of("Ingredient_Scraps", 20L), in.arrived);
        assertNull(in.left);

        MoveSides out = MoveSides.of(new MoveTransaction<>(true, slot(ActionType.REMOVE, 4, new Stack("Ingredient_Scraps", 25),
                new Stack("Ingredient_Scraps", 10)), MoveType.MOVE_FROM_SELF, null, SlotTransaction.FAILED_ADD));
        assertEquals(Map.of("Ingredient_Scraps", 15L), out.left);
        assertNull(out.arrived);
    }

    @Test
    void aMoveThatChangedNothingIsNull() {
        assertNull(MoveSides.of(new MoveTransaction<>(true, slot(ActionType.REMOVE, 0, new Stack(GLIDER, 1), new Stack(GLIDER, 1)),
                MoveType.MOVE_FROM_SELF, null, SlotTransaction.FAILED_ADD)));
        assertNull(MoveSides.of(new MoveTransaction<>(false, slot(ActionType.REMOVE, 0, new Stack(GLIDER, 1), null),
                MoveType.MOVE_FROM_SELF, null, SlotTransaction.FAILED_ADD)));
    }

    @Test
    void aTakeAllListAddsUpEverySlot() {
        SlotTransaction a = slot(ActionType.ADD, 1, null, new Stack("Ingredient_Rune_Shard", 8));
        SlotTransaction b = slot(ActionType.ADD, 2, new Stack("Ingredient_Rune_Shard", 60), new Stack("Ingredient_Rune_Shard", 64));
        ListTransaction<SlotTransaction> both = new ListTransaction<>(true, List.of(a, b));
        MoveSides sides = MoveSides.of(new MoveTransaction<>(true, slot(ActionType.REMOVE, 0, new Stack("Ingredient_Rune_Shard", 12), null),
                MoveType.MOVE_TO_SELF, null, both));
        assertEquals(Map.of("Ingredient_Rune_Shard", 12L), sides.arrived);
        assertNull(sides.left);
    }
}
