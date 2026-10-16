package dev.ryvrook.feats.track;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VitalsTest {
    private final UUID p = UUID.randomUUID();

    private static Vitals.Events health(Vitals v, UUID p, float health) {
        return v.sample(p, health, 100f, -1f, -1f, -1f, -1f);
    }

    @Test
    void aCloseCallIsUnderTenThenOverTwentyFiveWithoutDying() {
        Vitals v = new Vitals();
        assertFalse(health(v, p, 50f).closeCall());
        assertFalse(health(v, p, 9f).closeCall(), "low, not back yet");
        assertFalse(health(v, p, 20f).closeCall(), "not over a quarter yet");
        assertTrue(health(v, p, 26f).closeCall());
        assertFalse(health(v, p, 90f).closeCall(), "counted once");
        assertFalse(health(v, p, 5f).closeCall());
        assertFalse(health(v, p, 0f).closeCall(), "died");
        assertFalse(health(v, p, 100f).closeCall(), "a respawn at full health is not a close call");
    }

    @Test
    void oxygenLowIsUnderTenPercentThenRising() {
        Vitals v = new Vitals();
        Vitals.Events e = v.sample(p, -1f, -1f, 50f, 100f, -1f, -1f);
        assertFalse(e.oxygenLow());
        assertFalse(v.sample(p, -1f, -1f, 8f, 100f, -1f, -1f).oxygenLow(), "low");
        assertFalse(v.sample(p, -1f, -1f, 4f, 100f, -1f, -1f).oxygenLow(), "still sinking");
        assertTrue(v.sample(p, -1f, -1f, 6f, 100f, -1f, -1f).oxygenLow(), "surfaced, the first rise");
        assertFalse(v.sample(p, -1f, -1f, 40f, 100f, -1f, -1f).oxygenLow(), "once per dive");
    }

    @Test
    void manaEmptiedIsZeroFromAboveZero() {
        Vitals v = new Vitals();
        assertFalse(v.sample(p, -1f, -1f, -1f, -1f, 0f, 100f).manaEmptied(), "a first read at zero is not an emptying");
        assertFalse(v.sample(p, -1f, -1f, -1f, -1f, 30f, 100f).manaEmptied());
        assertTrue(v.sample(p, -1f, -1f, -1f, -1f, 0f, 100f).manaEmptied());
        assertFalse(v.sample(p, -1f, -1f, -1f, -1f, 0f, 100f).manaEmptied(), "staying empty counts once");
        v.forget(p);
        assertFalse(v.sample(p, -1f, -1f, -1f, -1f, 0f, 100f).manaEmptied(), "forgotten, so the first read again");
        assertFalse(v.sample(null, 1f, 1f, 1f, 1f, 1f, 1f).closeCall());
    }
}
