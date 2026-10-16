package dev.ryvrook.feats.integration;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginManager;

/**
 * Asks the engine's plugin manager whether another Ryvrook plugin is on this server. Touches no class of
 * that plugin, so it is safe on a server without it.
 */
public final class Siblings {
    public static final String GROUP = "Ryvrook";
    public static final String DOMINION = "Dominion";
    public static final String COINAGE = "Coinage";
    public static final String SOCIALBRIDGE = "SocialBridge";

    private Siblings() {
    }

    /**
     * True when the plugin is loaded, or its jar was found at boot and it is waiting for its own setup. A
     * plugin that lists Feats as a dependency sets up after Feats, so in Feats' setup it is only in the list
     * of available plugins. A failed lookup counts as not installed.
     */
    public static boolean installed(String name) {
        try {
            PluginManager manager = PluginManager.get();
            if (manager == null) {
                return false;
            }
            PluginIdentifier id = new PluginIdentifier(GROUP, name);
            return manager.getPlugin(id) != null || manager.getAvailablePlugins().containsKey(id);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
