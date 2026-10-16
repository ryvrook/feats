package dev.ryvrook.feats.track;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.Category;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Requirement;
import dev.ryvrook.feats.model.Scope;
import dev.ryvrook.feats.model.Tier;
import dev.ryvrook.feats.model.Trigger;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.stats.WorldStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;

/**
 * Central entry point for every game event. Bumps counters, evaluates the feats that
 * depend on them, and persists progress. Safe to call from any thread.
 *
 * <p>Saves for players leaving and for resets run on the plugin's Feats-IO thread. Until such a save has
 * finished the progress stays in {@code unloading}, and a player who comes straight back picks up
 * that same object instead of reading a file that is still being written.
 */
public final class FeatsTracker {
    /** At most this many unlocks found by {@link #catchUp} get the full toast, sound and chat line. */
    private static final int CATCH_UP_ANNOUNCED = 5;
    /** At most this many shared milestones found on join get their own chat line. */
    private static final int SHARED_ANNOUNCED = 3;

    private final FeatRegistry registry;
    private final ProgressStore store;
    private final UnlockNotifier notifier;
    private final HytaleLogger logger;
    private final Executor io;
    private final Map<UUID, PlayerProgress> loaded = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> unloading = new ConcurrentHashMap<>();
    /** Players read from disk whose feats have not been checked against their counters yet, see {@link #catchUp}. */
    private final Set<UUID> needsCatchUp = ConcurrentHashMap.newKeySet();
    private volatile UnlockListener unlockListener;
    private final List<Consumer<Unlock>> unlockHooks = new CopyOnWriteArrayList<>();
    /**
     * Offline players an admin command is editing on the IO thread. A player who joins meanwhile picks
     * up this same object, so the edit and the join never see two copies.
     */
    private final Map<UUID, PlayerProgress> held = new ConcurrentHashMap<>();
    /** Players whose counters are paused, in Creative mode or in a disabled world. Usually empty. */
    private final Set<UUID> paused = ConcurrentHashMap.newKeySet();
    private volatile boolean pausePlaytime = true;
    /** Server milestones and firsts, null when the plugin did not hand one in. */
    private final WorldStore world;
    /** Shared milestones added on load, told to the player once they are in a world, see {@link #catchUp}. */
    private final Map<UUID, List<String>> sharedNotice = new ConcurrentHashMap<>();
    /** Hands out the rewards of feats unlocked while the player was offline, see {@link #catchUp}. */
    private volatile Consumer<Unlock> deferredRewards;
    /**
     * What the tracker needs to know about each counter key, worked out once per key. The registry never
     * changes after start, so an entry never goes stale. Bounded like the {@link Classify} memos.
     */
    private final Map<String, KeyInfo> keyInfo = new ConcurrentHashMap<>();
    /**
     * Counter adds from other plugins for players who are offline, by player and key, waiting for the IO
     * thread to write them into the player file. See {@link #addOffline}.
     */
    private final Map<UUID, Map<String, Long>> offlineAdds = new ConcurrentHashMap<>();
    /** Players with adds waiting. Past this further offline adds are dropped. */
    static final int OFFLINE_ADDS_LIMIT = 10_000;
    /** Running kill streaks, in memory only. See {@link #countKill}. */
    private final KillStreaks streaks = new KillStreaks();

    /** Keys whose info is remembered. Past this a key is looked up each time instead. */
    static final int KEY_INFO_LIMIT = 16_384;

    // ---- 1.7.2 meta feats and server totals, worked out once from the registry ----
    /** Personal feats of each tab that count towards its closer, so every feat of the tab but the closers. */
    private final Map<Category, List<Feat>> categoryPool = new EnumMap<>(Category.class);
    /** Platinum personal feats that count towards Grand Slam, so none that read the meta counters. */
    private final List<Feat> platinumPool = new ArrayList<>();
    /** Every personal feat that reads a meta counter, re-checked when a revoke lowers one. */
    private final List<Feat> metaFeats = new ArrayList<>();
    /** World milestones raised by a server total, by the total's key. */
    private final Map<String, List<Feat>> totalsByKey = new HashMap<>();
    /** Whether the server totals are kept at all, the ServerTotals setting. Read on every mirrored increment. */
    private volatile BooleanSupplier serverTotals = () -> true;
    /** The online count for the Full House milestone, the FullHouse setting. */
    private volatile IntSupplier fullHouse = () -> FeatRegistry.FULL_HOUSE_DEFAULT;

    /**
     * Whether a counter is kept at all, which feats read it and whether it is mirrored into the server totals.
     * Totals are always kept, per id keys only when a feat watches them or a stats row shows them.
     */
    private record KeyInfo(boolean persisted, List<Feat> watchers, boolean mirrored) {}

    /** One unload waiting for its save. A new instance per unload so a late finish never removes a newer one. */
    private record Pending(PlayerProgress progress) {}

    /**
     * Why an unlock happened. Meta feats unlocked as a result inherit it. {@code deferRewards} keeps the
     * rewards in the player's file until their next join, {@code raiseOffline} lets an unlock on a held
     * offline copy claim the world and first feats that follow it.
     */
    private record Source(boolean catchUp, boolean admin, boolean rewards, boolean deferRewards,
                          boolean raiseOffline) {
        Source(boolean catchUp, boolean admin, boolean rewards) {
            this(catchUp, admin, rewards, false, false);
        }

        static final Source PLAY = new Source(false, false, true);
        static final Source CATCH_UP = new Source(true, false, true);
        /** Found for a player who is offline. Silent, rewards wait for the next join. */
        static final Source OFFLINE = new Source(true, false, false, true, true);
        /** Like {@link #OFFLINE}, but the world and first feats that follow are left to the caller. */
        static final Source OFFLINE_QUIET = new Source(true, false, false, true, false);
    }

    /**
     * One unlock as seen by the hooks. {@code admin} is true for admin grants and admin stat edits,
     * {@code rewards} says whether rewards may be handed out for it.
     */
    public record Unlock(UUID uuid, PlayerProgress progress, Feat feat, long at, boolean catchUp, boolean admin,
                         boolean rewards) {}

    /** A player's progress held for an admin command, and whether it is the live copy of an online player. */
    public record Lease(PlayerProgress progress, boolean online) {}

    /** Thrown by {@link #acquire} when another edit of the same player is still running. */
    public static final class BusyException extends RuntimeException {
        BusyException() {
            super("player progress is busy", null, false, false);
        }
    }

    /**
     * Told about every unlock, whether or not the player is online to see it. {@code catchUp} is true
     * for unlocks found by {@link #catchUp} as the player joined, {@code admin} for admin grants and
     * admin stat edits.
     */
    @FunctionalInterface
    public interface UnlockListener {
        void unlocked(PlayerProgress progress, Feat feat, long at, boolean catchUp, boolean admin);
    }

