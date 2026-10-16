package dev.ryvrook.feats.integration;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.track.Hooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;

/**
 * Starts and stops the bindings to Coinage, Chronicle Stats and Atlas that write sibling counters, see
 * docs/EXPANSION-PLAN.md section 8. Each binding is loaded by name only when its sibling is in
 * {@code ctx.siblings()}, so no class of a missing sibling is touched. Each binds on the sibling's enable hook,
 * unbinds on its disable hook and survives a reload of either side. {@link #close()} closes them in reverse
 * order and touches only classes loaded at start, since a stop path must never load a sibling class for the
 * first time.
 */
public final class SiblingLinks implements AutoCloseable {
    static final String COINAGE_FEATS = "dev.ryvrook.feats.integration.CoinageFeats";
    static final String CHRONICLE_FEATS = "dev.ryvrook.feats.integration.ChronicleFeats";
    static final String ATLAS_FEATS = "dev.ryvrook.feats.integration.AtlasFeats";

    private final HytaleLogger logger;
    private final List<AutoCloseable> links = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    private SiblingLinks(HytaleLogger logger) {
        this.logger = logger;
    }

    /** Called from the plugin's start. Timers run on the engine scheduler. Never throws. */
    public static SiblingLinks start(Hooks.Context ctx) {
        return start(ctx, HytaleServer.SCHEDULED_EXECUTOR);
    }

    /** As {@link #start(Hooks.Context)} with the scheduler the bindings put their timers on. */
    public static SiblingLinks start(Hooks.Context ctx, ScheduledExecutorService scheduler) {
        SiblingLinks out = new SiblingLinks(ctx == null ? null : ctx.logger());
        if (ctx == null || ctx.tracker() == null) {
            return out;
        }
        Set<Sibling> siblings = ctx.siblings() == null ? Set.of() : ctx.siblings();
        if (siblings.contains(Sibling.COINAGE)) {
            out.open(COINAGE_FEATS, "Coinage", ctx, scheduler, siblings.contains(Sibling.DOMINION)
                    ? "Coinage and Dominion are installed, so the Coinage feats and the Allied Trade and War Chest feats count"
                    : "Coinage is installed, so the Coinage feats count");
        }
        if (siblings.contains(Sibling.CHRONICLE)) {
            out.open(CHRONICLE_FEATS, "Chronicle Stats", ctx, scheduler, "Chronicle Stats is installed, so the Chronicle feats count");
        }
        if (siblings.contains(Sibling.ATLAS)) {
            out.open(ATLAS_FEATS, "Atlas", ctx, scheduler, "Atlas is installed, so On the Map and Surveyor's Mark count");
        }
        return out;
    }

    /** Loads one binding by name and starts it. A binding that fails to start is logged and the others still run. */
    private void open(String className, String sibling, Hooks.Context ctx, ScheduledExecutorService scheduler, String line) {
        try {
            Class<?> type = Class.forName(className, true, SiblingLinks.class.getClassLoader());
            Object link = type.getMethod("install", Hooks.Context.class, ScheduledExecutorService.class).invoke(null, ctx, scheduler);
            links.add((AutoCloseable) link);
            names.add(sibling);
            if (logger != null) {
                logger.at(Level.INFO).log(line);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if (logger != null) {
                logger.at(Level.WARNING).withCause(e).log("Feats could not link to %s, so its feats do not count", sibling);
            }
        }
    }

    /** The siblings linked, in start order. */
    public List<String> linked() {
        return Collections.unmodifiableList(names);
    }

    /** Closes every binding, the last started first. Never throws. */
    @Override
    public void close() {
        for (int i = links.size() - 1; i >= 0; i--) {
            try {
                links.get(i).close();
            } catch (Exception | LinkageError e) {
                if (logger != null) {
                    logger.at(Level.FINE).withCause(e).log("Feats could not unlink from %s", names.get(i));
                }
            }
        }
        links.clear();
        names.clear();
    }
}
