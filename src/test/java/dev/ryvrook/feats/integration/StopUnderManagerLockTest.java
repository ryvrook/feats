package dev.ryvrook.feats.integration;

import dev.ryvrook.feats.PoolStop;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plugin manager holds the write side of its lock for a whole server stop and calls Feats' shutdown under it.
 * A timer or IO task that looked a plugin up then parked on the read side, and the stop waited for it.
 */
class StopUnderManagerLockTest {
    private static final Object DOMINION = new Object();

    /** A look that takes the read lock like PluginManager.getPlugin does. */
    private static Supplier<Object> engineLike(ReentrantReadWriteLock rw) {
        return () -> {
            rw.readLock().lock();
            try {
                return DOMINION;
            } finally {
                rw.readLock().unlock();
            }
        };
    }

    /** Holds the write lock on another thread, as the engine's stop does, until the returned latch counts down. */
    private static CountDownLatch holdWrite(ReentrantReadWriteLock rw) throws InterruptedException {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            rw.writeLock().lock();
            try {
                held.countDown();
                release.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                rw.writeLock().unlock();
            }
        }, "manager-stop");
        t.setDaemon(true);
        t.start();
        assertTrue(held.await(5, TimeUnit.SECONDS));
        return release;
    }

    @Test
    void aLookupUnderTheWriteLockAnswersTheLastStateAtOnce() throws Exception {
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        GatedLookup lookup = new GatedLookup(() -> rw, () -> false);
        assertSame(DOMINION, lookup.find("Dominion", engineLike(rw)));
        CountDownLatch release = holdWrite(rw);
        try {
            long t0 = System.nanoTime();
            assertSame(DOMINION, lookup.find("Dominion", engineLike(rw)), "the last answer, so no binding drops");
            assertNull(lookup.find("Chronicle", engineLike(rw)), "nothing known yet is null");
            assertTrue(System.nanoTime() - t0 < TimeUnit.MILLISECONDS.toNanos(200), "never waits for the manager");
        } finally {
            release.countDown();
        }
    }

    @Test
    void theOwnerOfTheWriteLockStillLooksUp() {
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        GatedLookup lookup = new GatedLookup(() -> rw, () -> false);
        rw.writeLock().lock();
        try {
            assertSame(DOMINION, lookup.find("Dominion", engineLike(rw)));
        } finally {
            rw.writeLock().unlock();
        }
    }

    @Test
    void aStoppingServerIsNotAskedAndAMissingLockIsNotGated() {
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        AtomicBoolean stopping = new AtomicBoolean();
        GatedLookup lookup = new GatedLookup(() -> rw, stopping::get);
        assertSame(DOMINION, lookup.find("Dominion", engineLike(rw)));
        stopping.set(true);
        assertSame(DOMINION, lookup.find("Dominion", () -> {
            throw new AssertionError("looked while stopping");
        }));
        GatedLookup open = new GatedLookup(() -> null, () -> false);
        assertSame(DOMINION, open.find("Dominion", engineLike(rw)));
    }

    @Test
    void stopIsBoundedEvenWithAStuckIoTask() throws Exception {
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        AtomicReference<Thread> worker = new AtomicReference<>();
        ExecutorService io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Feats-IO");
            t.setDaemon(true);
            worker.set(t);
            return t;
        });
        CountDownLatch release = holdWrite(rw);
        CountDownLatch entered = new CountDownLatch(1);
        // An ungated engine lookup, the old behaviour, parks uninterruptibly on the read lock.
        io.execute(() -> {
            entered.countDown();
            engineLike(rw).get();
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        try {
            long t0 = System.nanoTime();
            assertFalse(PoolStop.stop(io, worker::get, PoolStop.GRACE_MILLIS, PoolStop.INTERRUPT_MILLIS),
                    "the stuck task is left behind");
            assertFalse(PoolStop.stop(io, worker::get, PoolStop.GRACE_MILLIS, PoolStop.INTERRUPT_MILLIS),
                    "a second call does not wait again");
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            assertTrue(ms < 2_000, "stop took " + ms + " ms");
        } finally {
            release.countDown();
        }
        assertTrue(io.awaitTermination(5, TimeUnit.SECONDS));
        worker.get().join(5_000);
        assertEquals(Thread.State.TERMINATED, worker.get().getState());
    }
}
