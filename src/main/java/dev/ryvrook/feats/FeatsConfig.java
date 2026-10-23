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
            .append(new KeyedCodec<>("ChatLine", Codec.BOOLEAN),
                    (c, v, extra) -> c.chatLine = v,
                    (c, extra) -> c.chatLine)
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
            .append(new KeyedCodec<>("Coinage", Codec.STRING),
                    (c, v, extra) -> c.linkCoinage = v,
                    (c, extra) -> c.linkCoinage)
            .add()
            .append(new KeyedCodec<>("Dominion", Codec.STRING),
                    (c, v, extra) -> c.linkDominion = v,
                    (c, extra) -> c.linkDominion)
            .add()
            .append(new KeyedCodec<>("Chronicle", Codec.STRING),
                    (c, v, extra) -> c.linkChronicle = v,
                    (c, extra) -> c.linkChronicle)
            .add()
            .append(new KeyedCodec<>("Atlas", Codec.STRING),
                    (c, v, extra) -> c.linkAtlas = v,
                    (c, extra) -> c.linkAtlas)
            .add()
            .append(new KeyedCodec<>("LinkRPGLeveling", Codec.STRING),
                    (c, v, extra) -> c.linkRpgLeveling = v,
                    (c, extra) -> c.linkRpgLeveling)
            .add()
            .append(new KeyedCodec<>("LinkMajorDungeons", Codec.STRING),
                    (c, v, extra) -> c.linkMajorDungeons = v,
                    (c, extra) -> c.linkMajorDungeons)
            .add()
            .append(new KeyedCodec<>("LinkPerfectParries", Codec.STRING),
                    (c, v, extra) -> c.linkPerfectParries = v,
                    (c, extra) -> c.linkPerfectParries)
            .add()
            .append(new KeyedCodec<>("LinkWonderWeapons", Codec.STRING),
                    (c, v, extra) -> c.linkWonderWeapons = v,
                    (c, extra) -> c.linkWonderWeapons)
            .add()
            .append(new KeyedCodec<>("LinkZephyr", Codec.STRING),
                    (c, v, extra) -> c.linkZephyr = v,
                    (c, extra) -> c.linkZephyr)
            .add()
            .append(new KeyedCodec<>("LinkSimpleEnchantments", Codec.STRING),
                    (c, v, extra) -> c.linkSimpleEnchantments = v,
                    (c, extra) -> c.linkSimpleEnchantments)
            .add()
            .append(new KeyedCodec<>("LinkVeinMining", Codec.STRING),
                    (c, v, extra) -> c.linkVeinMining = v,
                    (c, extra) -> c.linkVeinMining)
            .add()
            .append(new KeyedCodec<>("LinkLuckyMining", Codec.STRING),
                    (c, v, extra) -> c.linkLuckyMining = v,
                    (c, extra) -> c.linkLuckyMining)
            .add()
            .append(new KeyedCodec<>("LinkMMOSkillTree", Codec.STRING),
                    (c, v, extra) -> c.linkMmoSkillTree = v,
                    (c, extra) -> c.linkMmoSkillTree)
            .add()
            .append(new KeyedCodec<>("LinkEndgame", Codec.STRING),
                    (c, v, extra) -> c.linkEndgame = v,
                    (c, extra) -> c.linkEndgame)
            .add()
            .append(new KeyedCodec<>("LinkTamework", Codec.STRING),
                    (c, v, extra) -> c.linkTamework = v,
                    (c, extra) -> c.linkTamework)
            .add()
            .append(new KeyedCodec<>("LinkAnglersAlmanac", Codec.STRING),
                    (c, v, extra) -> c.linkAnglersAlmanac = v,
                    (c, extra) -> c.linkAnglersAlmanac)
            .add()
            .append(new KeyedCodec<>("LinkArcanum", Codec.STRING),
                    (c, v, extra) -> c.linkArcanum = v,
                    (c, extra) -> c.linkArcanum)
            .add()
            .append(new KeyedCodec<>("LinkYmmersiveMelodies", Codec.STRING),
                    (c, v, extra) -> c.linkYmmersiveMelodies = v,
                    (c, extra) -> c.linkYmmersiveMelodies)
            .add()
            .append(new KeyedCodec<>("LinkModernStorage", Codec.STRING),
                    (c, v, extra) -> c.linkModernStorage = v,
                    (c, extra) -> c.linkModernStorage)
            .add()
            .append(new KeyedCodec<>("LinkMachineBlocks", Codec.STRING),
                    (c, v, extra) -> c.linkMachineBlocks = v,
                    (c, extra) -> c.linkMachineBlocks)
            .add()
            .append(new KeyedCodec<>("LinkFancyStorage", Codec.STRING),
                    (c, v, extra) -> c.linkFancyStorage = v,
                    (c, extra) -> c.linkFancyStorage)
            .add()
            .append(new KeyedCodec<>("LinkPotionShop", Codec.STRING),
                    (c, v, extra) -> c.linkPotionShop = v,
                    (c, extra) -> c.linkPotionShop)
            .add()
            .append(new KeyedCodec<>("LinkSigsGarden", Codec.STRING),
                    (c, v, extra) -> c.linkSigsGarden = v,
                    (c, extra) -> c.linkSigsGarden)
            .add()
            .append(new KeyedCodec<>("LinkBlahaj", Codec.STRING),
                    (c, v, extra) -> c.linkBlahaj = v,
                    (c, extra) -> c.linkBlahaj)
            .add()
            .append(new KeyedCodec<>("LinkInfiniteWater", Codec.STRING),
                    (c, v, extra) -> c.linkInfiniteWater = v,
                    (c, extra) -> c.linkInfiniteWater)
            .add()
            .append(new KeyedCodec<>("LinkInfiniteTeleporters", Codec.STRING),
                    (c, v, extra) -> c.linkInfiniteTeleporters = v,
                    (c, extra) -> c.linkInfiniteTeleporters)
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
    private boolean chatLine = true;
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
    private String banner = "OFF";
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
    private String linkCoinage = "auto";
    private String linkDominion = "auto";
    private String linkChronicle = "auto";
    private String linkAtlas = "auto";
    // The mods TrackerAPI 1.2.0 reads, see docs/MODS-PLAN.md. Each needs TrackerAPI 1.2.0 as well.
    private String linkRpgLeveling = "auto";
    private String linkMajorDungeons = "auto";
    private String linkPerfectParries = "auto";
    private String linkWonderWeapons = "auto";
    private String linkZephyr = "auto";
    private String linkSimpleEnchantments = "auto";
    private String linkVeinMining = "auto";
    private String linkLuckyMining = "auto";
    private String linkMmoSkillTree = "auto";
    private String linkEndgame = "auto";
    private String linkTamework = "auto";
    // 1.10.0, the three mods of TrackerAPI 1.3.0, the two plugins and the six asset packs.
    private String linkAnglersAlmanac = "auto";
    private String linkArcanum = "auto";
    private String linkYmmersiveMelodies = "auto";
    private String linkModernStorage = "auto";
    private String linkMachineBlocks = "auto";
    private String linkFancyStorage = "auto";
    private String linkPotionShop = "auto";
    private String linkSigsGarden = "auto";
    private String linkBlahaj = "auto";
    private String linkInfiniteWater = "auto";
    private String linkInfiniteTeleporters = "auto";

    public boolean showToasts() {
        return showToasts;
    }

    /** Whether the player gets their own unlock chat line and the catch up summary line. */
    public boolean chatLine() {
        return chatLine;
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

    /**
     * The link setting for one sibling, {@code auto}, {@code always} or {@code never}. TrackerAPI reads
     * {@link #tracker()}. {@code auto} links when the sibling is installed and new enough, {@code always} does
     * the same and warns at start when it is missing, {@code never} ignores it, so its feats and bindings stay
     * out. The mods TrackerAPI reads, the plugins and the asset packs of 1.10.0 have the {@code Link<Name>} keys,
     * {@code Sibling.settingKey()} names each. Read once at setup.
     */
    public String link(dev.ryvrook.feats.model.Sibling sibling) {
        String v = switch (sibling) {
            case TRACKER_API -> tracker;
            case COINAGE -> linkCoinage;
            case DOMINION -> linkDominion;
            case CHRONICLE -> linkChronicle;
            case ATLAS -> linkAtlas;
            case RPG_LEVELING -> linkRpgLeveling;
            case MAJOR_DUNGEONS -> linkMajorDungeons;
            case PERFECT_PARRIES -> linkPerfectParries;
            case WONDER_WEAPONS -> linkWonderWeapons;
            case ZEPHYR -> linkZephyr;
            case SIMPLE_ENCHANTMENTS -> linkSimpleEnchantments;
            case VEIN_MINING -> linkVeinMining;
            case LUCKY_MINING -> linkLuckyMining;
            case MMO_SKILL_TREE -> linkMmoSkillTree;
            case ENDGAME -> linkEndgame;
            case TAMEWORK -> linkTamework;
            case FISHING -> linkAnglersAlmanac;
            case ARCANUM -> linkArcanum;
            case MELODIES -> linkYmmersiveMelodies;
            case STORAGE -> linkModernStorage;
            case MACHINES -> linkMachineBlocks;
            case FANCY_STORAGE -> linkFancyStorage;
            case POTION_SHOP -> linkPotionShop;
            case SIGS_GARDEN -> linkSigsGarden;
            case BLAHAJ -> linkBlahaj;
            case INFINITE_WATER -> linkInfiniteWater;
            case INFINITE_TELEPORTERS -> linkInfiniteTeleporters;
        };
        return v == null || v.isBlank() ? "auto" : v.trim().toLowerCase(Locale.ROOT);
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

    /** OFF, PLATINUM, RARE or ALL, upper case. Unknown reads as OFF, the default. */
    public String banner() {
        String b = banner == null ? "OFF" : banner.trim().toUpperCase(Locale.ROOT);
        return switch (b) {
            case "OFF", "PLATINUM", "RARE", "ALL" -> b;
            default -> "OFF";
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
