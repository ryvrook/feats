package dev.ryvrook.feats.track;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameDaysTest {
    @Test
    void aDateThatMovesForwardIsARollover() {
        GameDays days = new GameDays();
        LocalDate day = LocalDate.of(1, 1, 1);
        assertFalse(days.rollover("orbis", day), "the first look only records");
        assertFalse(days.rollover("orbis", day), "the same date is not a rollover");
        assertTrue(days.rollover("orbis", day.plusDays(1)));
        assertFalse(days.rollover("orbis", day.plusDays(1)));
        assertTrue(days.rollover("orbis", day.plusDays(3)), "a skipped day still rolls once");
    }

    @Test
    void timeSetBackwardsOnlyRecords() {
        GameDays days = new GameDays();
        LocalDate day = LocalDate.of(1, 1, 10);
        days.rollover("orbis", day);
        assertFalse(days.rollover("orbis", day.minusDays(2)), "an admin set the clock back");
        assertTrue(days.rollover("orbis", day.minusDays(1)), "forward again from the new base");
        assertFalse(days.rollover("orbis", day.minusDays(1)));
    }

    @Test
    void worldsKeepTheirOwnClock() {
        GameDays days = new GameDays();
        LocalDate day = LocalDate.of(1, 1, 1);
        days.rollover("orbis", day);
        days.rollover("deepworks-1", day.plusDays(5));
        assertTrue(days.rollover("orbis", day.plusDays(1)));
        assertFalse(days.rollover("deepworks-1", day.plusDays(5)));
        days.forgetWorld("orbis");
        assertFalse(days.rollover("orbis", day.plusDays(2)), "a forgotten world starts over");
        assertFalse(days.rollover(null, day));
        assertFalse(days.rollover("orbis", null));
    }

    @Test
    void aRisingDeathsCounterEndsTheLife() {
        GameDays days = new GameDays();
        UUID p = UUID.randomUUID();
        assertFalse(days.died(p, 3), "the first look only records the deaths so far");
        assertFalse(days.died(p, 3));
        assertTrue(days.died(p, 4));
        assertFalse(days.died(p, 4));
        assertFalse(days.died(p, 2), "an admin lowering the counter is not a death");
        days.forget(p);
        assertFalse(days.died(p, 10), "forgotten, so the next look records again");
        assertFalse(days.died(null, 1));
    }
}
