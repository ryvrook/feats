package dev.ryvrook.feats.model;

/**
 * The plugins Feats adds feats or tracking for when they are installed. Each one is found by group and name in
 * the engine's plugin manager at setup, see {@code integration/Siblings}. {@code minimum} is the oldest version the
 * binding is written against. {@code Siblings.present} leaves out a loaded sibling that is older.
 *
 * <p>The first five are the Ryvrook plugins. Next come the third party mods TrackerAPI reads, see
 * {@code docs/MODS-PLAN.md}. Feats never touches a class of those mods, their counters arrive through TrackerAPI as
 * the generic mod records and are mirrored by {@code integration/ModKinds} and {@code integration/TallyKinds} under
 * {@link #modId()}, so each of them needs {@link #TRACKER_API} at {@link #trackerMinimum()} as well. Since 1.10.0 two
 * more kinds follow, see {@link Kind}: plugins whose feats read the item counters Feats keeps itself, and asset
 * packs, found by their {@code Group:Name} id among the engine's asset packs rather than in the plugin manager.
 */
public enum Sibling {
    TRACKER_API(Sibling.GROUP, "TrackerAPI", "TrackerAPI", "1.0.0", "Tracker", null),
    COINAGE(Sibling.GROUP, "Coinage", "Coinage", "1.2.0", "Coinage", null),
    DOMINION(Sibling.GROUP, "Dominion", "Dominion Factions", "0.5.1", "Dominion", null),
    CHRONICLE(Sibling.GROUP, "ChronicleStats", "Chronicle Stats", "1.5.0", "Chronicle", null),
    ATLAS(Sibling.GROUP, "Atlas", "Atlas", "1.9.0", "Atlas", null),
    // The mods TrackerAPI 1.2.0 reads, with the binding id TrackerAPI uses as their counter prefix.
    RPG_LEVELING("Zuxaw", "RPGLeveling", "RPG Leveling", "0.4.2", "LinkRPGLeveling", "rpg"),
    MAJOR_DUNGEONS("MAJOR76", "MajorDungeons", "Major Dungeons", "0.4.14", "LinkMajorDungeons", "dungeons"),
    PERFECT_PARRIES("narwhals", "Perfect Parries", "Perfect Parries", "0.9.7", "LinkPerfectParries", "parries"),
    WONDER_WEAPONS("wanmine", "WansWonderWeapon", "Wan's Wonder Weapons", "1.1.1", "LinkWonderWeapons", "relics"),
    ZEPHYR("narwhals", "Zephyr", "Zephyr", "0.4.2", "LinkZephyr", "zephyr"),
    SIMPLE_ENCHANTMENTS("org.herolias", "SimpleEnchantments", "Simple Enchantments", "1.2.1", "LinkSimpleEnchantments", "enchant"),
    VEIN_MINING("EineNT", "VeinMining", "Vein Mining", "2.5.0", "LinkVeinMining", "vein"),
    LUCKY_MINING("Buuz135", "LuckyMining", "Lucky Mining", "1.0.8", "LinkLuckyMining", "lucky"),
    MMO_SKILL_TREE("Ziggfreed", "MMOSkillTree", "MMO Skill Tree", "1.7.3", "LinkMMOSkillTree", "mmo"),
    ENDGAME("Config", "Endgame&QoL", "Endgame and QoL", "6.0.4", "LinkEndgame", "endgame"),
    TAMEWORK("Alechilles", "Alec's Tamework!", "Alec's Tamework", "5.5.1", "LinkTamework", "tamework"),
    // 1.10.0, the mods TrackerAPI 1.3.0 reads, whose counts arrive partly as the ModTally record of 1.3.0.
    FISHING("dev.rm20", "AnglersAlmanac", "Angler's Almanac", "1.2.2", "LinkAnglersAlmanac", "fish", Kind.MOD,
            Sibling.TALLY_TRACKER),
    ARCANUM("com.zenkuro", "Arcanum", "Arcanum", "0.6.3", "LinkArcanum", "arcanum", Kind.MOD, Sibling.TALLY_TRACKER),
    // The manifest of Ymmersive Melodies 1.1.5 says 0.0.1, so the minimum is that.
    MELODIES("Conczin", "Ymmersive Melodies", "Ymmersive Melodies", "0.0.1", "LinkYmmersiveMelodies", "melodies",
            Kind.MOD, Sibling.TALLY_TRACKER),
    // 1.10.0, plugins without a binding. Their feats read crafted:, placed: and obtained: keys Feats counts itself.
    STORAGE("ModernKnight", "ModernStorage", "Modern Storage", "1.0.33", "LinkModernStorage", null, Kind.PLUGIN, null),
    MACHINES("AipocsHouse", "MachineBlocks", "Machine Blocks", "1.4.5", "LinkMachineBlocks", null, Kind.PLUGIN, null),
    // 1.10.0, asset packs. The plugin name is the name half of the pack id, exactly as the pack's manifest writes it.
    FANCY_STORAGE("Sigyn_Laufeyson", "Sig's Fancy Storage", "Sig's Fancy Storage", "1.6.3", "LinkFancyStorage", null,
            Kind.ASSET_PACK, null),
    POTION_SHOP("Sigyn_Laufeyson", "Sig's Potion Shop", "Sig's Potion Shop", "1.5.7", "LinkPotionShop", null,
            Kind.ASSET_PACK, null),
    SIGS_GARDEN("Sigyn_Laufeyson", "Sig's Garden", "Sig's Garden", "0.5.8", "LinkSigsGarden", null, Kind.ASSET_PACK, null),
    BLAHAJ("Yernemm", "Blahaj", "Blahaj Plushie", "1.2.5", "LinkBlahaj", null, Kind.ASSET_PACK, null),
    INFINITE_WATER("AvidPixel", "AvidPixel's Infinite Water Supply", "Infinite Water Supply", "1.1.1", "LinkInfiniteWater",
            null, Kind.ASSET_PACK, null),
    INFINITE_TELEPORTERS("VictorLuck", "InfiniteTeleporters", "Infinite Teleporters", "1.0.5", "LinkInfiniteTeleporters",
            null, Kind.ASSET_PACK, null);

