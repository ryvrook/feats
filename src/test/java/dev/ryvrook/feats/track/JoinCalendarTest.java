package dev.ryvrook.feats.track;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Pack;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.stats.StatKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JoinCalendarTest {
    private static final ZoneId UTC = ZoneOffset.UTC;

    private static long at(int year, int month, int day, int hour, ZoneId zone) {
        return LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli();
    }

    @AfterAll
    static void restore() {
        TestTrackers.registry(); // the pack registry below set the shared feat predicates, put the plain list back
        new FeatRegistry(); // in case the shared one was already built, the last registry built is the one that counts
    }

    /** A tracker with the Calendar pack on, so the dated keys are watched and kept. */
    private static FeatsTracker calendarTracker() {
        try {
            FeatRegistry registry = new FeatRegistry(EnumSet.noneOf(Sibling.class), EnumSet.of(Pack.CALENDAR));
            ProgressStore store = new ProgressStore(Files.createTempDirectory("feats-calendar-test"), TestTrackers.LOGGER);
            return new FeatsTracker(registry, store, new UnlockNotifier(() -> null, TestTrackers.LOGGER), TestTrackers.LOGGER,
                    Runnable::run);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void streakRules() {
        int day = JoinCalendar.dayOf(LocalDate.of(2026, 3, 10));
        assertEquals(1, JoinCalendar.streakAfter(0, day, 0), "a first join starts at one");
        assertEquals(1, JoinCalendar.streakAfter(day, day, 0), "a same day join on a legacy file reads one");
        assertEquals(4, JoinCalendar.streakAfter(day, day, 4), "the same day keeps the streak");
        assertEquals(5, JoinCalendar.streakAfter(day, 20260311, 4), "the day after continues it");
        assertEquals(2, JoinCalendar.streakAfter(day, 20260311, 0), "the day after a legacy join counts both days");
        assertEquals(1, JoinCalendar.streakAfter(day, 20260312, 4), "a gap starts over");
        assertEquals(1, JoinCalendar.streakAfter(day, 20260309, 4), "going back in time starts over");
        assertEquals(1, JoinCalendar.streakAfter(99999999, day, 4), "a broken day in the file is a gap");
        assertEquals(20270101, JoinCalendar.dayOf(LocalDate.of(2027, 1, 1)));
        assertEquals(2, JoinCalendar.streakAfter(20261231, 20270101, 1), "the year boundary is one day");
    }

    @Test
    void joinsOnConsecutiveDaysBuildTheStreakAndTheBest() {
        FeatsTracker tracker = TestTrackers.tracker();
        JoinCalendar calendar = new JoinCalendar(tracker);
        UUID p = UUID.randomUUID();
        PlayerProgress progress = tracker.load(p, "Regular");
        calendar.joined(p, progress, at(2026, 3, 10, 12, UTC), UTC, 0);
        assertEquals(1, progress.get(StatKeys.JOIN_STREAK));
        assertEquals(20260310, progress.lastJoinDay());
        calendar.joined(p, progress, at(2026, 3, 10, 20, UTC), UTC, 0);
        assertEquals(1, progress.get(StatKeys.JOIN_STREAK), "same day");
        calendar.joined(p, progress, at(2026, 3, 11, 9, UTC), UTC, 0);
        calendar.joined(p, progress, at(2026, 3, 12, 9, UTC), UTC, 0);
        assertEquals(3, progress.get(StatKeys.JOIN_STREAK));
        assertEquals(3, progress.get(StatKeys.BEST_JOIN_STREAK));
        calendar.joined(p, progress, at(2026, 3, 14, 9, UTC), UTC, 0);
        assertEquals(1, progress.get(StatKeys.JOIN_STREAK), "a missed day resets");
        assertEquals(3, progress.get(StatKeys.BEST_JOIN_STREAK), "the best stays");
        assertEquals(20260314, progress.lastJoinDay());
    }

    @Test
    void theZoneDecidesWhichDayAJoinFallsOn() {
        FeatsTracker tracker = TestTrackers.tracker();
        JoinCalendar calendar = new JoinCalendar(tracker);
        UUID p = UUID.randomUUID();
        PlayerProgress progress = tracker.load(p, "Traveller");
        long lateUtc = at(2026, 3, 10, 23, UTC); // 23:00 UTC, already the 11th two hours east
        calendar.joined(p, progress, lateUtc, UTC, 0);
        assertEquals(20260310, progress.lastJoinDay());
        PlayerProgress other = tracker.load(UUID.randomUUID(), "Eastern");
        calendar.joined(other.uuid(), other, lateUtc, ZoneId.of("Europe/Athens"), 0);
        assertEquals(20260311, other.lastJoinDay());
        assertEquals(0, progress.get(StatKeys.JOIN_NIGHT), "23:00 is not the night window");
        assertEquals(1, other.get(StatKeys.JOIN_NIGHT), "01:00 in Athens is");
    }

    @Test
    void joinHoursServerYearAndDates() {
        FeatsTracker tracker = calendarTracker();
        JoinCalendar calendar = new JoinCalendar(tracker);
        UUID p = UUID.randomUUID();
        PlayerProgress progress = tracker.load(p, "Owl");
        progress.setFirstSeenIfAbsent(at(2025, 1, 1, 12, UTC));
        calendar.joined(p, progress, at(2025, 6, 21, 3, UTC), UTC, 0);
        assertEquals(1, progress.get(StatKeys.JOIN_NIGHT));
        assertEquals(0, progress.get(StatKeys.JOIN_EARLY));
        assertEquals(1, progress.get(StatKeys.DATE_PREFIX + "0621"), "midsummer is watched by the Calendar pack");
        assertEquals(0, progress.get(StatKeys.SERVER_YEAR), "not a year yet");
        calendar.joined(p, progress, at(2025, 12, 31, 6, UTC), UTC, 0);
        assertEquals(1, progress.get(StatKeys.JOIN_EARLY));
        assertEquals(0, progress.get(StatKeys.SERVER_YEAR), "the day before the anniversary");
        calendar.joined(p, progress, at(2026, 1, 1, 12, UTC), UTC, 0);
        assertEquals(1, progress.get(StatKeys.SERVER_YEAR));
        assertEquals(1, progress.get(StatKeys.DATE_PREFIX + "0101"));
        calendar.joined(p, progress, at(2026, 12, 21, 12, UTC), UTC, 0);
        assertEquals(1, progress.get(StatKeys.DATE_PREFIX + "1221"));
        assertEquals(0, progress.get(StatKeys.DATE_PREFIX + "1231"), "dates no feat watches are not kept");
    }

    @Test
    void withoutTheCalendarPackNoDateIsKept() {
        FeatsTracker tracker = TestTrackers.tracker();
        JoinCalendar calendar = new JoinCalendar(tracker);
        UUID p = UUID.randomUUID();
        PlayerProgress progress = tracker.load(p, "Plain");
        calendar.joined(p, progress, at(2026, 6, 21, 12, UTC), UTC, 0);
        assertEquals(0, progress.get(StatKeys.DATE_PREFIX + "0621"));
        assertEquals(1, progress.get(StatKeys.JOIN_STREAK), "the streak is kept on every server");
    }

    @Test
    void serverBirthdayNeedsAFullYear() {
        FeatsTracker tracker = calendarTracker();
        JoinCalendar calendar = new JoinCalendar(tracker);
        UUID p = UUID.randomUUID();
        PlayerProgress progress = tracker.load(p, "Founder");
        long installed = at(2025, 4, 5, 15, UTC);
        calendar.joined(p, progress, at(2025, 4, 5, 16, UTC), UTC, installed);
        assertEquals(0, progress.get(StatKeys.SERVER_BIRTHDAY), "the install day itself");
        calendar.joined(p, progress, at(2026, 4, 4, 16, UTC), UTC, installed);
        assertEquals(0, progress.get(StatKeys.SERVER_BIRTHDAY));
        calendar.joined(p, progress, at(2026, 4, 5, 1, UTC), UTC, installed);
        assertEquals(1, progress.get(StatKeys.SERVER_BIRTHDAY));
    }

    @Test
    void midnightWhileOnlineCountsTheNewDay() {
        FeatsTracker tracker = calendarTracker();
        JoinCalendar calendar = new JoinCalendar(tracker);
        UUID p = UUID.randomUUID();
        PlayerProgress progress = tracker.load(p, "Nighthawk");
        calendar.joined(p, progress, at(2026, 6, 20, 22, UTC), UTC, 0);
        calendar.tick(List.of(p), at(2026, 6, 20, 23, UTC), UTC, 0);
        assertEquals(1, progress.get(StatKeys.JOIN_STREAK), "the first tick only notes the day");
        calendar.tick(List.of(p), at(2026, 6, 20, 23, UTC) + 30 * 60_000L, UTC, 0);
        assertEquals(20260620, progress.lastJoinDay());
        calendar.tick(List.of(p), at(2026, 6, 21, 0, UTC), UTC, 0);
        assertEquals(2, progress.get(StatKeys.JOIN_STREAK), "present on the 21st without rejoining");
        assertEquals(20260621, progress.lastJoinDay());
        assertEquals(1, progress.get(StatKeys.DATE_PREFIX + "0621"));
        assertEquals(0, progress.get(StatKeys.JOIN_NIGHT), "a rollover is not a join, so no join hour");
        calendar.tick(List.of(p), at(2026, 6, 21, 0, UTC) + 1000, UTC, 0);
        assertEquals(2, progress.get(StatKeys.JOIN_STREAK));
    }
}
