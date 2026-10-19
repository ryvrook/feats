package dev.ryvrook.feats.systems;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveType;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.UnlockNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

import static dev.ryvrook.feats.systems.MoveSidesTest.GLIDER;
import static dev.ryvrook.feats.systems.MoveSidesTest.KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stash balance of the own tracking path, and the rules that decide whether a take is ever obtained. */
class StashBalanceTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String SHARD = "Ingredient_Rune_Shard";
    private static FeatRegistry registry;

    private FeatsTracker tracker;
    private StashBalance stash;
    private UUID p;
    private PlayerProgress progress;

    private static synchronized FeatRegistry registry() {
        if (registry == null) {
            registry = new FeatRegistry();
        }
        return registry;
    }

    @BeforeEach
    void setUp() throws IOException {
        ProgressStore store = new ProgressStore(Files.createTempDirectory("feats-stash"), LOGGER);
        tracker = new FeatsTracker(registry(), store, new UnlockNotifier(() -> null, LOGGER), LOGGER, Runnable::run);
        stash = new StashBalance(tracker);
        p = UUID.randomUUID();
        progress = tracker.load(p, "Stash");
    }

    /** Runs one move through the balance as the inventory system does, and returns what counted as obtained. */
    private long apply(MoveTransaction<SlotTransaction> move, boolean placed) {
        MoveSides sides = MoveSides.of(move);
        if (sides.left != null) {
            sides.left.forEach((id, n) -> stash.put(p, id, n));
        }
        long fresh = 0;
        if (sides.arrived != null) {
            for (Map.Entry<String, Long> e : sides.arrived.entrySet()) {
                fresh += stash.fresh(p, e.getKey(), e.getValue(), placed);
            }
        }
        return fresh;
    }

    @Test
    void theSwapLoopCountsOnceInEitherDirection() {
        // A world generated chest held the key. Swap it for the glider from the chest side, then back from the
        // player side, many times over. Only the first look at the key is new.
        long fresh = 0;
        for (int round = 0; round < 50; round++) {
            fresh += apply(round % 2 == 0
                    ? MoveSidesTest.swap(MoveType.MOVE_TO_SELF, new MoveSidesTest.Stack(GLIDER, 1), new MoveSidesTest.Stack(KEY, 1))
                    : MoveSidesTest.swap(MoveType.MOVE_FROM_SELF, new MoveSidesTest.Stack(KEY, 1), new MoveSidesTest.Stack(GLIDER, 1)), false);
        }
        assertEquals(1, fresh, "the key once, the glider never");
        assertEquals(1, stash.balance(p, GLIDER) + stash.balance(p, KEY));

        // The player starting the swap first.
        UUID q = UUID.randomUUID();
        tracker.load(q, "Other");
        p = q;
        long fresh2 = 0;
        for (int round = 0; round < 50; round++) {
            fresh2 += apply(round % 2 == 0
                    ? MoveSidesTest.swap(MoveType.MOVE_FROM_SELF, new MoveSidesTest.Stack(GLIDER, 1), new MoveSidesTest.Stack(KEY, 1))
                    : MoveSidesTest.swap(MoveType.MOVE_TO_SELF, new MoveSidesTest.Stack(KEY, 1), new MoveSidesTest.Stack(GLIDER, 1)), false);
        }
        assertEquals(1, fresh2);
    }

    @Test
    void puttingAwayAndTakingBackIsNeverObtained() {
        stash.put(p, SHARD, 30);
        assertEquals(0, stash.fresh(p, SHARD, 20, false));
        assertEquals(5, stash.fresh(p, SHARD, 15, false), "10 left on the balance, 5 are new");
        assertEquals(0, stash.balance(p, SHARD));
        assertEquals(0, progress.get(Classify.stashKey(SHARD)));
    }

    @Test
    void aTakeFromAPlacedContainerIsNeverObtainedAndStillLowersTheBalance() {
        stash.put(p, SHARD, 10);
        assertEquals(0, stash.fresh(p, SHARD, 4, true));
        assertEquals(6, stash.balance(p, SHARD));
        assertEquals(0, stash.fresh(p, SHARD, 50, true), "more than the balance, still nothing new");
        assertEquals(0, stash.balance(p, SHARD));
        assertEquals(0, stash.fresh(p, "Rock_Stone", 64, true), "an item no feat watches is never new either");
    }

    @Test
    void aTakeFromAGeneratedContainerCountsWhatTheBalanceDoesNotCover() {
        assertEquals(12, stash.fresh(p, SHARD, 12, false));
        assertEquals(64, stash.fresh(p, "Rock_Stone", 64, false), "an unwatched item keeps no balance");
    }

    @Test
    void aCreativeDepositIsNotObtainedWhenTakenOutInAdventure() {
        stash.creative(p, Map.of(SHARD, 40L), null);
        assertEquals(40, stash.balance(p, SHARD));
        assertEquals(0, stash.fresh(p, SHARD, 40, false));
        // A Creative take lowers the balance and is not obtained.
        stash.put(p, SHARD, 10);
        stash.creative(p, null, Map.of(SHARD, 4L));
        assertEquals(6, stash.balance(p, SHARD));
    }

    @Test
    void placedContainersAndBenchInputsNeverCountButBenchOutputsAndUnplacedBlocksDo() {
        UUID placer = UUID.randomUUID();
        assertTrue(InventorySystem.neverObtained(true, false, false, placer), "a placed chest");
        assertTrue(InventorySystem.neverObtained(false, true, false, placer), "a placed bench's input or fuel");
        assertFalse(InventorySystem.neverObtained(false, true, true, placer), "a placed bench's output");
        assertFalse(InventorySystem.neverObtained(true, false, false, null), "a world generated chest");
        assertFalse(InventorySystem.neverObtained(false, true, false, null), "a world generated bench");
        assertFalse(InventorySystem.neverObtained(false, false, false, placer), "a block window that is neither");
    }

    @Test
    void benchSlotsAreToldApartOnTheCombinedContainer() {
        ItemContainer input = new SimpleItemContainer((short) 2);
        ItemContainer fuel = new SimpleItemContainer((short) 1);
        ItemContainer output = new SimpleItemContainer((short) 3);
        CombinedItemContainer combined = new CombinedItemContainer(input, fuel, output);
        assertFalse(InventorySystem.benchOutput(output, input, fuel, combined, input, 0));
        assertFalse(InventorySystem.benchOutput(output, input, fuel, combined, fuel, 0));
        assertTrue(InventorySystem.benchOutput(output, input, fuel, combined, output, 1));
        assertFalse(InventorySystem.benchOutput(output, input, fuel, combined, combined, 1), "slot 1 is input");
        assertFalse(InventorySystem.benchOutput(output, input, fuel, combined, combined, 2), "slot 2 is fuel");
        assertTrue(InventorySystem.benchOutput(output, input, fuel, combined, combined, 4), "slot 4 is output");
        assertTrue(InventorySystem.benchOutput(output, input, fuel, combined, combined, -1), "unknown counts against the balance");
    }
}
