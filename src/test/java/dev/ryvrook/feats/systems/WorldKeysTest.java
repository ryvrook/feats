package dev.ryvrook.feats.systems;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorldKeysTest {
    @Test
    void volumePrefixDropsInstanceData() {
        assertEquals("Dungeon_Checkpoint", WorldKeys.volumePrefix("Dungeon_Checkpoint"));
        assertEquals("Dungeon_Checkpoint", WorldKeys.volumePrefix("Dungeon_Checkpoint_3"));
        assertEquals("Dungeon_Checkpoint", WorldKeys.volumePrefix("Dungeon_Checkpoint_12_a1b2"));
        assertEquals("Health_Pack_Volume", WorldKeys.volumePrefix("Health_Pack_Volume"));
        assertEquals("Health_Pack_Volume", WorldKeys.volumePrefix("Health_Pack_Volume-7f3c9d2e"));
        assertEquals("Health_Pack_Volume", WorldKeys.volumePrefix("Health_Pack_Volume #2"));
        assertEquals("Arena", WorldKeys.volumePrefix("Arena:north"));
        assertEquals("Bed_Room", WorldKeys.volumePrefix("Bed_Room"), "short hex looking words stay");
        assertEquals("Lab_Door", WorldKeys.volumePrefix("Lab_Door_"));
        assertNull(WorldKeys.volumePrefix(""));
        assertNull(WorldKeys.volumePrefix("  "));
        assertNull(WorldKeys.volumePrefix(null));
        assertNull(WorldKeys.volumePrefix("-abc"));
    }

    @Test
    void instanceAndMoonKeys() {
        assertEquals("Forgotten_Temple", WorldKeys.instanceKey(" Forgotten_Temple "));
        assertNull(WorldKeys.instanceKey(""));
        assertNull(WorldKeys.instanceKey(null));
        assertEquals("7", WorldKeys.moonKey(7));
        assertEquals("orbis", WorldKeys.worldKey("Orbis"));
        assertEquals("", WorldKeys.worldKey(null));
    }
}
