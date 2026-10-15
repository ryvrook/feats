package dev.ryvrook.feats.track;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecentDropsTest {
    @Test
    void pickingUpYourOwnDropTakesFromTheMemoryFirst() {
        RecentDrops drops = new RecentDrops();
        UUID uuid = UUID.randomUUID();
        drops.record(uuid, "Ingredient_Bar_Gold", 5);
        assertEquals(3, drops.take(uuid, "Ingredient_Bar_Gold", 3));
        assertEquals(2, drops.take(uuid, "Ingredient_Bar_Gold", 10));
        assertEquals(0, drops.take(uuid, "Ingredient_Bar_Gold", 1));
    }

    @Test
    void aFullMemoryForgetsTheOldestDropNotTheNewest() {
        RecentDrops drops = new RecentDrops();
        UUID uuid = UUID.randomUUID();
        for (int i = 0; i < 256; i++) {
            drops.record(uuid, "Rock_Stone_Cobble_" + i, 1);
        }
        drops.record(uuid, "Ingredient_Bar_Gold", 4);
        assertEquals(4, drops.take(uuid, "Ingredient_Bar_Gold", 4), "the newest drop is still remembered");
        assertEquals(0, drops.take(uuid, "Rock_Stone_Cobble_0", 1), "the oldest one made room for it");
        assertEquals(1, drops.take(uuid, "Rock_Stone_Cobble_1", 1));
    }
}
