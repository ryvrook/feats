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
                "ChronicleStats", "1.4.0", "SocialBridge", "0.2.0", "RPGLeveling", "0.4.2", "Zephyr", "0.3.0",
                "MMOSkillTree", "1.7.3");
        Map<Sibling, String> modes = Map.of(Sibling.ATLAS, "never", Sibling.MMO_SKILL_TREE, "never");
        List<String> lines = SuiteStatus.lines(new SuiteStatus.Inputs(
                EnumSet.of(Sibling.TRACKER_API, Sibling.COINAGE, Sibling.DOMINION, Sibling.RPG_LEVELING), List.of("Coinage"),
                true, true, s -> modes.getOrDefault(s, "auto"), "auto", v::get, v::containsKey));
        assertEquals(List.of(
                "TrackerAPI 1.1.0 feeds the counters, setting Tracker auto",
                "Coinage 1.3.0 is linked, with coin rewards, setting Coinage auto",
                "Dominion Factions 0.7.2 sends the Factions counters, setting Dominion auto",
                "Chronicle Stats 1.4.0 is older than 1.5.0, so its feats are out, setting Chronicle auto",
                "Atlas is ignored, setting Atlas never",
                "RPG Leveling 0.4.2 is installed, its feats need TrackerAPI 1.2.0 feeding the counters, setting LinkRPGLeveling auto",
                "Major Dungeons is not installed, setting LinkMajorDungeons auto",
                "Perfect Parries is not installed, setting LinkPerfectParries auto",
                "Wan's Wonder Weapons is not installed, setting LinkWonderWeapons auto",
                "Zephyr 0.3.0 is older than 0.4.2, so its feats are out, setting LinkZephyr auto",
                "Simple Enchantments is not installed, setting LinkSimpleEnchantments auto",
                "Vein Mining is not installed, setting LinkVeinMining auto",
                "Lucky Mining is not installed, setting LinkLuckyMining auto",
                "MMO Skill Tree 1.7.3 is installed and ignored, setting LinkMMOSkillTree never",
                "Endgame and QoL is not installed, setting LinkEndgame auto",
                "Alec's Tamework is not installed, setting LinkTamework auto",
                "SocialBridge 0.2.0 reads Feats and posts the unlocks, setting DiscordVia auto",
                "RestAPI is not installed",
                "Lullaby is not installed"), lines);
    }

    @Test
    void aModFeedsThroughTrackerApi120() {
        Map<String, String> v = Map.of("TrackerAPI", "1.2.0", "MajorDungeons", "0.4.14");
        List<String> lines = SuiteStatus.lines(new SuiteStatus.Inputs(
                EnumSet.of(Sibling.TRACKER_API, Sibling.MAJOR_DUNGEONS), List.of(), true, false,
                s -> "auto", "auto", v::get, v::containsKey));
        assertEquals("Major Dungeons 0.4.14 feeds its counters through TrackerAPI, setting LinkMajorDungeons auto",
                lines.get(Sibling.MAJOR_DUNGEONS.ordinal()));
        List<String> own = SuiteStatus.lines(new SuiteStatus.Inputs(
                EnumSet.of(Sibling.TRACKER_API, Sibling.MAJOR_DUNGEONS), List.of(), false, false,
                s -> "auto", "auto", v::get, v::containsKey));
        assertEquals("Major Dungeons 0.4.14 is installed, its feats need TrackerAPI 1.2.0 feeding the counters,"
                + " setting LinkMajorDungeons auto", own.get(Sibling.MAJOR_DUNGEONS.ordinal()));
    }
}
