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
    /** Only filled when Dominion Factions is installed, an empty tab is never shown. */
    FACTIONS("Factions", "Furniture_Human_Ruins_Banner"),
    MISC("Misc", "Deco_Treasure"),
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
}
