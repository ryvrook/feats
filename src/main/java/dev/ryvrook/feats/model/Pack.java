package dev.ryvrook.feats.model;

import java.util.Locale;

/**
 * An optional group of feats an admin switches on in Feats.json. Each pack is a tab of its own that exists only
 * while the pack is on. Counters keep moving while a pack is off, so turning it on later credits players at
 * their next join. Read at start, like the Tracker setting.
 */
public enum Pack {
    /** Feats that need other players online, see the Social tab. */
    SOCIAL(Category.SOCIAL),
    /** Feats for servers with hardcore mode on. {@code auto} turns it on when the universe runs hardcore. */
    HARDCORE(Category.HARDCORE),
    /** Player versus player feats beyond the three hidden ones in Combat. */
    PVP(Category.PVP),
    /** Real date feats, in the server's local time or {@code CalendarZone}. */
    CALENDAR(Category.CALENDAR),
    /** Very long ladders that would otherwise sit unreachable in the main tabs. */
    GRIND(Category.GRIND);

    private final Category category;

    Pack(Category category) {
        this.category = category;
    }

    public Category category() {
        return category;
    }

    /** The Feats.json key, such as PackSocial. */
    public String settingKey() {
        String n = name().toLowerCase(Locale.ROOT);
        return "Pack" + Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
