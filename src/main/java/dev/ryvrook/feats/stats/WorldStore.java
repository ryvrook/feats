package dev.ryvrook.feats.stats;

import com.hypixel.hytale.logger.HytaleLogger;
import org.bson.Document;
import org.bson.json.JsonWriterSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Server wide feat records in {@code <plugin data>/server.json}. Holds the world milestones that were
 * reached, with the player who raised each one when known, and the holders of the server firsts.
 *
 * <p>Kept in memory and safe from any thread. {@link #load} and {@link #save} touch the disk, so run
 * them on the Feats IO thread, or inline at shutdown. The file is replaced atomically.
 *
 * <pre>
 * {"version": 1,
 *  "milestones": {"world_power": {"at": 1760000000000, "by": null, "byName": null}},
 *  "firsts": {"first_gobliterator": {"at": 1760000000000, "by": "uuid", "byName": "Name"}}}
 * </pre>
 */
public final class WorldStore {
    private static final JsonWriterSettings PRETTY = JsonWriterSettings.builder().indent(true).build();

    /**
     * One record. {@code by} and {@code byName} are null for a milestone nobody raised that Feats
     * could see, never null for a first.
     */
    public record Entry(long at, UUID by, String byName) {
    }

    private final Path file;
    private final Path temp;
    private final HytaleLogger logger;
    private final Map<String, Entry> milestones = new LinkedHashMap<>();
    private final Map<String, Entry> firsts = new LinkedHashMap<>();
    private final Object fileLock = new Object();
    private volatile boolean dirty;

    public WorldStore(Path dataDirectory, HytaleLogger logger) {
        this.file = dataDirectory.resolve("server.json");
        this.temp = dataDirectory.resolve("server.json.tmp");
        this.logger = logger;
    }

    public Path file() {
        return file;
    }

    // ---- reads -----------------------------------------------------------

    /** The milestone record of a world feat, or null when it has not been reached. */
    public synchronized Entry milestone(String featId) {
        return featId == null ? null : milestones.get(featId);
    }

    /** The holder of a first feat, or null when nobody has claimed it. */
    public synchronized Entry first(String featId) {
        return featId == null ? null : firsts.get(featId);
    }

    public synchronized boolean hasMilestone(String featId) {
        return featId != null && milestones.containsKey(featId);
    }

    public synchronized boolean hasFirst(String featId) {
        return featId != null && firsts.containsKey(featId);
    }

    /** Every reached milestone, oldest first. Unmodifiable copy. */
    public synchronized Map<String, Entry> milestones() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(milestones));
    }

    /** Every claimed first, oldest first. Unmodifiable copy. */
    public synchronized Map<String, Entry> firsts() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(firsts));
    }

    // ---- writes, in memory only. Call save on the IO thread afterwards ------

    /** Records a milestone unless it was already reached. Returns true when this call recorded it. */
    public synchronized boolean putMilestoneIfAbsent(String featId, Entry entry) {
        if (featId == null || entry == null || milestones.containsKey(featId)) {
            return false;
        }
        milestones.put(featId, entry);
        dirty = true;
        return true;
    }

    /** Records a first unless someone holds it already. Returns true when this call recorded it. */
    public synchronized boolean putFirstIfAbsent(String featId, Entry entry) {
        if (featId == null || entry == null || entry.by() == null || firsts.containsKey(featId)) {
            return false;
        }
        firsts.put(featId, entry);
        dirty = true;
        return true;
    }

    /** Forgets a milestone, for an admin. Returns false when it was not recorded. */
    public synchronized boolean removeMilestone(String featId) {
        if (featId == null || milestones.remove(featId) == null) {
            return false;
        }
        dirty = true;
        return true;
    }

    /** Forgets the holder of a first, for an admin. Returns false when nobody held it. */
    public synchronized boolean removeFirst(String featId) {
        if (featId == null || firsts.remove(featId) == null) {
            return false;
        }
        dirty = true;
        return true;
    }

    /** Forgets the holder of a first only when it is {@code by}. Returns true when this call cleared it. */
    public synchronized boolean removeFirstIfHeldBy(String featId, UUID by) {
        Entry held = featId == null ? null : firsts.get(featId);
        if (held == null || by == null || !by.equals(held.by())) {
            return false;
        }
        firsts.remove(featId);
        dirty = true;
        return true;
    }

    public boolean isDirty() {
        return dirty;
    }

    // ---- disk --------------------------------------------------------------

    /** Reads the file, replacing what is in memory. A missing file means nothing reached yet. */
    public void load() {
        synchronized (fileLock) {
            if (!Files.exists(file)) {
                return;
            }
            Document doc;
            try {
                doc = Document.parse(Files.readString(file, StandardCharsets.UTF_8));
            } catch (Exception e) {
                logger.at(Level.WARNING).withCause(e).log("Corrupt Feats server file %s, starting fresh", file);
                try {
                    Files.move(file, file.resolveSibling("server.corrupt-" + System.currentTimeMillis() + ".json"),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // best effort
                }
                return;
            }
            synchronized (this) {
                milestones.clear();
                firsts.clear();
                read(doc.get("milestones"), milestones, false);
                read(doc.get("firsts"), firsts, true);
                dirty = false;
            }
        }
    }

    private static void read(Object section, Map<String, Entry> into, boolean needsHolder) {
        if (!(section instanceof Document doc)) {
            return;
        }
        for (Map.Entry<String, Object> e : doc.entrySet()) {
            if (!(e.getValue() instanceof Document row) || !(row.get("at") instanceof Number at)) {
                continue;
            }
            UUID by = null;
            if (row.get("by") instanceof String s && !s.isEmpty()) {
                try {
                    by = UUID.fromString(s);
                } catch (IllegalArgumentException ignored) {
                    // keep it without a holder
                }
            }
            if (needsHolder && by == null) {
                continue;
            }
            String name = row.get("byName") instanceof String n && !n.isEmpty() ? n : null;
            into.put(e.getKey(), new Entry(at.longValue(), by, by == null ? null : name));
        }
    }

    /** Writes a temp file and moves it over the real one. Returns false and stays dirty on failure. */
    public boolean save() {
        synchronized (fileLock) {
            Document doc;
            synchronized (this) {
                doc = new Document()
                        .append("version", 1)
                        .append("milestones", write(milestones))
                        .append("firsts", write(firsts));
                dirty = false;
            }
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(temp, doc.toJson(PRETTY), StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } catch (IOException | RuntimeException e) {
                dirty = true;
                logger.at(Level.SEVERE).withCause(e).log("Could not save the Feats server file %s", file);
                return false;
            }
        }
    }

    private static Document write(Map<String, Entry> rows) {
        Document out = new Document();
        for (Map.Entry<String, Entry> e : rows.entrySet()) {
            Entry v = e.getValue();
            out.append(e.getKey(), new Document()
                    .append("at", v.at())
                    .append("by", v.by() == null ? null : v.by().toString())
                    .append("byName", v.byName()));
        }
        return out;
    }
}
