package dev.ryvrook.feats.integration;

import dev.ryvrook.feats.model.Sibling;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The minimum version check in {@link Siblings#present()}, over a fake plugin list. */
class SiblingsTest {
    @Test
    void anOlderSiblingIsLeftOutWithOneLine() {
        Map<String, String> versions = Map.of(
                Sibling.COINAGE.pluginName(), "1.1.9",
                Sibling.ATLAS.pluginName(), "1.9.0",
                Sibling.TRACKER_API.pluginName(), "1.0.3");
        List<String> log = new ArrayList<>();
        Set<Sibling> present = Siblings.present(name -> versions.containsKey(name) || name.equals("Dominion"),
                versions::get, log::add);
        assertEquals(EnumSet.of(Sibling.ATLAS, Sibling.TRACKER_API, Sibling.DOMINION), present,
                "Dominion is not loaded yet, so it has no version and counts as new enough");
        assertEquals(1, log.size(), log.toString());
        assertTrue(log.getFirst().contains("Coinage 1.1.9") && log.getFirst().contains("1.2.0"), log.getFirst());
    }

    @Test
    void nothingInstalledMeansNoSiblings() {
        assertEquals(Set.of(), Siblings.present(name -> false, name -> "9.9.9", line -> {
            throw new AssertionError(line);
        }));
    }
}
