package dev.ryvrook.feats.integration;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.plugin.PluginState;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.Hooks;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Level;

/**
 * Raises the {@code chronicle:} max counters of the Chronicle tab, docs/EXPANSION-PLAN.md section 8.4, from
 * Chronicle Stats' time windows and its RPG Leveling bridge. Chronicle lists Feats as an optional dependency,
 * so Feats cannot list Chronicle and its classes are not on the Feats class path. The binding finds the
 * Chronicle plugin in the plugin manager and reaches {@code ChronicleApi} through that plugin's own class
 * loader with method handles, the way Chronicle itself binds Feats.
 *
 * <p>A 5 second poll on the engine scheduler watches the plugin object. A new one, which a Chronicle reload
 * gives, drops the old handles and binds again. The enable hook runs at once when Chronicle is already up and
 * registers {@code onChange} for blocks_broken, kills, days_active and rpg_level. On each change the binding
 * reads the window that matters and raises the max counter. Window reads are limited to one per player per key
 * per 10 seconds, and a change dropped by that limit is read on the next poll once the 10 seconds passed, so a
 * burst that ends never misses its last blocks. Every call into Chronicle is guarded and the first failure is
 * logged once.
 */
public final class ChronicleFeats implements AutoCloseable {
    static final String API = "dev.ryvrook.chronicle.api.ChronicleApi";
    static final String WINDOW = "dev.ryvrook.chronicle.api.Window";
    static final String STAT_CHANGE = "dev.ryvrook.chronicle.api.StatChange";

    static final String BLOCKS_BROKEN = "blocks_broken";
    static final String KILLS = "kills";
    static final String DAYS_ACTIVE = "days_active";
    static final String RPG_LEVEL = "rpg_level";
    static final Set<String> KEYS = Set.of(BLOCKS_BROKEN, KILLS, DAYS_ACTIVE, RPG_LEVEL);

    static final String DAY_BLOCKS_MAX = StatKeys.CHRONICLE_PREFIX + "day_blocks_max";
    static final String WEEK_KILLS_MAX = StatKeys.CHRONICLE_PREFIX + "week_kills_max";
    static final String MONTH_DAYS_MAX = StatKeys.CHRONICLE_PREFIX + "month_days_max";
    static final String RPG_LEVEL_MAX = StatKeys.CHRONICLE_PREFIX + "rpg_level_max";

    static final String TODAY = "TODAY";
    static final String WEEK = "WEEK";
    static final String MONTH = "MONTH";

    /** At most one window read per player and key in this many milliseconds. */
    static final long READ_GAP_MILLIS = 10_000;
    static final long POLL_SECONDS = 5;
    /** Past this many remembered read times the table is cleared, so a busy server does not grow it forever. */
    static final int READ_TIMES_LIMIT = 20_000;

    /** What the mapping reads from Chronicle. The live binding answers through the handles, the tests with a fake. */
    interface Windows {
        /** The player's value of the key in the window named TODAY, WEEK or MONTH. */
        long window(UUID uuid, String key, String window);

        /** True when Chronicle defines the stat, for rpg_level only while the RPG Leveling bridge is on. */
        boolean hasStat(String key);
    }

    /** The handles on one loaded Chronicle, replaced when a reload gives it a new plugin object. */
    record Handles(PluginBase plugin, MethodHandle onEnable, MethodHandle onDisable, MethodHandle removeHooks,
                           MethodHandle window, MethodHandle onChange, MethodHandle stat, MethodHandle changeUuid,
                           MethodHandle changeKey, MethodHandle changeValue, Map<String, Object> windows) {
    }

    private final FeatsTracker tracker;
    private final HytaleLogger logger;
    private final LongSupplier clock;
    private final AtomicBoolean warned = new AtomicBoolean();
    /** The last window read per player and key, as uuid + key. */
    private final ConcurrentHashMap<String, Long> readTimes = new ConcurrentHashMap<>();
    /** Changes dropped by the read gap, read again by the poll. */
    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();
    private final Consumer<Object> enable = this::apiUp;
    private final Runnable disable = this::apiDown;

    private volatile Windows windows;
    private volatile Handles handles;
    private volatile Object api;
    private volatile Runnable changeStop;
    private volatile ScheduledFuture<?> poll;
    private volatile boolean closed;

    private record Pending(UUID uuid, String key, long value) {
    }

    ChronicleFeats(FeatsTracker tracker, HytaleLogger logger, LongSupplier clock) {
        this.tracker = tracker;
        this.logger = logger;
        this.clock = clock;
    }