    /** How Feats finds a sibling and where its counters come from. */
    public enum Kind {
        /** A Ryvrook plugin. */
        SUITE,
        /** A third party mod read through TrackerAPI, its counters under {@link #modId()}. */
        MOD,
        /** A third party plugin with no binding, its feats read the item counters Feats keeps on either tracking path. */
        PLUGIN,
        /** An asset pack, found by its {@code Group:Name} id in the engine's asset module, counted like {@link #PLUGIN}. */
        ASSET_PACK
    }

    /** The TrackerAPI that publishes the records of the eleven mods of 1.9.0. */
    static final String MOD_TRACKER = "1.2.0";
    /** The TrackerAPI that publishes ModTally and binds the three mods of 1.10.0. */
    static final String TALLY_TRACKER = "1.3.0";

    /** The group of the Ryvrook plugins. */
    public static final String GROUP = "Ryvrook";

    private final String group;
    private final String pluginName;
    private final String displayName;
    private final String minimum;
    private final String settingKey;
    private final String modId;
    private final Kind kind;
    private final String trackerMinimum;

    Sibling(String group, String pluginName, String displayName, String minimum, String settingKey, String modId) {
        this(group, pluginName, displayName, minimum, settingKey, modId,
                modId != null ? Kind.MOD : Kind.SUITE, modId != null ? MOD_TRACKER : null);
    }

    Sibling(String group, String pluginName, String displayName, String minimum, String settingKey, String modId,
            Kind kind, String trackerMinimum) {
        this.group = group;
        this.pluginName = pluginName;
        this.displayName = displayName;
        this.minimum = minimum;
        this.settingKey = settingKey;
        this.modId = modId;
        this.kind = kind;
        this.trackerMinimum = trackerMinimum;
    }

    public Kind kind() {
        return kind;
    }

    /** True for an asset pack, looked up with {@code AssetModule.getAssetPack(pluginId())}. */
    public boolean isAssetPack() {
        return kind == Kind.ASSET_PACK;
    }

    /** True for a Ryvrook plugin. */
    public boolean isSuite() {
        return kind == Kind.SUITE;
    }

    /**
     * The oldest TrackerAPI whose records this mod's feats read, {@code 1.2.0} for the eleven mods of 1.9.0 and
     * {@code 1.3.0} for the three of 1.10.0, or null for a sibling that does not go through TrackerAPI.
     */
    public String trackerMinimum() {
        return trackerMinimum;
    }

    /** The plugin group, {@code Ryvrook} for the suite, the author's group for a third party mod. */
    public String group() {
        return group;
    }

    /** The plugin name under its group, such as ChronicleStats or RPGLeveling. */
    public String pluginName() {
        return pluginName;
    }

    /** {@code group:name}, as the engine's manifest writes it, and the asset pack id of an asset pack. */
    public String pluginId() {
        return group + ":" + pluginName;
    }

    /** The name shown in the log and in {@code /feats admin status}. */
    public String displayName() {
        return displayName;
    }

    public String minimum() {
        return minimum;
    }

    /** The Feats.json key of the link setting, {@code Tracker}, {@code Coinage} or {@code LinkRPGLeveling}. */
    public String settingKey() {
        return settingKey;
    }

    /**
     * The TrackerAPI binding id and counter prefix for a mod TrackerAPI reads, such as {@code rpg}, or null for a
     * Ryvrook plugin. A mod's counters are {@code <modId>:<stat>} and {@code <modId>:<stat>:<id>}.
     */
    public String modId() {
        return modId;
    }

    /** True for a third party mod read through TrackerAPI, whose feats also need {@link #TRACKER_API}. */
    public boolean isMod() {
        return modId != null;
    }

    /** The sibling with this plugin name, or null. Names are distinct across the groups. */
    public static Sibling byPluginName(String name) {
        if (name == null) {
            return null;
        }
        for (Sibling s : values()) {
            if (s.pluginName.equals(name)) {
                return s;
            }
        }
        return null;
    }

    /** The sibling read through TrackerAPI under this binding id, or null. */
    public static Sibling byModId(String modId) {
        if (modId == null) {
            return null;
        }
        for (Sibling s : values()) {
            if (modId.equals(s.modId)) {
                return s;
            }
        }
        return null;
    }
}
