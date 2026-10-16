package dev.ryvrook.feats.track;

import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.systems.CombatMemory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatMemoryTest {
    private final UUID p = UUID.randomUUID();

    @Test
    void aFallWithNoDeathIsSurvivedAfterItSettles() {
        FeatsTracker tracker = TestTrackers.tracker();
        PlayerProgress progress = tracker.load(p, "Faller");
        CombatMemory memory = new CombatMemory(tracker);
        memory.fall(p, 32, 1_000);
        memory.flush(1_500);
        assertEquals(0, progress.get(StatKeys.FALL_SURVIVED_MAX), "too soon to tell");
        memory.flush(1_000 + CombatMemory.SETTLE_MILLIS);
        assertEquals(32, progress.get(StatKeys.FALL_SURVIVED_MAX));
        memory.fall(p, 12, 5_000);
        memory.flush(7_000);
        assertEquals(32, progress.get(StatKeys.FALL_SURVIVED_MAX), "a smaller fall never lowers the record");
    }

    @Test
    void aFatalFallIsNotSurvived() {
        FeatsTracker tracker = TestTrackers.tracker();
        PlayerProgress progress = tracker.load(p, "Splat");
        CombatMemory memory = new CombatMemory(tracker);
        memory.fall(p, 80, 1_000);
        memory.died(p);
        memory.flush(3_000);
        assertEquals(0, progress.get(StatKeys.FALL_SURVIVED_MAX));
    }

    @Test
    void aDrowningEpisodeCountsOnceWhenItEnds() {
        FeatsTracker tracker = TestTrackers.tracker();
        PlayerProgress progress = tracker.load(p, "Gasp");
        CombatMemory memory = new CombatMemory(tracker);
        memory.drowning(p, 1_000);
        memory.drowning(p, 2_000);
        memory.drowning(p, 3_000);
        memory.flush(4_000);
        assertEquals(0, progress.get(StatKeys.DROWNING_SURVIVED), "still under water a second ago");
        memory.flush(3_000 + CombatMemory.EPISODE_GAP_MILLIS);
        assertEquals(1, progress.get(StatKeys.DROWNING_SURVIVED));
        memory.flush(20_000);
        assertEquals(1, progress.get(StatKeys.DROWNING_SURVIVED), "the episode is spent");
        memory.drowning(p, 30_000);
        memory.died(p);
        memory.flush(40_000);
        assertEquals(1, progress.get(StatKeys.DROWNING_SURVIVED), "drowned, so not survived");
    }

    @Test
    void theLastProjectileHitIsRememberedBriefly() {
        FeatsTracker tracker = TestTrackers.tracker();
        CombatMemory memory = new CombatMemory(tracker);
        long now = 10_000_000_000L;
        assertEquals(-1, memory.rangedDistance(p, now));
        memory.rangedHit(p, 61.5, now);
        assertEquals(61.5, memory.rangedDistance(p, now + 1_000_000_000L));
        assertEquals(-1, memory.rangedDistance(p, now + 3_000_000_000L), "too old to be the killing blow");
        memory.rangedHit(p, -1, now + 2_500_000_000L);
        assertEquals(-1, memory.rangedDistance(p, now + 2_500_000_000L), "a negative distance is ignored, it does not refresh the old hit");
    }

    @Test
    void forgetDropsTheNotesButNotTheGrudge() {
        FeatsTracker tracker = TestTrackers.tracker();
        PlayerProgress progress = tracker.load(p, "Leaver");
        CombatMemory memory = new CombatMemory(tracker);
        UUID other = UUID.randomUUID();
        memory.fall(p, 40, 0);
        memory.lastKillers().killed(other, p);
        memory.windows().kill(p, 0);
        memory.forget(p);
        memory.flush(10_000);
        assertEquals(0, progress.get(StatKeys.FALL_SURVIVED_MAX));
        assertEquals(0, memory.size());
        assertEquals(0, memory.windows().size());
        assertEquals(other, memory.lastKillers().lastKillerOf(p));
    }
}