    public FeatsTracker(FeatRegistry registry, ProgressStore store, UnlockNotifier notifier, HytaleLogger logger,
                        Executor io) {
        this(registry, store, notifier, logger, io, null);
    }

    public FeatsTracker(FeatRegistry registry, ProgressStore store, UnlockNotifier notifier, HytaleLogger logger,
                        Executor io, WorldStore world) {
        this.registry = registry;
        this.store = store;
        this.notifier = notifier;
        this.logger = logger;
        this.io = io;
        this.world = world;
        indexMeta();
        // The banner asks before showing, so a player who ran /feats notify off sees none.
        notifier.setNotifications(uuid -> {
            PlayerProgress p = loaded.get(uuid);
            return p == null || p.notifications();
        });
    }

    /** Builds the meta feat pools and the totals index once. The registry never changes after start. */
    private void indexMeta() {
        for (Category c : Category.values()) {
            categoryPool.put(c, new ArrayList<>());
        }
        for (Feat feat : registry.personal()) {
            boolean closer = false;
            boolean meta = false;
            for (Requirement r : feat.requirements()) {
                String key = r.key();
                if (key.startsWith(StatKeys.CATEGORY_DONE_PREFIX)) {
                    closer = true;
                    meta = true;
                } else if (StatKeys.FEATS_UNLOCKED.equals(key) || StatKeys.FEAT_POINTS.equals(key)
                        || StatKeys.PLATINUMS_DONE.equals(key)) {
                    meta = true;
                }
            }
            if (!closer) {
                categoryPool.get(feat.category()).add(feat);
            }
            if (meta) {
                metaFeats.add(feat);
            }
            boolean ladder = feat.requirements().stream().anyMatch(r -> StatKeys.FEATS_UNLOCKED.equals(r.key())
                    || StatKeys.PLATINUMS_DONE.equals(r.key()) || r.key().startsWith(StatKeys.CATEGORY_DONE_PREFIX));
            if (feat.tier() == Tier.PLATINUM && !ladder) {
                platinumPool.add(feat);
            }
        }
        for (Feat feat : registry.totalTriggered()) {
            totalsByKey.computeIfAbsent(feat.trigger().key(), k -> new ArrayList<>()).add(feat);
        }
    }

    /**
     * Where the ServerTotals and FullHouse settings come from. Read each time, so a reload applies at once.
     * Without this the totals are kept and Full House waits for 20 players.
     */
    public void setServerTotals(BooleanSupplier on, IntSupplier fullHouse) {
        this.serverTotals = on == null ? () -> true : on;
        this.fullHouse = fullHouse == null ? () -> FeatRegistry.FULL_HOUSE_DEFAULT : fullHouse;
    }

    /**
     * Why a feat id the registry knows is not in it, such as "pack GRIND is off" or "needs [COINAGE]", for the
     * admin grant command. Null for a feat that is in, and for an id nothing defines.
     */
    public String whySkipped(String featId) {
        return registry.whySkipped(featId);
    }

    public FeatRegistry registry() {
        return registry;
    }

    /** The server milestones and firsts, or null when world feats are off. */
    public WorldStore world() {
        return world;
    }

    public void setUnlockListener(UnlockListener listener) {
        this.unlockListener = listener;
    }

    /**
     * Where the rewards of feats unlocked while a player was offline go once they join. Only the
     * rewards, the listener and the other hooks were told at unlock time.
     */
    public void setDeferredRewards(Consumer<Unlock> rewards) {
        this.deferredRewards = rewards;
    }

    /** Adds a hook told about every unlock after the unlock listener, on the thread that caused it. */
    public void addUnlockHook(Consumer<Unlock> hook) {
        unlockHooks.add(hook);
    }

    // ---- scoping -------------------------------------------------------

    /** Pauses or resumes every counter for one player. Cheap, called on mode and world changes. */
    public void setPaused(UUID uuid, boolean pause) {
        if (uuid == null) {
            return;
        }
        if (pause) {
            paused.add(uuid);
        } else {
            paused.remove(uuid);
        }
    }

    public boolean isPaused(UUID uuid) {
        return uuid != null && paused.contains(uuid);
    }

    /** When false, time played keeps counting for paused players. */
    public void setPausePlaytime(boolean pause) {
        this.pausePlaytime = pause;
    }

    /**
     * One isEmpty check for nearly every event, a hash lookup only while someone is paused. Time played
     * and the longest session are wall clock time, so with PausePlaytimeWhenDisabled off they keep
     * counting for paused players.
     */
    private boolean gated(UUID uuid, String key) {
        if (paused.isEmpty() || !paused.contains(uuid)) {
            return false;
        }
        return pausePlaytime || !(StatKeys.PLAYTIME.equals(key) || StatKeys.LONGEST_SESSION.equals(key));
    }

    /** The cached facts about a counter key. One map lookup on the hot path. */
    private KeyInfo info(String key) {
        KeyInfo hit = keyInfo.get(key);
        if (hit != null) {
            return hit;
        }
        List<Feat> watchers = registry.dependingOn(key);
        KeyInfo fresh = new KeyInfo(!StatKeys.isPerId(key) || !watchers.isEmpty() || StatKeys.isDisplayed(key),
                watchers, StatKeys.MIRRORED_TOTALS.contains(key));
        if (keyInfo.size() < KEY_INFO_LIMIT) {
            KeyInfo raced = keyInfo.putIfAbsent(key, fresh);
            if (raced != null) {
                return raced;
            }
        }
        return fresh;
    }

    /** Loads (or creates) progress for a player, caching it until {@link #unload}. */
    public PlayerProgress load(UUID uuid, String username) {
        PlayerProgress progress = loaded.compute(uuid, (id, existing) -> {
            if (existing != null) {
                existing.setUsername(username);
                return existing;
            }
            Pending pending = unloading.get(id);
            if (pending != null) {
                pending.progress().setUsername(username);
                return pending.progress(); // newer than the file, which may still be mid write
            }
            PlayerProgress editing = held.get(id);
            if (editing != null) {
                editing.setUsername(username); // an admin command is editing this copy, share it
                needsCatchUp.add(id);
                return editing;
            }
            PlayerProgress fresh = store.load(id, username);
            fresh.retainStats(this::isPersisted); // older files may hold counters nothing reads any more
            needsCatchUp.add(id);
            return fresh;
        });
        List<String> added = fillShared(progress);
        if (!added.isEmpty()) {
            sharedNotice.merge(uuid, added, (a, b) -> {
                List<String> both = new ArrayList<>(a);
                both.addAll(b);
                return both;
            });
        }
        return progress;
    }

