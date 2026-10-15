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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * The public Feats API for other plugins. Get it with {@link #get()}, which returns null while Feats
 * is not loaded, and look it up again after a {@code /plugin reload} of Feats instead of keeping it.
 * A plugin using it should list Feats as a dependency in its manifest so the classes are visible.
 *
 * <p>Every method is safe from any thread. Reads come from memory for online players. For offline
 * players {@link #points}, {@link #unlocked}, {@link #hasFeat}, {@link #progress}, {@link #title} and
 * {@link #stats} answer from the leaderboard's last read of the player files, which is at most a
 * minute old, and return zero, empty or null for a player Feats has never seen. Writes only work for
 * online players.
 *
 * <p>This class is stable. Methods are only ever added, and existing ones keep their meaning.
 */
public final class FeatsApi {
    private static volatile FeatsApi instance;
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
     * Adds to a counter for an online player and unlocks whatever feats read it, exactly as if Feats
     * had seen the action itself, including the Creative mode and disabled world pause. Returns false
     * when the player is not online, {@code delta} is not positive or the key is not a counter Feats
     * keeps or watches. An ignored key is logged at most once a minute.
     */
    public boolean addStat(UUID uuid, String key, long delta) {
        if (uuid == null || key == null || key.isEmpty() || delta <= 0) {
            return false;
        }
        if (!tracker().isPersisted(key) && !tracker().isWatched(key)) {
            ignoredKey(key);
            return false;
        }
        if (!tracker().isLoaded(uuid)) {
            return false;
        }
        tracker().increment(uuid, key, delta);
        return true;
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
        return new FeatInfo(f.id(), f.name(), f.description(), f.category().displayName(), f.tier().name(),
                f.hidden(), f.tier().points(), f.scope().name());
    }
}
