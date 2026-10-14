package dev.ryvrook.feats.systems;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The last few uses of a Goblin Lab device, newest last, so {@link FlagWatcher} can name the player who
 * repaired one. Fed by {@link UseBlockSystem} or by the TrackerAPI bridge, whichever path is active.
 * Safe from any thread.
 */
public final class LabUses {
    /** The Goblin Lab devices that raise a server wide flag once repaired. */
    private static final List<String> LAB_DEVICES = List.of("GoblinLab_Tinkering_Bench_Recipeset_", "GoblinLab_Generator");
    private static final int LAB_RING_SIZE = 16;

    /** One use of a Goblin Lab device. */
    public record LabUse(UUID uuid, String blockId, long at) {
    }

    /** Guarded by itself. */
    private final Deque<LabUse> labUses = new ArrayDeque<>(LAB_RING_SIZE);

    /** Notes a block use when the block is a Goblin Lab device, at {@code atMillis}. */
    public void remember(UUID uuid, String blockId, long atMillis) {
        if (blockId == null) {
            return;
        }
        String base = blockId.startsWith("*") ? blockId.substring(1) : blockId;
        for (String device : LAB_DEVICES) {
            if (base.startsWith(device)) {
                synchronized (labUses) {
                    if (labUses.size() >= LAB_RING_SIZE) {
                        labUses.removeFirst();
                    }
                    labUses.addLast(new LabUse(uuid, base, atMillis));
                }
                return;
            }
        }
    }

    /**
     * The player who last used a Goblin Lab device within {@code withinMillis}, preferring a use of a
     * block whose id starts with {@code preferredPrefix}. Null when nobody did.
     */
    public UUID lastLabUser(String preferredPrefix, long withinMillis) {
        long since = System.currentTimeMillis() - withinMillis;
        UUID any = null;
        synchronized (labUses) {
            Iterator<LabUse> newestFirst = labUses.descendingIterator();
            while (newestFirst.hasNext()) {
                LabUse use = newestFirst.next();
                if (use.at() < since) {
                    break;
                }
                if (preferredPrefix != null && use.blockId().startsWith(preferredPrefix)) {
                    return use.uuid();
                }
                if (any == null) {
                    any = use.uuid();
                }
            }
        }
        return any;
    }
}
