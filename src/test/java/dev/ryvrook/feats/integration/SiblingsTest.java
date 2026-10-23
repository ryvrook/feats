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

    @Test
    void neverLeavesASiblingOutQuietlyAndAlwaysWarnsWhenItIsMissing() {
        Map<String, String> versions = Map.of(Sibling.COINAGE.pluginName(), "1.3.0", Sibling.ATLAS.pluginName(), "1.9.0");
        Map<Sibling, String> modes = Map.of(Sibling.COINAGE, "never", Sibling.CHRONICLE, "always", Sibling.TRACKER_API, "always");
        List<String> log = new ArrayList<>();
        Set<Sibling> present = Siblings.present(versions::containsKey, versions::get,
                s -> modes.getOrDefault(s, "auto"), log::add);
        assertEquals(EnumSet.of(Sibling.ATLAS), present);
        assertEquals(1, log.size(), "TrackerAPI warns through its own Tracker setting, " + log);
        assertTrue(log.getFirst().contains("always link to ChronicleStats"), log.getFirst());
    }

    @Test
    void aModIsFoundByItsOwnGroupAndName() {
        assertEquals("Zuxaw", Siblings.groupOf("RPGLeveling"));
        assertEquals("Alechilles", Siblings.groupOf("Alec's Tamework!"));
        assertEquals("Ryvrook", Siblings.groupOf("SocialBridge"), "a suite plugin that is no sibling stays under Ryvrook");
        assertEquals("Ryvrook", Siblings.groupOf("ChronicleStats"));
        assertEquals(Sibling.MMO_SKILL_TREE, Sibling.byPluginName("MMOSkillTree"));
        assertEquals(Sibling.ENDGAME, Sibling.byModId("endgame"));
        assertEquals("Config:Endgame&QoL", Sibling.ENDGAME.pluginId());
        assertEquals("LinkWonderWeapons", Sibling.WONDER_WEAPONS.settingKey());
        assertEquals("Tracker", Sibling.TRACKER_API.settingKey());
        for (Sibling s : Sibling.values()) {
            assertEquals(s.modId() != null, s.isMod(), s.name());
            assertEquals(s.group().equals(Sibling.GROUP), !s.isMod(), s.name() + " is a third party mod exactly when it is not under Ryvrook");
            assertEquals(s, Sibling.byPluginName(s.pluginName()), "plugin names are distinct across the groups");
        }
        Map<String, String> versions = Map.of("TrackerAPI", "1.2.0", "MajorDungeons", "0.4.14", "VeinMining", "2.4.0");
        List<String> log = new ArrayList<>();
        Set<Sibling> present = Siblings.present(versions::containsKey, versions::get, log::add);
        assertEquals(EnumSet.of(Sibling.TRACKER_API, Sibling.MAJOR_DUNGEONS), present);
        assertEquals(1, log.size(), log.toString());
        assertTrue(log.getFirst().contains("VeinMining 2.4.0") && log.getFirst().contains("2.5.0"), log.getFirst());
    }

    @Test
    void theConfigReadsEachLinkSettingAndDefaultsToAuto() {
        dev.ryvrook.feats.FeatsConfig config = new dev.ryvrook.feats.FeatsConfig();
        for (Sibling s : Sibling.values()) {
            assertEquals("auto", config.link(s), s.name());
        }
    }
}
