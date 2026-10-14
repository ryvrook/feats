package dev.ryvrook.feats.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackerSwitchTest {
    private static final TrackerSwitch.Found RUNNING = new TrackerSwitch.Found(new Object(), "1.0.0", true);
    private static final TrackerSwitch.Found OLD = new TrackerSwitch.Found(new Object(), "0.9.0", false);

    @Test
    void modesParseWithAutoAsTheDefault() {
        assertEquals(TrackerSwitch.Mode.AUTO, TrackerSwitch.Mode.parse(null));
        assertEquals(TrackerSwitch.Mode.AUTO, TrackerSwitch.Mode.parse("Auto"));
        assertEquals(TrackerSwitch.Mode.AUTO, TrackerSwitch.Mode.parse("sometimes"));
        assertEquals(TrackerSwitch.Mode.ALWAYS, TrackerSwitch.Mode.parse(" ALWAYS "));
        assertEquals(TrackerSwitch.Mode.NEVER, TrackerSwitch.Mode.parse("never"));
    }

    @Test
    void neverBothPaths() {
        assertEquals(TrackerSwitch.Path.TRACKER_API, TrackerSwitch.decide(TrackerSwitch.Mode.AUTO, RUNNING));
        assertEquals(TrackerSwitch.Path.OWN, TrackerSwitch.decide(TrackerSwitch.Mode.AUTO, OLD));
        assertEquals(TrackerSwitch.Path.OWN, TrackerSwitch.decide(TrackerSwitch.Mode.AUTO, TrackerSwitch.Found.MISSING));
        assertEquals(TrackerSwitch.Path.TRACKER_API, TrackerSwitch.decide(TrackerSwitch.Mode.ALWAYS, RUNNING));
        assertEquals(TrackerSwitch.Path.NONE, TrackerSwitch.decide(TrackerSwitch.Mode.ALWAYS, TrackerSwitch.Found.MISSING));
        assertEquals(TrackerSwitch.Path.OWN, TrackerSwitch.decide(TrackerSwitch.Mode.NEVER, RUNNING));
    }

    @Test
    void versionsCompareAsNumbers() {
        assertTrue(VersionCompare.below("0.9.9", TrackerSwitch.MINIMUM));
        assertTrue(!VersionCompare.below("1.0.0", TrackerSwitch.MINIMUM));
        assertTrue(!VersionCompare.below("1.10.0", "1.9.0"));
    }

    @Test
    void aReloadOfTrackerApiIsLoggedOnce() {
        TrackerSwitch.Found[] now = {RUNNING};
        TrackerSwitch watch = new TrackerSwitch(TrackerSwitch.Mode.AUTO, TrackerSwitch.Path.TRACKER_API, RUNNING,
                () -> now[0]);
        assertNull(watch.poll());
        now[0] = new TrackerSwitch.Found(new Object(), "1.0.1", true);
        String line = watch.poll();
        assertNotNull(line);
        assertTrue(line.contains("/plugin reload Feats"));
        assertTrue(line.contains("own tracking"), "auto mode says it cannot fall back");
        assertNull(watch.poll());
    }
}