    /**
     * Brings a player's shared milestones in line with the server file. Adds every reached milestone
     * they did not raise themselves and drops any an admin cleared. Returns the ids added.
     */
    private List<String> fillShared(PlayerProgress progress) {
        if (world == null || progress == null) {
            return List.of();
        }
        Map<String, WorldStore.Entry> reached = world.milestones();
        progress.retainShared(reached::containsKey);
        List<String> added = new ArrayList<>(0);
        for (Map.Entry<String, WorldStore.Entry> e : reached.entrySet()) {
            Feat feat = registry.get(e.getKey());
            if (feat != null && feat.scope() == Scope.WORLD && !progress.isUnlocked(feat.id())
                    && progress.addShared(feat.id(), e.getValue().at())) {
                added.add(feat.id());
            }
        }
        return added;
    }

    /**
     * Checks every feat once against a player's counters after their file was read, so a feat added by
     * an update, or one whose target was lowered, unlocks now instead of on the next matching event.
     * Call it once the player is in a world, because the unlocks are announced through the usual path.
     * Does nothing unless the progress came from disk since the last call.
     *
     * <p>To keep a big catch up from flooding the screen, the first 5 unlocks get the normal toast,
     * sound, chat line and broadcast. Any further ones unlock silently, still counting for points, the
     * export and meta feats, and the player gets one chat line saying how many more unlocked.
     */
    public void catchUp(UUID uuid) {
        if (uuid == null) {
            return;
        }
        List<String> shared = sharedNotice.remove(uuid);
        if (shared != null) {
            tellShared(uuid, shared);
        }
        settleRewards(uuid);
        if (!needsCatchUp.remove(uuid)) {
            return;
        }
        PlayerProgress progress = loaded.get(uuid);
        if (progress == null) {
            return;
        }
        syncMeta(progress); // points, closers and Grand Slam for a file written before 1.7.2, or after a feat moved
        Announcements budget = new Announcements(CATCH_UP_ANNOUNCED);
        evaluate(uuid, progress, registry.all(), budget, Source.CATCH_UP);
        if (budget.silenced > 0) {
            try {
                notifier.moreUnlocked(uuid, budget.silenced, progress.unlockedCount(), registry.count());
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Unlock summary failed for %s", uuid);
            }
        }
    }

    /** Hands the rewards that waited for this join to the rewards, each once. */
    private void settleRewards(UUID uuid) {
        PlayerProgress progress = loaded.get(uuid);
        Consumer<Unlock> rewards = deferredRewards;
        if (progress == null || rewards == null) {
            return;
        }
        for (String id : progress.drainPendingRewards()) {
            Feat feat = registry.get(id);
            Long at = progress.unlockedAt(id);
            if (feat == null || at == null) {
                continue; // revoked or removed since
            }
            try {
                rewards.accept(new Unlock(uuid, progress, feat, at, true, false, true));
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Deferred rewards failed for %s", id);
            }
        }
    }

    /** One quiet chat line per shared milestone, a summary line past the first few. */
    private void tellShared(UUID uuid, List<String> ids) {
        try {
            int told = 0;
            for (String id : ids) {
                Feat feat = registry.get(id);
                WorldStore.Entry entry = world == null ? null : world.milestone(id);
                if (feat == null || entry == null) {
                    continue;
                }
                if (told++ < SHARED_ANNOUNCED) {
                    notifier.sharedMilestone(uuid, feat, entry.byName());
                }
            }
            if (told > SHARED_ANNOUNCED) {
                notifier.moreShared(uuid, told - SHARED_ANNOUNCED);
            }
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Shared milestone notice failed for %s", uuid);
        }
    }

    /** How many unlocks may still be announced in full, and how many went quiet after that. */
    private static final class Announcements {
        private int left;
        private int silenced;

        Announcements(int left) {
            this.left = left;
        }

        /** True when this unlock gets the full notification, false when it should stay quiet. */
        boolean claim() {
            if (left > 0) {
                left--;
                return true;
            }
            silenced++;
            return false;
        }
    }

    public boolean isLoaded(UUID uuid) {
        return loaded.containsKey(uuid);
    }

    public Collection<PlayerProgress> loadedPlayers() {
        return loaded.values();
    }

    /** Drops a player from memory and saves them on the Feats IO thread. */
    public void unload(UUID uuid) {
        Pending[] out = {null};
        // Moving the progress to unloading inside the same compute as load keeps a rejoin from reading the old file.
        loaded.computeIfPresent(uuid, (id, progress) -> {
            out[0] = new Pending(progress);
            unloading.put(id, out[0]);
            // Cleared under the same bin lock as load sets it, so a late unload never clears a newer load's flag.
            needsCatchUp.remove(id);
            sharedNotice.remove(id);
            return null;
        });
        Pending pending = out[0];
        if (pending == null) {
            return;
        }
        try {
            notifier.banners().clear(uuid); // a banner queued for a player who left
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Banner clear failed for %s", uuid);
        }
        runInBackground(() -> {
            if (store.save(pending.progress())) {
                unloading.remove(uuid, pending);
            } // on failure it stays pending, so a rejoin or the shutdown save still has it
        });
    }

    public void saveDirty() {
        for (PlayerProgress progress : loaded.values()) {
            if (progress.isDirty()) {
                store.save(progress);
            }
        }
    }

    /** Saves everyone in memory, including players whose leaving save has not finished. Runs inline. */
    public void saveAll() {
        for (PlayerProgress progress : loaded.values()) {
            store.save(progress);
        }
        for (Map.Entry<UUID, Pending> e : unloading.entrySet()) {
            if (store.save(e.getValue().progress())) {
                unloading.remove(e.getKey(), e.getValue());
            }
        }
    }

    /** Wipes a player's progress. The file write happens on the Feats IO thread. */
    public void reset(UUID uuid) {
        PlayerProgress progress = loaded.get(uuid);
        if (progress == null) {
            Pending pending = unloading.get(uuid);
            progress = pending != null ? pending.progress() : null;
        }
        if (progress != null) {
            progress.reset();
            fillShared(progress);
            PlayerProgress target = progress;
            runInBackground(() -> store.save(target));
        } else {
            // Rewrite instead of delete, so the rewarded set survives and a reset cannot farm rewards.
            runInBackground(() -> resetOffline(uuid));
        }
    }

    /** Resets a player who is not loaded. Runs on the IO thread. */
    private void resetOffline(UUID uuid) {
        Lease lease;
        try {
            lease = acquire(uuid);
        } catch (BusyException e) {
            logger.at(Level.WARNING).log("Feats could not reset %s because their progress is busy", uuid);
            return;
        }
        if (lease == null) {
            return; // no file, nothing to reset
        }
        lease.progress().reset();
        fillShared(lease.progress());
        release(uuid, lease);
    }

    // ---- admin access ----------------------------------------------------

