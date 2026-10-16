package dev.ryvrook.feats.track;

import dev.ryvrook.feats.model.Category;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.Requirement;
import dev.ryvrook.feats.model.Scope;
import dev.ryvrook.feats.model.Tier;
import dev.ryvrook.feats.model.Trigger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerQueueTest {
    private static final double UNKNOWN = -1;

    private static Feat personal(String id, Tier tier) {
        return new Feat(id, id, "do " + id, Category.EXPLORATION, tier, "Tool_Map",
                List.of(new Requirement("k", 1)), false);
    }

    private static Feat milestone(String id) {
        return new Feat(id, id, "the server did " + id, Category.WORLD, Tier.GOLD, "Tool_Map", List.of(), false,
                Scope.WORLD, Trigger.total("k", 10));
    }

    private static Feat first(String id) {
        return new Feat(id, id, "someone got " + id, Category.WORLD, Tier.GOLD, "Tool_Map", List.of(), false,
                Scope.FIRST, Trigger.after("other"));
    }

    @Test
    void offShowsNothingAtAll() {
        assertNull(BannerQueue.reason("OFF", personal("a", Tier.PLATINUM), 0.001, 1));
        assertNull(BannerQueue.reason("OFF", milestone("m"), UNKNOWN, 1));
        assertNull(BannerQueue.reason("OFF", first("f"), UNKNOWN, 1));
        assertNull(BannerQueue.reason("OFF", personal("a", Tier.GOLD).withBanner(), UNKNOWN, 1));
        assertNull(BannerQueue.reason("RARE", null, UNKNOWN, 1));
        assertNull(BannerQueue.reason(null, personal("a", Tier.PLATINUM), UNKNOWN, 1));
    }

    @Test
    void platinumShowsPlatinumAndServerEvents() {
        assertEquals("Platinum", BannerQueue.reason("PLATINUM", personal("a", Tier.PLATINUM), UNKNOWN, 1));
        assertEquals("Server milestone", BannerQueue.reason("PLATINUM", milestone("m"), UNKNOWN, 1));
        assertEquals("Server first", BannerQueue.reason("PLATINUM", first("f"), UNKNOWN, 1));
        assertNull(BannerQueue.reason("PLATINUM", personal("a", Tier.GOLD).withBanner(), 0.001, 1));
        assertNull(BannerQueue.reason("PLATINUM", personal("a", Tier.GOLD), 0.001, 1));
    }

    @Test
    void rareAddsTheFlagAndTheRarityRule() {
        assertEquals("Platinum", BannerQueue.reason("RARE", personal("a", Tier.PLATINUM), UNKNOWN, 1));
        assertEquals("Gold", BannerQueue.reason("RARE", personal("a", Tier.GOLD).withBanner(), UNKNOWN, 1));
        assertEquals("Rare, under 1% of players", BannerQueue.reason("RARE", personal("a", Tier.SILVER), 0.005, 1));
        assertEquals("Rare, 3% of players", BannerQueue.reason("RARE", personal("a", Tier.SILVER), 0.03, 5));
        assertNull(BannerQueue.reason("RARE", personal("a", Tier.SILVER), 0.05, 5), "5% is not under 5%");
        assertNull(BannerQueue.reason("RARE", personal("a", Tier.GOLD), UNKNOWN, 1), "unknown rarity is not rare");
        assertNull(BannerQueue.reason("RARE", personal("a", Tier.GOLD), 0.001, 0), "a zero threshold turns rarity off");
        assertEquals("Server milestone", BannerQueue.reason("RARE", milestone("m"), UNKNOWN, 1));
        assertEquals("Server first", BannerQueue.reason("RARE", first("f"), UNKNOWN, 1));
        assertEquals("Rare, under 1% of players", BannerQueue.reason("RARE", personal("a", Tier.GOLD).withBanner(), 0.001, 1),
                "a known rarity is the more useful line for a flagged feat");
    }

    @Test
    void allShowsEveryUnlockWithTheTierAsTheWhy() {
        assertEquals("Bronze", BannerQueue.reason("ALL", personal("a", Tier.BRONZE), UNKNOWN, 1));
        assertEquals("Platinum", BannerQueue.reason("ALL", personal("a", Tier.PLATINUM), UNKNOWN, 1));
        assertEquals("Rare, under 1% of players", BannerQueue.reason("ALL", personal("a", Tier.BRONZE), 0.001, 1));
        assertEquals("Gold", BannerQueue.reason("all", personal("a", Tier.GOLD), UNKNOWN, 1), "case does not matter");
        assertEquals("Gold", BannerQueue.reason("nonsense", personal("a", Tier.GOLD).withBanner(), UNKNOWN, 1),
                "an unknown setting reads as RARE");
        assertNull(BannerQueue.reason("nonsense", personal("a", Tier.GOLD), UNKNOWN, 1));
    }

    @Test
    void broadcastLinesCarryTheName() {
        assertEquals("Server first, by Ada", BannerQueue.broadcastReason(first("f"), "Ada"));
        assertEquals("Server milestone, by Ada", BannerQueue.broadcastReason(milestone("m"), " Ada "));
        assertEquals("Server milestone", BannerQueue.broadcastReason(milestone("m"), null));
        assertEquals("Server first", BannerQueue.broadcastReason(first("f"), ""));
    }

    @Test
    void threeInARowThenNothingUntilTheMinutePasses() {
        BannerQueue queue = new BannerQueue();
        UUID p = UUID.randomUUID();
        assertNotNull(queue.offer(p, 0, 6_000));
        assertNotNull(queue.offer(p, 10_000, 6_000));
        assertNotNull(queue.offer(p, 20_000, 6_000));
        assertNull(queue.offer(p, 30_000, 6_000), "the fourth inside a minute is dropped");
        assertNull(queue.offer(p, 59_999, 6_000));
        assertNotNull(queue.offer(p, 60_000, 6_000), "the first one is a minute old now");
        assertNull(queue.offer(p, 60_001, 6_000), "the second and third are still inside the window");
        assertNotNull(queue.offer(p, 70_000, 6_000));
        assertNull(queue.offer(null, 0, 6_000));
    }

    @Test
    void playersAreCountedApart() {
        BannerQueue queue = new BannerQueue();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            assertNotNull(queue.offer(a, i, 6_000));
        }
        assertNull(queue.offer(a, 3, 6_000));
        assertNotNull(queue.offer(b, 3, 6_000));
        queue.clear(a);
        assertNotNull(queue.offer(a, 4, 6_000), "a cleared player starts over");
    }

    @Test
    void aSecondBannerExtendsTheRemovalAndOutlivesTheFirstTimer() {
        BannerQueue queue = new BannerQueue();
        UUID p = UUID.randomUUID();
        BannerQueue.Shown one = queue.offer(p, 0, 6_000);
        assertEquals(6_000, one.removeAt());
        assertTrue(queue.isUp(p, 5_999));
        BannerQueue.Shown two = queue.offer(p, 4_000, 6_000);
        assertEquals(10_000, two.removeAt(), "a full stay from the second show, not the first one's end");
        assertTrue(two.generation() > one.generation());
        assertFalse(queue.pending(p, one.generation()), "the first timer has nothing left to do");
        assertFalse(queue.removed(p, one.generation()), "the first timer must not remove the second banner");
        assertTrue(queue.isUp(p, 6_000), "still up after the first timer fired");
        assertTrue(queue.pending(p, two.generation()));
        assertTrue(queue.removed(p, two.generation()), "the second timer removes it");
        assertFalse(queue.isUp(p, 10_000));
        assertFalse(queue.pending(p, two.generation()));
        assertFalse(queue.removed(p, two.generation()), "a removal only counts once");
        assertFalse(queue.removed(UUID.randomUUID(), 1), "an unknown player has nothing up");
    }

    @Test
    void aBannerStaysPendingUntilTheRemovalIsRecorded() {
        BannerQueue queue = new BannerQueue();
        UUID p = UUID.randomUUID();
        BannerQueue.Shown shown = queue.offer(p, 0, 6_000);
        assertTrue(queue.pending(p, shown.generation()), "asking does not take it down");
        assertTrue(queue.pending(p, shown.generation()), "a removal that did not land leaves it pending");
        assertEquals(java.util.Set.of(p), queue.withBannerUp());
        assertTrue(queue.removed(p, shown.generation()));
        assertTrue(queue.withBannerUp().isEmpty());
    }

    @Test
    void generationsAreNeverReusedAcrossPlayersQueuesOrAReconnect() {
        BannerQueue queue = new BannerQueue();
        UUID p = UUID.randomUUID();
        BannerQueue.Shown before = queue.offer(p, 0, 6_000);
        queue.clear(p); // the player left
        BannerQueue.Shown after = queue.offer(p, 1_000, 6_000);
        assertTrue(after.generation() > before.generation(), "a reconnect does not start the count again");
        assertFalse(queue.pending(p, before.generation()));
        assertFalse(queue.removed(p, before.generation()), "the timer from before the reconnect cannot match");
        assertTrue(queue.pending(p, after.generation()), "the new banner is still up");
        BannerQueue.Shown other = queue.offer(UUID.randomUUID(), 1_000, 6_000);
        BannerQueue.Shown elsewhere = new BannerQueue().offer(p, 1_000, 6_000);
        assertTrue(other.generation() > after.generation(), "one count for every player");
        assertTrue(elsewhere.generation() > other.generation(), "and for every queue");
        queue.clearAll();
        assertTrue(queue.withBannerUp().isEmpty());
    }

    @Test
    void aShorterStayNeverPullsTheRemovalForward() {
        BannerQueue queue = new BannerQueue();
        UUID p = UUID.randomUUID();
        queue.offer(p, 0, 30_000);
        BannerQueue.Shown later = queue.offer(p, 1_000, 2_000);
        assertEquals(30_000, later.removeAt(), "the later banner keeps the longer time already granted");
    }
}
