package dev.ryvrook.feats.track;

import dev.ryvrook.feats.FeatsConfig;
import dev.ryvrook.feats.model.Category;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.Requirement;
import dev.ryvrook.feats.model.Scope;
import dev.ryvrook.feats.model.Tier;
import dev.ryvrook.feats.model.Trigger;
import dev.ryvrook.feats.stats.PlayerProgress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The notifier's banner path without a server. The engine calls are skipped, the rules still run. */
class UnlockNotifierBannerTest {
    private static Feat gold(String id) {
        return new Feat(id, id, "do " + id, Category.EXPLORATION, Tier.GOLD, "Tool_Map",
                List.of(new Requirement("k", 1)), false);
    }

    private static Feat milestone(String id) {
        return new Feat(id, id, "the server did " + id, Category.WORLD, Tier.GOLD, "Tool_Map", List.of(), false,
                Scope.WORLD, Trigger.total("k", 10));
    }

    @Test
    void reasonFollowsTheSettingsAndTheRarityHook() {
        FeatsConfig cfg = new FeatsConfig();
        UnlockNotifier notifier = new UnlockNotifier(() -> cfg, TestTrackers.LOGGER);
        assertEquals("RARE", cfg.banner(), "the default setting");
        assertNull(notifier.bannerReason(cfg, gold("plain")), "rarity unknown and no flag");
        assertEquals("Gold", notifier.bannerReason(cfg, gold("flagged").withBanner()));
        notifier.setRarity(id -> id.equals("scarce") ? 0.004 : 0.5);
        assertEquals("Rare, under 1% of players", notifier.bannerReason(cfg, gold("scarce")));
        assertNull(notifier.bannerReason(cfg, gold("common")));
        assertEquals("Server milestone", notifier.bannerReason(cfg, milestone("m")));
        notifier.setRarity(id -> {
            throw new IllegalStateException("export not ready");
        });
        assertNull(notifier.bannerReason(cfg, gold("plain")), "a failing rarity hook reads as unknown");
        assertNull(notifier.bannerReason(null, gold("plain")));
        assertNull(notifier.bannerReason(cfg, null));
    }

    @Test
    void nothingThrowsWithoutAServer() {
        FeatsConfig cfg = new FeatsConfig();
        UnlockNotifier notifier = new UnlockNotifier(() -> cfg, TestTrackers.LOGGER);
        PlayerProgress progress = new PlayerProgress(UUID.randomUUID(), "Ada");
        Feat feat = gold("plain").withBanner();
        assertDoesNotThrow(() -> notifier.unlocked(progress, feat, 1, 2));
        assertDoesNotThrow(() -> notifier.recordBanner(milestone("m"), UUID.randomUUID(), "Ada"));
        assertDoesNotThrow(() -> notifier.recordBanner(milestone("m"), null, null));
        assertDoesNotThrow(() -> notifier.recordBanner(null, null, null));
        UnlockNotifier noConfig = new UnlockNotifier(() -> null, TestTrackers.LOGGER);
        assertDoesNotThrow(() -> noConfig.recordBanner(milestone("m"), null, "Ada"));
        assertDoesNotThrow(() -> noConfig.setNotifications(null));
        assertDoesNotThrow(() -> noConfig.setClock(null));
    }
}
