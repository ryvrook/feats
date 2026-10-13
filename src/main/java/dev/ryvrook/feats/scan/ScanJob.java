package dev.ryvrook.feats.scan;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.playerdata.PlayerStorage;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.FeatsPlugin;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.Scope;
import dev.ryvrook.feats.model.Trigger;
import dev.ryvrook.feats.stats.WorldStore;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.FeatsTracker.Lease;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Runs a retroactive scan over one player or everyone, on the Feats IO thread in batches of
 * {@link #BATCH}. Each batch is its own IO task, so saves and other file work queued meanwhile run
 * between batches. Engine objects of online players are only read on their world thread, saved player
 * data is loaded through the engine's own player storage.
 */
public final class ScanJob {
    public static final int BATCH = 25;

    /** Where the job reports to. Called on the Feats IO thread. */
    public interface Reporter {
        void progress(String text);

        /** {@code lines} has one entry per player, the ones with news first. */
        void done(List<String> lines, String totals);
    }

    private enum Status { CHANGED, NOTHING, BUSY, PAUSED, NO_DATA, FAILED }

    private record Outcome(Status status, String name, int raised, List<String> unlocked, boolean atJoin,
                           int unmapped) {
        static Outcome of(Status status, String name) {
            return new Outcome(status, name, 0, List.of(), false, 0);
        }
    }

    private final FeatsPlugin plugin;
    private final FeatsTracker tracker;
    private final boolean dryRun;
    private final Reporter reporter;
    private final boolean all;
    private final Map<UUID, String> names;
    private final List<UUID> queue = new ArrayList<>();
    private final List<Outcome> outcomes = new ArrayList<>();
    private Map<String, String> zoneNames = Map.of();
    /** Earliest unlock of every personal feat a world or first feat follows, found while scanning. */
    private final Map<String, Earliest> earliest = new HashMap<>();
    private final Set<String> followed = new HashSet<>();
    /** Engine loads started for a whole batch at once, so their waits overlap. Only touched on the IO thread. */
    private final Map<UUID, CompletableFuture<Holder<EntityStore>>> prefetched = new HashMap<>();
    /**
     * In a scan of everyone, offline players come first and the backfill runs before the first online
     * player, whose live unlocks claim records at once. So a record goes to the earliest unlock on file
     * and not to whoever the scan reaches first.
     */
    private int onlineFrom = Integer.MAX_VALUE;
    private boolean backfilled;
    private List<String> records = List.of();

    /** Who unlocked a personal feat first among the scanned players, and when. */
    private record Earliest(long at, UUID uuid, String name) {
    }

    private ScanJob(FeatsPlugin plugin, Map<UUID, String> names, boolean all, boolean dryRun, Reporter reporter) {
        this.plugin = plugin;
        this.tracker = plugin.tracker();
        this.names = names;
        this.all = all;
        this.dryRun = dryRun;
        this.reporter = reporter;
    }

    /** Scans one player. Call it on the Feats IO thread. */
    public static void one(FeatsPlugin plugin, UUID uuid, String name, boolean dryRun, Reporter reporter) {
        Map<UUID, String> given = new HashMap<>();
        if (name != null) {
            given.put(uuid, name);
        }
        ScanJob job = new ScanJob(plugin, given, false, dryRun, reporter);
        job.queue.add(uuid);
        job.begin();
    }

    /**
     * Scans every player with a Feats file plus every player the engine has saved data for. Throws
     * RejectedExecutionException once Feats is shutting down.
     */
    public static void everyone(FeatsPlugin plugin, boolean dryRun, Reporter reporter) {
        ScanJob job = new ScanJob(plugin, Map.of(), true, dryRun, reporter);
        plugin.runIo(job::begin);
    }

    private void begin() {
        zoneNames = ZoneNames.collect();
        for (Feat feat : tracker.registry().server()) {
            if (feat.trigger() != null && feat.trigger().kind() == Trigger.Kind.AFTER) {
                followed.add(feat.trigger().key());
            }
        }
        if (all) {
            Set<UUID> everyone = new TreeSet<>(plugin.store().knownPlayers());
            try {
                everyone.addAll(Universe.get().getPlayerStorage().getPlayers());
            } catch (Exception e) {
                plugin.getLogger().at(Level.WARNING).withCause(e).log("Feats could not list the saved player data");
            }
            List<UUID> online = new ArrayList<>();
            for (UUID uuid : everyone) {
                (tracker.isLoaded(uuid) ? online : queue).add(uuid);
            }
            onlineFrom = queue.size();
            queue.addAll(online);
            for (PlayerProgress live : tracker.loadedPlayers()) {
                noteUnlocks(live, nameOf(live.uuid(), live.username())); // their old unlocks count for the backfill too
            }
            reporter.progress((dryRun ? "Dry run over " : "Scanning ") + queue.size() + " players in batches of "
                    + BATCH + ".");
        }
        batch(0);
    }

    private void batch(int from) {
        int to = Math.min(queue.size(), from + BATCH);
        prefetch(from, to);
        for (int i = from; i < to; i++) {
            if (plugin.io().isShutdown()) {
                prefetched.clear();
                backfilled = true; // no new records while the server stops
                reporter.progress("Feats is shutting down, the scan stopped after " + i + " players.");
                finish();
                return;
            }
            if (all && !backfilled && i >= onlineFrom) {
                backfilled = true;
                records = backfill();
                prefetched.clear();
                int next = i;
                try {
                    plugin.runIo(() -> batch(next)); // after the records the backfill queued for offline holders
                } catch (RejectedExecutionException e) {
                    reporter.progress("Feats is shutting down, the scan stopped after " + i + " players.");
                    finish();
                }
                return;
            }
            UUID uuid = queue.get(i);
            Outcome outcome;
            try {
                outcome = scan(uuid);
            } catch (Exception | LinkageError e) {
                plugin.getLogger().at(Level.WARNING).withCause(e).log("Feats scan failed for %s", uuid);
                outcome = Outcome.of(Status.FAILED, nameOf(uuid, null));
            }
            outcomes.add(outcome);
        }
        if (to >= queue.size()) {
            finish();
            return;
        }
        if (all) {
            reporter.progress("Scanned " + to + " of " + queue.size() + " players.");
        }
        try {
            plugin.runIo(() -> batch(to));
        } catch (RejectedExecutionException e) {
            backfilled = true; // no new records while the server stops
            reporter.progress("Feats is shutting down, the scan stopped after " + to + " players.");
            finish();
        }
    }

    /** Starts the engine loads of the offline players in this batch, so each one waits at most once. */
    private void prefetch(int from, int to) {
        PlayerStorage storage;
        try {
            storage = Universe.get().getPlayerStorage();
        } catch (RuntimeException e) {
            return; // readEngine reports it per player
        }
        for (int i = from; i < to; i++) {
            UUID uuid = queue.get(i);
            if (Universe.get().getPlayer(uuid) != null) {
                continue;
            }
            try {
                prefetched.put(uuid, storage.load(uuid));
            } catch (RuntimeException e) {
                // readEngine tries again and reports it
            }
        }
    }

    private Outcome scan(UUID uuid) throws Exception {
        PlayerRef online = Universe.get().getPlayer(uuid);
        PlayerProgress seen = tracker.peek(uuid);
        String name = nameOf(uuid, online != null ? online.getUsername() : seen != null ? seen.username() : null);
        if (seen != null) {
            noteUnlocks(seen, name); // before any change, live unlocks made below are noted again after them
        }
        EngineRecord record = readEngine(uuid, online);
        if (record == null) {
            return Outcome.of(Status.NO_DATA, name);
        }
        PlayerProgress base = seen != null ? seen : new PlayerProgress(uuid, null);
        WorldScan.Plan plan = WorldScan.plan(tracker, record, zoneNames, base);
        // An offline player may already meet feats their counters reached before, such as a lowered target.
        boolean catchable = seen != null && online == null && !tracker.isLoaded(uuid)
                && !WorldScan.pendingUnlocks(tracker, seen).isEmpty();
        if (plan.changes().isEmpty() && !catchable) {
            return new Outcome(Status.NOTHING, name, 0, List.of(), false, plan.unmappedRegions());
        }
        if (dryRun) {
            PlayerProgress copy = WorldScan.copy(base);
            // An offline player gets everything they meet unlocked by the real scan, an online player
            // only what the raised counters bring, the rest was settled by their catch up at join.
            Set<String> before = tracker.isLoaded(uuid) ? ids(WorldScan.pendingUnlocks(tracker, copy)) : Set.of();
            int moved = WorldScan.applyOffline(copy, plan.changes());
            List<Feat> would = newly(WorldScan.pendingUnlocks(tracker, copy), before);
            noteWouldUnlock(uuid, name, would);
            return outcome(name, moved, names(would), !tracker.isLoaded(uuid), plan.unmappedRegions());
        }
        Lease lease;
        try {
            lease = lease(uuid);
        } catch (FeatsTracker.BusyException e) {
            return Outcome.of(Status.BUSY, name);
        }
        if (lease == null) {
            return Outcome.of(Status.FAILED, name);
        }
        try {
            if (lease.online()) {
                return live(uuid, name, online, record, plan.unmappedRegions());
            }
            PlayerProgress held = lease.progress();
            int moved = WorldScan.applyOffline(held, WorldScan.plan(tracker, record, zoneNames, held).changes());
            // Unlocked now and silently, rewards wait for the next join. A scan of everyone backfills the
            // records that follow these unlocks itself, a single scan claims them right here.
            List<Feat> unlocked = tracker.catchUpOffline(lease, !all);
            noteUnlocks(held, name);
            return outcome(name, moved, names(unlocked), true, plan.unmappedRegions());
        } finally {
            tracker.release(uuid, lease);
        }
    }

    /** Holds the player, creating a file first for a player Feats has never seen. Null when that failed. */
    private Lease lease(UUID uuid) {
        Lease lease = tracker.acquire(uuid);
        if (lease == null && plugin.store().createIfAbsent(uuid)) {
            lease = tracker.acquire(uuid);
        }
        return lease;
    }

    /** Applies the plan to an online player on their world thread, through the tracker. */
    private Outcome live(UUID uuid, String name, PlayerRef online, EngineRecord record, int unmapped) throws Exception {
        if (tracker.isPaused(uuid)) {
            return Outcome.of(Status.PAUSED, name);
        }
        World world = worldOf(online != null ? online : Universe.get().getPlayer(uuid));
        if (world == null) {
            return Outcome.of(Status.FAILED, name);
        }
        return Threads.onWorld(world, () -> {
            PlayerProgress live = tracker.loadedProgress(uuid);
            if (live == null) {
                return Outcome.of(Status.NOTHING, name); // left meanwhile, the next scan picks them up
            }
            Set<String> before = new HashSet<>(live.unlockedSnapshot().keySet());
            int moved = WorldScan.applyLive(tracker, uuid, live,
                    WorldScan.plan(tracker, record, zoneNames, live).changes());
            List<String> unlocked = new ArrayList<>();
            for (String id : live.unlockedSnapshot().keySet()) {
                Feat feat = before.contains(id) ? null : tracker.registry().get(id);
                if (feat != null) {
                    unlocked.add(feat.name());
                }
            }
            noteUnlocks(live, name);
            return outcome(name, moved, unlocked, false, unmapped);
        });
    }

    /** Remembers the earliest unlock of each followed personal feat. Called on the IO and world threads in turn. */
    private void noteUnlocks(PlayerProgress progress, String name) {
        synchronized (earliest) {
            for (String id : followed) {
                Long at = progress.unlockedAt(id);
                Earliest known = earliest.get(id);
                if (at != null && (known == null || at < known.at())) {
                    earliest.put(id, new Earliest(at, progress.uuid(), name));
                }
            }
        }
    }

    /** Notes what a dry run would unlock, dated now, so the dry run backfill matches the real one. */
    private void noteWouldUnlock(UUID uuid, String name, List<Feat> would) {
        long now = System.currentTimeMillis();
        synchronized (earliest) {
            for (Feat feat : would) {
                if (followed.contains(feat.id()) && !earliest.containsKey(feat.id())) {
                    earliest.put(feat.id(), new Earliest(now, uuid, name));
                }
            }
        }
    }

    /**
     * For every world or first feat that follows a personal feat and is not held yet, hands it to the
     * scanned player who unlocked that personal feat earliest, dated with that unlock, through the
     * tracker's normal path. Flag triggered milestones are left to the flag watcher. Only runs for a
     * scan of everyone, before the first online player.
     */
    private List<String> backfill() {
        List<String> lines = new ArrayList<>();
        WorldStore world = tracker.world();
        if (!all || world == null) {
            return lines;
        }
        Map<String, Earliest> found;
        synchronized (earliest) {
            found = new LinkedHashMap<>(earliest);
        }
        for (Feat feat : tracker.registry().server()) {
            Trigger trigger = feat.trigger();
            if (trigger == null || trigger.kind() != Trigger.Kind.AFTER) {
                continue;
            }
            Earliest first = found.get(trigger.key());
            if (first == null) {
                continue;
            }
            boolean isFirst = feat.scope() == Scope.FIRST;
            if (isFirst ? world.hasFirst(feat.id()) : world.hasMilestone(feat.id())) {
                continue;
            }
            String kind = isFirst ? "server first " : "world milestone ";
            if (dryRun) {
                lines.add("Would record the " + kind + feat.name() + " for " + first.name() + ".");
                continue;
            }
            boolean done = isFirst ? tracker.recordFirst(feat.id(), first.uuid(), first.at())
                    : tracker.recordMilestone(feat.id(), first.uuid(), first.at());
            if (done) {
                lines.add("Recorded the " + kind + feat.name() + " for " + first.name() + ".");
            }
        }
        return lines;
    }

    private static Outcome outcome(String name, int moved, List<String> unlocked, boolean atJoin, int unmapped) {
        return new Outcome(moved > 0 || !unlocked.isEmpty() ? Status.CHANGED : Status.NOTHING, name, moved, unlocked,
                atJoin, unmapped);
    }

    /** The live player on their world thread, or their saved data through the engine's player storage. */
    private EngineRecord readEngine(UUID uuid, PlayerRef online) throws Exception {
        World world = worldOf(online);
        if (world != null) {
            EngineRecord live = Threads.onWorld(world, () -> {
                Ref<EntityStore> ref = online.getReference();
                return ref != null && ref.isValid() ? EngineRecord.ofLive(ref.getStore(), ref) : null;
            });
            if (live != null) {
                return live;
            }
        }
        CompletableFuture<Holder<EntityStore>> load = prefetched.remove(uuid);
        if (load == null) {
            load = Universe.get().getPlayerStorage().load(uuid);
        }
        return load == null ? null : EngineRecord.ofSaved(load.get(Threads.TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private static World worldOf(PlayerRef online) {
        if (online == null) {
            return null;
        }
        Ref<EntityStore> ref = online.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return ref.getStore().getExternalData().getWorld();
    }

    private String nameOf(UUID uuid, String known) {
        String given = names.get(uuid);
        if (given != null && !given.isBlank()) {
            return given;
        }
        return known != null && !known.isBlank() ? known : uuid.toString();
    }

    private static Set<String> ids(List<Feat> feats) {
        Set<String> ids = new HashSet<>();
        for (Feat feat : feats) {
            ids.add(feat.id());
        }
        return ids;
    }

    private static List<Feat> newly(List<Feat> after, Set<String> before) {
        List<Feat> out = new ArrayList<>();
        for (Feat feat : after) {
            if (!before.contains(feat.id())) {
                out.add(feat);
            }
        }
        return out;
    }

    private static List<String> names(List<Feat> feats) {
        List<String> out = new ArrayList<>(feats.size());
        for (Feat feat : feats) {
            out.add(feat.name());
        }
        return out;
    }

    private void finish() {
        List<String> news = new ArrayList<>();
        List<String> quiet = new ArrayList<>();
        int raised = 0;
        int unlocked = 0;
        int atJoin = 0;
        int changed = 0;
        int busy = 0;
        int failed = 0;
        int unmapped = 0;
        for (Outcome o : outcomes) {
            raised += o.raised();
            if (o.atJoin()) {
                atJoin += o.unlocked().size();
            } else {
                unlocked += o.unlocked().size();
            }
            unmapped += o.unmapped();
            switch (o.status()) {
                case CHANGED -> {
                    changed++;
                    news.add(line(o));
                }
                case BUSY -> {
                    busy++;
                    news.add(o.name() + " is being changed by another command and was skipped.");
                }
                case PAUSED -> news.add(o.name() + " is paused in Creative or a disabled world and was skipped.");
                case FAILED -> {
                    failed++;
                    news.add(o.name() + " could not be scanned, see the server log.");
                }
                case NO_DATA -> quiet.add(o.name() + " has no saved game data.");
                case NOTHING -> quiet.add(o.name() + " has nothing new." + unmappedNote(o.unmapped()));
            }
        }
        if (!backfilled) {
            backfilled = true;
            records = backfill();
        }
        news.addAll(0, records);
        news.addAll(quiet);
        String totals = (dryRun ? "Dry run finished. " : "Scan finished. ") + count(outcomes.size(), "player")
                + ", " + changed + " with news. " + (dryRun ? "Would raise " : "Raised ") + count(raised, "counter")
                + ", " + count(unlocked + atJoin, "feat") + (dryRun ? " would unlock" : " unlocked")
                + (atJoin > 0 ? ", " + atJoin + " of them for offline players who get the rewards at their next join." : ".")
                + (busy > 0 ? " " + busy + " busy." : "") + (failed > 0 ? " " + failed + " failed." : "")
                + unmappedNote(unmapped)
                + (all ? " " + (dryRun ? "Would backfill " : "Backfilled ") + count(records.size(), "server record") + "." : "");
        reporter.done(news, totals);
    }

    private String line(Outcome o) {
        StringBuilder b = new StringBuilder(o.name()).append(dryRun ? " would get " : " got ")
                .append(count(o.raised(), "counter")).append(" raised");
        if (!o.unlocked().isEmpty()) {
            b.append(dryRun ? ", unlocking " : ", unlocked ").append(String.join(", ", o.unlocked()));
            if (o.atJoin()) {
                b.append(", rewards at their next join");
            }
        }
        return b.append('.').append(unmappedNote(o.unmapped())).toString();
    }

    private static String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    private static String unmappedNote(int unmapped) {
        return unmapped > 0 ? " " + unmapped + " saved regions are not generated by any loaded world." : "";
    }
}