    /** Called by name from {@link SiblingLinks}. Starts the poll that finds and follows the Chronicle plugin. */
    public static ChronicleFeats install(Hooks.Context ctx, ScheduledExecutorService scheduler) {
        ChronicleFeats feats = new ChronicleFeats(ctx.tracker(), ctx.logger(), System::currentTimeMillis);
        feats.poll = scheduler.scheduleWithFixedDelay(feats::poll, 0, POLL_SECONDS, TimeUnit.SECONDS);
        return feats;
    }

    /** A binding over a fake Chronicle, for the tests. */
    static ChronicleFeats over(FeatsTracker tracker, HytaleLogger logger, LongSupplier clock, Windows windows) {
        ChronicleFeats feats = new ChronicleFeats(tracker, logger, clock);
        feats.windows = windows;
        return feats;
    }

    /** True while Chronicle is up and the change listener is registered. */
    public boolean bound() {
        return changeStop != null;
    }

    // ---- the mapping ----

    /**
     * One Chronicle change. Reads the window that matters, unless this player and key were read in the last
     * 10 seconds, in which case the change waits for the next poll.
     */
    void changed(UUID uuid, String key, long value) {
        if (uuid == null || key == null) {
            return;
        }
        long now = clock.getAsLong();
        String id = uuid + key;
        Long last = readTimes.get(id);
        if (last != null && now - last < READ_GAP_MILLIS) {
            pending.put(id, new Pending(uuid, key, value));
            return;
        }
        read(id, uuid, key, value, now);
    }

    private void read(String id, UUID uuid, String key, long value, long now) {
        Windows w = windows;
        if (w == null) {
            return;
        }
        if (readTimes.size() >= READ_TIMES_LIMIT) {
            readTimes.clear();
        }
        readTimes.put(id, now);
        pending.remove(id);
        try {
            switch (key) {
                case BLOCKS_BROKEN -> tracker.raiseTo(uuid, DAY_BLOCKS_MAX, w.window(uuid, key, TODAY));
                case KILLS -> tracker.raiseTo(uuid, WEEK_KILLS_MAX, w.window(uuid, key, WEEK));
                case DAYS_ACTIVE -> tracker.raiseTo(uuid, MONTH_DAYS_MAX, w.window(uuid, key, MONTH));
                case RPG_LEVEL -> {
                    if (value > 0 && w.hasStat(RPG_LEVEL)) {
                        tracker.raiseTo(uuid, RPG_LEVEL_MAX, value);
                    }
                }
                default -> {
                    // a key the listener was not registered for
                }
            }
        } catch (RuntimeException | LinkageError e) {
            warn(e);
        }
    }

    /** Reads the changes the gap held back once their 10 seconds passed. */
    void flushPending() {
        if (pending.isEmpty()) {
            return;
        }
        long now = clock.getAsLong();
        for (Map.Entry<String, Pending> e : pending.entrySet()) {
            Long last = readTimes.get(e.getKey());
            if (last == null || now - last >= READ_GAP_MILLIS) {
                Pending p = e.getValue();
                read(e.getKey(), p.uuid(), p.key(), p.value(), now);
            }
        }
    }

    int pendingCount() {
        return pending.size();
    }

    // ---- following the plugin ----

    /** Every 5 seconds. Binds to the Chronicle that is loaded, drops one that went away or was replaced. */
    void poll() {
        if (closed) {
            return;
        }
        try {
            PluginBase plugin = plugin();
            Handles h = handles;
            if (plugin == null || plugin.getState() != PluginState.ENABLED) {
                if (h != null) {
                    unbind(h);
                }
                return;
            }
            if (h != null && h.plugin() == plugin) {
                flushPending();
                return;
            }
            if (h != null) {
                unbind(h);
            }
            bind(plugin);
        } catch (RuntimeException | LinkageError e) {
            warn(e);
        }
    }

    private void bind(PluginBase plugin) {
        Handles h;
        try {
            h = handles(plugin);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            // Remember the failure for this plugin object, so the poll does not retry and log every 5 seconds
            handles = new Handles(plugin, null, null, null, null, null, null, null, null, null, Map.of());
            if (warned.compareAndSet(false, true)) {
                logger.at(Level.WARNING).withCause(e).log("Feats could not bind to this Chronicle Stats, so the Chronicle feats do not count until one of them is reloaded");
            }
            return;
        }
        handles = h;
        if (closed) {
            return;
        }
        try {
            h.onDisable().invoke(disable);
            h.onEnable().invoke(enable); // runs apiUp at once when Chronicle is already up
        } catch (Throwable t) {
            warn(t);
        }
    }

