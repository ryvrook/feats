package dev.ryvrook.feats.integration;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.plugin.PluginState;

import java.util.Locale;

/**
 * Decides at setup whether Feats tracks with its own engine systems or through TrackerAPI, and watches
 * TrackerAPI afterwards so a reload of it is noticed. Nothing here touches a TrackerAPI class, so this
 * runs on a server without TrackerAPI. Only {@link TrackerBridge} does.
 *
 * <p>ECS systems can only be registered in setup, and TrackerAPI publishes its API in its own start, so
 * the decision is made from the engine's plugin manager rather than from the API.
 */
public final class TrackerSwitch {
    /** The oldest TrackerAPI Feats binds to. */
    public static final String MINIMUM = "1.0.0";
    /** The first TrackerAPI that reloads its dependents, Feats included, after its own reload. */
    public static final String RELOADS_DEPENDENTS = "1.0.3";
    public static final String GROUP = "Ryvrook";
    public static final String NAME = "TrackerAPI";

    /** The {@code Tracker} setting in Feats.json. */
    public enum Mode {
        /** TrackerAPI when it is installed and new enough, else the Feats systems. The default. */
        AUTO,
        /** TrackerAPI only. When it is missing Feats logs one warning and tracks nothing. */
        ALWAYS,
        /** The Feats systems only, TrackerAPI is ignored. */
        NEVER;

        /** Reads the setting. Anything unknown, blank or null is {@link #AUTO}. */
        public static Mode parse(String value) {
            if (value == null) {
                return AUTO;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "always" -> ALWAYS;
                case "never" -> NEVER;
                default -> AUTO;
            };
        }
    }

    /** Which path feeds the Feats counters. */
    public enum Path {
        /** The Feats engine systems and samplers. */
        OWN,
        /** TrackerAPI events through {@link TrackerBridge}. */
        TRACKER_API,
        /** Nothing, the setting asks for TrackerAPI and it is missing. */
        NONE
    }

    /**
     * What the plugin manager knows about TrackerAPI. {@code plugin} is the live plugin object, used only
     * for its identity, or null when it is not loaded. {@code usable} is true when it is loaded, not
     * failed or stopped, and at least {@link #MINIMUM}.
     */
    public record Found(Object plugin, String version, boolean usable) {
        public static final Found MISSING = new Found(null, null, false);
    }

    /** Looks TrackerAPI up. The engine lookup in production, a fake in tests. */
    @FunctionalInterface
    public interface Lookup {
        Found find();
    }

    /** The engine lookup. Safe from any thread, a failed lookup counts as missing. */
    public static final Lookup ENGINE = TrackerSwitch::findInEngine;

    private final Mode mode;
    private final Path path;
    private final Lookup lookup;
    private Object seen;

    public TrackerSwitch(Mode mode, Path path, Found atSetup, Lookup lookup) {
        this.mode = mode;
        this.path = path;
        this.lookup = lookup;
        this.seen = atSetup.plugin();
    }

    public static Path decide(Mode mode, Found found) {
        return switch (mode) {
            case NEVER -> Path.OWN;
            case AUTO -> found.usable() ? Path.TRACKER_API : Path.OWN;
            case ALWAYS -> found.usable() ? Path.TRACKER_API : Path.NONE;
        };
    }

    public Mode mode() {
        return mode;
    }

    public Path path() {
        return path;
    }

    /** The one line logged at setup that says which path is active. */
    public static String describe(Mode mode, Path path, Found found) {
        return switch (path) {
            case TRACKER_API -> "Feats tracks through TrackerAPI " + found.version()
                    + " and does not register its own tracking systems";
            case NONE -> found.plugin() == null
                    ? "Feats Tracker is set to always but TrackerAPI is not installed, so Feats tracks nothing"
                    : "Feats Tracker is set to always but TrackerAPI " + found.version() + " is older than "
                    + MINIMUM + " or not running, so Feats tracks nothing";
            case OWN -> mode == Mode.NEVER ? "Feats tracks with its own systems, Tracker is set to never"
                    : found.plugin() == null ? "Feats tracks with its own systems, TrackerAPI is not installed"
                    : "Feats tracks with its own systems, TrackerAPI " + found.version() + " is older than "
                    + MINIMUM + " or not running";
        };
    }

    /**
     * Run every 5 seconds. Returns one line to log when the TrackerAPI plugin object changed since the last
     * look, which means it was reloaded, removed or added, else null. A reloaded TrackerAPI has new API
     * classes in a new loader, and the hooks Feats registered live on the old ones, so Feats has to be
     * reloaded as well. TrackerAPI 1.0.3 and newer do that reload themselves, so a reload of one of those
     * is not logged. An unload, or a reload of an older one, still is.
     */
    public synchronized String poll() {
        Found now;
        try {
            now = lookup.find();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
        Object current = now.plugin();
        if (current == seen) {
            return null;
        }
        seen = current;
        if (path == Path.TRACKER_API && current != null && reloadsDependents(now.version())) {
            return null; // TrackerAPI reloads Feats right after, which links again
        }
        String noSwitch = mode == Mode.AUTO
                ? " Feats cannot switch to its own tracking after setup, so the reload is needed either way." : "";
        return switch (path) {
            case TRACKER_API -> current == null
                    ? "TrackerAPI was unloaded, so Feats counts nothing until TrackerAPI is back and /plugin reload Feats has run." + noSwitch
                    : "TrackerAPI was reloaded, so Feats lost its link to it and counts nothing. Run /plugin reload Feats to link again." + noSwitch;
            case NONE -> current == null ? null
                    : "TrackerAPI is now loaded. Run /plugin reload Feats to start tracking through it.";
            case OWN -> mode == Mode.AUTO && current != null && now.usable()
                    ? "TrackerAPI is now loaded but Feats chose its own tracking at setup. Run /plugin reload Feats to track through TrackerAPI."
                    : null;
        };
    }

    /** True when this TrackerAPI version reloads Feats after its own reload. Null or unreadable is false. */
    public static boolean reloadsDependents(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        try {
            return !VersionCompare.below(version, RELOADS_DEPENDENTS);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Found findInEngine() {
        try {
            PluginManager manager = PluginManager.get();
            if (manager == null) {
                return Found.MISSING;
            }
            PluginBase plugin = manager.getPlugin(new PluginIdentifier(GROUP, NAME));
            if (plugin == null) {
                return Found.MISSING;
            }
            String version = plugin.getManifest() == null || plugin.getManifest().getVersion() == null
                    ? "0.0.0" : plugin.getManifest().getVersion().toString();
            PluginState state = plugin.getState();
            boolean running = state != PluginState.FAILED && state != PluginState.DISABLED
                    && state != PluginState.SHUTDOWN;
            return new Found(plugin, version, running && !VersionCompare.below(version, MINIMUM));
        } catch (RuntimeException | LinkageError e) {
            return Found.MISSING;
        }
    }
}
