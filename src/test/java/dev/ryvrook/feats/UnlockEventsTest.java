package dev.ryvrook.feats;

import dev.ryvrook.feats.api.FeatUnlockedEvent;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnlockEventsTest {
    private final FeatRegistry registry = new FeatRegistry(false);

    @Test
    void theEventCarriesWhatSocialBridgeFiltersOn() {
        Feat feat = registry.all().stream().filter(Feat::hidden).findFirst().orElseThrow();
        UUID uuid = UUID.randomUUID();
        FeatUnlockedEvent e = UnlockEvents.of(uuid, "Ryv", feat, 120, false, true, 0.031);
        assertEquals(uuid, e.uuid());
        assertEquals(feat.id(), e.featId());
        assertEquals(feat.tier().name(), e.tier());
        assertEquals(feat.tier().points(), e.points());
        assertEquals(120, e.totalPoints());
        assertTrue(e.admin());
        assertTrue(e.hidden());
        assertEquals(0.031, e.rarity());
        assertEquals(feat.category().displayName(), e.category());
        assertEquals(feat.description(), e.description());
        assertFalse(e.banner());
    }

    @Test
    void theBannerFlagComesFromTheFeat() {
        Feat feat = registry.all().getFirst().withBanner();
        assertTrue(UnlockEvents.of(UUID.randomUUID(), "Ryv", feat, 5, false, false, -1, feat.banner()).banner());
        assertFalse(UnlockEvents.of(UUID.randomUUID(), "Ryv", feat, 5, false, false, -1).banner());
    }

    @Test
    void aMissingNameBecomesEmpty() {
        Feat feat = registry.all().getFirst();
        assertEquals("", UnlockEvents.of(UUID.randomUUID(), null, feat, 5, true, false, -1).playerName());
    }

    @Test
    void theOldConstructorFillsTheNewFields() {
        FeatUnlockedEvent e = new FeatUnlockedEvent(UUID.randomUUID(), "Ryv", "digger", "Digger", "BRONZE", 5, 5, false);
        assertFalse(e.admin());
        assertFalse(e.hidden());
        assertEquals(-1, e.rarity());
        assertEquals("", e.category());
        assertEquals("", e.description());
        assertFalse(e.banner());
        FeatUnlockedEvent v2 = new FeatUnlockedEvent(UUID.randomUUID(), "Ryv", "digger", "Digger", "BRONZE", 5, 5,
                false, false, false, -1, "Mining", "Dig");
        assertFalse(v2.banner());
    }
}
