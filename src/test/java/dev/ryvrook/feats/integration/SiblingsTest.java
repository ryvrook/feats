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
            assertEquals(s.group().equals(Sibling.GROUP), s.isSuite(), s.name() + " is third party exactly when it is not under Ryvrook");
            assertEquals(s.isMod(), s.trackerMinimum() != null, s.name() + " needs a TrackerAPI exactly when TrackerAPI reads it");
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

    @Test
    void theNewSiblingsOf1100() {
        assertEquals(Sibling.Kind.MOD, Sibling.FISHING.kind());
        assertEquals("dev.rm20:AnglersAlmanac", Sibling.FISHING.pluginId());
        assertEquals("com.zenkuro:Arcanum", Sibling.ARCANUM.pluginId());
        assertEquals("Conczin:Ymmersive Melodies", Sibling.MELODIES.pluginId());
        for (Sibling mod : List.of(Sibling.FISHING, Sibling.ARCANUM, Sibling.MELODIES)) {
            assertEquals("1.3.0", mod.trackerMinimum(), mod.name());
        }
        assertEquals("1.2.0", Sibling.RPG_LEVELING.trackerMinimum());
        assertEquals(Sibling.MELODIES, Sibling.byModId("melodies"));
        assertEquals("ModernKnight:ModernStorage", Sibling.STORAGE.pluginId());
        assertEquals("AipocsHouse:MachineBlocks", Sibling.MACHINES.pluginId());
        assertEquals(Sibling.Kind.PLUGIN, Sibling.STORAGE.kind());
        assertEquals(Sibling.Kind.PLUGIN, Sibling.MACHINES.kind());
        Map<Sibling, String> packs = Map.of(
                Sibling.FANCY_STORAGE, "Sigyn_Laufeyson:Sig's Fancy Storage",
                Sibling.POTION_SHOP, "Sigyn_Laufeyson:Sig's Potion Shop",
                Sibling.SIGS_GARDEN, "Sigyn_Laufeyson:Sig's Garden",
                Sibling.BLAHAJ, "Yernemm:Blahaj",
                Sibling.INFINITE_WATER, "AvidPixel:AvidPixel's Infinite Water Supply",
                Sibling.INFINITE_TELEPORTERS, "VictorLuck:InfiniteTeleporters");
        for (Sibling s : Sibling.values()) {
            assertEquals(packs.containsKey(s), s.isAssetPack(), s.name());
        }
        packs.forEach((s, id) -> assertEquals(id, s.pluginId(), s.name()));
    }

    @Test
    void anAssetPackIsFoundAmongTheEnginesAssetPacks() {
        Map<String, String> loaded = Map.of("Sigyn_Laufeyson:Sig's Garden", "0.5.8", "Yernemm:Blahaj", "1.2.4",
                "VictorLuck:InfiniteTeleporters", "1.0.5");
        Siblings.useAssetPacks(new Siblings.AssetPacks() {
            @Override
            public boolean has(String packId) {
                return loaded.containsKey(packId);
            }

            @Override
            public String version(String packId) {
                return loaded.get(packId);
            }
        });
        try {
            assertTrue(Siblings.installed(Sibling.SIGS_GARDEN));
            assertTrue(Siblings.installed("Sig's Garden"), "a lookup by name reaches the asset packs too");
            assertEquals("0.5.8", Siblings.version("Sig's Garden"));
            assertTrue(!Siblings.installed(Sibling.POTION_SHOP));
            assertTrue(Siblings.installedNow(Sibling.INFINITE_TELEPORTERS));
            List<String> log = new ArrayList<>();
            Set<Sibling> present = Siblings.present(Siblings::installed, Siblings::version, s -> "auto", log::add);
            assertEquals(EnumSet.of(Sibling.SIGS_GARDEN, Sibling.INFINITE_TELEPORTERS), present,
                    "no plugin manager in the tests, the old Blahaj is left out");
            assertEquals(1, log.size(), log.toString());
            assertTrue(log.getFirst().contains("Blahaj 1.2.4") && log.getFirst().contains("1.2.5"), log.getFirst());
            Set<Sibling> never = Siblings.present(Siblings::installed, Siblings::version,
                    s -> s == Sibling.SIGS_GARDEN ? "never" : "auto", line -> { });
            assertEquals(EnumSet.of(Sibling.INFINITE_TELEPORTERS), never, "LinkSigsGarden never leaves the pack out");
        } finally {
            Siblings.useAssetPacks(null);
        }
        assertTrue(!Siblings.installed(Sibling.SIGS_GARDEN), "the engine lookup answers no without a server");
    }
}
