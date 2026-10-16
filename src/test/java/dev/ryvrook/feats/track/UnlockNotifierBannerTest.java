package dev.ryvrook.feats.track;

import dev.ryvrook.feats.FeatsConfig;
import dev.ryvrook.feats.model.Category;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.Requirement;
import dev.ryvrook.feats.model.Scope;
import dev.ryvrook.feats.model.Tier;
import dev.ryvrook.feats.model.Trigger;
import dev.ryvrook.feats.stats.PlayerProgress;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The notifier's banner path without a server. The engine calls are skipped, the rules still run. The
 * removal timing runs on a hand driven clock and a fake world that takes the banner down when told.
 */
class UnlockNotifierBannerTest {
    private static Feat gold(String id) {
        return new Feat(id, id, "do " + id, Category.EXPLORATION, Tier.GOLD, "Tool_Map",
                List.of(new Requirement("k", 1)), false);
    }

    private static Feat milestone(String id) {
        return new Feat(id, id, "the server did " + id, Category.WORLD, Tier.GOLD, "Tool_Map", List.of(), false,
                Scope.WORLD, Trigger.total("k", 10));
    }

    @Test
    void reasonFollowsTheSettingsAndTheRarityHook() {
        FeatsConfig cfg = new FeatsConfig();
        UnlockNotifier notifier = new UnlockNotifier(() -> cfg, TestTrackers.LOGGER);
        assertEquals("RARE", cfg.banner(), "the default setting");
        assertNull(notifier.bannerReason(cfg, gold("plain")), "rarity unknown and no flag");
        assertEquals("Gold", notifier.bannerReason(cfg, gold("flagged").withBanner()));
        notifier.setRarity(id -> id.equals("scarce") ? 0.004 : 0.5);
        assertEquals("Rare, under 1% of players", notifier.bannerReason(cfg, gold("scarce")));
        assertNull(notifier.bannerReason(cfg, gold("common")));
        assertEquals("Server milestone", notifier.bannerReason(cfg, milestone("m")));
        notifier.setRarity(id -> {
            throw new IllegalStateException("export not ready");
        });
        assertNull(notifier.bannerReason(cfg, gold("plain")), "a failing rarity hook reads as unknown");
        assertNull(notifier.bannerReason(null, gold("plain")));
        assertNull(notifier.bannerReason(cfg, null));
    }

    @Test
    void nothingThrowsWithoutAServer() {
        FeatsConfig cfg = new FeatsConfig();
        UnlockNotifier notifier = new UnlockNotifier(() -> cfg, TestTrackers.LOGGER);
        PlayerProgress progress = new PlayerProgress(UUID.randomUUID(), "Ada");
        Feat feat = gold("plain").withBanner();
        assertDoesNotThrow(() -> notifier.unlocked(progress, feat, 1, 2));
        assertDoesNotThrow(() -> notifier.recordBanner(milestone("m"), UUID.randomUUID(), "Ada"));
        assertDoesNotThrow(() -> notifier.recordBanner(milestone("m"), null, null));
        assertDoesNotThrow(() -> notifier.recordBanner(null, null, null));
        UnlockNotifier noConfig = new UnlockNotifier(() -> null, TestTrackers.LOGGER);
        assertDoesNotThrow(() -> noConfig.recordBanner(milestone("m"), null, "Ada"));
        assertDoesNotThrow(() -> noConfig.setNotifications(null));
        assertDoesNotThrow(() -> noConfig.setClock(null));
    }

    private static final long STAY = 6_000;

    /** A scheduler on a clock the test moves by hand. */
    private static final class Time implements UnlockNotifier.Scheduler {
        private static final class Task {
            final long due;
            final Runnable run;
            boolean cancelled;

            Task(long due, Runnable run) {
                this.due = due;
                this.run = run;
            }
        }

        long now;
        final List<Task> tasks = new ArrayList<>();

        @Override
        public UnlockNotifier.Timer schedule(Runnable task, long delayMillis) {
            Task t = new Task(now + delayMillis, task);
            tasks.add(t);
            return () -> t.cancelled = true;
        }

