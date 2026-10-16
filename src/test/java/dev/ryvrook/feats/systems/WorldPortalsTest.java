package dev.ryvrook.feats.systems;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldPortalsTest {
    private final UUID p = UUID.randomUUID();

    @Test
    void leavingATimedWorldWithUnderAMinuteLeftIsACloseExit() {
        WorldPortals portals = new WorldPortals();
        portals.inside(p, "breach-1", true, 120.0, 1_000);
        assertFalse(portals.elsewhere(p, "orbis", 2_000), "two minutes left is not close");
        portals.inside(p, "breach-1", true, 45.0, 3_000);
        assertFalse(portals.elsewhere(p, "breach-1", 4_000), "still in the same world");
        portals.inside(p, "breach-1", true, 44.0, 4_000);
        assertTrue(portals.elsewhere(p, "orbis", 5_000));
        assertFalse(portals.elsewhere(p, "orbis", 6_000), "counted once");
    }

    @Test
    void untimedWorldsStaleSamplesAndDisconnectsDoNotCount() {
        WorldPortals portals = new WorldPortals();
        portals.inside(p, "deepworks-1", false, -1.0, 1_000);
        assertFalse(portals.elsewhere(p, "orbis", 2_000), "no time limit");
        portals.inside(p, "breach-1", true, 30.0, 10_000);
        assertFalse(portals.elsewhere(p, "orbis", 10_000 + WorldPortals.RECENT_MILLIS + 1), "too long ago");
        portals.inside(p, "breach-1", true, 30.0, 20_000);
        portals.forget(p);
        assertFalse(portals.elsewhere(p, "orbis", 21_000), "a disconnect is not an exit");
        portals.inside(p, "breach-1", true, 0.0, 30_000);
        assertFalse(portals.elsewhere(p, "orbis", 31_000), "thrown out at zero is not leaving");
        assertFalse(portals.elsewhere(null, "orbis", 0));
    }
}
