package dev.ryvrook.feats.track;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Cause ids are the DamageCause asset ids of 0.7.1, Server/Entity/Damage/*.json. */
class DeathCauseTest {
    @Test
    void causeIdsAreLowerCased() {
        assertEquals(List.of("deaths", "death:fall"), Classify.death("Fall"));
        assertEquals(List.of("deaths", "death:outofworld"), Classify.death("OutOfWorld"));
        assertEquals(List.of("deaths", "death:drowning"), Classify.death("Drowning", null));
        assertEquals(List.of("deaths"), Classify.death(null));
        assertEquals(List.of("deaths"), Classify.death("", null));
    }

    @Test
    void fireIsTheFireCause() {
        assertEquals(List.of("deaths", "death:fire"), Classify.death("Fire", null));
        assertEquals(List.of("deaths", "death:fire"), Classify.death("Fire", "Burn"));
    }

    @Test
    void lavaIsFireFromTheLavaBurnEffect() {
        // Lava applies Lava_Burn, whose damage has the Fire cause. Both keys count, death:fire as before.
        assertEquals(List.of("deaths", "death:fire", "death:lava"), Classify.death("Fire", "Lava_Burn"));
    }
}
