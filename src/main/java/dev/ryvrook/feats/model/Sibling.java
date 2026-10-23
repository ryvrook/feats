package dev.ryvrook.feats.model;

/**
 * The plugins Feats adds feats or tracking for when they are installed. Each one is found by group and name in
 * the engine's plugin manager at setup, see {@code integration/Siblings}. {@code minimum} is the oldest version the
 * binding is written against. {@code Siblings.present} leaves out a loaded sibling that is older.
 *
 * <p>The first five are the Ryvrook plugins. The rest are the third party mods TrackerAPI 1.2.0 reads, see
 * {@code docs/MODS-PLAN.md}. Feats never touches a class of those mods, their counters arrive through TrackerAPI as
 * the generic mod records and are mirrored by {@code integration/ModKinds} under {@link #modId()}, so each of them
 * needs {@link #TRACKER_API} as well.
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
    TAMEWORK("Alechilles", "Alec's Tamework!", "Alec's Tamework", "5.5.1", "LinkTamework", "tamework");

    /** The group of the Ryvrook plugins. */
    public static final String GROUP = "Ryvrook";

    private final String group;
    private final String pluginName;
    private final String displayName;
    private final String minimum;
    private final String settingKey;
    private final String modId;

    Sibling(String group, String pluginName, String displayName, String minimum, String settingKey, String modId) {
        this.group = group;
        this.pluginName = pluginName;
        this.displayName = displayName;
        this.minimum = minimum;
        this.settingKey = settingKey;
        this.modId = modId;
    }

    /** The plugin group, {@code Ryvrook} for the suite, the author's group for a third party mod. */
    public String group() {
        return group;
    }

    /** The plugin name under its group, such as ChronicleStats or RPGLeveling. */
    public String pluginName() {
        return pluginName;
    }

    /** {@code group:name}, as the engine's manifest writes it. */
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
