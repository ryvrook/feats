package dev.ryvrook.feats.track;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldBoundsTest {
    @Test
    void floorAndCeilingOfA320BlockWorld() {
        assertEquals(3.0, WorldBounds.FLOOR);
        assertEquals(316.0, WorldBounds.CEILING);
        assertTrue(WorldBounds.atFloor(1.0)); // standing on the bottom layer
        assertTrue(WorldBounds.atFloor(0.0));
        assertFalse(WorldBounds.atFloor(-5.0), "falling into the void is not reaching the bottom");
        assertFalse(WorldBounds.atFloor(3.5));
        assertTrue(WorldBounds.atCeiling(320.0)); // standing on the top layer
        assertTrue(WorldBounds.atCeiling(316.0));
        assertFalse(WorldBounds.atCeiling(250.0));
    }
}
