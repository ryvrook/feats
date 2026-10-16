package dev.ryvrook.feats;

import java.util.Locale;

/** The values of the {@code DiscordVia} setting and the rule that picks the webhook or SocialBridge. */
public final class DiscordVia {
    public static final String AUTO = "auto";
    public static final String WEBHOOK = "webhook";
    public static final String OFF = "off";

    private DiscordVia() {
    }

    /** The setting in lower case, {@code auto} for anything blank or unknown. */
    public static String parse(String value) {
        if (value == null) {
            return AUTO;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return WEBHOOK.equals(v) || OFF.equals(v) ? v : AUTO;
    }

    /**
     * True when Feats posts through its own webhook. On {@code auto} that is only while SocialBridge is not
     * installed, since SocialBridge reads the unlock event and posts it to its own channel.
     */
    public static boolean useWebhook(String via, boolean socialBridgeInstalled) {
        return switch (parse(via)) {
            case WEBHOOK -> true;
            case OFF -> false;
            default -> !socialBridgeInstalled;
        };
    }
}