    private void unbind(Handles h) {
        handles = null;
        apiDown();
        if (h.removeHooks() == null) {
            return;
        }
        try {
            h.removeHooks().invoke(enable, disable);
        } catch (Throwable ignored) {
            // the old Chronicle is gone with its hooks
        }
    }

    /** The Chronicle enable hook. Registers the change listener on the live API. */
    private void apiUp(Object live) {
        Handles h = handles;
        if (closed || h == null || h.onChange() == null) {
            return;
        }
        try {
            Runnable old = changeStop;
            if (old != null) {
                old.run();
            }
            api = live;
            windows = new LiveWindows(h, live);
            Consumer<Object> listener = this::changedRecord;
            changeStop = (Runnable) h.onChange().invoke(live, KEYS, listener);
        } catch (Throwable t) {
            warn(t);
        }
    }

    /** The Chronicle disable hook. Drops the listener and the live API. */
    private void apiDown() {
        Runnable stop = changeStop;
        changeStop = null;
        api = null;
        windows = null;
        pending.clear();
        readTimes.clear();
        if (stop != null) {
            try {
                stop.run();
            } catch (RuntimeException | LinkageError ignored) {
                // Chronicle dropped its listeners when it stopped
            }
        }
    }

    /** One StatChange record of Chronicle's class, read through the handles. */
    private void changedRecord(Object change) {
        Handles h = handles;
        if (h == null || change == null) {
            return;
        }
        try {
            UUID uuid = (UUID) h.changeUuid().invoke(change);
            String key = (String) h.changeKey().invoke(change);
            long value = (long) h.changeValue().invoke(change);
            changed(uuid, key, value);
        } catch (Throwable t) {
            warn(t);
        }
    }

    private static Handles handles(PluginBase plugin) throws ReflectiveOperationException {
        return handles(plugin, plugin.getClass().getClassLoader());
    }

    /** The handles through one class loader. Package private so a test can resolve them against a Chronicle jar. */
    static Handles handles(PluginBase plugin, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> api = Class.forName(API, true, loader);
        Class<?> window = Class.forName(WINDOW, true, loader);
        Class<?> change = Class.forName(STAT_CHANGE, true, loader);
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Map<String, Object> windows = Map.of(
                TODAY, enumConstant(window, TODAY),
                WEEK, enumConstant(window, WEEK),
                MONTH, enumConstant(window, MONTH));
        return new Handles(plugin,
                lookup.findStatic(api, "onEnable", MethodType.methodType(void.class, Consumer.class)),
                lookup.findStatic(api, "onDisable", MethodType.methodType(void.class, Runnable.class)),
                lookup.findStatic(api, "removeHooks", MethodType.methodType(void.class, Consumer.class, Runnable.class)),
                lookup.findVirtual(api, "window", MethodType.methodType(long.class, UUID.class, String.class, window, String.class)),
                lookup.findVirtual(api, "onChange", MethodType.methodType(Runnable.class, Set.class, Consumer.class)),
                lookup.findVirtual(api, "stat", MethodType.methodType(Optional.class, String.class)),
                lookup.findVirtual(change, "uuid", MethodType.methodType(UUID.class)),
                lookup.findVirtual(change, "key", MethodType.methodType(String.class)),
                lookup.findVirtual(change, "value", MethodType.methodType(long.class)),
                windows);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private static PluginBase plugin() {
        try {
            PluginManager manager = PluginManager.get();
            return manager == null ? null : manager.getPlugin(new PluginIdentifier(Sibling.GROUP, Sibling.CHRONICLE.pluginName()));
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private void warn(Throwable e) {
        if (warned.compareAndSet(false, true)) {
            logger.at(Level.WARNING).withCause(e).log("Feats could not read from Chronicle Stats, so some Chronicle feats may miss a change");
        }
    }

    /** Removes the hooks and stops the poll, for the Feats shutdown. Touches only classes already loaded. */
    @Override
    public void close() {
        closed = true;
        ScheduledFuture<?> p = poll;
        poll = null;
        if (p != null) {
            p.cancel(false);
        }
        Handles h = handles;
        if (h != null) {
            unbind(h);
        } else {
            apiDown();
        }
    }

    /** The windows of the live Chronicle, read through the handles. */
    private static final class LiveWindows implements Windows {
        private final Handles h;
        private final Object api;

        LiveWindows(Handles h, Object api) {
            this.h = h;
            this.api = api;
        }

        @Override
        public long window(UUID uuid, String key, String window) {
            try {
                return (long) h.window().invoke(api, uuid, key, h.windows().get(window), null);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }

        @Override
        public boolean hasStat(String key) {
            try {
                Object r = h.stat().invoke(api, key);
                return r instanceof Optional<?> o && o.isPresent();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }
    }
}
