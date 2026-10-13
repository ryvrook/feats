package dev.ryvrook.feats.model;

/** Rarity tier of a feat. Points feed the player's total score. */
public enum Tier {
    BRONZE("Bronze", 10, "#cd7f32"),
    SILVER("Silver", 25, "#c0c0c0"),
    GOLD("Gold", 50, "#E8A93B"),
    PLATINUM("Platinum", 100, "#7fe3ff");

    private final String displayName;
    private final int points;
    private final String color;

    Tier(String displayName, int points, String color) {
        this.displayName = displayName;
        this.points = points;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public int points() {
        return points;
    }

    public String color() {
        return color;
    }
}
