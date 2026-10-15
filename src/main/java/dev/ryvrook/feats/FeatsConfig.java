package dev.ryvrook.feats;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Server-owner options, written to {@code Feats.json} inside the mod data folder. */
public final class FeatsConfig {

    public static final BuilderCodec<FeatsConfig> CODEC = BuilderCodec.builder(FeatsConfig.class, FeatsConfig::new)
            .append(new KeyedCodec<>("ShowToasts", Codec.BOOLEAN),
                    (c, v, extra) -> c.showToasts = v,
                    (c, extra) -> c.showToasts)
            .add()
            .append(new KeyedCodec<>("PlaySound", Codec.BOOLEAN),
                    (c, v, extra) -> c.playSound = v,
                    (c, extra) -> c.playSound)
            .add()
            .append(new KeyedCodec<>("BroadcastUnlocks", Codec.BOOLEAN),
                    (c, v, extra) -> c.broadcastUnlocks = v,
                    (c, extra) -> c.broadcastUnlocks)
            .add()
            .append(new KeyedCodec<>("BroadcastMinimumTier", Codec.STRING),
                    (c, v, extra) -> c.broadcastMinimumTier = v,
                    (c, extra) -> c.broadcastMinimumTier)
            .add()
            .append(new KeyedCodec<>("WelcomeHint", Codec.BOOLEAN),
                    (c, v, extra) -> c.welcomeHint = v,
                    (c, extra) -> c.welcomeHint)
            .add()
            .append(new KeyedCodec<>("TrackDistance", Codec.BOOLEAN),
                    (c, v, extra) -> c.trackDistance = v,
                    (c, extra) -> c.trackDistance)
            .add()
            .append(new KeyedCodec<>("AutosaveSeconds", Codec.INTEGER),
                    (c, v, extra) -> c.autosaveSeconds = v,
                    (c, extra) -> c.autosaveSeconds)
            .add()
            .append(new KeyedCodec<>("ExportSeconds", Codec.INTEGER),
                    (c, v, extra) -> c.exportSeconds = v,
                    (c, extra) -> c.exportSeconds)
            .add()
            .append(new KeyedCodec<>("RewardsEnabled", Codec.BOOLEAN),
                    (c, v, extra) -> c.rewardsEnabled = v,
                    (c, extra) -> c.rewardsEnabled)
            .add()
            .append(new KeyedCodec<>("IgnoreCreative", Codec.BOOLEAN),
                    (c, v, extra) -> c.ignoreCreative = v,
                    (c, extra) -> c.ignoreCreative)
            .add()
            .append(new KeyedCodec<>("ShowRarity", Codec.BOOLEAN),
                    (c, v, extra) -> c.showRarity = v,
                    (c, extra) -> c.showRarity)
            .add()
            .append(new KeyedCodec<>("RarityMinimumPlayers", Codec.INTEGER),
                    (c, v, extra) -> c.rarityMinimumPlayers = v,
                    (c, extra) -> c.rarityMinimumPlayers)
            .add()
            .append(new KeyedCodec<>("RareBelowPercent", Codec.INTEGER),
                    (c, v, extra) -> c.rareBelowPercent = v,
                    (c, extra) -> c.rareBelowPercent)
            .add()
            .append(new KeyedCodec<>("AllowViewOthers", Codec.BOOLEAN),
                    (c, v, extra) -> c.allowViewOthers = v,
                    (c, extra) -> c.allowViewOthers)
            .add()
            .append(new KeyedCodec<>("BigTitleMinimumTier", Codec.STRING),
                    (c, v, extra) -> c.bigTitleMinimumTier = v,
                    (c, extra) -> c.bigTitleMinimumTier)
            .add()
            .append(new KeyedCodec<>("Titles", Codec.STRING),
                    (c, v, extra) -> c.titles = v,
                    (c, extra) -> c.titles)
            .add()
            .append(new KeyedCodec<>("DiscordWebhookUrl", Codec.STRING),
                    (c, v, extra) -> c.discordWebhookUrl = v,
                    (c, extra) -> c.discordWebhookUrl)
            .add()
            .append(new KeyedCodec<>("DiscordMinimumTier", Codec.STRING),
                    (c, v, extra) -> c.discordMinimumTier = v,
                    (c, extra) -> c.discordMinimumTier)
            .add()
            .append(new KeyedCodec<>("DisabledWorlds", Codec.STRING_ARRAY),
                    (c, v, extra) -> c.disabledWorlds = v,
                    (c, extra) -> c.disabledWorlds)
            .add()
            .append(new KeyedCodec<>("PausePlaytimeWhenDisabled", Codec.BOOLEAN),
                    (c, v, extra) -> c.pausePlaytimeWhenDisabled = v,
                    (c, extra) -> c.pausePlaytimeWhenDisabled)
            .add()
            .append(new KeyedCodec<>("UnlockSound", Codec.STRING),
                    (c, v, extra) -> c.unlockSound = v,
                    (c, extra) -> c.unlockSound)
            .add()
            .append(new KeyedCodec<>("Tracker", Codec.STRING),
                    (c, v, extra) -> c.tracker = v,
                    (c, extra) -> c.tracker)
            .add()
            .build();

