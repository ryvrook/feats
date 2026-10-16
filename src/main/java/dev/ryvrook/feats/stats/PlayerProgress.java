package dev.ryvrook.feats.stats;

import org.bson.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Everything Feats remembers about one player, which is raw counters plus unlocked feat ids.
 * Instances are shared between world threads, so all mutation is synchronized.
 *
 * <p>Counters are kept as mutable holders rather than boxed longs, so bumping a counter that already
 * exists allocates nothing. Callers that touch several counters for one event can hold the monitor of
 * this object around the whole batch, every method here locks on it.
 */
public final class PlayerProgress {
    private final UUID uuid;
    private String username;
    private final Map<String, Counter> stats = new HashMap<>();
    private final Map<String, Long> unlocked = new LinkedHashMap<>();
    /**
     * Server milestones this player shares in without having raised them, feat id to when the milestone
     * was reached. Never counted in points, unlocks, completion or rarity.
     */
    private final Map<String, Long> shared = new LinkedHashMap<>();
    /** Rewards already handed out, as feat:id, points:at and count:at. Survives a reset so rewards cannot be farmed. */
    private final Set<String> rewarded = new LinkedHashSet<>();
    /**
     * Feats unlocked while the player was offline, by a scan or a backfilled record, whose rewards wait
     * for the next join. Cleared once handed to the rewards, and by a reset.
     */
    private final Set<String> pendingRewards = new LinkedHashSet<>();
    /** Other players seen online at the same time, oldest first, at most {@link #MET_CAP}. */
    private final Set<UUID> met = new LinkedHashSet<>();
    /** Past this the met set stops growing, which is far above any feat target. */
    public static final int MET_CAP = 2_000;
    /** Epoch millis of the first time Feats saw the player. Set on first load, so for a file from before 1.7.2 it is the upgrade. */
    private long firstSeen;
    /** The local date of the last counted join as yyyymmdd, for the join streak. 0 before the first. */
    private int lastJoinDay;
    /** The feat id shown as this player's title, or null for none. */
    private String title;
    /** False when the player muted their own toasts, sounds and big titles with /feats notify off. */
    private boolean notify = true;
    /**
     * Epoch millis the player's current session began, or 0 when they are not online. Only read back
     * when Feats reloads while the player stays connected, so a reload does not split their session. A
     * normal join always starts afresh, which also clears a value left behind by a crash.
     */
    private long sessionStart;
    private volatile boolean dirty;
    /** Tells a feat id that the running registry knows. Unlocks of removed or parked feats stay in the file but are not counted. */
    private static volatile java.util.function.Predicate<String> knownFeat = id -> true;
    /** Tells a world or first feat id, which never moves the feats unlocked counter. */
    private static volatile java.util.function.Predicate<String> serverFeat = id -> false;

    public PlayerProgress(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username == null ? "" : username;
    }

    public UUID uuid() {
        return uuid;
    }

    public synchronized String username() {
        return username;
    }

    public synchronized void setUsername(String username) {
        if (username != null && !username.equals(this.username)) {
            this.username = username;
            dirty = true;
        }
    }

    /** One counter value. Only touched under the progress monitor. */
    private static final class Counter {
        long value;

        Counter(long value) {
            this.value = value;
        }
    }

    public synchronized long get(String key) {
        Counter c = stats.get(key);
        return c == null ? 0L : c.value;
    }

    /** Adds to a counter and returns the new value. */
    public synchronized long add(String key, long amount) {
        if (amount == 0) {
            return get(key);
        }
        Counter c = stats.get(key);
        dirty = true;
        if (c == null) {
            stats.put(key, new Counter(amount));
            return amount;
        }
        c.value += amount;
        return c.value;
    }

    /** Raises a counter to at least {@code value} and returns true if it changed. */
    public synchronized boolean raiseTo(String key, long value) {
        Counter c = stats.get(key);
        long current = c == null ? 0L : c.value;
        if (value > current) {
            if (c == null) {
                stats.put(key, new Counter(value));
            } else {
                c.value = value;
            }
            dirty = true;
            return true;
        }
        return false;
    }

