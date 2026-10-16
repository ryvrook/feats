package dev.ryvrook.feats.export;

import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Pack;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.model.Tier;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The Discord webhook keeps its tier rule. Every banner feat is Platinum, so a banner feat always meets any
 * {@code DiscordMinimumTier} and no banner rule is needed in {@link LeaderboardExporter}.
 */
class BannerWebhookTest {
    @Test
    void everyBannerFeatIsPlatinumSoTheTierRuleAlreadyPostsIt() {
        FeatRegistry registry = new FeatRegistry(EnumSet.allOf(Sibling.class), EnumSet.allOf(Pack.class));
        int banners = 0;
        for (Feat f : registry.all()) {
            if (f.banner()) {
                banners++;
                assertTrue(DiscordWebhook.meetsTier(f.tier(), Tier.PLATINUM.name()), f.id() + " is a " + f.tier() + " banner feat");
            }
        }
        assumeTrue(banners > 0, "no banner feats in the registry yet, the 1.7.2 feat wave adds them");
    }
}
