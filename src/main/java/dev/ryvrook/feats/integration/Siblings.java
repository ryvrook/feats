package dev.ryvrook.feats.integration;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import dev.ryvrook.feats.model.Sibling;

import java.util.EnumSet;
import java.util.Set;

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

    /** The version string of a loaded plugin, or null when it is not loaded or has no manifest version. */
    public static String version(String name) {
        try {
            PluginManager manager = PluginManager.get();
            if (manager == null) {
                return null;
            }
            PluginBase plugin = manager.getPlugin(new PluginIdentifier(GROUP, name));
            if (plugin == null || plugin.getManifest() == null || plugin.getManifest().getVersion() == null) {
                return null;
            }
            return plugin.getManifest().getVersion().toString();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** Every sibling installed on this server, read once at setup. */
    public static Set<Sibling> present() {
        Set<Sibling> out = EnumSet.noneOf(Sibling.class);
        for (Sibling s : Sibling.values()) {
            if (installed(s.pluginName())) {
                out.add(s);
            }
        }
        return out;
    }
}
