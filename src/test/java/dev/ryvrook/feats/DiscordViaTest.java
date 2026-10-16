package dev.ryvrook.feats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordViaTest {
    @Test
    void autoUsesTheWebhookOnlyWithoutSocialBridge() {
        assertTrue(DiscordVia.useWebhook("auto", false));
        assertFalse(DiscordVia.useWebhook("auto", true));
    }

    @Test
    void webhookPostsEvenWithSocialBridge() {
        assertTrue(DiscordVia.useWebhook("webhook", true));
        assertTrue(DiscordVia.useWebhook("WEBHOOK", false));
    }

    @Test
    void offNeverPosts() {
        assertFalse(DiscordVia.useWebhook("off", false));
        assertFalse(DiscordVia.useWebhook(" Off ", true));
    }

    @Test
    void blankOrUnknownReadsAsAuto() {
        assertEquals("auto", DiscordVia.parse(null));
        assertEquals("auto", DiscordVia.parse(""));
        assertEquals("auto", DiscordVia.parse("discord"));
        assertFalse(DiscordVia.useWebhook("discord", true));
    }

    @Test
    void theDefaultIsAuto() {
        assertEquals("auto", new FeatsConfig().discordVia());
    }
}
