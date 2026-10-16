package dev.ryvrook.feats.track;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The game calendar as the samples see it. Each world keeps the last game date read from its
 * {@code WorldTimeResource}, so two players in different worlds get their own world's clock. A date that
 * moved forward is a rollover, which the hooks turn into a game day for every player in that world. A
 * date that moved backwards, for example after an admin set the time, is only recorded.
 *
 * <p>Deaths are watched the same way. The combat hooks own the deaths counter, so this class compares
 * the counter it is handed with the value it saw last and reports a rise, which ends the current life.
 * The first look at a player or a world only records what is there.
 */
public final class GameDays {
    private final Map<String, LocalDate> dateOf = new ConcurrentHashMap<>();
    private final Map<UUID, Long> deathsOf = new ConcurrentHashMap<>();

    /** Records the world's date. True when it moved on from the date seen before. */
    public boolean rollover(String world, LocalDate date) {
        if (world == null || date == null) {
            return false;
        }
        LocalDate last = dateOf.put(world, date);
        return last != null && date.isAfter(last);
    }

    /** True when the player's deaths counter rose since the last look. The first look only records it. */
    public boolean died(UUID player, long deaths) {
        if (player == null) {
            return false;
        }
        Long last = deathsOf.put(player, deaths);
        return last != null && deaths > last;
    }

    /** The player left. */
    public void forget(UUID player) {
        if (player != null) {
            deathsOf.remove(player);
        }
    }

    /** The world went away. */
    public void forgetWorld(String world) {
        if (world != null) {
            dateOf.remove(world);
        }
    }
}
