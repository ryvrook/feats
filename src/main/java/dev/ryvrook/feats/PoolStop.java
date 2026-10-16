package dev.ryvrook.feats;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The bounded wait a stop gives Feats' own threads. The plugin manager calls the shutdown under the write side of
 * its lock, so the whole server stop waits for it. Saves of the players online are written inline by the shutdown
 * before this runs, and a queued file task takes milliseconds, so a task still running after
 * {@link #GRACE_MILLIS} is stuck. It is interrupted, given {@link #INTERRUPT_MILLIS} more and then left behind as a
 * daemon thread rather than holding up the stop.
 */
public final class PoolStop {
    /** How long a stop lets the queue finish before it interrupts the running task. */
    public static final long GRACE_MILLIS = 1_000;
    /** How long a stop waits after the interrupt. */
    public static final long INTERRUPT_MILLIS = 500;

    private PoolStop() {
    }

    /**
     * Shuts the pool down and waits {@code graceMillis} for its queue, then interrupts {@code worker} and waits
     * {@code interruptMillis} more. The queue is kept, so work after an interrupted task still runs. A second call
     * does not wait again. Returns true when the pool ended.
     */
    public static boolean stop(ExecutorService pool, Supplier<Thread> worker, long graceMillis, long interruptMillis) {
        if (pool == null) {
            return true;
        }
        if (pool.isShutdown()) {
            return pool.isTerminated();
        }
        pool.shutdown();
        try {
            if (pool.awaitTermination(graceMillis, TimeUnit.MILLISECONDS)) {
                return true;
            }
            Thread t = worker == null ? null : worker.get();
            if (t != null) {
                t.interrupt();
            }
            return pool.awaitTermination(interruptMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return pool.isTerminated();
        }
    }
}