    /**
     * Gets a player's progress for an admin command. Online players get their live copy. Offline
     * players are read from disk and held until {@link #release}, and a join meanwhile shares the held
     * copy. Returns null when the player has no file. Blocks on file reads, so call it on the IO thread.
     *
     * @throws BusyException when another admin edit of this player has not been released
     */
    public Lease acquire(UUID uuid) {
        Lease[] out = {null};
        loaded.compute(uuid, (id, existing) -> {
            if (existing != null) {
                out[0] = new Lease(existing, true);
                return existing;
            }
            if (held.containsKey(id)) {
                throw new BusyException();
            }
            Pending pending = unloading.get(id);
            PlayerProgress progress;
            if (pending != null) {
                progress = pending.progress(); // newer than the file
            } else if (store.exists(id)) {
                progress = store.load(id, null);
                progress.retainStats(this::isPersisted);
            } else {
                return null;
            }
            held.put(id, progress);
            out[0] = new Lease(progress, false);
            return null;
        });
        return out[0];
    }

    /** Saves and lets go of progress from {@link #acquire}. Online copies are left to the autosave. */
    public void release(UUID uuid, Lease lease) {
        if (lease == null || lease.online()) {
            return;
        }
        try {
            if (!store.save(lease.progress()) && loaded.get(uuid) != lease.progress()) {
                logger.at(Level.WARNING).log("Feats could not save the admin change for %s", uuid);
            }
        } finally {
            held.remove(uuid, lease.progress());
        }
    }

    /**
     * Unlocks a feat for an admin, whatever its requirements. Announced when the player is online.
     * Rewards follow only when {@code rewards} is true, and meta feats unlocked as a result follow
     * the same rule. Returns false when the feat was already unlocked.
     */
    public boolean adminGrant(PlayerProgress progress, Feat feat, boolean rewards) {
        Source source = new Source(false, true, rewards);
        return feat.isPersonal() ? force(progress, feat, source) : grantServer(progress, feat, source);
    }

    /**
     * Unlocks a feat for another plugin through the API. Counts as a normal unlock, rewards included.
     * Only for players who are online. Returns false when not online or already unlocked.
     */
    public boolean apiGrant(UUID uuid, Feat feat) {
        PlayerProgress progress = uuid == null ? null : loaded.get(uuid);
        if (progress == null) {
            return false;
        }
        return feat.isPersonal() ? force(progress, feat, Source.PLAY) : grantServer(progress, feat, Source.PLAY);
    }

    /**
     * Grants a world or first feat by recording the player as its holder in the server file, so a first
     * never ends up with two holders. False when it was already reached or claimed, by anyone.
     */
    private boolean grantServer(PlayerProgress progress, Feat feat, Source source) {
        if (world == null || progress.isUnlocked(feat.id())) {
            return false;
        }
        return claim(feat, progress, System.currentTimeMillis(), source);
    }

    private boolean force(PlayerProgress progress, Feat feat, Source source) {
        return force(progress, feat, source, System.currentTimeMillis());
    }

    private boolean force(PlayerProgress progress, Feat feat, Source source, long now) {
        if (!progress.unlock(feat.id(), now)) {
            return false;
        }
        UUID uuid = progress.uuid();
        Announcements budget = isLive(progress) ? null : new Announcements(0);
        announce(uuid, progress, List.of(feat), now, budget, source);
        if (!feat.isPersonal()) {
            // World and first feats stay out of the feats unlocked counter but score points like /feats top shows.
            progress.add(StatKeys.FEAT_POINTS, feat.tier().points());
            evaluate(uuid, progress, registry.dependingOn(StatKeys.FEAT_POINTS), budget, source);
            return true;
        }
        afterUnlocks(uuid, progress, List.of(feat), budget, source);
        return true;
    }

    /**
     * Follows fresh personal unlocks with the meta counters they move. The feats unlocked and points counters
     * rise and the ladders that read them are checked. Then, only for the tabs the unlocks sit in, the tab is
     * counted and category_done:<name> set when every feat of it but the closers is unlocked, and the Platinum
     * pool is counted after a Platinum unlock for Grand Slam. Last, the world and first feats that follow.
     * Never runs on a plain increment, so the counting costs nothing on the hot path.
     */
    private void afterUnlocks(UUID uuid, PlayerProgress progress, List<Feat> unlockedNow, Announcements budget,
                              Source source) {
        int points = 0;
        EnumSet<Category> tabs = EnumSet.noneOf(Category.class);
        boolean platinum = false;
        for (Feat feat : unlockedNow) {
            points += feat.tier().points();
            tabs.add(feat.category());
            platinum |= feat.tier() == Tier.PLATINUM;
        }
        progress.add(StatKeys.FEATS_UNLOCKED, unlockedNow.size());
        progress.add(StatKeys.FEAT_POINTS, points);
        evaluate(uuid, progress, registry.dependingOn(StatKeys.FEATS_UNLOCKED), budget, source);
        evaluate(uuid, progress, registry.dependingOn(StatKeys.FEAT_POINTS), budget, source);
        for (Category tab : tabs) {
            List<Feat> pool = categoryPool.get(tab);
            if (pool.isEmpty() || !allUnlocked(progress, pool)) {
                continue;
            }
            String key = StatKeys.CATEGORY_DONE_PREFIX + tab.name();
            if (progress.raiseTo(key, 1)) {
                evaluate(uuid, progress, registry.dependingOn(key), budget, source);
            }
        }
        if (platinum && !platinumPool.isEmpty() && allUnlocked(progress, platinumPool)
                && progress.raiseTo(StatKeys.PLATINUMS_DONE, 1)) {
            evaluate(uuid, progress, registry.dependingOn(StatKeys.PLATINUMS_DONE), budget, source);
        }
        raiseServerFeats(uuid, progress, unlockedNow, source);
    }

