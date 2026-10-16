package dev.ryvrook.feats.api;

import dev.ryvrook.feats.export.LeaderboardExporter.Ranked;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardEntriesTest {
    private final Ranked first = new Ranked(UUID.randomUUID(), "Ryv", 900, 40, 1_000L);
    private final Ranked second = new Ranked(UUID.randomUUID(), "Moss", 650, 31, 2_000L);
    private final Ranked third = new Ranked(UUID.randomUUID(), "Fern", 20, 2, 3_000L);

    @Test
    void rowsKeepTheRankingOrderAndCountFromOne() {
        List<LeaderboardEntry> top = FeatsApi.entries(List.of(first, second, third), 10);
        assertEquals(3, top.size());
        assertEquals(new LeaderboardEntry(1, first.uuid(), "Ryv", 900, 40, 1_000L), top.get(0));
        assertEquals(2, top.get(1).rank());
        assertEquals("Fern", top.get(2).name());
    }

    @Test
    void theLimitCutsTheList() {
        assertEquals(2, FeatsApi.entries(List.of(first, second, third), 2).size());
    }

    @Test
    void aLimitBelowOneGivesNothing() {
        assertTrue(FeatsApi.entries(List.of(first), 0).isEmpty());
        assertTrue(FeatsApi.entries(List.of(first), -5).isEmpty());
    }

    @Test
    void theListCannotBeChanged() {
        List<LeaderboardEntry> top = FeatsApi.entries(List.of(first), 1);
        assertThrows(UnsupportedOperationException.class, () -> top.add(top.getFirst()));
    }
}
