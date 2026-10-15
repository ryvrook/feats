package dev.ryvrook.feats.export;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.ryvrook.feats.FeatsConfig;
import dev.ryvrook.feats.FeatsPlugin;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.stats.WorldStore;
import dev.ryvrook.feats.track.FeatsTracker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Writes {@code export/feats.json} so web maps and other tools can show a leaderboard
 * and recent unlocks without linking against Feats. The file is replaced atomically.
 *
 * <p>Each export also keeps the ranking it wrote in memory, so {@code /feats top} can answer from it
 * without touching the disk. Player files are re-read at most once a minute, and only the ones saved since
 * the last read are parsed again.
 *
 * <p>{@link #tick} and {@link #flush} do file work and run on the Feats IO thread, or inline at shutdown.
 * The online count is read by the caller and passed in, so this class never touches the universe itself.
 *
 * <p>The same disk read counts how many players own each feat, which is where {@link #rarity} comes from.
 * A light scan task on the IO thread keeps that read going at most once a minute even when the export
 * itself is turned off. Unlocks recorded here are also handed to the optional Discord webhook.
 */
public final class LeaderboardExporter {
    private static final int TOP_PLAYERS = 100;
    private static final int RECENT_UNLOCKS = 50;
    private static final long DISK_REFRESH_MILLIS = 60_000L;
    /** How often the scan task wakes up. It only reads the disk once {@link #DISK_REFRESH_MILLIS} has passed. */
    private static final long SCAN_CHECK_SECONDS = 15L;
    private static final long SCAN_FIRST_SECONDS = 5L;
    /** A player counts toward rarity with this much play time, in seconds, or an unlock beyond the session feats. */
    private static final long COUNTED_PLAYTIME_SECONDS = 600L;
    private static final long RANK_CACHE_MILLIS = 5_000L;

    private final FeatsTracker tracker;
    private final FeatRegistry registry;
    private final ProgressStore store;
    private final HytaleLogger logger;
    private final Path file;
    private final Path temp;

    private final Deque<RecentUnlock> recent = new ArrayDeque<>();
    private volatile Map<UUID, PlayerProgress> diskCache = Map.of();
    private long diskCacheAt;
    private boolean seeded;
    private String lastBody;

    private volatile List<Ranked> ranking = List.of();
    /** Set by {@link #forget} so the next tick writes the file even if its content looks unchanged. */
    private final AtomicBoolean forceNext = new AtomicBoolean();
    /** Set by {@link #forget} so the next export re-reads the player files instead of trusting the cache. */
    private volatile boolean diskStale;

    /** The plugin, handed in or looked up on first use. Gives the config supplier and the IO thread. */
    private volatile FeatsPlugin plugin;
    private final AtomicBoolean scannerStarted = new AtomicBoolean();
    /** Owner counts from the last disk read, null before the first one. */
    private volatile RarityCounts rarityCounts;
    private volatile DiscordWebhook discord;
    private final Object discordLock = new Object();
    private volatile List<Ranked> liveRanking = List.of();
    private volatile long liveRankingAt;

    /** How many counted players own each feat, from one disk read. */
    private record RarityCounts(Map<String, Integer> owners, int counted) {
    }

    /** One leaderboard row with its points worked out once. */
    public record Ranked(UUID uuid, String name, int points, int unlocked, long lastUnlockAt) {
    }

    /**
     * Leaderboard order, shared by the export and {@code /feats top}. Most points first, then whoever
     * reached their latest unlock earliest, then name.
     */
    public static final Comparator<Ranked> ORDER = Comparator.comparingInt(Ranked::points).reversed()
            .thenComparingLong(Ranked::lastUnlockAt)
            .thenComparing(Ranked::name);

    /** Ranks a player from their unlocked feats. */
    public static Ranked rank(PlayerProgress p, FeatRegistry registry) {
        int points = 0;
        int unlocked = 0;
        long last = 0;
        for (Map.Entry<String, Long> e : p.unlockedSnapshot().entrySet()) {
            Feat feat = registry.get(e.getKey());
            if (feat == null) {
                continue;
            }
            points += feat.tier().points();
            if (feat.isPersonal()) {
                unlocked++; // world and first feats earn points but stay out of the unlock count
            }
            Long at = e.getValue();
            if (at != null && at > last) {
                last = at;
            }
        }
        return new Ranked(p.uuid(), displayName(p), points, unlocked, last);
    }

    /**
     * Everyone from the last export, best first. Empty until the first export has run. Players online
     * at the time carry their in-memory points from that moment.
     */
    public List<Ranked> ranking() {
        return ranking;
    }

    /**
     * Preferred constructor. Call it in setup after the tracker, the store and the IO thread exist. It
     * starts the rarity scan on the IO thread straight away.
     */
    public LeaderboardExporter(FeatsPlugin plugin) {
        this(plugin.tracker(), plugin.store(), plugin.getDataDirectory(), plugin.getLogger());
        this.plugin = plugin;
        ensureScanner();
    }

    /**
     * Older constructor. The plugin is looked up from the plugin manager a few seconds later, which
     * then starts the rarity scan and the Discord webhook.
     */
    public LeaderboardExporter(FeatsTracker tracker, ProgressStore store, Path dataDirectory, HytaleLogger logger) {
        this.tracker = tracker;
        this.registry = tracker.registry();
        this.store = store;
        this.logger = logger;
        Path dir = dataDirectory.resolve("export");
        this.file = dir.resolve("feats.json");
        this.temp = dir.resolve("feats.json.tmp");
        try {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(this::ensureScanner, SCAN_FIRST_SECONDS, TimeUnit.SECONDS);
        } catch (RuntimeException | LinkageError e) {
            // no engine scheduler, as in a unit test. The scan starts on the first rarity or export call instead.
        }
    }

    /** One unlock in the recent list. The feat is kept by id so a removed feat drops out. */
    public record RecentUnlock(long at, UUID uuid, String name, String featId) {
    }

    /**
     * Called by the tracker on every unlock, from whichever thread caused it. Every unlock goes into the
     * recent list. Catch up unlocks found as a player joins, admin grants and hidden feats are never
     * posted to Discord.
     */
    public void recordUnlock(PlayerProgress progress, Feat feat, long at, boolean catchUp, boolean admin) {
        RecentUnlock entry = new RecentUnlock(at, progress.uuid(), displayName(progress), feat.id());
        synchronized (recent) {
            recent.addLast(entry);
            while (recent.size() > RECENT_UNLOCKS) {
                recent.removeFirst();
            }
        }
        if (catchUp || admin || feat.hidden()) {
            return;
        }
        try {
            postToDiscord(entry, feat);
        } catch (RuntimeException e) {
            logger.at(Level.WARNING).withCause(e).log("Feats could not queue the Discord post for %s", feat.id());
        }
    }

    /** The last unlocks on the server, newest first. At most 50. Safe from any thread. */
    public List<RecentUnlock> recentUnlocks() {
        ensureScanner();
        List<RecentUnlock> out;
        synchronized (recent) {
            out = new ArrayList<>(recent);
        }
        out.sort(Comparator.comparingLong(RecentUnlock::at).reversed());
        return List.copyOf(out);
    }

    /**
     * Fraction of counted players, from 0 to 1, who own the feat. Returns -1 when the feat is unknown,
     * before the first disk read, or while fewer players count than {@code RarityMinimumPlayers}. A
     * counted player has 10 minutes of play time or at least one unlock beyond the
     * session feats every joiner gets. Safe from any thread.
     */
    public double rarity(String featId) {
        ensureScanner();
        RarityCounts counts = rarityCounts;
        Feat feat = featId == null ? null : registry.get(featId);
        if (feat == null || !feat.isPersonal() || counts == null || counts.counted() < minimumPlayers()) {
            return -1; // world and first feats have no rarity
        }
        Integer owners = counts.owners().get(featId);
        return owners == null ? 0 : Math.min(1.0, owners / (double) counts.counted());
    }

    /** Players the rarity is worked out over, or -1 while rarity is unknown, see {@link #rarity}. */
    public int rarityPlayers() {
        ensureScanner();
        RarityCounts counts = rarityCounts;
        if (counts == null || counts.counted() < minimumPlayers()) {
            return -1;
        }
        return counts.counted();
    }

    /**
     * A player's progress, from memory when they are online, otherwise from the last disk read. Null
     * when the player is unknown. Safe from any thread.
     */
    public PlayerProgress progressOf(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        PlayerProgress live = tracker.loadedProgress(uuid);
        return live != null ? live : diskCache.get(uuid);
    }

    /**
     * A player's leaderboard position counting from 1, or 0 when they have no place yet. Uses the last
     * ranking with online players brought up to date, recomputed at most every 5 seconds.
     */
    public int rankOf(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        List<Ranked> rows = liveRanking;
        if (now - liveRankingAt >= RANK_CACHE_MILLIS) {
            Map<UUID, Ranked> byId = new HashMap<>();
            List<Ranked> exported = ranking;
            if (exported.isEmpty()) {
                for (PlayerProgress p : diskCache.values()) { // the export is off, so rank from the last disk read
                    byId.put(p.uuid(), rank(p, registry));
                }
            }
            for (Ranked r : exported) {
                byId.put(r.uuid(), r);
            }
            for (PlayerProgress p : tracker.loadedPlayers()) {
                byId.put(p.uuid(), rank(p, registry));
            }
            List<Ranked> merged = new ArrayList<>(byId.values());
            merged.sort(ORDER);
            rows = List.copyOf(merged);
            liveRanking = rows;
            liveRankingAt = now;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).uuid().equals(uuid)) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * Drops a player who reset from the recent unlocks and from the in memory ranking, and makes the
     * next tick rewrite the file. Cheap and safe to call from any thread.
     */
    public void forget(UUID uuid) {
        synchronized (recent) {
            recent.removeIf(r -> r.uuid().equals(uuid));
        }
        List<Ranked> rows = new ArrayList<>(ranking);
        if (rows.removeIf(r -> r.uuid().equals(uuid))) {
            ranking = List.copyOf(rows);
        }
        diskStale = true; // the reset save is queued on the IO thread ahead of the next export, so the re-read sees it
        forceNext.set(true);
    }

    /**
     * Scheduled task. Writes only when the content changed since the last write, or when a reset asked
     * for a rewrite, and never throws.
     */
    public void tick(int online) {
        ensureScanner();
        boolean force = forceNext.getAndSet(false);
        try {
            export(force, online);
        } catch (Throwable t) {
            if (force) {
                forceNext.set(true); // try the rewrite again next time
            }
            logger.at(Level.WARNING).withCause(t).log("Feats export failed");
        }
    }

    /** Writes the file whether or not anything changed. Used at start and at shutdown. */
    public void flush(int online) {
        ensureScanner();
        try {
            export(true, online);
        } catch (Throwable t) {
            logger.at(Level.WARNING).withCause(t).log("Feats export failed");
        }
    }

    private synchronized void export(boolean force, int online) throws IOException {
        refreshDiskCache();
        String body = body(online);
        if (!force && body.equals(lastBody)) {
            return;
        }
        String json = "{\"updatedAt\": " + System.currentTimeMillis() + ", " + body;
        Files.createDirectories(file.getParent());
        Files.writeString(temp, json, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        lastBody = body;
    }

    /** Scan task on the IO thread. Keeps rarity fresh when the export is off. Never throws. */
    private synchronized void scan() {
        try {
            refreshDiskCache();
        } catch (Throwable t) {
            logger.at(Level.WARNING).withCause(t).log("Feats rarity scan failed");
        }
    }

    /**
     * Starts the scan task on the plugin's IO thread the first time the plugin is known. Cheap to call
     * again. The task stops by itself when the IO thread shuts down.
     */
    private void ensureScanner() {
        if (scannerStarted.get()) {
            return;
        }
        FeatsPlugin p = plugin();
        if (p == null) {
            return;
        }
        ScheduledExecutorService io = p.io();
        if (io == null || io.isShutdown() || !scannerStarted.compareAndSet(false, true)) {
            return;
        }
        try {
            io.scheduleWithFixedDelay(this::scan, SCAN_FIRST_SECONDS, SCAN_CHECK_SECONDS, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            // shutting down, nothing left to scan for
        }
    }

    private FeatsPlugin plugin() {
        FeatsPlugin p = plugin;
        if (p == null) {
            try {
                PluginManager manager = PluginManager.get();
                p = manager == null ? null : manager.getPlugin(FeatsPlugin.class);
            } catch (RuntimeException | LinkageError e) {
                p = null;
            }
            if (p != null) {
                plugin = p;
            }
        }
        return p;
    }

    /** The live config, or null before the plugin is known. */
    private FeatsConfig config() {
        FeatsPlugin p = plugin();
        try {
            return p == null ? null : p.config();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private int minimumPlayers() {
        FeatsConfig cfg = config();
        return Math.max(1, cfg == null ? 10 : cfg.rarityMinimumPlayers());
    }

    /** Queues the Discord post when a webhook is set and the tier is high enough. Never blocks. */
    private void postToDiscord(RecentUnlock entry, Feat feat) {
        FeatsConfig cfg = config();
        if (cfg == null) {
            return;
        }
        String url = cfg.discordWebhookUrl();
        if (url == null || url.isBlank() || !DiscordWebhook.meetsTier(feat.tier(), cfg.discordMinimumTier())) {
            return;
        }
        DiscordWebhook hook = discord;
        if (hook == null) {
            FeatsPlugin p = plugin();
            ScheduledExecutorService io = p == null ? null : p.io();
            if (io == null) {
                return;
            }
            synchronized (discordLock) {
                hook = discord;
                if (hook == null) {
                    hook = new DiscordWebhook(io, logger);
                    discord = hook;
                }
            }
        }
        double rarity = rarity(feat.id());
        hook.post(url.trim(), DiscordWebhook.embed(entry.name(), feat, rarity, cfg.rareBelowPercent()));
    }

    /** Re-reads the player files at most once a minute. The first read also seeds the recent list. */
    private void refreshDiskCache() {
        long now = System.currentTimeMillis();
        if (seeded && !diskStale && now - diskCacheAt < DISK_REFRESH_MILLIS) {
            return;
        }
        diskStale = false;
        Collection<PlayerProgress> all = store.loadAll(tracker.loadedPlayers());
        Map<UUID, PlayerProgress> fresh = new HashMap<>();
        for (PlayerProgress p : all) {
            fresh.put(p.uuid(), p);
        }
        diskCache = fresh;
        diskCacheAt = now;
        rarityCounts = countOwners(all);
        if (!seeded) {
            seedRecent(all);
            seeded = true;
        }
    }

    /** Counts, per feat id, the counted players who own it. */
    private RarityCounts countOwners(Collection<PlayerProgress> all) {
        Map<String, Integer> owners = new HashMap<>();
        Set<String> joinOnly = new HashSet<>();
        for (Feat feat : registry.dependingOn(StatKeys.JOINS)) {
            joinOnly.add(feat.id());
        }
        int counted = 0;
        for (PlayerProgress p : all) {
            Map<String, Long> unlocked = p.unlockedSnapshot();
            if (p.get(StatKeys.PLAYTIME) < COUNTED_PLAYTIME_SECONDS && !hasActiveUnlock(unlocked, joinOnly)) {
                continue;
            }
            counted++;
            for (String id : unlocked.keySet()) {
                if (registry.get(id) != null) {
                    owners.merge(id, 1, Integer::sum);
                }
            }
        }
        return new RarityCounts(Map.copyOf(owners), counted);
    }

    /** True when the player owns a feat other than the ones every joiner gets just for logging in. */
    private boolean hasActiveUnlock(Map<String, Long> unlocked, Set<String> joinOnly) {
        for (String id : unlocked.keySet()) {
            if (!joinOnly.contains(id) && registry.get(id) != null) {
                return true;
            }
        }
        return false;
    }

    private void seedRecent(Collection<PlayerProgress> all) {
        List<RecentUnlock> merged = new ArrayList<>();
        for (PlayerProgress p : all) {
            String name = displayName(p);
            for (Map.Entry<String, Long> e : p.unlockedSnapshot().entrySet()) {
                Feat feat = registry.get(e.getKey());
                if (feat != null && e.getValue() != null) {
                    merged.add(new RecentUnlock(e.getValue(), p.uuid(), name, feat.id()));
                }
            }
        }
        synchronized (recent) {
            Set<String> seen = new HashSet<>();
            for (RecentUnlock r : recent) {
                seen.add(r.uuid() + "/" + r.featId());
            }
            for (RecentUnlock r : merged) {
                if (!seen.contains(r.uuid() + "/" + r.featId())) {
                    recent.addLast(r);
                }
            }
            List<RecentUnlock> sorted = new ArrayList<>(recent);
            sorted.sort(Comparator.comparingLong(RecentUnlock::at));
            recent.clear();
            int from = Math.max(0, sorted.size() - RECENT_UNLOCKS);
            recent.addAll(sorted.subList(from, sorted.size()));
        }
    }

    private String body(int online) {
        Map<UUID, PlayerProgress> everyone = new HashMap<>(diskCache);
        for (PlayerProgress p : tracker.loadedPlayers()) {
            everyone.put(p.uuid(), p);
        }
        List<Ranked> rows = new ArrayList<>(everyone.size());
        for (PlayerProgress p : everyone.values()) {
            rows.add(rank(p, registry));
        }
        rows.sort(ORDER);
        ranking = List.copyOf(rows);

        StringBuilder sb = new StringBuilder(8192);
        sb.append("\"totalFeats\": ").append(registry.count())
                .append(", \"totalPoints\": ").append(registry.totalPoints())
                .append(", \"online\": ").append(online);
        appendRarity(sb);
        sb.append(",\n \"players\": [");
        int count = 0;
        for (Ranked row : rows) {
            if (count >= TOP_PLAYERS) {
                break;
            }
            sb.append(count == 0 ? "\n  " : ",\n  ");
            sb.append("{\"uuid\": ").append(quote(row.uuid().toString()))
                    .append(", \"name\": ").append(quote(row.name()))
                    .append(", \"points\": ").append(row.points())
                    .append(", \"unlocked\": ").append(row.unlocked())
                    .append(", \"lastUnlockAt\": ").append(row.lastUnlockAt())
                    .append('}');
            count++;
        }
        sb.append("],\n \"recent\": [");
        List<RecentUnlock> latest;
        synchronized (recent) {
            latest = new ArrayList<>(recent);
        }
        int written = 0;
        for (RecentUnlock r : latest) {
            Feat feat = registry.get(r.featId());
            if (feat == null) {
                continue;
            }
            sb.append(written++ == 0 ? "\n  " : ",\n  ");
            sb.append("{\"at\": ").append(r.at())
                    .append(", \"uuid\": ").append(quote(r.uuid().toString()))
                    .append(", \"name\": ").append(quote(r.name()))
                    .append(", \"feat\": ").append(quote(feat.id()))
                    .append(", \"featName\": ").append(quote(feat.name()))
                    .append(", \"tier\": ").append(quote(feat.tier().displayName()))
                    .append(", \"points\": ").append(feat.tier().points())
                    .append(", \"category\": ").append(quote(feat.category().displayName()))
                    .append(", \"hidden\": ").append(feat.hidden())
                    .append('}');
        }
        sb.append(']');
        appendWorld(sb);
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Adds {@code "milestones"} and {@code "firsts"}, the records of server.json keyed by feat id, each
     * with {@code at}, {@code by}, {@code byName}, {@code featName} and {@code tier}. {@code by} and
     * {@code byName} are null for a milestone reached with nobody named.
     */
    private void appendWorld(StringBuilder sb) {
        WorldStore world = tracker.world();
        sb.append(",\n \"milestones\": {");
        if (world != null) {
            appendRecords(sb, world.milestones());
        }
        sb.append("},\n \"firsts\": {");
        if (world != null) {
            appendRecords(sb, world.firsts());
        }
        sb.append('}');
    }

    private void appendRecords(StringBuilder sb, Map<String, WorldStore.Entry> rows) {
        int i = 0;
        for (Map.Entry<String, WorldStore.Entry> e : rows.entrySet()) {
            Feat feat = registry.get(e.getKey());
            if (feat == null) {
                continue;
            }
            WorldStore.Entry v = e.getValue();
            sb.append(i++ == 0 ? "\n  " : ",\n  ").append(quote(feat.id())).append(": ")
                    .append("{\"at\": ").append(v.at())
                    .append(", \"by\": ").append(v.by() == null ? "null" : quote(v.by().toString()))
                    .append(", \"byName\": ").append(v.byName() == null ? "null" : quote(v.byName()))
                    .append(", \"featName\": ").append(quote(feat.name()))
                    .append(", \"tier\": ").append(quote(feat.tier().displayName()))
                    .append('}');
        }
    }

    /**
     * Adds {@code "rarityPlayers"}, the number of counted players, and {@code "rarity"}, the owned
     * fraction per feat id. The map stays empty until enough players count.
     */
    private void appendRarity(StringBuilder sb) {
        RarityCounts counts = rarityCounts;
        int counted = counts == null ? 0 : counts.counted();
        sb.append(", \"rarityPlayers\": ").append(counted).append(",\n \"rarity\": {");
        if (counts != null && counted >= minimumPlayers()) {
            int i = 0;
            for (Feat feat : registry.personal()) {
                Integer owners = counts.owners().get(feat.id());
                double fraction = owners == null ? 0 : owners / (double) counted;
                sb.append(i++ == 0 ? "" : ", ").append(quote(feat.id())).append(": ")
                        .append(String.format(Locale.ROOT, "%.4f", fraction));
            }
        }
        sb.append('}');
    }

    /** Players online right now. Read on the engine scheduler and handed to the export. */
    public static int onlineNow() {
        try {
            Universe universe = Universe.get();
            return universe == null ? 0 : universe.getPlayerCount();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String displayName(PlayerProgress p) {
        String name = p.username();
        return name == null || name.isEmpty() ? p.uuid().toString().substring(0, 8) : name;
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
