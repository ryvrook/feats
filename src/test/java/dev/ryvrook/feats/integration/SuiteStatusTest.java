package dev.ryvrook.feats.integration;

import dev.ryvrook.feats.model.Sibling;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SuiteStatusTest {
    @Test
    void oneLinePerSuiteModWithItsSetting() {
        Map<String, String> v = Map.of("TrackerAPI", "1.1.0", "Coinage", "1.3.0", "Dominion", "0.7.2",
                "ChronicleStats", "1.4.0", "SocialBridge", "0.2.0");
        Map<Sibling, String> modes = Map.of(Sibling.ATLAS, "never");
        List<String> lines = SuiteStatus.lines(new SuiteStatus.Inputs(
                EnumSet.of(Sibling.TRACKER_API, Sibling.COINAGE, Sibling.DOMINION), List.of("Coinage"), true, true,
                s -> modes.getOrDefault(s, "auto"), "auto", v::get, v::containsKey));
        assertEquals(List.of(
                "TrackerAPI 1.1.0 feeds the counters, setting Tracker auto",
                "Coinage 1.3.0 is linked, with coin rewards, setting Coinage auto",
                "Dominion 0.7.2 sends the Factions counters, setting Dominion auto",
                "ChronicleStats 1.4.0 is older than 1.5.0, so its feats are out, setting Chronicle auto",
                "Atlas is ignored, setting Atlas never",
                "SocialBridge 0.2.0 reads Feats and posts the unlocks, setting DiscordVia auto",
                "RestAPI is not installed",
                "Lullaby is not installed"), lines);
    }
}
