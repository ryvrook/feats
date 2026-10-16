package dev.ryvrook.feats.api;

import dev.ryvrook.feats.FeatsPlugin;
import dev.ryvrook.feats.export.LeaderboardExporter;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.WorldStore;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The public Feats API for other plugins. Get it with {@link #get()}, which returns null while Feats
 * is not loaded, and look it up again after a {@code /plugin reload} of Feats instead of keeping it.
 * A plugin using it should list Feats as a dependency in its manifest so the classes are visible.
 *
 * <p>Every method is safe from any thread. Reads come from memory for online players. For offline
 * players {@link #points}, {@link #unlocked}, {@link #hasFeat}, {@link #progress}, {@link #title} and
 * {@link #stats} answer from the leaderboard's last read of the player files, which is at most a
 * minute old, and return zero, empty or null for a player Feats has never seen. {@link #addStat}
 * also takes offline players, {@link #grant} only online ones.
 *
 * <p>Bind from {@link #onEnable(Consumer)}, which runs at once when Feats is already started and again every
 * time it starts, and drop your state in {@link #onDisable(Runnable)}. The hooks live on this class, and a
 * {@code /plugin reload} of Feats loads a new copy of it that your classes do not see. Feats makes up for that by
 * reloading every enabled plugin that lists {@code Ryvrook:Feats} under its dependencies or optional dependencies
 * two seconds after it started again, so your plugin starts over and registers its hooks on the new copy.
 *
 * <p>This class is stable. Methods are only ever added, and existing ones keep their meaning. Check
 * {@link #API_VERSION} when a newer method is needed.
 */
public final class FeatsApi {
    /**
     * Raised whenever a method, a record component or an event field is added. Version 2 came with Feats 1.7.0
     * and added the enable and disable hooks, {@link #top}, and the admin, hidden, rarity, category and description
     * of {@link FeatUnlockedEvent}. Version 3 came with Feats 1.7.2 and added the banner, pack and requires of
     * {@link FeatInfo}, {@link #packs()}, {@link #siblings()} and {@link #serverStat}. Feats before 1.7.0 has no
     * such field, so read it inside a try that catches {@link NoSuchFieldError} and treat a miss as version 1.
     */
    public static final int API_VERSION = 3;

    private static final Logger LOG = Logger.getLogger("Feats");
    private static final List<Consumer<FeatsApi>> ENABLE = new CopyOnWriteArrayList<>();
    private static final List<Runnable> DISABLE = new CopyOnWriteArrayList<>();
    private static volatile FeatsApi instance;
    /** The API once Feats has started, null before and after. */
    private static volatile FeatsApi started;
    private static final long IGNORED_LOG_MILLIS = 60_000L;

    private final FeatsPlugin plugin;
    private final List<Consumer<FeatUnlockedEvent>> listeners = new CopyOnWriteArrayList<>();
    private final List<FeatInfo> feats;
    private final AtomicLong lastIgnoredLog = new AtomicLong(Long.MIN_VALUE / 2);

    private FeatsApi(FeatsPlugin plugin) {
        this.plugin = plugin;
        List<FeatInfo> all = new ArrayList<>();
        for (Feat f : plugin.tracker().registry().all()) {
            all.add(info(f));
        }
        this.feats = Collections.unmodifiableList(all);
    }

    /** The running API, or null when Feats is not loaded. */
    public static FeatsApi get() {
        return instance;
    }

    /**
     * Runs the hook now when Feats has started, and every time it starts. Since API version 2.
     */
    public static void onEnable(Consumer<FeatsApi> hook) {
        Objects.requireNonNull(hook, "hook");
        ENABLE.add(hook);
        FeatsApi api = started;
        if (api != null) {
            runHook(() -> hook.accept(api));
        }
    }

    /**
     * Runs the hook every time Feats stops, before its unlock listeners are dropped and {@link #get} turns null.
     * Since API version 2.
     */
    public static void onDisable(Runnable hook) {
        Objects.requireNonNull(hook, "hook");
        DISABLE.add(hook);
    }

    /** Removes hooks added with {@link #onEnable} and {@link #onDisable}. Since API version 2. */
    public static void removeHooks(Consumer<FeatsApi> enable, Runnable disable) {
        if (enable != null) {
            ENABLE.remove(enable);
        }
        if (disable != null) {
            DISABLE.remove(disable);
        }
    }

    /** True once Feats has started and until it stops. Since API version 2. */
    public static boolean isEnabled() {
        return started != null;
    }

    /** Called by Feats itself at the end of start. Runs the enable hooks. Not for other plugins. */
    public static void enable(FeatsApi api) {
        Objects.requireNonNull(api, "api");
        started = api;
        for (Consumer<FeatsApi> hook : ENABLE) {
            runHook(() -> hook.accept(api));
        }
    }

    /** Called by Feats itself first thing at shutdown. Runs the disable hooks. Not for other plugins. */
    public static void disable(FeatsApi api) {
        if (api == null || started != api) {
            return;
        }
        started = null;
        for (Runnable hook : DISABLE) {
            runHook(hook);
        }
    }

    private static void runHook(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException | LinkageError e) {
            LOG.log(Level.WARNING, "A Feats lifecycle hook failed", e);
        }
    }

    /** Called by Feats itself during setup. Not for other plugins. */
    public static FeatsApi install(FeatsPlugin plugin) {
        FeatsApi api = new FeatsApi(plugin);
        instance = api;
        return api;
    }

    /** Called by Feats itself at shutdown. Not for other plugins. */
    public static void uninstall(FeatsApi api) {
        if (instance == api) {
            instance = null;
        }
        if (api != null) {
            api.listeners.clear();
        }
    }

    /** The player's points total. */
    public int points(UUID uuid) {
        PlayerProgress p = progressOf(uuid);
        return p == null ? 0 : tracker().points(p);
    }

    /** Ids of every feat the player has unlocked, oldest first. Unmodifiable. */
    public Set<String> unlocked(UUID uuid) {
        PlayerProgress p = progressOf(uuid);
        if (p == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(p.unlockedSnapshot().keySet()));
    }

    public boolean hasFeat(UUID uuid, String featId) {
        PlayerProgress p = progressOf(uuid);
        return p != null && featId != null && p.isUnlocked(featId);
    }

    /** Progress toward one feat, or null when the feat id is unknown. */
    public FeatProgress progress(UUID uuid, String featId) {
        Feat feat = featId == null ? null : tracker().registry().get(featId);
        if (feat == null) {
            return null;
        }
        long target = feat.totalTarget();
        PlayerProgress p = progressOf(uuid);
        if (p == null) {
            return new FeatProgress(0, target);
        }
        if (p.isUnlocked(featId)) {
            return new FeatProgress(target, target);
        }
        return new FeatProgress(feat.currentProgress(p), target);
    }

    /** The feat id the player chose as their title, or null for none. */
    public String title(UUID uuid) {
        PlayerProgress p = progressOf(uuid);
        return p == null ? null : p.title();
    }

    /** Leaderboard position counting from 1, or 0 when the player has no place yet. */
    public int rank(UUID uuid) {
        LeaderboardExporter exporter = plugin.exporter();
        return exporter == null ? 0 : exporter.rankOf(uuid);
    }

    /**
     * The points leaderboard, best first, at most {@code limit} rows. The same order as {@code /feats top} and
     * {@link #rank}: most points, then whoever reached their latest unlock earliest, then name. Built from the last
     * export with online players up to date, at most 5 seconds old. Empty when {@code limit} is below 1. Since
     * API version 2.
     */
    public List<LeaderboardEntry> top(int limit) {
        LeaderboardExporter exporter = plugin.exporter();
        return exporter == null ? List.of() : entries(exporter.liveRanking(), limit);
    }

    static List<LeaderboardEntry> entries(List<LeaderboardExporter.Ranked> rows, int limit) {
        int n = Math.min(Math.max(0, limit), rows.size());
        List<LeaderboardEntry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LeaderboardExporter.Ranked r = rows.get(i);
            out.add(new LeaderboardEntry(i + 1, r.uuid(), r.name(), r.points(), r.unlocked(), r.lastUnlockAt()));
        }
        return Collections.unmodifiableList(out);
    }

    /** The packs that are on, such as SOCIAL, in display order. Since API version 3. */
    public List<String> packs() {
        List<String> out = new ArrayList<>();
        for (dev.ryvrook.feats.model.Pack p : plugin.tracker().registry().packs()) {
            out.add(p.name());
        }
        return Collections.unmodifiableList(out);
    }

    /** The plugin names of the siblings Feats found at setup, such as Coinage. Since API version 3. */
    public List<String> siblings() {
        List<String> out = new ArrayList<>();
        for (dev.ryvrook.feats.model.Sibling s : plugin.siblings()) {
            out.add(s.pluginName());
        }
        return Collections.unmodifiableList(out);
    }

    /** A server total from server.json, such as {@code blocks_broken} by everyone. 0 when unknown. Since API version 3. */
    public long serverStat(String key) {
        dev.ryvrook.feats.stats.WorldStore world = plugin.world();
        return world == null ? 0L : world.total(key);
    }

    /** Every feat in display order. Unmodifiable. */
    public List<FeatInfo> feats() {
        return feats;
    }

    /** Every reached server milestone by world feat id, oldest first. Unmodifiable. */
    public Map<String, WorldRecord> milestones() {
        WorldStore world = tracker().world();
        return world == null ? Map.of() : records(world.milestones());
    }

    /** Every claimed server first by first feat id, oldest first. Unmodifiable. */
    public Map<String, WorldRecord> firsts() {
        WorldStore world = tracker().world();
        return world == null ? Map.of() : records(world.firsts());
    }

    private Map<String, WorldRecord> records(Map<String, WorldStore.Entry> rows) {
        Map<String, WorldRecord> out = new LinkedHashMap<>();
        rows.forEach((id, e) -> {
            Feat feat = tracker().registry().get(id);
            out.put(id, new WorldRecord(id, e.at(), e.by(), e.byName(), feat != null && feat.hidden()));
        });
        return Collections.unmodifiableMap(out);
    }

    /** One counter's value, for example {@code blocks_broken}. Zero when unknown. */
    public long stats(UUID uuid, String key) {
        PlayerProgress p = progressOf(uuid);
        return p == null || key == null ? 0 : p.get(key);
    }

    /**
     * Adds to a counter and unlocks whatever feats read it, exactly as if Feats had seen the action itself,
     * including the Creative mode and disabled world pause. For an offline player the add waits in memory
     * and the Feats file thread writes it into their file, where the feats it meets unlock quietly with
     * their rewards held for the next join. A player Feats has never seen gets nothing. Returns false when
     * {@code delta} is not positive, the key is not a counter Feats keeps or watches, too many offline adds
     * are waiting, or this API belongs to a Feats that has stopped since. After a Feats reload ask
     * {@link #get} again. An ignored key is logged at most once a minute.
     */
    public boolean addStat(UUID uuid, String key, long delta) {
        if (uuid == null || key == null || key.isEmpty() || delta <= 0 || instance != this) {
            return false; // a stopped Feats has no file thread, its write would run on the caller's thread
        }
        if (!tracker().isPersisted(key) && !tracker().isWatched(key)) {
            ignoredKey(key);
            return false;
        }
        return tracker().addOffline(uuid, key, delta);
    }

    /**
     * Unlocks a feat for an online player whatever its requirements, with the usual toast, chat line
     * and rewards. Returns false when the player is not online, the id is unknown or the feat was
     * already unlocked. It counts as a real unlock, so granting a personal feat nobody had yet can claim
     * the world milestones and server firsts that follow it. Granting a world or first feat records the
     * player as its holder, and returns false when it was already reached or claimed.
     */
    public boolean grant(UUID uuid, String featId) {
        Feat feat = featId == null ? null : tracker().registry().get(featId);
        return feat != null && tracker().apiGrant(uuid, feat);
    }

    /**
     * Calls {@code listener} for every unlock, on the thread that caused it. Returns a handle that
     * removes the listener again when run. Listeners are dropped when Feats shuts down.
     */
    public Runnable onUnlock(Consumer<FeatUnlockedEvent> listener) {
        if (listener == null) {
            return () -> {};
        }
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** Called by Feats for every unlock. Not for other plugins. */
    public void fire(FeatUnlockedEvent event, Consumer<Throwable> onError) {
        for (Consumer<FeatUnlockedEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException | LinkageError e) {
                onError.accept(e);
            }
        }
    }

    /** Warns about a key addStat refused, at most once a minute. */
    private void ignoredKey(String key) {
        long now = System.currentTimeMillis();
        long last = lastIgnoredLog.get();
        if (now - last >= IGNORED_LOG_MILLIS && lastIgnoredLog.compareAndSet(last, now)) {
            plugin.getLogger().at(Level.WARNING).log("A plugin called the Feats addStat API with %s, which is not a counter Feats keeps, so it was ignored", key);
        }
    }

    private FeatsTracker tracker() {
        return plugin.tracker();
    }

    private PlayerProgress progressOf(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        PlayerProgress live = tracker().loadedProgress(uuid);
        if (live != null) {
            return live;
        }
        LeaderboardExporter exporter = plugin.exporter();
        return exporter == null ? null : exporter.progressOf(uuid);
    }

    static FeatInfo info(Feat f) {
        java.util.Set<String> requires = new java.util.TreeSet<>();
        for (dev.ryvrook.feats.model.Sibling s : f.requires()) {
            requires.add(s.pluginName());
        }
        return new FeatInfo(f.id(), f.name(), f.description(), f.category().displayName(), f.tier().name(),
                f.hidden(), f.tier().points(), f.scope().name(), f.banner(), f.pack() == null ? null : f.pack().name(),
                requires);
    }
}
