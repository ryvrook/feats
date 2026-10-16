package dev.ryvrook.feats.integration;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import dev.ryvrook.feats.model.Sibling;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * The Dominion facts the cross mod feats of docs/EXPANSION-PLAN.md section 8.6 need, whether two players are in
 * allied factions and whether a player's faction is at war. Dominion lists Feats as an optional dependency, so
 * Feats cannot list Dominion and its classes are not on the Feats class path. The facts are read through the
 * Dominion plugin's own class loader with method handles on {@code DominionApi.get()}, {@code factionOf},
 * {@code relationBetween} and {@code Faction.relations()}. A Dominion that is not started answers false, and a
 * Dominion whose API lacks any of these switches the facts off with one warning.
 */
public final class CrossModFeats implements CoinageFeats.Facts {
    static final String API = "dev.ryvrook.dominion.api.DominionApi";
    static final String FACTION = "dev.ryvrook.dominion.model.Faction";
    static final String RELATION = "dev.ryvrook.dominion.model.Relation";

    /** The handles on one loaded Dominion, replaced when a reload gives Dominion a new plugin object. */
    record Handles(PluginBase plugin, MethodHandle get, MethodHandle factionOf, MethodHandle relationBetween,
                           MethodHandle relations, MethodHandle isSystem) {
    }

    private final HytaleLogger logger;
    private final AtomicBoolean warned = new AtomicBoolean();
    private volatile Handles handles;
    private volatile boolean off;

    CrossModFeats(HytaleLogger logger) {
        this.logger = logger;
    }

    /** True when both players are in factions that are allies of each other. */
    @Override
    public boolean allied(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        Handles h = handles();
        if (h == null) {
            return false;
        }
        try {
            Object api = h.get.invoke();
            Object relation = h.relationBetween.invoke(api, a, b);
            return relation != null && "ALLY".equals(((Enum<?>) relation).name());
        } catch (Throwable t) {
            return failed(t);
        }
    }

    /** True when the player's faction is a normal faction with at least one enemy. */
    @Override
    public boolean atWar(UUID player) {
        if (player == null) {
            return false;
        }
        Handles h = handles();
        if (h == null) {
            return false;
        }
        try {
            Object api = h.get.invoke();
            Object found = h.factionOf.invoke(api, player);
            if (!(found instanceof Optional<?> o) || o.isEmpty()) {
                return false;
            }
            Object faction = o.get();
            if ((boolean) h.isSystem.invoke(faction)) {
                return false;
            }
            Object relations = h.relations.invoke(faction);
            if (!(relations instanceof Map<?, ?> m)) {
                return false;
            }
            for (Object r : m.values()) {
                if (r instanceof Enum<?> e && "ENEMY".equals(e.name())) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return failed(t);
        }
    }

    /** True while the facts are still read. */
    public boolean active() {
        return !off;
    }

    /**
     * The handles on the Dominion that is loaded now, built on the first call and again after a Dominion
     * reload. Null when Dominion is not loaded or the facts were switched off.
     */
    private Handles handles() {
        if (off) {
            return null;
        }
        PluginBase plugin = plugin();
        if (plugin == null) {
            return null;
        }
        Handles h = handles;
        if (h != null && h.plugin == plugin) {
            return h;
        }
        try {
            h = handles(plugin, plugin.getClass().getClassLoader());
            handles = h;
            return h;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            off = true;
            handles = null;
            if (warned.compareAndSet(false, true)) {
                logger.at(Level.WARNING).withCause(e).log("Feats cannot read factions from this Dominion, so the Allied Trade and War Chest feats do not count");
            }
            return null;
        }
    }

    /** The handles through one class loader. Package private so a test can resolve them against a Dominion jar. */
    static Handles handles(PluginBase plugin, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> api = Class.forName(API, true, loader);
        Class<?> faction = Class.forName(FACTION, true, loader);
        Class<?> relation = Class.forName(RELATION, true, loader);
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        return new Handles(plugin,
                lookup.findStatic(api, "get", MethodType.methodType(api)),
                lookup.findVirtual(api, "factionOf", MethodType.methodType(Optional.class, UUID.class)),
                lookup.findVirtual(api, "relationBetween", MethodType.methodType(relation, UUID.class, UUID.class)),
                lookup.findVirtual(faction, "relations", MethodType.methodType(Map.class)),
                lookup.findVirtual(faction, "isSystem", MethodType.methodType(boolean.class)));
    }

    private static PluginBase plugin() {
        try {
            return GatedLookup.plugin(new PluginIdentifier(Sibling.GROUP, Sibling.DOMINION.pluginName()));
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** A call that failed. A Dominion that is not started is quiet, anything else warns once. */
    private boolean failed(Throwable t) {
        if (t instanceof IllegalStateException) {
            return false; // DominionApi.get() throws while Dominion is stopped
        }
        if (warned.compareAndSet(false, true)) {
            logger.at(Level.WARNING).withCause(t).log("Feats could not read a faction fact from Dominion, the Allied Trade and War Chest feats may miss it");
        }
        return false;
    }
}