        /** Moves the clock on, running each task when its time comes, earliest first. */
        void advance(long millis) {
            long target = now + millis;
            while (true) {
                Task next = null;
                for (Task t : tasks) {
                    if (!t.cancelled && t.due <= target && (next == null || t.due < next.due)) {
                        next = t;
                    }
                }
                if (next == null) {
                    break;
                }
                tasks.remove(next);
                now = next.due;
                next.run.run();
            }
            now = target;
        }
    }

    /** The player's world. Posted removals wait in a queue until the test runs the world. */
    private static final class World implements UnlockNotifier.Remover {
        UnlockNotifier.Attempt mode = UnlockNotifier.Attempt.POSTED;
        boolean dropTasks;
        boolean hudUp;
        int posts;
        final List<Runnable> queued = new ArrayList<>();

        @Override
        public UnlockNotifier.Attempt post(UUID uuid, BooleanSupplier wanted, Runnable removed) {
            posts++;
            if (mode != UnlockNotifier.Attempt.POSTED) {
                return mode;
            }
            if (!dropTasks) {
                queued.add(() -> {
                    if (wanted.getAsBoolean()) {
                        hudUp = false;
                        removed.run();
                    }
                });
            }
            return mode;
        }

        void run() {
            List<Runnable> now = new ArrayList<>(queued);
            queued.clear();
            now.forEach(Runnable::run);
        }
    }

    private Time time;
    private World world;

    private UnlockNotifier timed() {
        FeatsConfig cfg = new FeatsConfig();
        UnlockNotifier notifier = new UnlockNotifier(() -> cfg, TestTrackers.LOGGER);
        time = new Time();
        world = new World();
        notifier.setClock(() -> time.now);
        notifier.setScheduler(time);
        notifier.setRemover(world);
        return notifier;
    }

    @Test
    void theRemovalIsTimedEvenWhenTheAddNeverHappened() {
        UnlockNotifier notifier = timed();
        UUID p = UUID.randomUUID();
        BannerQueue.Shown a = notifier.grant(p, STAY);
        world.hudUp = true; // banner A was added
        time.advance(2_000);
        BannerQueue.Shown b = notifier.grant(p, STAY); // B is granted while the player changes world, its add bails
        assertNotNull(b);
        assertTrue(b.generation() > a.generation());
        time.advance(4_000); // A's time
        assertEquals(0, world.posts, "A's timer was replaced and does nothing");
        time.advance(2_000); // B's time
        assertEquals(1, world.posts, "B's timer runs although B was never added");
        world.run();
        assertFalse(world.hudUp, "what was on screen, A, is taken down");
        assertFalse(notifier.banners().pending(p, b.generation()));
        time.advance(5_000);
        assertEquals(0, notifier.timersWaiting());
    }

    @Test
    void aRemovalWaitsForTheWorldAndIsMarkedOnlyOnceTheHudWent() {
        UnlockNotifier notifier = timed();
        UUID p = UUID.randomUUID();
        BannerQueue.Shown shown = notifier.grant(p, STAY);
        world.hudUp = true;
        world.mode = UnlockNotifier.Attempt.WAIT; // between worlds
        time.advance(STAY);
        assertEquals(1, world.posts);
        assertTrue(notifier.banners().pending(p, shown.generation()), "not marked gone while the player has no world");
        time.advance(3_000);
        assertEquals(4, world.posts, "tried again once a second");
        world.mode = UnlockNotifier.Attempt.POSTED;
        world.dropTasks = true; // the world closes and drops the task
        time.advance(1_000);
        assertEquals(5, world.posts);
        assertTrue(world.hudUp);
        assertTrue(notifier.banners().pending(p, shown.generation()), "a dropped task leaves the banner pending");
        world.dropTasks = false;
        time.advance(1_000);
        assertEquals(6, world.posts, "the next try reaches the new world");
        world.run();
        assertFalse(world.hudUp);
        assertFalse(notifier.banners().pending(p, shown.generation()));
        time.advance(1_000);
        assertEquals(6, world.posts, "nothing more once it is down");
        assertEquals(0, notifier.timersWaiting());
    }

