package dev.ryvrook.feats.systems;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveType;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The repair rule of the inventory wear scan, over hand built slot changes. */
class RepairScanTest {
    private static final String PICK = "Tool_Pickaxe_Iron";

    /** A stand in stack, since the real constructor looks the item up in an asset store the tests do not load. */
    private static ItemStack pick(double wear) {
        return new ItemStack() {
            @Override
            public String getItemId() {
                return PICK;
            }

            @Override
            public double getDurability() {
                return wear; // named apart from ItemStack's own durability field, which would shadow it
            }

            @Override
            public boolean isBroken() {
                return wear == 0;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }
        };
    }

    private static SlotTransaction set(int slot, ItemStack before, ItemStack after) {
        return new SlotTransaction(true, ActionType.SET, (short) slot, before, after, null, false, false, false);
    }

    private static int repairs(com.hypixel.hytale.server.core.inventory.transaction.Transaction t) {
        InventorySystem.Totals totals = new InventorySystem.Totals();
        InventorySystem.scanWear(t, totals, true);
        return totals.repairs;
    }

    @Test
    void aDurabilityRiseOnItsOwnIsARepair() {
        assertEquals(1, repairs(set(0, pick(50), pick(80))));
    }

    @Test
    void twoCopiesTradingPlacesAreNoRepair() {
        assertEquals(0, repairs(new ListTransaction<>(true, List.of(set(0, pick(50), pick(80)), set(1, pick(80), pick(50))))));
    }

    @Test
    void aRiseWhileAnotherSlotLosesThatToolIsNoRepair() {
        assertEquals(0, repairs(new ListTransaction<>(true, List.of(set(0, pick(50), pick(80)), set(1, pick(80), null)))));
    }

    @Test
    void movesAndSwapsAreSkippedWhole() {
        MoveTransaction<SlotTransaction> move = new MoveTransaction<>(true, set(0, pick(50), pick(80)), MoveType.MOVE_FROM_SELF, null, set(1, pick(80), pick(50)));
        assertEquals(0, repairs(move));
        assertEquals(0, repairs(new ListTransaction<>(true, List.of(move))));
    }

    @Test
    void twoRepairsInOneTransactionBothCount() {
        assertEquals(2, repairs(new ListTransaction<>(true, List.of(set(0, pick(50), pick(80)), set(1, pick(20), pick(100))))));
    }
}