    public synchronized boolean isUnlocked(String featId) {
        return unlocked.containsKey(featId);
    }

    /** Records an unlock and returns false if it was already unlocked. */
    public synchronized boolean unlock(String featId, long epochMillis) {
        if (unlocked.containsKey(featId)) {
            return false;
        }
        unlocked.put(featId, epochMillis);
        dirty = true;
        return true;
    }

    /**
     * Removes an unlock and takes one off the feats unlocked counter. Clears the title when it was this
     * feat. Returns false when the feat was not unlocked.
     */
    public synchronized boolean revoke(String featId) {
        if (unlocked.remove(featId) == null) {
            return false;
        }
        Counter count = stats.get(StatKeys.FEATS_UNLOCKED);
        if (count != null && count.value > 0 && !serverFeat.test(featId)) {
            count.value--;
        }
        if (featId.equals(title)) {
            title = null;
        }
        dirty = true;
        return true;
    }

    /** Sets a counter to an exact value. Negative values become zero. */
    public synchronized void set(String key, long value) {
        Counter c = stats.get(key);
        if (c == null) {
            stats.put(key, new Counter(Math.max(0L, value)));
        } else {
            c.value = Math.max(0L, value);
        }
        dirty = true;
    }

    /** The chosen title feat id, or null when the player has none. */
    public synchronized String title() {
        return title;
    }

    /** Sets the title feat id. Null or an empty string clears it. */
    public synchronized void setTitle(String featId) {
        String next = featId == null || featId.isEmpty() ? null : featId;
        if (!Objects.equals(next, title)) {
            title = next;
            dirty = true;
        }
    }

    /** When the current session began in epoch millis, 0 when none is recorded. */
    public synchronized long sessionStart() {
        return sessionStart;
    }

    /** Records when the current session began, 0 to clear it once the player leaves. */
    public synchronized void setSessionStart(long epochMillis) {
        long next = Math.max(0L, epochMillis);
        if (next != sessionStart) {
            sessionStart = next;
            dirty = true;
        }
    }

    /** True unless the player muted their own unlock toasts, sounds and big titles. */
    public synchronized boolean notifications() {
        return notify;
    }

    public synchronized void setNotifications(boolean on) {
        if (notify != on) {
            notify = on;
            dirty = true;
        }
    }

    public synchronized boolean hasRewarded(String key) {
        return rewarded.contains(key);
    }

    /** Records a reward as handed out. Returns false when it already was. */
    public synchronized boolean markRewarded(String key) {
        if (rewarded.add(key)) {
            dirty = true;
            return true;
        }
        return false;
    }

    /** Queues the rewards of a feat unlocked while offline for the next join. */
    public synchronized void addPendingReward(String featId) {
        if (featId != null && pendingRewards.add(featId)) {
            dirty = true;
        }
    }

