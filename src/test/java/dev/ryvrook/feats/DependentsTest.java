package dev.ryvrook.feats;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DependentsTest {
    private static final String DOMINION = "ryvrook:dominion";
    private static final String SOCIALBRIDGE = "ryvrook:socialbridge";
    private static final String RESTAPI = "ryvrook:restapi";
    private static final String FEATS = "ryvrook:feats";
    private static final String COINAGE = "ryvrook:coinage";
    private static final String TRACKER = "ryvrook:trackerapi";

    /** The optional dependencies of the Feats dependents as their manifests list them. */
    private static final Map<String, Set<String>> SUITE = Map.of(
            DOMINION, Set.of(TRACKER, FEATS, "ryvrook:atlas", COINAGE),
            SOCIALBRIDGE, Set.of(TRACKER, COINAGE, DOMINION, FEATS, "ryvrook:chroniclestats", "ryvrook:atlas"),
            RESTAPI, Set.of(TRACKER, COINAGE, DOMINION, FEATS, "ryvrook:chroniclestats", "ryvrook:atlas",
                    "ryvrook:lullaby"));

    @Test
    void dominionComesBackBeforeThePluginsThatReadIt() {
        assertEquals(List.of(DOMINION, SOCIALBRIDGE, RESTAPI),
                Dependents.order(List.of(SOCIALBRIDGE, RESTAPI, DOMINION), SUITE));
    }

    @Test
    void featsTakesTheSameLockAsTheOtherReloaders() {
        assertSame(Dependents.sharedLock(), System.getProperties().get("ryvrook.dependents.lock"));
    }
}
