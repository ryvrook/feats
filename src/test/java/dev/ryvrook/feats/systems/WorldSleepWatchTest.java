package dev.ryvrook.feats.systems;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSleepWatchTest {
    private final Instant night = Instant.ofEpochSecond(1_000);
    private final Instant morning = Instant.ofEpochSecond(30_000);

    @Test
    void aFinishedNightCreditsEveryoneWhoSlept() {
        SleepWatch watch = new SleepWatch();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(watch.world("orbis", null, night).isEmpty(), "awake all along");
        assertTrue(watch.world("orbis", morning, night).isEmpty(), "slumber begins");
        watch.sleeping("orbis", a);
        watch.sleeping("orbis", b);
        assertTrue(watch.world("orbis", morning, night.plusSeconds(100)).isEmpty(), "still asleep");
        Set<UUID> slept = watch.world("orbis", null, morning.plusSeconds(5));
        assertEquals(Set.of(a, b), slept);
        assertTrue(watch.world("orbis", null, morning.plusSeconds(6)).isEmpty(), "counted once");
    }

    @Test
    void anInterruptedNightAndLeaversDoNotCount() {
        SleepWatch watch = new SleepWatch();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        watch.world("orbis", morning, night);
        watch.sleeping("orbis", a);
        watch.sleeping("orbis", b);
        assertTrue(watch.world("orbis", null, Instant.ofEpochSecond(15_000)).isEmpty(), "someone woke up halfway");
        watch.world("orbis", morning, night);
        watch.sleeping("orbis", a);
        watch.sleeping("orbis", b);
        watch.forget(b);
        assertEquals(Set.of(a), watch.world("orbis", null, morning), "b left before morning");
        watch.world("orbis", morning, night);
        watch.sleeping("orbis", a);
        assertTrue(watch.world("orbis", null, null).isEmpty(), "no clock, no credit");
        assertTrue(watch.world(null, morning, night).isEmpty());
    }

    @Test
    void worldsSleepOnTheirOwn() {
        SleepWatch watch = new SleepWatch();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        watch.world("orbis", morning, night);
        watch.world("other", morning, night);
        watch.sleeping("orbis", a);
        watch.sleeping("other", b);
        assertEquals(Set.of(a), watch.world("orbis", null, morning));
        assertTrue(watch.world("other", morning, night.plusSeconds(10)).isEmpty());
        assertEquals(Set.of(b), watch.world("other", null, morning));
    }
}