    private static boolean allUnlocked(PlayerProgress progress, List<Feat> feats) {
        for (int i = 0, n = feats.size(); i < n; i++) {
            if (!progress.isUnlocked(feats.get(i).id())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Brings the meta counters in line with the unlocks in the file, once per load from disk. A file written
     * before the points counter existed gets its points, and a tab completed before its closer existed gets
     * its category_done flag, so the catch up that follows unlocks the closer. Lowers flags that no longer hold.
     */
    private void syncMeta(PlayerProgress progress) {
        synchronized (progress) {
            long points = points(progress);
            if (progress.get(StatKeys.FEAT_POINTS) != points) {
                progress.set(StatKeys.FEAT_POINTS, points);
            }
            for (Map.Entry<Category, List<Feat>> e : categoryPool.entrySet()) {
                if (!e.getValue().isEmpty()) {
                    setFlag(progress, StatKeys.CATEGORY_DONE_PREFIX + e.getKey().name(), allUnlocked(progress, e.getValue()));
                }
            }
            setFlag(progress, StatKeys.PLATINUMS_DONE, !platinumPool.isEmpty() && allUnlocked(progress, platinumPool));
        }
    }

    private static void setFlag(PlayerProgress progress, String key, boolean on) {
        if (on) {
            progress.raiseTo(key, 1);
        } else if (progress.get(key) != 0) {
            progress.set(key, 0);
        }
    }

    /** Lowers the category_done and platinums_done flags that a revoke broke. Never raises one. */
    private void lowerMeta(PlayerProgress progress) {
        for (Map.Entry<Category, List<Feat>> e : categoryPool.entrySet()) {
            String key = StatKeys.CATEGORY_DONE_PREFIX + e.getKey().name();
            if (progress.get(key) != 0 && !allUnlocked(progress, e.getValue())) {
                progress.set(key, 0);
            }
        }
        if (progress.get(StatKeys.PLATINUMS_DONE) != 0 && !allUnlocked(progress, platinumPool)) {
            progress.set(StatKeys.PLATINUMS_DONE, 0);
        }
    }

    private static void lowerPoints(PlayerProgress progress, Feat feat) {
        progress.set(StatKeys.FEAT_POINTS, Math.max(0L, progress.get(StatKeys.FEAT_POINTS) - feat.tier().points()));
    }

    /**
     * Removes a feat and lowers the feats unlocked counter. Meta feats that are no longer met are
     * removed too, until nothing changes. Returns every feat id taken away, empty when the feat was
     * not unlocked. A feat whose counters still meet its target unlocks again on the next matching
     * event or join.
     */
    public List<String> revoke(PlayerProgress progress, String featId) {
        List<String> removed = new ArrayList<>();
        if (!progress.revoke(featId)) {
            return removed;
        }
        removed.add(featId);
        Feat revoked = registry.get(featId);
        if (revoked != null) {
            lowerPoints(progress, revoked);
        }
        if (revoked != null && !revoked.isPersonal()) {
            releaseServer(progress, revoked);
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            lowerMeta(progress);
            for (Feat meta : metaFeats) {
                if (progress.isUnlocked(meta.id()) && !meta.isSatisfied(progress) && progress.revoke(meta.id())) {
                    removed.add(meta.id());
                    lowerPoints(progress, meta);
                    changed = true;
                }
            }
        }
        return removed;
    }

    /**
     * After an admin revoked a world or first feat. A first they held is cleared in the server file, so
     * the next player to get there claims it. A milestone stays reached and they share in it instead.
     */
    private void releaseServer(PlayerProgress progress, Feat feat) {
        if (world == null) {
            return;
        }
        if (feat.scope() == Scope.FIRST) {
            if (world.removeFirstIfHeldBy(feat.id(), progress.uuid())) {
                runInBackground(world::save);
                logger.at(Level.INFO).log("Feats server first %s is unclaimed again", feat.id());
            }
            return;
        }
        WorldStore.Entry reached = world.milestone(feat.id());
        if (reached != null) {
            progress.addShared(feat.id(), reached.at());
        }
    }

    /**
     * Checks every personal feat against a held offline copy from {@link #acquire}, as the catch up at
     * join would, and records what it now meets. Nothing is shown to anyone, and the rewards wait in the
     * player's file until their next join, see {@link #catchUp}. The unlock listener and hooks are told
     * as for a catch up. With {@code raiseServer} the world and first feats that follow these unlocks are
     * claimed for the player, otherwise the caller backfills them. A copy that became live because the
     * player joined meanwhile gets the normal catch up instead. Call it on the Feats IO thread. Returns
     * every feat unlocked, meta, world and first feats included.
     */
    public List<Feat> catchUpOffline(Lease lease, boolean raiseServer) {
        if (lease == null) {
            return List.of();
        }
        PlayerProgress progress = lease.progress();
        UUID uuid = progress.uuid();
        Set<String> before = new HashSet<>(progress.unlockedSnapshot().keySet());
        if (isLive(progress)) {
            evaluate(uuid, progress, registry.personal(), new Announcements(CATCH_UP_ANNOUNCED), Source.CATCH_UP);
        } else {
            evaluate(uuid, progress, registry.personal(), new Announcements(0),
                    raiseServer ? Source.OFFLINE : Source.OFFLINE_QUIET);
        }
        List<Feat> unlocked = new ArrayList<>();
        for (String id : progress.unlockedSnapshot().keySet()) {
            Feat feat = before.contains(id) ? null : registry.get(id);
            if (feat != null) {
                unlocked.add(feat);
            }
        }
        return unlocked;
    }

    /**
     * Sets a counter for an admin and checks the feats that read it. Unlocks that follow count as
     * admin unlocks without rewards. Lowering a counter never removes a feat.
     */
    public void adminSetStat(PlayerProgress progress, String key, long value) {
        progress.set(key, value);
        Announcements budget = isLive(progress) ? null : new Announcements(0);
        evaluate(progress.uuid(), progress, registry.dependingOn(key), budget, new Source(false, true, false));
    }

    /**
     * A read only look at a player's progress for the browser or a comparison. Online players give
     * their live copy, offline players a fresh read of their file that nobody saves. Null when the
     * player has no file. Reads files, so call it on the IO thread.
     */
    public PlayerProgress peek(UUID uuid) {
        PlayerProgress live = loaded.get(uuid);
        if (live != null) {
            return live;
        }
        Pending pending = unloading.get(uuid);
        if (pending != null) {
            return pending.progress();
        }
        PlayerProgress editing = held.get(uuid);
        if (editing != null) {
            return editing;
        }
        return store.exists(uuid) ? store.load(uuid, null) : null;
    }

    /** The loaded progress of an online player, or null. */
    public PlayerProgress loadedProgress(UUID uuid) {
        return uuid == null ? null : loaded.get(uuid);
    }

    private boolean isLive(PlayerProgress progress) {
        return loaded.get(progress.uuid()) == progress;
    }

    private void runInBackground(Runnable task) {
        Runnable guarded = () -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Feats background save failed");
            }
        };
        try {
            io.execute(guarded);
        } catch (RejectedExecutionException e) {
            guarded.run(); // the executor is shutting down, finish the write here instead
        }
    }

    // ---- counters ------------------------------------------------------

    /**
     * Adds to a counter of a player who may be offline, for the API. An online player is counted at once
     * as {@link #increment} would. Otherwise the add waits in memory, merged with any other adds for that
     * player, and the IO thread writes the whole batch into the player file, checks the feats that read
     * those counters and saves. Feats unlocked that way stay quiet and their rewards wait for the next join.
     * A player without a file gets nothing. A player who joins before the batch is written gets it on the
     * live copy. Safe from any thread, never touches a file itself. Returns false only when the add was
     * dropped because too many players have adds waiting.
     */
    public boolean addOffline(UUID uuid, String key, long amount) {
        if (uuid == null || key == null || amount <= 0) {
            return false;
        }
        if (loaded.containsKey(uuid)) {
            increment(uuid, key, amount);
            return true;
        }
        if (!offlineAdds.containsKey(uuid) && offlineAdds.size() >= OFFLINE_ADDS_LIMIT) {
            return false;
        }
        boolean[] first = {false};
        offlineAdds.compute(uuid, (id, adds) -> {
            if (adds == null) {
                adds = new HashMap<>();
                first[0] = true;
            }
            adds.merge(key, amount, Long::sum);
            return adds;
        });
        if (first[0]) {
            runInBackground(() -> applyOffline(uuid));
        }
        return true;
    }

    /** Players with offline adds waiting to be written, for tests and status. */
    public int offlineAddsWaiting() {
        return offlineAdds.size();
    }

    /** Writes one player's waiting adds. Runs on the IO thread. */
    private void applyOffline(UUID uuid) {
        Map<String, Long> adds = offlineAdds.remove(uuid);
        if (adds == null || adds.isEmpty()) {
            return;
        }
        if (loaded.containsKey(uuid)) {
            adds.forEach((key, amount) -> increment(uuid, key, amount)); // joined meanwhile
            return;
        }
        Lease lease;
        try {
            lease = acquire(uuid);
        } catch (BusyException e) {
            // An admin edit holds the player. Put the adds back and try again after it.
            offlineAdds.compute(uuid, (id, newer) -> {
                if (newer != null) {
                    adds.forEach((key, amount) -> newer.merge(key, amount, Long::sum));
                    return newer;
                }
                return adds;
            });
            runInBackground(() -> applyOffline(uuid));
            return;
        }
        if (lease == null) {
            return; // never seen on this server, nothing to add to
        }
        if (lease.online()) {
            adds.forEach((key, amount) -> increment(uuid, key, amount));
            return;
        }
        try {
            PlayerProgress progress = lease.progress();
            Set<Feat> watchers = new LinkedHashSet<>();
            synchronized (progress) {
                adds.forEach((key, amount) -> {
                    progress.add(key, amount);
                    watchers.addAll(info(key).watchers());
                });
            }
            evaluate(uuid, progress, List.copyOf(watchers), new Announcements(0), Source.OFFLINE);
        } finally {
            release(uuid, lease);
        }
    }

    public void increment(UUID uuid, String key, long amount) {
        if (amount <= 0 || uuid == null) {
            return;
        }
        if (gated(uuid, key)) {
            return; // Creative mode or a disabled world
        }
        KeyInfo info = info(key);
        if (!info.persisted()) {
            return; // a per item counter no feat or stats row reads
        }
        PlayerProgress progress = loaded.get(uuid);
        if (progress == null) {
            return; // not ready yet, ignore events fired before PlayerReady
        }
        List<Feat> unlockedNow;
        long now;
        synchronized (progress) {
            progress.add(key, amount);
            now = info.watchers().isEmpty() ? 0L : System.currentTimeMillis();
            unlockedNow = collectUnlocks(progress, info.watchers(), now, null);
        }
        if (info.mirrored()) {
            mirror(key, amount, uuid);
        }
        finish(uuid, progress, unlockedNow, now, null, Source.PLAY);
    }

    /**
     * Raises a counter to at least {@code value}, for records such as a best time or a highest level,
     * and checks the feats that read it. Paused like {@link #increment}.
     */
    public void raiseTo(UUID uuid, String key, long value) {
        if (uuid == null || gated(uuid, key)) {
            return;
        }
        KeyInfo info = info(key);
        if (!info.persisted()) {
            return;
        }
        PlayerProgress progress = loaded.get(uuid);
        if (progress == null) {
            return;
        }
        List<Feat> unlockedNow;
        long now;
        synchronized (progress) {
            if (!progress.raiseTo(key, value)) {
                return;
            }
            now = info.watchers().isEmpty() ? 0L : System.currentTimeMillis();
            unlockedNow = collectUnlocks(progress, info.watchers(), now, null);
        }
        finish(uuid, progress, unlockedNow, now, null, Source.PLAY);
    }

    /**
     * Adds the same amount to every key of one event, such as the five keys of one ore block, then checks
     * the feats that read any of them. The counters move under one hold of the player's monitor and the
     * feats are checked in the same pass, so the work is one map lookup per key and one lock per event.
     * The end state is the same as calling {@link #increment} once per key.
     */
    public void incrementAll(UUID uuid, List<String> keys, long amount) {
        if (amount <= 0 || uuid == null || keys.isEmpty()) {
            return;
        }
        PlayerProgress progress = loaded.get(uuid);
        if (progress == null) {
            return; // not ready yet, ignore events fired before PlayerReady
        }
        boolean anyPaused = !paused.isEmpty() && paused.contains(uuid);
        List<Feat> unlockedNow = null;
        List<String> mirrored = null;
        long now = 0L;
        synchronized (progress) {
            int n = keys.size();
            for (int i = 0; i < n; i++) {
                String key = keys.get(i);
                if (anyPaused && gated(uuid, key)) {
                    continue; // Creative mode or a disabled world
                }
                KeyInfo info = info(key);
                if (info.persisted()) {
                    progress.add(key, amount);
                    if (info.mirrored()) {
                        if (mirrored == null) {
                            mirrored = new ArrayList<>(2);
                        }
                        mirrored.add(key);
                    }
                }
            }
            for (int i = 0; i < n; i++) {
                String key = keys.get(i);
                if (anyPaused && gated(uuid, key)) {
                    continue;
                }
                KeyInfo info = info(key);
                if (info.persisted() && !info.watchers().isEmpty()) {
                    if (now == 0L) {
                        now = System.currentTimeMillis();
                    }
                    unlockedNow = collectUnlocks(progress, info.watchers(), now, unlockedNow);
                }
            }
        }
        if (mirrored != null) {
            for (String key : mirrored) {
                mirror(key, amount, uuid); // outside the monitor, a milestone touches other players' progress
            }
        }
        finish(uuid, progress, unlockedNow, now, null, Source.PLAY);
    }

    // ---- server totals ----------------------------------------------------

    /** Adds a mirrored counter to the server total and records any milestone the total just reached. */
    private void mirror(String key, long amount, UUID by) {
        if (world == null || !serverTotals.getAsBoolean()) {
            return;
        }
        long total = world.addTotal(key, amount);
        List<Feat> feats = totalsByKey.get(key);
        if (feats != null) {
            checkTotal(feats, total, by);
        }
    }

    /**
     * Checks every world milestone raised by a server total against the totals in server.json and records
     * the ones reached, with {@code by} named as the player whose action crossed the line, or nobody when
     * null. For the hooks that raise a total straight on the WorldStore, such as the peak online count.
     */
    public void checkTotals(UUID by) {
        if (world == null || !serverTotals.getAsBoolean()) {
            return;
        }
        for (Map.Entry<String, List<Feat>> e : totalsByKey.entrySet()) {
            checkTotal(e.getValue(), world.total(e.getKey()), by);
        }
    }

    /** The target of a total milestone, the FullHouse setting for Full House and the trigger's target otherwise. */
    private long targetOf(Feat feat) {
        Trigger t = feat.trigger();
        if (FeatRegistry.FULL_HOUSE_ID.equals(feat.id())) {
            return Math.max(1, fullHouse.getAsInt());
        }
        return t.target();
    }

    private void checkTotal(List<Feat> feats, long total, UUID by) {
        for (Feat feat : feats) {
            if (total >= targetOf(feat) && !world.hasMilestone(feat.id())) {
                try {
                    recordMilestone(feat.id(), by);
                } catch (Exception e) {
                    logger.at(Level.WARNING).withCause(e).log("Feats could not record %s", feat.id());
                }
            }
        }
    }

    /** True when at least one feat is defined against the given counter key. */
    public boolean isWatched(String key) {
        return !info(key).watchers().isEmpty();
    }

    /**
     * True when a counter is worth keeping. Totals always are. Per block, item or creature counters
     * are only kept when a feat watches them or a stats row shows them.
     */
    public boolean isPersisted(String key) {
        return info(key).persisted();
    }

    // ---- kill streaks ----------------------------------------------------

    /** The running kill streaks, so the plugin can forget a player who stayed away. */
    public KillStreaks killStreaks() {
        return streaks;
    }

    /**
     * Counts one kill towards the player's running streak and raises their best streak to match. Kills
     * and deaths both follow the pause, so while a player is in Creative mode or a disabled world their
     * streak neither grows nor ends. A player who is not loaded keeps their streak as it is.
     */
    public void countKill(UUID uuid) {
        if (uuid == null || gated(uuid, StatKeys.KILL_STREAK) || !loaded.containsKey(uuid)) {
            return;
        }
        raiseTo(uuid, StatKeys.KILL_STREAK, streaks.kill(uuid));
    }

    /** Ends the player's running kill streak unless they are paused, see {@link #countKill}. Their best streak stays. */
    public void countDeath(UUID uuid) {
        if (uuid == null || gated(uuid, StatKeys.KILL_STREAK)) {
            return;
        }
        streaks.died(uuid);
    }

    /** Adds to a bookkeeping counter that no feat reads, without evaluating anything. */
    public void addSilently(UUID uuid, String key, long amount) {
        if (amount <= 0 || uuid == null) {
            return;
        }
        PlayerProgress progress = loaded.get(uuid);
        if (progress != null) {
            progress.add(key, amount);
        }
    }

    /**
     * Takes up to {@code amount} from a bookkeeping counter and returns how much was taken.
     * Used to cancel out items a player takes back after putting them away.
     */
    public long takeSilently(UUID uuid, String key, long amount) {
        if (amount <= 0 || uuid == null) {
            return 0;
        }
        PlayerProgress progress = loaded.get(uuid);
        if (progress == null) {
            return 0;
        }
        synchronized (progress) {
            long taken = Math.min(amount, progress.get(key));
            if (taken > 0) {
                progress.add(key, -taken);
            }
            return taken;
        }
    }

    /** Marks a one-time discovery that only counts the first time a given id is seen. */
    public void discover(UUID uuid, String prefix, String id, String totalKey) {
        if (uuid == null || id == null || id.isEmpty() || gated(uuid, totalKey)) {
            return;
        }
        PlayerProgress progress = loaded.get(uuid);
        if (progress == null) {
            return;
        }
        String key = prefix + id;
        if (progress.raiseTo(key, 1)) {
            evaluate(uuid, progress, registry.dependingOn(key), null, Source.PLAY);
            progress.add(totalKey, 1);
            evaluate(uuid, progress, registry.dependingOn(totalKey), null, Source.PLAY);
        }
    }

    /**
     * Unlocks every candidate that is now satisfied and adds it to {@code into}, which is created on the
     * first unlock. Call it while holding the progress monitor so the checks and the unlocks see one
     * consistent set of counters.
     */
    private static List<Feat> collectUnlocks(PlayerProgress progress, List<Feat> candidates, long now,
                                             List<Feat> into) {
        for (int i = 0, n = candidates.size(); i < n; i++) {
            Feat feat = candidates.get(i);
            if (progress.isUnlocked(feat.id())) {
                continue;
            }
            if (feat.isSatisfied(progress) && progress.unlock(feat.id(), now)) {
                if (into == null) {
                    into = new ArrayList<>(2);
                }
                into.add(feat);
            }
        }
        return into;
    }

    /** Announces fresh unlocks outside the monitor and follows the meta feats they move. */
    private void finish(UUID uuid, PlayerProgress progress, List<Feat> unlockedNow, long now, Announcements budget,
                        Source source) {
        if (unlockedNow == null) {
            return;
        }
        announce(uuid, progress, unlockedNow, now, budget, source);
        afterUnlocks(uuid, progress, unlockedNow, budget, source);
    }

    /**
     * Unlocks whichever candidates are now satisfied. With a budget only the unlocks it allows are
     * announced, without one every unlock is.
     */
    private void evaluate(UUID uuid, PlayerProgress progress, List<Feat> candidates, Announcements budget,
                          Source source) {
        if (candidates.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Feat> newlyUnlocked;
        synchronized (progress) {
            newlyUnlocked = collectUnlocks(progress, candidates, now, null);
        }
        finish(uuid, progress, newlyUnlocked, now, budget, source);
    }

    // ---- world milestones and server firsts --------------------------------

    /**
     * The first unlock of a personal feat anywhere on the server raises the world and first feats that
     * follow it. The record in the server file is claimed with one atomic check and put, so when two
     * players finish the same fight at once only one of them gets the first. Admin grants and admin
     * stat edits never raise anything, an admin uses {@link #recordMilestone} or {@link #recordFirst}.
     */
    private void raiseServerFeats(UUID uuid, PlayerProgress progress, List<Feat> unlockedNow, Source source) {
        if (world == null || source.admin() || !(isLive(progress) || source.raiseOffline())) {
            return;
        }
        for (Feat feat : unlockedNow) {
            for (Feat follower : registry.triggeredBy(feat.id())) {
                if (follower.scope() == Scope.WORLD ? world.hasMilestone(follower.id()) : world.hasFirst(follower.id())) {
                    continue;
                }
                try {
                    claim(follower, progress, System.currentTimeMillis(), source);
                } catch (Exception e) {
                    logger.at(Level.WARNING).withCause(e).log("Feats could not record %s", follower.id());
                }
            }
        }
    }

    /**
     * Records a world milestone as reached, for a game flag, a personal feat or an admin. The player
     * who raised it, when known, unlocks it with points through the usual path, and every other online
     * player shares in it with a quiet chat line. Players who are offline pick it up on their next load.
     * A null {@code by} records it with nobody named and no points for anyone.
     *
     * <p>Safe from any thread. When {@code by} is not online their file is read and written on the Feats
     * IO thread and the call returns true before that has finished, unless the milestone was already
     * reached. Returns false for an unknown or non world feat id and for a milestone already reached.
     */
    public boolean recordMilestone(String featId, UUID by) {
        return recordMilestone(featId, by, System.currentTimeMillis());
    }

    /** As {@link #recordMilestone(String, UUID)}, dated {@code at}, such as the unlock that raised it. */
    public boolean recordMilestone(String featId, UUID by, long at) {
        Feat feat = featId == null ? null : registry.get(featId);
        if (world == null || feat == null || feat.scope() != Scope.WORLD || world.hasMilestone(feat.id())) {
            return false;
        }
        return record(feat, by, at);
    }

    /**
     * Records a player as the holder of a server first and unlocks it for them through the usual path.
     * Same threading as {@link #recordMilestone}. Returns false for an unknown or non first feat id, a
     * null player and a first someone already holds. Clear it with {@link WorldStore#removeFirst} to
     * hand it to someone else.
     */
    public boolean recordFirst(String featId, UUID by) {
        return recordFirst(featId, by, System.currentTimeMillis());
    }

    /** As {@link #recordFirst(String, UUID)}, dated {@code at}, such as the holder's own unlock. */
    public boolean recordFirst(String featId, UUID by, long at) {
        Feat feat = featId == null ? null : registry.get(featId);
        if (world == null || feat == null || by == null || feat.scope() != Scope.FIRST || world.hasFirst(feat.id())) {
            return false;
        }
        return record(feat, by, at);
    }

    /**
     * Claims the record for an online holder right here. An offline holder is held on the IO thread,
     * gets the unlock silently and their rewards at the next join.
     */
    private boolean record(Feat feat, UUID by, long at) {
        PlayerProgress live = by == null ? null : loaded.get(by);
        if (by == null || live != null) {
            return claim(feat, live, at, Source.PLAY);
        }
        runInBackground(() -> {
            Lease lease;
            try {
                lease = acquire(by);
            } catch (BusyException e) {
                logger.at(Level.WARNING).log("Feats left %s unrecorded for now because %s is busy", feat.id(), by);
                return; // a flag poll or the next scan tries again
            }
            try {
                if (lease != null) {
                    claim(feat, lease.progress(), at, lease.online() ? Source.PLAY : Source.OFFLINE);
                } else if (feat.scope() == Scope.WORLD) {
                    claim(feat, null, at, Source.PLAY); // nobody Feats knows, reached without a name
                }
            } finally {
                release(by, lease);
            }
        });
        return true;
    }

    /**
     * Claims the record, then unlocks the feat for the holder and shares a milestone with everyone else
     * online. Returns false when another call claimed it first.
     */
    private boolean claim(Feat feat, PlayerProgress holder, long now, Source source) {
        UUID by = holder == null ? null : holder.uuid();
        String name = holder == null ? null : nameOf(holder);
        WorldStore.Entry entry = new WorldStore.Entry(now, by, name);
        boolean claimed = feat.scope() == Scope.WORLD
                ? world.putMilestoneIfAbsent(feat.id(), entry)
                : world.putFirstIfAbsent(feat.id(), entry);
        if (!claimed) {
            return false;
        }
        runInBackground(world::save);
        if (feat.scope() == Scope.WORLD) {
            logger.at(Level.INFO).log("Feats server milestone %s reached%s", feat.id(), name == null ? "" : " by " + name);
        } else {
            logger.at(Level.INFO).log("Feats server first %s goes to %s", feat.id(), name);
        }
        try {
            notifier.recordBanner(feat, by, name); // the top of screen banner for everyone online
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Banner failed for %s", feat.id());
        }
        if (holder != null) {
            holder.retainShared(id -> !id.equals(feat.id()));
            force(holder, feat, source, now);
        }
        if (feat.scope() == Scope.WORLD) {
            shareWithOnline(feat, now, by, name);
            witness();
        }
        return true;
    }

    /** Everyone online when a milestone is reached shares in it, see the Social pack's Shared First. */
    private void witness() {
        for (PlayerProgress p : loaded.values()) {
            raiseTo(p.uuid(), StatKeys.MILESTONE_WITNESSED, 1);
        }
    }

    /** Hands a fresh milestone to every online player who did not raise it, with a quiet chat line. */
    private void shareWithOnline(Feat feat, long at, UUID by, String byName) {
        for (PlayerProgress p : loaded.values()) {
            if (p.uuid().equals(by) || p.isUnlocked(feat.id()) || !p.addShared(feat.id(), at)) {
                continue;
            }
            try {
                notifier.sharedMilestone(p.uuid(), feat, byName);
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Shared milestone notice failed for %s", p.uuid());
            }
        }
    }

    private static String nameOf(PlayerProgress p) {
        String name = p.username();
        return name == null || name.isEmpty() ? p.uuid().toString().substring(0, 8) : name;
    }

    /** Tells the listener, the hooks and, budget allowing, the player about fresh unlocks. */
    private void announce(UUID uuid, PlayerProgress progress, List<Feat> unlockedNow, long now, Announcements budget,
                          Source source) {
        UnlockListener listener = unlockListener;
        for (Feat feat : unlockedNow) {
            if (source.deferRewards()) {
                progress.addPendingReward(feat.id()); // handed to the rewards at the next join
            }
            if (listener != null) {
                try {
                    listener.unlocked(progress, feat, now, source.catchUp(), source.admin());
                } catch (Exception e) {
                    logger.at(Level.WARNING).withCause(e).log("Unlock listener failed for %s", feat.id());
                }
            }
            if (!unlockHooks.isEmpty()) {
                Unlock unlock = new Unlock(uuid, progress, feat, now, source.catchUp(), source.admin(), source.rewards());
                for (Consumer<Unlock> hook : unlockHooks) {
                    try {
                        hook.accept(unlock);
                    } catch (Exception e) {
                        logger.at(Level.WARNING).withCause(e).log("Unlock hook failed for %s", feat.id());
                    }
                }
            }
            if (budget != null && !budget.claim()) {
                continue; // over the catch up limit, counted in the summary line instead
            }
            try {
                notifier.unlocked(progress, feat, progress.unlockedCount(), registry.count());
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Unlock notification failed for %s", feat.id());
            }
        }
    }

    /** Points earned so far. */
    public int points(PlayerProgress progress) {
        int sum = 0;
        for (String id : progress.unlockedSnapshot().keySet()) {
            Feat feat = registry.get(id);
            if (feat != null) {
                sum += feat.tier().points();
            }
        }
        return sum;
    }
}
