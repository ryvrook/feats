package dev.ryvrook.feats.systems;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepworksRunsTest {
    private static final long MINUTE = 60_000L;
    private final UUID p = UUID.randomUUID();

    @Test
    void fastWithinTwentyMinutesOfEntering() {
        DeepworksRuns runs = new DeepworksRuns();
        runs.entered(p, DeepworksRuns.PORTAL_TYPE, "instance-1", 0);
        assertTrue(runs.isFast(p, "instance-1", 19 * MINUTE));
        assertTrue(runs.isFast(p, "instance-1", 20 * MINUTE));
        assertFalse(runs.isFast(p, "instance-1", 20 * MINUTE + 1));
        assertFalse(runs.isFast(p, "instance-2", MINUTE));
    }

    @Test
    void reenteringTheSameInstanceKeepsTheClock() {
        DeepworksRuns runs = new DeepworksRuns();
        runs.entered(p, DeepworksRuns.PORTAL_TYPE, "instance-1", 0);
        runs.entered(p, "Goblin_Breach", "elsewhere", 5 * MINUTE); // not a Deepworks world, nothing changes
        runs.entered(p, DeepworksRuns.PORTAL_TYPE, "instance-1", 15 * MINUTE);
        assertFalse(runs.isFast(p, "instance-1", 25 * MINUTE));
        runs.entered(p, DeepworksRuns.PORTAL_TYPE, "instance-2", 30 * MINUTE);
        assertTrue(runs.isFast(p, "instance-2", 45 * MINUTE));
    }

    @Test
    void dyingKeepsTheEntryTimeButEndsTheCleanRun() {
        DeepworksRuns runs = new DeepworksRuns();
        runs.entered(p, DeepworksRuns.PORTAL_TYPE, "instance-1", 0);
        runs.died(p, "instance-1");
        assertTrue(runs.isFast(p, "instance-1", 10 * MINUTE));
        assertFalse(runs.isClean(p, "instance-1"));
    }

    @Test
    void aRunFirstSeenThroughADeathIsNeverFast() {
        DeepworksRuns runs = new DeepworksRuns();
        runs.died(p, "instance-1");
        assertFalse(runs.isFast(p, "instance-1", 0));
        assertFalse(runs.isFast(null, "instance-1", 0));
        assertTrue(runs.isClean(p, "instance-2"));
    }
}
