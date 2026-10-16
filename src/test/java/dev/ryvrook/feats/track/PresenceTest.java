package dev.ryvrook.feats.track;

import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceTest {
    @Test
    void playersMetCountsEveryoneSeenOnlineTogether() {
        FeatsTracker tracker = TestTrackers.tracker();
        Presence presence = new Presence(tracker);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        PlayerProgress pa = tracker.load(a, "A");
        PlayerProgress pb = tracker.load(b, "B");
        presence.tick(List.of(a));
        assertEquals(0, pa.get(StatKeys.PLAYERS_MET), "alone");
        assertEquals(1, pa.get(StatKeys.PEAK_WITH_ME));
        presence.tick(List.of(a, b));
        assertEquals(1, pa.get(StatKeys.PLAYERS_MET));
        assertEquals(1, pb.get(StatKeys.PLAYERS_MET));
        assertTrue(pa.hasMet(b));
        assertTrue(pb.hasMet(a));
        assertEquals(2, pa.get(StatKeys.PEAK_WITH_ME));
        presence.tick(List.of(a, b));
        assertEquals(1, pa.get(StatKeys.PLAYERS_MET), "the same company again does not count twice");
        PlayerProgress pc = tracker.load(c, "C");
        presence.tick(List.of(a, b, c));
        assertEquals(2, pa.get(StatKeys.PLAYERS_MET));
        assertEquals(2, pb.get(StatKeys.PLAYERS_MET));
        assertEquals(2, pc.get(StatKeys.PLAYERS_MET), "the newcomer met both");
        assertEquals(3, pc.get(StatKeys.PEAK_WITH_ME));
        presence.tick(List.of(a));
        presence.tick(List.of(a, b));
        assertEquals(2, pa.get(StatKeys.PLAYERS_MET), "B coming back is still B");
        assertEquals(3, pa.get(StatKeys.PEAK_WITH_ME), "the peak stays");
    }

    @Test
    void coopHoursNeedCompanyForAWholeHour() {
        FeatsTracker tracker = TestTrackers.tracker();
        Presence presence = new Presence(tracker);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        PlayerProgress pa = tracker.load(a, "A");
        PlayerProgress pb = tracker.load(b, "B");
        for (int i = 0; i < Presence.SECONDS_PER_HOUR; i++) {
            presence.tick(List.of(a));
        }
        assertEquals(0, pa.get(StatKeys.COOP_HOURS), "an hour alone is not company");
        assertEquals(0, presence.coopSeconds(a));
        for (int i = 0; i < Presence.SECONDS_PER_HOUR - 1; i++) {
            presence.tick(List.of(a, b));
        }
        assertEquals(0, pa.get(StatKeys.COOP_HOURS));
        assertEquals(Presence.SECONDS_PER_HOUR - 1, presence.coopSeconds(a));
        presence.tick(List.of(a, b));
        assertEquals(1, pa.get(StatKeys.COOP_HOURS));
        assertEquals(1, pb.get(StatKeys.COOP_HOURS));
        for (int i = 0; i < Presence.SECONDS_PER_HOUR; i++) {
            presence.tick(List.of(a, b));
        }
        assertEquals(2, pa.get(StatKeys.COOP_HOURS));
        presence.forget(b);
        assertEquals(0, presence.coopSeconds(b), "the partial hour leaves with the player");
        assertEquals(2 * Presence.SECONDS_PER_HOUR, presence.coopSeconds(a));
    }

    @Test
    void theMetSetStopsAtTheCap() {
        FeatsTracker tracker = TestTrackers.tracker();
        UUID a = UUID.randomUUID();
        PlayerProgress pa = tracker.load(a, "A");
        for (int i = 0; i < PlayerProgress.MET_CAP + 5; i++) {
            pa.meet(UUID.randomUUID());
        }
        assertEquals(PlayerProgress.MET_CAP, pa.metCount());
        assertFalse(pa.meet(UUID.randomUUID()), "full");
        assertFalse(pa.meet(a), "never meets themselves");
        Presence presence = new Presence(tracker);
        UUID b = UUID.randomUUID();
        PlayerProgress pb = tracker.load(b, "B");
        presence.tick(List.of(a, b));
        assertEquals(0, pa.get(StatKeys.PLAYERS_MET), "nothing new fits, so the counter is not touched");
        assertEquals(1, pb.get(StatKeys.PLAYERS_MET), "B has room");
        assertTrue(tracker.isPersisted(StatKeys.PLAYERS_MET));
        assertTrue(tracker.isPersisted(StatKeys.COOP_HOURS));
        assertTrue(tracker.isPersisted(StatKeys.PEAK_WITH_ME));
    }
}
