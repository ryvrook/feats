package dev.ryvrook.feats.track;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Items each player dropped on the ground in the last few minutes, by item id and count. Picking
 * the same items back up takes from here first, so a stack dropped and picked up again does not
 * count as a new pickup.
 */
public final class RecentDrops {
    private static final long WINDOW_NANOS = 5L * 60 * 1_000_000_000L;
    private static final int MAX_ENTRIES = 256;

    private static final class Drop {
        long amount;
        final long at;

        Drop(long amount, long at) {
            this.amount = amount;
            this.at = at;
        }
    }

    private final Map<UUID, Map<String, Deque<Drop>>> drops = new ConcurrentHashMap<>();

    public void record(UUID uuid, String itemId, long amount) {
        if (uuid == null || itemId == null || amount <= 0) {
            return;
        }
        Map<String, Deque<Drop>> mine = drops.computeIfAbsent(uuid, id -> new HashMap<>());
        long now = System.nanoTime();
        synchronized (mine) {
            prune(mine, now);
            if (size(mine) >= MAX_ENTRIES) {
                return; // keep memory bounded, a flood of drops simply stops being remembered
            }
            mine.computeIfAbsent(itemId, id -> new ArrayDeque<>()).addLast(new Drop(amount, now));
        }
    }

    /** Takes up to {@code amount} of this item from the player's recent drops and returns how much was taken. */
    public long take(UUID uuid, String itemId, long amount) {
        if (uuid == null || itemId == null || amount <= 0) {
            return 0;
        }
        Map<String, Deque<Drop>> mine = drops.get(uuid);
        if (mine == null) {
            return 0;
        }
        synchronized (mine) {
            prune(mine, System.nanoTime());
            Deque<Drop> queue = mine.get(itemId);
            if (queue == null) {
                return 0;
            }
            long taken = 0;
            while (taken < amount && !queue.isEmpty()) {
                Drop drop = queue.peekFirst();
                long part = Math.min(amount - taken, drop.amount);
                drop.amount -= part;
                taken += part;
                if (drop.amount <= 0) {
                    queue.removeFirst();
                }
            }
            if (queue.isEmpty()) {
                mine.remove(itemId);
            }
            return taken;
        }
    }

    public void forget(UUID uuid) {
        if (uuid != null) {
            drops.remove(uuid);
        }
    }

    private static void prune(Map<String, Deque<Drop>> mine, long now) {
        Iterator<Deque<Drop>> it = mine.values().iterator();
        while (it.hasNext()) {
            Deque<Drop> queue = it.next();
            while (!queue.isEmpty() && now - queue.peekFirst().at > WINDOW_NANOS) {
                queue.removeFirst();
            }
            if (queue.isEmpty()) {
                it.remove();
            }
        }
    }

    private static int size(Map<String, Deque<Drop>> mine) {
        int n = 0;
        for (Deque<Drop> queue : mine.values()) {
            n += queue.size();
        }
        return n;
    }
}