    /** The feats whose rewards wait for this join, oldest first, and forgets them. */
    public synchronized List<String> drainPendingRewards() {
        if (pendingRewards.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(pendingRewards);
        pendingRewards.clear();
        dirty = true;
        return out;
    }

    public synchronized Set<String> rewardedSnapshot() {
        return Set.copyOf(rewarded);
    }

    public synchronized long firstSeen() {
        return firstSeen;
    }

    /** Sets the first seen time once. Returns true when this call set it. */
    public synchronized boolean setFirstSeenIfAbsent(long epochMillis) {
        if (firstSeen > 0 || epochMillis <= 0) {
            return false;
        }
        firstSeen = epochMillis;
        dirty = true;
        return true;
    }

    public synchronized int lastJoinDay() {
        return lastJoinDay;
    }

    public synchronized void setLastJoinDay(int yyyymmdd) {
        if (lastJoinDay != yyyymmdd) {
            lastJoinDay = yyyymmdd;
            dirty = true;
        }
    }

    /** Remembers another player seen online. Returns true when they were new and the set was not full. */
    public synchronized boolean meet(UUID other) {
        if (other == null || other.equals(uuid) || met.size() >= MET_CAP || met.contains(other)) {
            return false;
        }
        met.add(other);
        dirty = true;
        return true;
    }

    public synchronized int metCount() {
        return met.size();
    }

    public synchronized boolean hasMet(UUID other) {
        return other != null && met.contains(other);
    }

    public synchronized Long unlockedAt(String featId) {
        return unlocked.get(featId);
    }

    /**
     * Set once by the registry so counts ignore unlocks of feats that no longer exist, and of world and
     * first feats, which are not part of the totals.
     */
    public static void knownFeats(java.util.function.Predicate<String> known) {
        knownFeat = known == null ? id -> true : known;
    }

    /** Set once by the registry, tells the world and first feat ids. */
    public static void serverFeats(java.util.function.Predicate<String> server) {
        serverFeat = server == null ? id -> false : server;
    }

    /** True for a world or first feat id of the running registry. */
    public static boolean isServerFeat(String featId) {
        return featId != null && serverFeat.test(featId);
    }

    // ---- shared server milestones ----------------------------------------

    /** Records a server milestone this player shares in. Returns false when already shared. */
    public synchronized boolean addShared(String featId, long epochMillis) {
        if (featId == null || shared.containsKey(featId)) {
            return false;
        }
        shared.put(featId, epochMillis);
        dirty = true;
        return true;
    }

    public synchronized boolean isShared(String featId) {
        return shared.containsKey(featId);
    }

    /** When the shared milestone was reached, or null when this player does not share it. */
    public synchronized Long sharedAt(String featId) {
        return shared.get(featId);
    }

    public synchronized Map<String, Long> sharedSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(shared));
    }

    /** Drops shared milestones the filter rejects, such as one an admin cleared. Returns true when any went. */
    public synchronized boolean retainShared(Predicate<String> keep) {
        boolean removed = shared.keySet().removeIf(id -> !keep.test(id));
        if (removed) {
            dirty = true;
        }
        return removed;
    }

    /** Number of unlocked personal feats the running registry still defines. */
    public synchronized int unlockedCount() {
        java.util.function.Predicate<String> known = knownFeat;
        int n = 0;
        for (String id : unlocked.keySet()) {
            if (known.test(id)) {
                n++;
            }
        }
        return n;
    }

    public synchronized Map<String, Long> statsSnapshot() {
        Map<String, Long> copy = new HashMap<>(Math.max(16, stats.size() * 2));
        stats.forEach((key, c) -> copy.put(key, c.value));
        return Collections.unmodifiableMap(copy);
    }

    public synchronized Map<String, Long> unlockedSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(unlocked));
    }

    public boolean isDirty() {
        return dirty;
    }

    /**
     * Takes the document to write and clears the dirty flag in one step, so an increment that lands
     * while the file is being written marks the progress dirty again instead of being lost.
     */
    public synchronized Document snapshotForSave() {
        Document doc = toDocument();
        dirty = false;
        return doc;
    }

    /** Flags the progress for the next save, used when a write failed. */
    public void markDirty() {
        dirty = true;
    }

    /**
     * Drops every counter the filter rejects. Returns true and marks the progress dirty when anything
     * was removed, so the trimmed version reaches disk on the next save.
     */
    public synchronized boolean retainStats(Predicate<String> keep) {
        boolean removed = stats.keySet().removeIf(key -> !keep.test(key));
        if (removed) {
            dirty = true;
        }
        return removed;
    }

    /**
     * Wipes counters, unlocks, shared milestones and the title. The rewarded set and the notification choice stay, so a
     * reset cannot earn the same rewards twice.
     */
    public synchronized void reset() {
        stats.clear();
        unlocked.clear();
        shared.clear(); // refilled from the server milestones by the tracker
        pendingRewards.clear(); // the feats went with the reset, earning them again rewards them then
        met.clear();
        lastJoinDay = 0; // firstSeen stays, the player did first arrive when they did
        title = null;
        dirty = true;
    }

    // ---- serialization -------------------------------------------------

    public synchronized Document toDocument() {
        Document statsDoc = new Document();
        stats.forEach((key, c) -> statsDoc.append(key, c.value));
        Document unlockedDoc = new Document();
        unlocked.forEach(unlockedDoc::append);
        Document doc = new Document()
                .append("Version", 1)
                .append("Uuid", uuid.toString())
                .append("Username", username)
                .append("Stats", statsDoc)
                .append("Unlocked", unlockedDoc);
        if (!shared.isEmpty()) {
            Document sharedDoc = new Document();
            shared.forEach(sharedDoc::append);
            doc.append("Shared", sharedDoc);
        }
        if (!rewarded.isEmpty()) {
            doc.append("Rewarded", new ArrayList<>(rewarded));
        }
        if (!pendingRewards.isEmpty()) {
            doc.append("PendingRewards", new ArrayList<>(pendingRewards));
        }
        if (title != null) {
            doc.append("Title", title);
        }
        if (sessionStart > 0) {
            doc.append("SessionStart", sessionStart);
        }
        if (!notify) {
            doc.append("Notify", false);
        }
        if (firstSeen > 0) {
            doc.append("FirstSeen", firstSeen);
        }
        if (lastJoinDay > 0) {
            doc.append("LastJoinDay", lastJoinDay);
        }
        if (!met.isEmpty()) {
            List<String> ids = new ArrayList<>(met.size());
            for (UUID m : met) {
                ids.add(m.toString());
            }
            doc.append("Met", ids);
        }
        return doc;
    }

    public static PlayerProgress fromDocument(UUID uuid, Document doc) {
        PlayerProgress progress = new PlayerProgress(uuid, doc.getString("Username"));
        Object statsObj = doc.get("Stats");
        if (statsObj instanceof Document statsDoc) {
            for (Map.Entry<String, Object> e : statsDoc.entrySet()) {
                if (e.getValue() instanceof Number n) {
                    progress.stats.put(e.getKey(), new Counter(n.longValue()));
                }
            }
        }
        Object unlockedObj = doc.get("Unlocked");
        if (unlockedObj instanceof Document unlockedDoc) {
            for (Map.Entry<String, Object> e : unlockedDoc.entrySet()) {
                if (e.getValue() instanceof Number n) {
                    progress.unlocked.put(e.getKey(), n.longValue());
                }
            }
        }
        if (doc.get("Shared") instanceof Document sharedDoc) {
            for (Map.Entry<String, Object> e : sharedDoc.entrySet()) {
                if (e.getValue() instanceof Number n) {
                    progress.shared.put(e.getKey(), n.longValue());
                }
            }
        }
        if (doc.get("Rewarded") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String key && !key.isEmpty()) {
                    progress.rewarded.add(key);
                }
            }
        }
        if (doc.get("PendingRewards") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String id && !id.isEmpty()) {
                    progress.pendingRewards.add(id);
                }
            }
        }
        if (doc.get("Title") instanceof String t && !t.isEmpty()) {
            progress.title = t;
        }
        if (doc.get("SessionStart") instanceof Number n && n.longValue() > 0) {
            progress.sessionStart = n.longValue();
        }
        if (doc.get("Notify") instanceof Boolean b) {
            progress.notify = b;
        }
        if (doc.get("FirstSeen") instanceof Number n && n.longValue() > 0) {
            progress.firstSeen = n.longValue();
        }
        if (doc.get("LastJoinDay") instanceof Number n && n.intValue() > 0) {
            progress.lastJoinDay = n.intValue();
        }
        if (doc.get("Met") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String id && progress.met.size() < MET_CAP) {
                    try {
                        progress.met.add(UUID.fromString(id));
                    } catch (IllegalArgumentException ignored) {
                        // skip a bad entry
                    }
                }
            }
        }
        progress.dirty = false;
        return progress;
    }
}
