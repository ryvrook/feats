package dev.ryvrook.feats.track;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowsTest {
    private final UUID p = UUID.randomUUID();

    @Test
    void aBurstOfFourPaysEachWindowOnce() {
        Windows windows = new Windows();
        assertEquals(0, windows.kill(p, 0));
        assertEquals(Windows.DOUBLE, windows.kill(p, 500));
        assertEquals(Windows.TRIPLE, windows.kill(p, 1_000), "the two kill window is still full, only the three kill window fills");
        assertEquals(0, windows.kill(p, 1_500), "a fourth kill fills nothing new");
    }

    @Test
    void aWindowPaysAgainOnceItHasEmptied() {
        Windows windows = new Windows();
        windows.kill(p, 0);
        assertEquals(Windows.DOUBLE, windows.kill(p, 1_000));
        assertEquals(0, windows.kill(p, 6_000), "one kill alone in the last 3 s, and the first two are older than 5 s");
        assertEquals(Windows.DOUBLE, windows.kill(p, 7_000), "two kills within 3 s again, and still only two within 5 s");
    }

    @Test
    void killsOutsideTheWindowDoNotCount() {
        Windows windows = new Windows();
        windows.kill(p, 0);
        assertEquals(0, windows.kill(p, 3_001), "just outside 3 s");
        assertEquals(Windows.DOUBLE | Windows.TRIPLE, windows.kill(p, 4_000), "the pair at 3.001 s and 4 s fills the two kill window, all three fill the three kill window");
    }

    @Test
    void tenInFifteenSecondsOnlyWithAllTen() {
        Windows windows = new Windows();
        for (int i = 0; i < 9; i++) {
            windows.kill(p, i * 1_000L);
        }
        int tenth = windows.kill(p, 9_000);
        assertEquals(Windows.TEN, tenth & Windows.TEN);
        assertEquals(0, windows.kill(p, 10_000) & Windows.TEN, "the window stays full, no second payout");
        assertEquals(List.of("multikill_10"), Windows.keys(Windows.TEN));
        assertEquals(List.of("multikill_2", "multikill_3"), Windows.keys(Windows.DOUBLE | Windows.TRIPLE));
        assertEquals(List.of(), Windows.keys(0));
    }

    @Test
    void steadyKillsEverySecondPayOnce() {
        Windows windows = new Windows();
        int doubles = 0;
        int triples = 0;
        for (int i = 0; i < 30; i++) {
            int won = windows.kill(p, i * 1_000L);
            doubles += (won & Windows.DOUBLE) != 0 ? 1 : 0;
            triples += (won & Windows.TRIPLE) != 0 ? 1 : 0;
        }
        assertEquals(1, doubles);
        assertEquals(1, triples);
    }

    @Test
    void playersAreKeptApartAndForgotten() {
        Windows windows = new Windows();
        UUID other = UUID.randomUUID();
        windows.kill(p, 0);
        assertEquals(0, windows.kill(other, 100), "another player's kill does not pair with mine");
        assertEquals(2, windows.size());
        windows.forget(p);
        assertEquals(1, windows.size());
        assertEquals(0, windows.kill(p, 200), "forgotten, so this is a first kill");
        assertEquals(0, windows.kill(null, 0));
    }
}
