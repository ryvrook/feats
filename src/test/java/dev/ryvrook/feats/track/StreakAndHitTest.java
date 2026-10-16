package dev.ryvrook.feats.track;

import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreakAndHitTest {
    @Test
    void streakCountsKillsAndResetsOnDeath() {
        KillStreaks streaks = new KillStreaks();
        UUID p = UUID.randomUUID();
        assertEquals(1, streaks.kill(p));
        assertEquals(2, streaks.kill(p));
        streaks.died(p);
        assertEquals(0, streaks.current(p));
        assertEquals(1, streaks.kill(p));
        streaks.forget(p);
        assertEquals(0, streaks.size());
        assertEquals(0, streaks.kill(null));
    }

    @Test
    void bestStreakIsKeptAcrossDeaths() {
        FeatsTracker tracker = TestTrackers.tracker();
        UUID p = UUID.randomUUID();
        PlayerProgress progress = tracker.load(p, "Streaker");
        for (int i = 0; i < 7; i++) {
            tracker.countKill(p);
        }
        assertEquals(7, progress.get(StatKeys.KILL_STREAK));
        tracker.countDeath(p);
        for (int i = 0; i < 3; i++) {
            tracker.countKill(p);
        }
        assertEquals(7, progress.get(StatKeys.KILL_STREAK), "a shorter run never lowers the best");
        assertEquals(3, tracker.killStreaks().current(p));
        for (int i = 0; i < 5; i++) {
            tracker.countKill(p);
        }
        assertEquals(8, progress.get(StatKeys.KILL_STREAK));
    }

    @Test
    void streakSurvivesARelogButNotAPause() {
        FeatsTracker tracker = TestTrackers.tracker();
        UUID p = UUID.randomUUID();
        tracker.load(p, "Relog");
        tracker.countKill(p);
        tracker.countKill(p);
        tracker.unload(p);
        PlayerProgress back = tracker.load(p, "Relog");
        tracker.countKill(p);
        assertEquals(3, back.get(StatKeys.KILL_STREAK));
        tracker.setPaused(p, true);
        tracker.countKill(p);
        assertEquals(3, tracker.killStreaks().current(p), "Creative kills do not extend the streak");
        tracker.countDeath(p);
        assertEquals(3, tracker.killStreaks().current(p), "and a Creative death does not end it");
        tracker.setPaused(p, false);
        tracker.countDeath(p);
        assertEquals(0, tracker.killStreaks().current(p));
    }

    @Test
    void playerKillsHaveTheirOwnStreak() {
        KillStreaks streaks = new KillStreaks();
        UUID p = UUID.randomUUID();
        assertEquals(1, streaks.kill(p));
        assertEquals(1, streaks.playerKill(p));
        assertEquals(2, streaks.kill(p));
        assertEquals(2, streaks.playerKill(p));
        assertEquals(2, streaks.current(p));
        assertEquals(2, streaks.currentPlayerKills(p));
        streaks.died(p);
        assertEquals(0, streaks.currentPlayerKills(p));
        assertEquals(1, streaks.playerKill(p));
        assertEquals(0, streaks.playerKill(null));
    }

    @Test
    void bestPlayerStreakIsKeptAcrossDeaths() {
        FeatsTracker tracker = TestTrackers.tracker();
        UUID p = UUID.randomUUID();
        PlayerProgress progress = tracker.load(p, "Duelist");
        for (int i = 0; i < 4; i++) {
            tracker.countKill(p);
            tracker.raiseTo(p, StatKeys.PLAYER_KILL_STREAK, tracker.killStreaks().playerKill(p));
        }
        assertEquals(4, progress.get(StatKeys.PLAYER_KILL_STREAK));
        assertEquals(4, progress.get(StatKeys.KILL_STREAK));
        tracker.countDeath(p);
        assertEquals(0, tracker.killStreaks().currentPlayerKills(p), "a death ends both streaks");
        tracker.raiseTo(p, StatKeys.PLAYER_KILL_STREAK, tracker.killStreaks().playerKill(p));
        assertEquals(4, progress.get(StatKeys.PLAYER_KILL_STREAK), "a shorter run never lowers the best");
    }

    @Test
    void biggestHitKeepsTheLargest() {
        FeatsTracker tracker = TestTrackers.tracker();
        UUID p = UUID.randomUUID();
        PlayerProgress progress = tracker.load(p, "Hitter");
        tracker.raiseTo(p, StatKeys.BIGGEST_HIT, 12);
        tracker.raiseTo(p, StatKeys.BIGGEST_HIT, 40);
        tracker.raiseTo(p, StatKeys.BIGGEST_HIT, 25);
        assertEquals(40, progress.get(StatKeys.BIGGEST_HIT));
    }
}
