package dev.ryvrook.feats.track;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastKillersTest {
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    @Test
    void killingYourLastKillerIsAVendettaOnce() {
        LastKillers killers = new LastKillers();
        assertFalse(killers.killed(bob, alice), "bob kills alice, no score to settle");
        assertEquals(bob, killers.lastKillerOf(alice));
        assertTrue(killers.killed(alice, bob), "alice gets bob back");
        assertNull(killers.lastKillerOf(alice), "the score is settled");
        assertEquals(alice, killers.lastKillerOf(bob));
        assertFalse(killers.killed(alice, bob), "a second kill of bob is not a vendetta until he kills her again");
    }

    @Test
    void onlyTheLastKillerCounts() {
        LastKillers killers = new LastKillers();
        killers.killed(bob, alice);
        killers.killed(carol, alice);
        assertFalse(killers.killed(alice, bob), "bob was not the last one");
        assertTrue(killers.killed(alice, carol));
    }

    @Test
    void nullsAndSelfKillsAreIgnored() {
        LastKillers killers = new LastKillers();
        assertFalse(killers.killed(null, alice));
        assertFalse(killers.killed(alice, null));
        assertFalse(killers.killed(alice, alice));
        assertEquals(0, killers.size());
        assertNull(killers.lastKillerOf(null));
    }

    @Test
    void theRecordIsBounded() {
        LastKillers killers = new LastKillers();
        for (int i = 0; i <= LastKillers.MAX; i++) {
            killers.killed(bob, UUID.randomUUID());
        }
        assertEquals(LastKillers.MAX, killers.size());
    }
}
