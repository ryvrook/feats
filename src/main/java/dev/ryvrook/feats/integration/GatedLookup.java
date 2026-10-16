package dev.ryvrook.feats.integration;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Plugin manager lookups that never wait for the manager. {@code getPlugin} and {@code getPlugins} take the read
 * side of the manager's lock, and the manager holds the write side for a whole server stop and for every
 * {@code /plugin reload}, calling each plugin's shutdown under it. A timer or IO task that looked a plugin up then
 * parked on the lock until the manager was done, and the manager was waiting for Feats' shutdown, which waited for
 * that task. So the read lock is only tried. While another thread holds the write lock, or once the server is
 * stopping, the last answer for that key is given again, or null when there is none yet. The next look after the
 * manager let go sees the real state. The thread that holds the write lock still looks up as before.
 *
 * <p>Lookups in Feats' own setup and start go to the manager directly. Everything on the engine scheduler, the
 * Feats-IO thread or the dependents thread goes through {@link #ENGINE}. Safe from any thread.
 */
public final class GatedLookup {
    /** The gate in front of the engine's plugin manager. */
    public static final GatedLookup ENGINE = new GatedLookup(GatedLookup::managerLock, GatedLookup::serverStopping);

    private static final Object NONE = new Object();
    private static volatile Field lockField;
    private static volatile boolean lockFieldMissing;

    private final Supplier<ReadWriteLock> lock;
    private final BooleanSupplier stopping;
    private final Map<Object, Object> last = new ConcurrentHashMap<>();

    /**
     * {@code lock} gives the manager's lock, or null when it is not known, and then every lookup goes through.
     * {@code stopping} is true once the server stops, and then no lookup is made.
     */
    public GatedLookup(Supplier<ReadWriteLock> lock, BooleanSupplier stopping) {
        this.lock = lock;
        this.stopping = stopping;
    }

    /** The answer of {@code look} under the tried read lock, or the last answer for {@code key} when it is held. */
    @SuppressWarnings("unchecked")
    public <T> T find(Object key, Supplier<T> look) {
        if (isStopping()) {
            return remembered(key);
        }
        ReadWriteLock rw;
        try {
            rw = lock == null ? null : lock.get();
        } catch (RuntimeException | LinkageError e) {
            rw = null;
        }
        if (rw == null) {
            return remember(key, look.get());
        }
        if (!rw.readLock().tryLock()) {
            return remembered(key);
        }
        try {
            return remember(key, look.get());
        } finally {
            rw.readLock().unlock();
        }
    }

    private boolean isStopping() {
        try {
            return stopping != null && stopping.getAsBoolean();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T remembered(Object key) {
        Object o = last.get(key);
        return o == null || o == NONE ? null : (T) o;
    }

    private <T> T remember(Object key, T found) {
        last.put(key, found == null ? NONE : found);
        return found;
    }

    // ---- the engine side ----

    /** {@code PluginManager.getPlugin(id)} through {@link #ENGINE}. Null when not loaded or the manager is busy and never saw it. */
    public static PluginBase plugin(PluginIdentifier id) {
        return ENGINE.find(id, () -> {
            PluginManager manager = PluginManager.get();
            return manager == null ? null : manager.getPlugin(id);
        });
    }

    /** {@code PluginManager.getPlugin(type)} through {@link #ENGINE}. */
    public static <T extends PluginBase> T plugin(Class<T> type) {
        return ENGINE.find(type, () -> {
            PluginManager manager = PluginManager.get();
            return manager == null ? null : manager.getPlugin(type);
        });
    }

    /** {@code PluginManager.getPlugins()} through {@link #ENGINE}, an empty list when nothing is known. */
    public static List<PluginBase> plugins() {
        List<PluginBase> out = ENGINE.find(GatedLookup.class, () -> {
            PluginManager manager = PluginManager.get();
            return manager == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(manager.getPlugins()));
        });
        return out == null ? List.of() : out;
    }

    private static boolean serverStopping() {
        HytaleServer server = HytaleServer.get();
        return server != null && server.isShuttingDown();
    }

    /**
     * The plugin manager's private {@code lock} field, a ReentrantReadWriteLock in 0.7.1, read reflectively. Null when
     * the manager is not up or the field is not there, and then lookups are not gated.
     */
    static ReadWriteLock managerLock() {
        if (lockFieldMissing) {
            return null;
        }
        try {
            PluginManager manager = PluginManager.get();
            if (manager == null) {
                return null;
            }
            Field f = lockField;
            if (f == null) {
                f = PluginManager.class.getDeclaredField("lock");
                f.setAccessible(true);
                lockField = f;
            }
            return f.get(manager) instanceof ReadWriteLock rw ? rw : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            lockFieldMissing = true;
            return null;
        }
    }
}
