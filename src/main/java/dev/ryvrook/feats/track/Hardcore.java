package dev.ryvrook.feats.track;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.HytaleServerConfig;
import com.hypixel.hytale.server.core.universe.hardcore.HardcoreMode;

import java.util.logging.Level;

/**
 * Whether the server runs hardcore lives. The mode lives in the server config, not in a world or universe
 * resource. {@code HardcoreGameOver.isActive} and {@code HardcoreLivesHud.refresh} both read
 * {@code HytaleServer.get().getConfig().getDefaults().getHardcoreMode()}, so Feats reads the same field.
 * {@code PerPlayer} keeps a {@code PlayerLives} component on each player, {@code Global} keeps one pool
 * in the {@code HardcoreState} universe resource, and {@code None} is a normal server.
 */
public final class Hardcore {
    /** The hardcore mode as Feats sees it, mirroring the engine enum without naming it in callers. */
    public enum Mode {
        NONE,
        PER_PLAYER,
        GLOBAL
    }

    private Hardcore() {
    }

    /** True when the server runs with hardcore lives on. Read once at setup for the Hardcore pack on auto. */
    public static boolean detect(HytaleLogger logger) {
        return mode(logger) != Mode.NONE;
    }

    /** The mode from the server config, NONE when it cannot be read, with one warning in that case. */
    public static Mode mode(HytaleLogger logger) {
        try {
            HytaleServer server = HytaleServer.get();
            HytaleServerConfig config = server != null ? server.getConfig() : null;
            HytaleServerConfig.Defaults defaults = config != null ? config.getDefaults() : null;
            HardcoreMode mode = defaults != null ? defaults.getHardcoreMode() : null;
            if (mode == null) {
                return Mode.NONE;
            }
            return switch (mode.name()) {
                case "PerPlayer" -> Mode.PER_PLAYER;
                case "Global" -> Mode.GLOBAL;
                default -> Mode.NONE;
            };
        } catch (RuntimeException | LinkageError e) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(e).log("Feats cannot read the hardcore mode on this server, hardcore is treated as off");
            }
            return Mode.NONE;
        }
    }

    /** The lives a player starts with, from the server config. Zero when it cannot be read. */
    public static int defaultLives() {
        try {
            HytaleServer server = HytaleServer.get();
            HytaleServerConfig config = server != null ? server.getConfig() : null;
            HytaleServerConfig.Defaults defaults = config != null ? config.getDefaults() : null;
            return defaults != null ? Math.max(0, defaults.getHardcoreLives()) : 0;
        } catch (RuntimeException | LinkageError e) {
            return 0;
        }
    }
}
