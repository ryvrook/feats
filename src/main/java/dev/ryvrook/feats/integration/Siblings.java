package dev.ryvrook.feats.integration;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import dev.ryvrook.feats.model.Sibling;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Asks the engine's plugin manager whether another Ryvrook plugin is on this server. Touches no class of
 * that plugin, so it is safe on a server without it.
 */
public final class Siblings {
    public static final String GROUP = "Ryvrook";
    public static final String DOMINION = "Dominion";
    public static final String COINAGE = "Coinage";
    public static final String SOCIALBRIDGE = "SocialBridge";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

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

    /**
     * Every sibling installed on this server at its minimum version or newer, read once at setup. A sibling
     * that is not loaded yet has no version to read, since a plugin that lists Feats sets up after it, and
     * counts as new enough. Its binding checks the API it needs once the sibling is up.
     */
    public static Set<Sibling> present() {
        return present(s -> "auto");
    }

    /**
     * As {@link #present()} with the link setting of each sibling, {@code auto}, {@code always} or {@code never}.
     * A sibling on {@code never} is left out without a word, one on {@code always} that is missing gets a warning.
     */
    public static Set<Sibling> present(Function<Sibling, String> mode) {
        return present(Siblings::installed, Siblings::version, mode, line -> LOGGER.at(Level.WARNING).log("%s", line));
    }

    /** {@link #present()} over any lookup, for the tests. Logs one line per sibling it skips. */
    static Set<Sibling> present(Predicate<String> installed, Function<String, String> version, Consumer<String> log) {
        return present(installed, version, s -> "auto", log);
    }

    /** {@link #present(Function)} over any lookup, for the tests. */
    static Set<Sibling> present(Predicate<String> installed, Function<String, String> version,
                                Function<Sibling, String> mode, Consumer<String> log) {
        Set<Sibling> out = EnumSet.noneOf(Sibling.class);
        for (Sibling s : Sibling.values()) {
            String m = mode == null ? "auto" : mode.apply(s);
            if ("never".equals(m)) {
                continue;
            }
            if (!installed.test(s.pluginName())) {
                if ("always".equals(m) && s != Sibling.TRACKER_API) {
                    log.accept("Feats is set to always link to " + s.pluginName() + " but " + s.pluginName()
                            + " is not installed, so its feats are left out");
                }
                continue;
            }
            String found = version.apply(s.pluginName());
            if (found != null && VersionCompare.below(found, s.minimum())) {
                log.accept("Feats leaves out the " + s.pluginName() + " feats because " + s.pluginName() + " " + found
                        + " is older than " + s.minimum());
                continue;
            }
            out.add(s);
        }
        return out;
    }
}
