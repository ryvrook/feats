package dev.ryvrook.feats.track;

import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionClockTest {
    @Test
    void secondsSinceTheFirstReady() {
        SessionClock clock = new SessionClock();
        UUID p = UUID.randomUUID();
        clock.start(p, 10_000);
        clock.start(p, 50_000); // a world change fires PlayerReady again and must not restart it
        assertEquals(90, clock.seconds(p, 100_999));
        assertEquals(90, clock.end(p, 100_000));
        assertFalse(clock.isRunning(p));
        assertEquals(0, clock.end(p, 200_000));
        assertEquals(0, clock.seconds(null, 0));
    }

    @Test
    void longestSessionOnlyGrows() {
        FeatsTracker tracker = TestTrackers.tracker();
        SessionClock clock = new SessionClock();
        UUID p = UUID.randomUUID();
        PlayerProgress progress = tracker.load(p, "Sitter");
        clock.start(p, 0);
        tracker.raiseTo(p, StatKeys.LONGEST_SESSION, clock.seconds(p, 300_000)); // the five minute write
        assertEquals(300, progress.get(StatKeys.LONGEST_SESSION));
        tracker.raiseTo(p, StatKeys.LONGEST_SESSION, clock.end(p, 3_600_000)); // the disconnect
        assertEquals(3_600, progress.get(StatKeys.LONGEST_SESSION));
        clock.start(p, 4_000_000);
        tracker.raiseTo(p, StatKeys.LONGEST_SESSION, clock.end(p, 4_060_000));
        assertEquals(3_600, progress.get(StatKeys.LONGEST_SESSION));
        assertTrue(tracker.isPersisted(StatKeys.LONGEST_SESSION));
    }

    @Test
    void aReloadPicksTheSessionBackUp() {
        SessionClock clock = new SessionClock();
        UUID p = UUID.randomUUID();
        long sixHours = 6 * 3_600_000L;
        assertEquals(1_000, clock.resume(p, 1_000, 1_000 + sixHours)); // the start saved by the old instance
        assertEquals(9 * 3_600, clock.end(p, 1_000 + sixHours + 3 * 3_600_000L), "6 h, a reload, then 3 h is one session");
        assertEquals(5_000, clock.resume(p, 0, 5_000), "nothing saved starts now");
        clock.end(p, 5_000);
        assertEquals(5_000, clock.resume(p, 9_000, 5_000), "a start in the future is not trusted");
    }

    @Test
    void longestSessionCountsWhilePausedWhenPlaytimeDoes() {
        FeatsTracker tracker = TestTrackers.tracker();
        UUID p = UUID.randomUUID();
        PlayerProgress progress = tracker.load(p, "Builder");
        tracker.setPaused(p, true);
        tracker.raiseTo(p, StatKeys.LONGEST_SESSION, 100);
        assertEquals(0, progress.get(StatKeys.LONGEST_SESSION), "paused with PausePlaytimeWhenDisabled on");
        tracker.setPausePlaytime(false);
        tracker.raiseTo(p, StatKeys.LONGEST_SESSION, 100);
        assertEquals(100, progress.get(StatKeys.LONGEST_SESSION));
    }
}