    private boolean showToasts = true;
    private boolean playSound = true;
    private boolean broadcastUnlocks = true;
    private String broadcastMinimumTier = "SILVER";
    private boolean welcomeHint = true;
    private boolean trackDistance = true;
    private int autosaveSeconds = 60;
    private int exportSeconds = 15;
    private boolean rewardsEnabled = false;
    private boolean ignoreCreative = true;
    private boolean showRarity = true;
    private int rarityMinimumPlayers = 10;
    private int rareBelowPercent = 5;
    private boolean allowViewOthers = true;
    private String bigTitleMinimumTier = "PLATINUM";
    private String titles = "PREFIX";
    private String discordWebhookUrl = "";
    private String discordMinimumTier = "GOLD";
    private String[] disabledWorlds = new String[0];
    private boolean pausePlaytimeWhenDisabled = true;
    private String unlockSound = "SFX_Memories_Unlock_Local";
    private String tracker = "auto";

    public boolean showToasts() {
        return showToasts;
    }

    public boolean playSound() {
        return playSound;
    }

    public boolean broadcastUnlocks() {
        return broadcastUnlocks;
    }

    public String broadcastMinimumTier() {
        return broadcastMinimumTier;
    }

    public boolean welcomeHint() {
        return welcomeHint;
    }

    public boolean trackDistance() {
        return trackDistance;
    }

    public int autosaveSeconds() {
        return Math.max(10, autosaveSeconds);
    }

    /** Seconds between leaderboard export checks. Zero or less turns the export off. */
    public int exportSeconds() {
        return exportSeconds <= 0 ? 0 : Math.max(5, exportSeconds);
    }

    public boolean rewardsEnabled() {
        return rewardsEnabled;
    }

    public boolean ignoreCreative() {
        return ignoreCreative;
    }

    public boolean showRarity() {
        return showRarity;
    }

    /** Counted players needed before rarity shows, at least 1. */
    public int rarityMinimumPlayers() {
        return Math.max(1, rarityMinimumPlayers);
    }

    /** Owned by fewer than this percent of counted players reads as rare, 0 to 100. */
    public int rareBelowPercent() {
        return Math.clamp(rareBelowPercent, 0, 100);
    }

    public boolean allowViewOthers() {
        return allowViewOthers;
    }

    public String bigTitleMinimumTier() {
        return bigTitleMinimumTier;
    }

    public String titles() {
        return titles;
    }

    public String discordWebhookUrl() {
        return discordWebhookUrl;
    }

    public String discordMinimumTier() {
        return discordMinimumTier;
    }

    /** World names where every counter stops, compared ignoring case. Never null. */
    public Set<String> disabledWorlds() {
        if (disabledWorlds == null || disabledWorlds.length == 0) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String name : disabledWorlds) {
            if (name != null && !name.isBlank()) {
                out.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(out);
    }

    /** True when time played also stops in Creative mode and disabled worlds. */
    public boolean pausePlaytimeWhenDisabled() {
        return pausePlaytimeWhenDisabled;
    }

    /** Sound asset id played on unlock. An empty or unknown id falls back to the Memories unlock sound. */
    public String unlockSound() {
        return unlockSound;
    }

    /**
     * Where the counters come from. {@code auto} uses TrackerAPI when it is installed and new enough, else
     * the Feats systems. {@code always} uses TrackerAPI only and tracks nothing without it. {@code never}
     * ignores TrackerAPI. Read once at setup, a change needs a restart or a reload of Feats.
     */
    public String tracker() {
        return tracker == null || tracker.isBlank() ? "auto" : tracker;
    }
}
