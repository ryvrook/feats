package dev.ryvrook.feats.model;

/**
 * The Ryvrook plugins Feats adds feats or tracking for when they are installed. Each one is found by name in the
 * engine's plugin manager at setup, see {@code integration/Siblings}. {@code minimum} is the oldest version the
 * binding is written against, checked by the binding itself once the sibling's API is up.
 */
public enum Sibling {
    TRACKER_API("TrackerAPI", "1.0.0"),
    COINAGE("Coinage", "1.2.0"),
    DOMINION("Dominion", "0.5.1"),
    CHRONICLE("ChronicleStats", "1.5.0"),
    ATLAS("Atlas", "1.9.0");

    public static final String GROUP = "Ryvrook";

    private final String pluginName;
    private final String minimum;

    Sibling(String pluginName, String minimum) {
        this.pluginName = pluginName;
        this.minimum = minimum;
    }

    /** The plugin name under the Ryvrook group, such as ChronicleStats. */
    public String pluginName() {
        return pluginName;
    }

    public String minimum() {
        return minimum;
    }
}
