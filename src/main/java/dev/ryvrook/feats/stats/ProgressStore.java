package dev.ryvrook.feats.stats;

import com.hypixel.hytale.logger.HytaleLogger;
import org.bson.Document;
import org.bson.json.JsonWriterSettings;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * One JSON file per player under {@code <plugin data>/players/}. Loads, saves and deletes for the
 * same player hold the same lock, so a save running on a background thread and a load for that
 * player never overlap.
 */
public final class ProgressStore {
    private static final JsonWriterSettings PRETTY = JsonWriterSettings.builder().indent(true).build();
    private static final int LOCK_STRIPES = 64;

    private final Path directory;
    private final HytaleLogger logger;
    private final Object[] locks = new Object[LOCK_STRIPES];
    /** Lower case username to uuid, filled by one scan of the player files and kept current by loads and saves. */
    private final Map<String, UUID> names = new ConcurrentHashMap<>();
    private volatile boolean namesScanned;

    public ProgressStore(Path dataDirectory, HytaleLogger logger) {
        this.directory = dataDirectory.resolve("players");
        this.logger = logger;
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            logger.at(Level.SEVERE).withCause(e).log("Could not create Feats data directory %s", directory);
        }
    }

    public Path directory() {
        return directory;
    }

    private Path fileFor(UUID uuid) {
        return directory.resolve(uuid + ".json");
    }

    private Object lockFor(UUID uuid) {
        return locks[Math.floorMod(uuid.hashCode(), LOCK_STRIPES)];
    }

    public PlayerProgress load(UUID uuid, String username) {
        synchronized (lockFor(uuid)) {
            Path file = fileFor(uuid);
            if (Files.exists(file)) {
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    PlayerProgress progress = PlayerProgress.fromDocument(uuid, Document.parse(json));
                    progress.setUsername(username);
                    remember(progress);
                    return progress;
                } catch (Exception e) {
                    logger.at(Level.WARNING).withCause(e).log("Corrupt Feats file for %s, starting fresh", uuid);
                    keepCorruptCopy(uuid, file);
                }
            }
            return new PlayerProgress(uuid, username);
        }
    }

    /** Moves a broken file aside, replacing any older broken copy so only the newest one is kept. */
    private void keepCorruptCopy(UUID uuid, Path file) {
        try (DirectoryStream<Path> old = Files.newDirectoryStream(directory, uuid + ".corrupt-*.json")) {
            for (Path stale : old) {
                Files.deleteIfExists(stale);
            }
        } catch (IOException ignored) {
            // best effort
        }
        try {
            Files.move(file, directory.resolve(uuid + ".corrupt-" + System.currentTimeMillis() + ".json"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // best effort
        }
    }

    /** Writes a temp file and moves it over the real one. Returns false and keeps the progress dirty on failure. */
    public boolean save(PlayerProgress progress) {
        synchronized (lockFor(progress.uuid())) {
            Path file = fileFor(progress.uuid());
            Path temp = directory.resolve(progress.uuid() + ".json.tmp");
            try {
                String json = progress.snapshotForSave().toJson(PRETTY);
                Files.writeString(temp, json, StandardCharsets.UTF_8);
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                remember(progress);
                return true;
            } catch (IOException | RuntimeException e) {
                progress.markDirty(); // the snapshot cleared the flag, put it back so the next save retries
                logger.at(Level.SEVERE).withCause(e).log("Could not save Feats progress for %s", progress.uuid());
                return false;
            }
        }
    }

    /**
     * Every player file on disk, with already-loaded (online) players substituted so their
     * in-memory counters win. Used by the leaderboard and cheap for the file counts a server sees.
     */
    public Collection<PlayerProgress> loadAll(Collection<PlayerProgress> loaded) {
        Map<UUID, PlayerProgress> result = new HashMap<>();
        for (PlayerProgress p : loaded) {
            result.put(p.uuid(), p);
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                UUID uuid;
                try {
                    uuid = UUID.fromString(name.substring(0, name.length() - ".json".length()));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (result.containsKey(uuid)) {
                    continue;
                }
                try {
                    String json = Files.readString(file, StandardCharsets.UTF_8);
                    PlayerProgress progress = PlayerProgress.fromDocument(uuid, Document.parse(json));
                    remember(progress);
                    result.put(uuid, progress);
                } catch (Exception e) {
                    logger.at(Level.FINE).withCause(e).log("Skipping unreadable Feats file %s", file);
                }
            }
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Could not list Feats player files");
        }
        return result.values();
    }

    public void delete(UUID uuid) {
        synchronized (lockFor(uuid)) {
            try {
                Files.deleteIfExists(fileFor(uuid));
            } catch (IOException e) {
                logger.at(Level.WARNING).withCause(e).log("Could not delete Feats progress for %s", uuid);
            }
        }
    }

    /**
     * Writes an empty file for a player Feats has never seen, so the tracker can hold it like any other
     * offline player. Returns false when a file already exists or the write failed.
     */
    public boolean createIfAbsent(UUID uuid) {
        synchronized (lockFor(uuid)) {
            if (exists(uuid)) {
                return false;
            }
            return save(new PlayerProgress(uuid, null));
        }
    }

    /** The uuid of every player file on disk. Lists the directory, so run it on the Feats IO thread. */
    public Set<UUID> knownPlayers() {
        Set<UUID> found = new HashSet<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                try {
                    found.add(UUID.fromString(name.substring(0, name.length() - ".json".length())));
                } catch (IllegalArgumentException e) {
                    // a corrupt copy or another file, not a player
                }
            }
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Could not list Feats player files");
        }
        return found;
    }

    /** True when a player file exists for this uuid. */
    public boolean exists(UUID uuid) {
        return Files.exists(fileFor(uuid));
    }

    /**
     * Finds a player who has a file here by their last known name, ignoring case. The first call scans
     * every player file, so run it on the Feats IO thread. Returns null when nobody matches.
     */
    public UUID findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        UUID known = names.get(key);
        if (known != null && exists(known)) {
            return known;
        }
        if (!namesScanned) {
            scanNames();
            known = names.get(key);
        }
        return known;
    }

    /**
     * Reads the stored name of every player file once. The newest file wins when two share a name, and
     * a name already learnt from a load or save since start wins over the scan.
     */
    private void scanNames() {
        Map<String, UUID> found = new HashMap<>();
        Map<String, Long> newest = new HashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                String fileName = file.getFileName().toString();
                UUID uuid;
                try {
                    uuid = UUID.fromString(fileName.substring(0, fileName.length() - ".json".length()));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                try {
                    Document doc = Document.parse(Files.readString(file, StandardCharsets.UTF_8));
                    String stored = doc.getString("Username");
                    if (stored == null || stored.isEmpty()) {
                        continue;
                    }
                    String key = stored.toLowerCase(Locale.ROOT);
                    long modified = Files.getLastModifiedTime(file).toMillis();
                    Long seen = newest.get(key);
                    if (seen == null || modified > seen) {
                        newest.put(key, modified);
                        found.put(key, uuid);
                    }
                } catch (Exception e) {
                    logger.at(Level.FINE).withCause(e).log("Skipping unreadable Feats file %s", file);
                }
            }
            found.forEach(names::putIfAbsent);
            namesScanned = true;
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Could not list Feats player files");
        }
    }

    private void remember(PlayerProgress progress) {
        String name = progress.username();
        if (name != null && !name.isEmpty()) {
            names.put(name.toLowerCase(Locale.ROOT), progress.uuid());
        }
    }
}
