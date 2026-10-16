package dev.ryvrook.feats.track;

import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Real dates in the server's calendar zone. A join, and a midnight that passes while the player is
 * online, mark the player present on that date. Being present the day after the last counted day
 * continues the join streak, the same day keeps it, anything else starts it over at one. The best
 * streak, the join hour feats, the anniversary of the first visit, the dated feats and the server
 * birthday all come out of the same look at the clock.
 *
 * <p>{@code join_streak} is set straight on the player file, since a streak has to be able to fall.
 * Every other counter goes through the tracker so its feats are checked.
 */
public final class JoinCalendar {
    private final FeatsTracker tracker;
    /** The calendar day of the last tick as yyyymmdd, 0 before the first. */
    private volatile int lastTickDay;

    public JoinCalendar(FeatsTracker tracker) {
        this.tracker = tracker;
    }

    /** A date as yyyymmdd, the form the player file keeps. */
    public static int dayOf(LocalDate date) {
        return date.getYear() * 10_000 + date.getMonthValue() * 100 + date.getDayOfMonth();
    }

    /** The calendar date of an instant in the zone. */
    public static LocalDate dateOf(long epochMillis, ZoneId zone) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate();
    }

    /** The streak after being present on {@code today}, given the last counted day and the streak so far. */
    public static long streakAfter(int lastDay, int today, long streak) {
        if (lastDay == today) {
            return Math.max(1L, streak);
        }
        LocalDate last = toDate(lastDay);
        if (last != null && last.plusDays(1).equals(toDate(today))) {
            return Math.max(1L, streak) + 1;
        }
        return 1L;
    }

    private static LocalDate toDate(int yyyymmdd) {
        if (yyyymmdd <= 0) {
            return null;
        }
        try {
            return LocalDate.of(yyyymmdd / 10_000, yyyymmdd / 100 % 100, yyyymmdd % 100);
        } catch (RuntimeException e) {
            return null; // a hand edited file, counts as a gap
        }
    }

    /** The first PlayerReady of a connection. */
    public void joined(UUID uuid, PlayerProgress progress, long nowMillis, ZoneId zone, long installedAt) {
        present(uuid, progress, nowMillis, zone, installedAt, true);
    }

    /** Once a second. When the calendar day changed, everyone online is present on the new day. */
    public void tick(List<UUID> online, long nowMillis, ZoneId zone, long installedAt) {
        int today = dayOf(dateOf(nowMillis, zone));
        int last = lastTickDay;
        if (last == today) {
            return;
        }
        lastTickDay = today;
        if (last == 0) {
            return; // the first tick after a start, joins already covered everyone online
        }
        for (UUID uuid : online) {
            PlayerProgress progress = uuid == null ? null : tracker.loadedProgress(uuid);
            if (progress != null) {
                present(uuid, progress, nowMillis, zone, installedAt, false);
            }
        }
    }

    private void present(UUID uuid, PlayerProgress progress, long nowMillis, ZoneId zone, long installedAt, boolean join) {
        ZonedDateTime at = Instant.ofEpochMilli(nowMillis).atZone(zone);
        LocalDate date = at.toLocalDate();
        int today = dayOf(date);
        long streak = streakAfter(progress.lastJoinDay(), today, progress.get(StatKeys.JOIN_STREAK));
        progress.set(StatKeys.JOIN_STREAK, streak);
        progress.setLastJoinDay(today);
        tracker.raiseTo(uuid, StatKeys.BEST_JOIN_STREAK, streak);
        if (join) {
            int hour = at.getHour();
            if (hour < 4) {
                tracker.raiseTo(uuid, StatKeys.JOIN_NIGHT, 1);
            } else if (hour >= 5 && hour < 7) {
                tracker.raiseTo(uuid, StatKeys.JOIN_EARLY, 1);
            }
        }
        long firstSeen = progress.firstSeen();
        if (firstSeen > 0 && !at.isBefore(Instant.ofEpochMilli(firstSeen).atZone(zone).plusYears(1))) {
            tracker.raiseTo(uuid, StatKeys.SERVER_YEAR, 1);
        }
        // Every date is offered. The tracker keeps only the ones a feat watches, so the file stays small.
        tracker.raiseTo(uuid, StatKeys.DATE_PREFIX + String.format("%02d%02d", date.getMonthValue(), date.getDayOfMonth()), 1);
        if (installedAt > 0) {
            LocalDate installed = dateOf(installedAt, zone);
            if (date.getYear() > installed.getYear() && date.getMonthValue() == installed.getMonthValue()
                    && date.getDayOfMonth() == installed.getDayOfMonth()) {
                tracker.raiseTo(uuid, StatKeys.SERVER_BIRTHDAY, 1);
            }
        }
    }
}
