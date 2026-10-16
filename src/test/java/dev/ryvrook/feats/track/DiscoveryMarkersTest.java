package dev.ryvrook.feats.track;

import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A discovery marker must survive a save and a load, or the next sighting counts as new and the distinct
 * kinds total grows again at every login.
 */
class DiscoveryMarkersTest {
    /** An id no feat watches, so only the discovery rule can keep the marker. */
    private static final String ID = "Nobody_Watches_This_Id";

    @Test
    void discoveryMarkersAreNeverPerIdKeys() {
        FeatsTracker tracker = TestTrackers.tracker();
        for (Map.Entry<String, String> e : StatKeys.DISCOVERIES.entrySet()) {
            String marker = e.getKey() + ID;
            assertFalse(StatKeys.isPerId(marker), marker);
            assertTrue(tracker.isPersisted(marker), marker);
            assertTrue(tracker.isPersisted(e.getValue()), e.getValue());
        }
    }

    @Test
    void aDiscoveryCountsOnceAcrossLoadsAndUnloads() {
        FeatsTracker tracker = TestTrackers.tracker();
        UUID uuid = UUID.randomUUID();
        for (int cycle = 1; cycle <= 3; cycle++) {
            PlayerProgress progress = tracker.load(uuid, "Explorer");
            for (Map.Entry<String, String> e : StatKeys.DISCOVERIES.entrySet()) {
                tracker.discover(uuid, e.getKey(), ID, e.getValue());
                assertEquals(1, progress.get(e.getValue()), e.getValue() + " after load " + cycle);
                assertEquals(1, progress.get(e.getKey() + ID), e.getKey() + " after load " + cycle);
            }
            tracker.unload(uuid); // the IO executor runs inline, so the file is written here
        }
    }

    /** Every discover call in the plugin pairs a prefix and a total listed in {@link StatKeys#DISCOVERIES}. */
    @Test
    void everyDiscoverCallSiteIsListed() throws IOException {
        Pattern call = Pattern.compile("\\.discover\\([^;]*?StatKeys\\.(\\w+_PREFIX)[^;]*?StatKeys\\.(\\w+)\\)");
        int found = 0;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = call.matcher(Files.readString(file));
                while (m.find()) {
                    String prefix = constant(m.group(1));
                    String total = constant(m.group(2));
                    assertEquals(total, StatKeys.DISCOVERIES.get(prefix), file.getFileName() + " " + m.group());
                    found++;
                }
            }
        }
        assertTrue(found > 20, "the scan should find the discover calls, found " + found);
    }

    private static String constant(String name) {
        try {
            return (String) StatKeys.class.getField(name).get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("StatKeys has no " + name, e);
        }
    }
}
