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
            .append(new KeyedCodec<>("DiscordVia", Codec.STRING),
                    (c, v, extra) -> c.discordVia = v,
                    (c, extra) -> c.discordVia)
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
            .append(new KeyedCodec<>("PackSocial", Codec.BOOLEAN),
                    (c, v, extra) -> c.packSocial = v,
                    (c, extra) -> c.packSocial)
            .add()
            .append(new KeyedCodec<>("PackHardcore", Codec.STRING),
                    (c, v, extra) -> c.packHardcore = v,
                    (c, extra) -> c.packHardcore)
            .add()
            .append(new KeyedCodec<>("PackPvp", Codec.BOOLEAN),
                    (c, v, extra) -> c.packPvp = v,
                    (c, extra) -> c.packPvp)
            .add()
            .append(new KeyedCodec<>("PackCalendar", Codec.BOOLEAN),
                    (c, v, extra) -> c.packCalendar = v,
                    (c, extra) -> c.packCalendar)
            .add()
            .append(new KeyedCodec<>("PackGrind", Codec.BOOLEAN),
                    (c, v, extra) -> c.packGrind = v,
                    (c, extra) -> c.packGrind)
            .add()
            .append(new KeyedCodec<>("Banner", Codec.STRING),
                    (c, v, extra) -> c.banner = v,
                    (c, extra) -> c.banner)
            .add()
            .append(new KeyedCodec<>("BannerSeconds", Codec.INTEGER),
                    (c, v, extra) -> c.bannerSeconds = v,
                    (c, extra) -> c.bannerSeconds)
            .add()
            .append(new KeyedCodec<>("BannerRareBelowPercent", Codec.INTEGER),
                    (c, v, extra) -> c.bannerRareBelowPercent = v,
                    (c, extra) -> c.bannerRareBelowPercent)
            .add()
            .append(new KeyedCodec<>("BannerBroadcast", Codec.BOOLEAN),
                    (c, v, extra) -> c.bannerBroadcast = v,
                    (c, extra) -> c.bannerBroadcast)
            .add()
            .append(new KeyedCodec<>("BannerSound", Codec.STRING),
                    (c, v, extra) -> c.bannerSound = v,
                    (c, extra) -> c.bannerSound)
            .add()
            .append(new KeyedCodec<>("BannerParticles", Codec.BOOLEAN),
                    (c, v, extra) -> c.bannerParticles = v,
                    (c, extra) -> c.bannerParticles)
            .add()
            .append(new KeyedCodec<>("FullHouse", Codec.INTEGER),
                    (c, v, extra) -> c.fullHouse = v,
                    (c, extra) -> c.fullHouse)
            .add()
            .append(new KeyedCodec<>("CalendarZone", Codec.STRING),
                    (c, v, extra) -> c.calendarZone = v,
                    (c, extra) -> c.calendarZone)
            .add()
            .append(new KeyedCodec<>("ServerTotals", Codec.BOOLEAN),
                    (c, v, extra) -> c.serverTotals = v,
                    (c, extra) -> c.serverTotals)
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
    private String discordVia = "auto";
    private boolean packSocial = true;
    private String packHardcore = "auto";
    private boolean packPvp = false;
    private boolean packCalendar = true;
    private boolean packGrind = false;
    private String banner = "RARE";
    private int bannerSeconds = 6;
    private int bannerRareBelowPercent = 1;
    private boolean bannerBroadcast = true;
    private String bannerSound = "SFX_Event_Chapter_1_Complete_Sting";
    private boolean bannerParticles = false;
    private int fullHouse = 20;
    private String calendarZone = "";
    private boolean serverTotals = true;
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

    /**
     * Who posts unlocks to Discord. {@code auto} leaves it to SocialBridge when that plugin is installed and
     * uses {@code DiscordWebhookUrl} otherwise. {@code webhook} always uses the webhook. {@code off} posts
     * nothing from Feats. Anything else reads as {@code auto}.
     */
    public String discordVia() {
        return DiscordVia.parse(discordVia);
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
    // ---- 1.7.2 ---------------------------------------------------------------

    /**
     * The packs that are on. {@code hardcore} answers whether the universe runs hardcore mode, read once at
     * start, for the {@code auto} value of PackHardcore.
     */
    public java.util.Set<dev.ryvrook.feats.model.Pack> packs(java.util.function.BooleanSupplier hardcore) {
        java.util.Set<dev.ryvrook.feats.model.Pack> out = java.util.EnumSet.noneOf(dev.ryvrook.feats.model.Pack.class);
        if (packSocial) {
            out.add(dev.ryvrook.feats.model.Pack.SOCIAL);
        }
        String hc = packHardcore == null ? "auto" : packHardcore.trim().toLowerCase(Locale.ROOT);
        boolean hardcoreOn = switch (hc) {
            case "true", "on", "yes" -> true;
            case "false", "off", "no" -> false;
            default -> hardcore != null && hardcore.getAsBoolean();
        };
        if (hardcoreOn) {
            out.add(dev.ryvrook.feats.model.Pack.HARDCORE);
        }
        if (packPvp) {
            out.add(dev.ryvrook.feats.model.Pack.PVP);
        }
        if (packCalendar) {
            out.add(dev.ryvrook.feats.model.Pack.CALENDAR);
        }
        if (packGrind) {
            out.add(dev.ryvrook.feats.model.Pack.GRIND);
        }
        return out;
    }

    /** OFF, PLATINUM, RARE or ALL, upper case. Unknown reads as RARE. */
    public String banner() {
        String b = banner == null ? "RARE" : banner.trim().toUpperCase(Locale.ROOT);
        return switch (b) {
            case "OFF", "PLATINUM", "RARE", "ALL" -> b;
            default -> "RARE";
        };
    }

    /** 2 to 30 seconds. */
    public int bannerSeconds() {
        return Math.max(2, Math.min(30, bannerSeconds));
    }

    /** 0 to 100. */
    public int bannerRareBelowPercent() {
        return Math.max(0, Math.min(100, bannerRareBelowPercent));
    }

    public boolean bannerBroadcast() {
        return bannerBroadcast;
    }

    /** The sound id, or empty for none. */
    public String bannerSound() {
        return bannerSound == null ? "" : bannerSound.trim();
    }

    public boolean bannerParticles() {
        return bannerParticles;
    }

    /** The online count for the Full House milestone, at least 2. */
    public int fullHouse() {
        return Math.max(2, fullHouse);
    }

    /** The zone for the Calendar pack and the join hour feats, the system default when empty or unknown. */
    public java.time.ZoneId calendarZone() {
        if (calendarZone == null || calendarZone.isBlank()) {
            return java.time.ZoneId.systemDefault();
        }
        try {
            return java.time.ZoneId.of(calendarZone.trim());
        } catch (RuntimeException e) {
            return java.time.ZoneId.systemDefault();
        }
    }

    public boolean serverTotals() {
        return serverTotals;
    }
}
