package dev.ryvrook.feats.integration;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.asset.AssetModule;
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
 * Asks the engine's plugin manager whether another plugin is on this server. Touches no class of that plugin, so
 * it is safe on a server without it. A plugin is looked up by group and name, the {@link Sibling} carries both,
 * and a bare name is resolved through {@link Sibling#byPluginName} with the Ryvrook group as the fallback, so the
 * suite plugins that are no sibling, such as SocialBridge, are found by name as before.
 *
 * <p>An asset pack sibling is no plugin. It is looked up among the engine's asset packs by its {@code Group:Name} id,
 * {@link Sibling#pluginId()}, through {@link #assetPacks}, which the tests replace. Every lookup by name or by
 * sibling sends an asset pack there, so {@link #present()} and {@code /feats admin status} need no case of their own.
 */
public final class Siblings {
    public static final String GROUP = "Ryvrook";
    public static final String DOMINION = "Dominion";
    public static final String COINAGE = "Coinage";
    public static final String SOCIALBRIDGE = "SocialBridge";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private Siblings() {
    }

    /** The group a bare plugin name belongs to, the sibling's group when it is one, else Ryvrook. */
    static String groupOf(String name) {
        Sibling s = Sibling.byPluginName(name);
        return s == null ? GROUP : s.group();
    }

    /** The engine's asset packs by their {@code Group:Name} id, a seam for the tests. */
    public interface AssetPacks {
        /** True when the pack with this id is registered. */
        boolean has(String packId);

        /** The manifest version of the pack, or null when it is not registered or has none. */
        String version(String packId);
    }

    /** {@code AssetModule.get().getAssetPack(id)}, the lookup is case sensitive and the id is used as written. */
    public static final AssetPacks ENGINE_PACKS = new AssetPacks() {
        @Override
        public boolean has(String packId) {
            return pack(packId) != null;
        }

        @Override
        public String version(String packId) {
            AssetPack pack = pack(packId);
            try {
                if (pack == null || pack.getManifest() == null || pack.getManifest().getVersion() == null) {
                    return null;
                }
                return pack.getManifest().getVersion().toString();
            } catch (RuntimeException | LinkageError e) {
                return null;
            }
        }

        private AssetPack pack(String packId) {
            try {
                AssetModule module = AssetModule.get();
                return module == null ? null : module.getAssetPack(packId);
            } catch (RuntimeException | LinkageError e) {
                return null;
            }
        }
    };

    private static volatile AssetPacks assetPacks = ENGINE_PACKS;

    /** Replaces the asset pack lookup, for the tests. Null puts the engine's back. */
    static void useAssetPacks(AssetPacks packs) {
        assetPacks = packs == null ? ENGINE_PACKS : packs;
    }

    /** The asset pack sibling with this name, or null for a plugin. */
    private static Sibling packNamed(String name) {
        Sibling s = Sibling.byPluginName(name);
        return s != null && s.isAssetPack() ? s : null;
    }

    private static PluginIdentifier id(String group, String name) {
        return new PluginIdentifier(group, name);
    }

    /**
     * True when the plugin is loaded, or its jar was found at boot and it is waiting for its own setup. A
     * plugin that lists Feats as a dependency sets up after Feats, so in Feats' setup it is only in the list
     * of available plugins. A failed lookup counts as not installed.
     */
    public static boolean installed(String name) {
        Sibling pack = packNamed(name);
        return pack != null ? installed(pack) : installed(groupOf(name), name);
    }

    /** {@link #installed(String)} for a sibling, by its group and name, or by its pack id for an asset pack. */
    public static boolean installed(Sibling sibling) {
        if (sibling.isAssetPack()) {
            try {
                return assetPacks.has(sibling.pluginId());
            } catch (RuntimeException | LinkageError e) {
                return false;
            }
        }
        return installed(sibling.group(), sibling.pluginName());
    }

    public static boolean installed(String group, String name) {
        try {
            PluginManager manager = PluginManager.get();
            if (manager == null) {
                return false;
            }
            PluginIdentifier id = id(group, name);
            return manager.getPlugin(id) != null || manager.getAvailablePlugins().containsKey(id);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * As {@link #installed} for a timer or the IO thread. The loaded check goes through {@link GatedLookup}, so it
     * never waits while the plugin manager stops or reloads a plugin.
     */
    public static boolean installedNow(String name) {
        Sibling pack = packNamed(name);
        return pack != null ? installed(pack) : installedNow(groupOf(name), name);
    }

    /** {@link #installedNow(String)} for a sibling, by its group and name. An asset pack needs no plugin lock. */
    public static boolean installedNow(Sibling sibling) {
        return sibling.isAssetPack() ? installed(sibling) : installedNow(sibling.group(), sibling.pluginName());
    }

    public static boolean installedNow(String group, String name) {
        try {
            PluginIdentifier id = id(group, name);
            if (GatedLookup.plugin(id) != null) {
                return true;
            }
            PluginManager manager = PluginManager.get();
            return manager != null && manager.getAvailablePlugins().containsKey(id);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** The version string of a loaded plugin, or null when it is not loaded or has no manifest version. */
    public static String version(String name) {
        Sibling pack = packNamed(name);
        return pack != null ? version(pack) : version(groupOf(name), name);
    }

    /** {@link #version(String)} for a sibling, by its group and name, or the pack manifest's for an asset pack. */
    public static String version(Sibling sibling) {
        if (sibling.isAssetPack()) {
            try {
                return assetPacks.version(sibling.pluginId());
            } catch (RuntimeException | LinkageError e) {
                return null;
            }
        }
        return version(sibling.group(), sibling.pluginName());
    }

    public static String version(String group, String name) {
        try {
            PluginManager manager = PluginManager.get();
            if (manager == null) {
                return null;
            }
            PluginBase plugin = manager.getPlugin(id(group, name));
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

    /**
     * {@link #present()} over any lookup, for the tests. The lookups take the plugin name, which is distinct across
     * the groups. Logs one line per sibling it skips.
     */
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
