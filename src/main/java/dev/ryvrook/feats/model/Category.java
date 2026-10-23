package dev.ryvrook.feats.model;

/** Grouping shown as tabs in the feats browser. Order here is display order. */
public enum Category {
    EXPLORATION("Exploration", "Tool_Map"),
    MINING("Mining", "Tool_Pickaxe_Iron"),
    BUILDING("Building", "Rock_Aqua_Brick"),
    CRAFTING("Crafting", "Bench_WorkBench"),
    COMBAT("Combat", "Weapon_Sword_Bronze"),
    SURVIVAL("Survival", "Furniture_Crude_Bed"),
    NATURE("Nature", "Plant_Crop_Carrot_Item"),
    CHAPTER("Chapter I", "Rune_Fireball"),
    MISC("Misc", "Deco_Treasure"),
    /** Only filled when Dominion Factions is installed, an empty tab is never shown. */
    FACTIONS("Factions", "Furniture_Human_Ruins_Banner"),
    /** Only filled when Coinage is installed. */
    COINAGE("Coinage", "Deco_Treasure_Pile_Small"),
    /** Only filled when Chronicle Stats is installed. */
    CHRONICLE("Chronicle", "Deco_Scroll"),
    // The tabs of the mods TrackerAPI 1.2.0 reads, each filled only when that mod and TrackerAPI 1.2.0 are
    // installed. Every icon is a vanilla item id from Assets.zip, never a mod item, since a tab icon shows before the
    // player has any of the mod's items.
    /** RPG Leveling. */
    RPG("RPG Leveling", "Weapon_Sword_Runic"),
    /** Major Dungeons. */
    DUNGEONS("Dungeons", "Coffer_Key"),
    /** Perfect Parries. */
    PARRIES("Parries", "Weapon_Shield_Iron"),
    /** Wan's Wonder Weapons. */
    RELICS("Relics", "Weapon_Staff_Wizard"),
    /** Zephyr. */
    ZEPHYR("Zephyr", "Ingredient_Feathers_Blue"),
    /** Simple Enchantments. */
    ENCHANTING("Enchanting", "Rune_Damage_Up"),
    /** Vein Mining. */
    VEIN_MINING("Vein Mining", "Ore_Cobalt"),
    /** Lucky Mining. */
    LUCKY_MINING("Lucky Mining", "Ore_Gold"),
    /** MMO Skill Tree. */
    MMO("Skill Tree", "Deco_Book_Pile_Large"),
    /** Endgame and QoL. */
    ENDGAME("Endgame", "Weapon_Sword_Adamantite"),
    /** Alec's Tamework. */
    TAMEWORK("Companions", "Food_Wildmeat_Raw"),
    // 1.10.0. The three mods TrackerAPI 1.3.0 reads, filled only with that mod and TrackerAPI 1.3.0, then the two
    // plugins and the asset packs whose feats read the item counters Feats keeps itself. Every icon is a vanilla id.
    /** Angler's Almanac. */
    FISHING("Fishing", "Fish_Bluegill_Item"),
    /** Arcanum. */
    ARCANUM("Arcanum", "Bench_Arcane"),
    /** Ymmersive Melodies. */
    MUSIC("Music", "Prototype_Tool_Guitar_Mana"),
    /** Modern Storage. */
    STORAGE("Storage", "Furniture_Village_Crate"),
    /** Machine Blocks. */
    MACHINES("Machines", "Bench_Furnace"),
    /** The Sig's Garden asset pack and its realm. */
    GARDEN("Lost Garden", "Plant_Flower_Orchid_Pink"),
    /** Five decoration asset packs, each feat only in while its own pack is loaded. */
    DECOR("Decor", "Furniture_Tavern_Barrel"),
    /** The Social pack. */
    SOCIAL("Social", "Deco_Kweebec_Plush"),
    /** The Hardcore pack. */
    HARDCORE("Hardcore", "Deco_Bone_Skulls"),
    /** The PvP pack. */
    PVP("PvP", "Weapon_Daggers_Bronze"),
    /** The Calendar pack. */
    CALENDAR("Calendar", "Deco_Scroll"),
    /** The Grind pack. */
    GRIND("Grind", "Tool_Pickaxe_Adamantite"),
    /** Server milestones and server firsts. Never counted in totals or completion. */
    WORLD("World", "Deco_Trophy_Harvest");

    private final String displayName;
    private final String icon;

    Category(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String displayName() {
        return displayName;
    }

    public String icon() {
        return icon;
    }

    /** True for the nine tabs every server has. Pack and sibling tabs come and go with their switch. */
    public boolean isCore() {
        return switch (this) {
            case EXPLORATION, MINING, BUILDING, CRAFTING, COMBAT, SURVIVAL, NATURE, CHAPTER, MISC -> true;
            default -> false;
        };
    }

    /** The tab of a mod TrackerAPI reads, or null for a Ryvrook sibling. */
    public static Category ofMod(Sibling mod) {
        return switch (mod) {
            case RPG_LEVELING -> RPG;
            case MAJOR_DUNGEONS -> DUNGEONS;
            case PERFECT_PARRIES -> PARRIES;
            case WONDER_WEAPONS -> RELICS;
            case ZEPHYR -> Category.ZEPHYR;
            case SIMPLE_ENCHANTMENTS -> ENCHANTING;
            case VEIN_MINING -> Category.VEIN_MINING;
            case LUCKY_MINING -> Category.LUCKY_MINING;
            case MMO_SKILL_TREE -> MMO;
            case ENDGAME -> Category.ENDGAME;
            case TAMEWORK -> Category.TAMEWORK;
            case FISHING -> Category.FISHING;
            case ARCANUM -> Category.ARCANUM;
            case MELODIES -> MUSIC;
            default -> null;
        };
    }

    /**
     * The tab a third party sibling's feats sit in, the mod tab of {@link #ofMod} for a mod TrackerAPI reads, or null
     * for a Ryvrook sibling. The five decoration packs share {@link #DECOR}.
     */
    public static Category ofSibling(Sibling sibling) {
        return switch (sibling) {
            case STORAGE -> Category.STORAGE;
            case MACHINES -> Category.MACHINES;
            case SIGS_GARDEN -> GARDEN;
            case FANCY_STORAGE, POTION_SHOP, BLAHAJ, INFINITE_WATER, INFINITE_TELEPORTERS -> DECOR;
            default -> ofMod(sibling);
        };
    }
}