    @Test
    void aRemovalGivesUpAfterTenSeconds() {
        UnlockNotifier notifier = timed();
        UUID p = UUID.randomUUID();
        BannerQueue.Shown shown = notifier.grant(p, STAY);
        world.mode = UnlockNotifier.Attempt.WAIT;
        time.advance(STAY + 20_000);
        assertEquals(UnlockNotifier.RETRIES + 1, world.posts, "the first try and ten more, a second apart");
        assertFalse(notifier.banners().pending(p, shown.generation()));
        assertEquals(0, notifier.timersWaiting());
    }

    @Test
    void anOfflinePlayerHasNothingLeftToTakeDown() {
        UnlockNotifier notifier = timed();
        UUID p = UUID.randomUUID();
        BannerQueue.Shown shown = notifier.grant(p, STAY);
        world.mode = UnlockNotifier.Attempt.GONE;
        time.advance(STAY + 5_000);
        assertEquals(1, world.posts);
        assertFalse(notifier.banners().pending(p, shown.generation()));
    }

    @Test
    void aTimerFromBeforeAReconnectLeavesTheNewBannerAlone() {
        UnlockNotifier notifier = timed();
        UUID p = UUID.randomUUID();
        notifier.grant(p, STAY);
        notifier.banners().clear(p); // the player left
        time.advance(1_000);
        BannerQueue.Shown again = notifier.grant(p, STAY); // back, a new banner due at 7 s
        world.hudUp = true;
        time.advance(5_000); // the old timer fires at 6 s
        assertEquals(0, world.posts, "the old timer does not touch the new banner");
        assertTrue(notifier.banners().pending(p, again.generation()));
        time.advance(1_000);
        assertEquals(1, world.posts);
        world.run();
        assertFalse(world.hudUp);
    }

    @Test
    void aRemovalPostedBeforeALaterBannerSkipsIt() {
        UnlockNotifier notifier = timed();
        UUID p = UUID.randomUUID();
        notifier.grant(p, STAY);
        world.hudUp = true;
        time.advance(STAY);
        assertEquals(1, world.posts);
        BannerQueue.Shown later = notifier.grant(p, STAY); // arrives before the world ran the removal
        world.run();
        assertTrue(world.hudUp, "the queued removal sees the later banner and leaves it");
        time.advance(STAY);
        world.run();
        assertFalse(world.hudUp);
        assertFalse(notifier.banners().pending(p, later.generation()));
    }

    @Test
    void stopCallsOffEveryTimerAndTakesTheBannersDown() {
        UnlockNotifier notifier = timed();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        notifier.grant(a, STAY);
        notifier.grant(b, STAY);
        world.hudUp = true;
        assertEquals(2, notifier.timersWaiting());
        notifier.stop();
        assertEquals(0, notifier.timersWaiting());
        assertEquals(2, world.posts, "one removal per banner still up");
        world.run();
        assertFalse(world.hudUp);
        time.advance(STAY + 20_000);
        assertEquals(2, world.posts, "no old timer fires after stop");
        assertNull(notifier.grant(a, STAY), "no banner after stop");
        assertEquals(0, notifier.timersWaiting());
        assertDoesNotThrow(notifier::stop);
    }

    @Test
    void aFailingSchedulerOrWorldNeverThrows() {
        UnlockNotifier notifier = timed();
        notifier.setRemover((uuid, wanted, removed) -> {
            throw new IllegalStateException("world closed");
        });
        UUID p = UUID.randomUUID();
        notifier.grant(p, STAY);
        assertDoesNotThrow(() -> time.advance(STAY + 20_000));
        assertTrue(notifier.banners().withBannerUp().isEmpty(), "gave up after the tries");
        notifier.setScheduler((task, delay) -> {
            throw new java.util.concurrent.RejectedExecutionException("shut down");
        });
        assertDoesNotThrow(() -> notifier.grant(UUID.randomUUID(), STAY));
        assertDoesNotThrow(notifier::stop);
    }
}
