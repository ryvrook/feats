package dev.ryvrook.feats.stats;

import com.hypixel.hytale.logger.HytaleLogger;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The export read skips unchanged files. A file edited by hand is read again, even with an older time on it. */
class HandEditReadTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static long read(ProgressStore store, UUID uuid) {
        return store.loadAll(List.of()).stream().filter(p -> p.uuid().equals(uuid)).findFirst().orElseThrow()
                .get("deaths");
    }

    @Test
    void aFileEditedByHandWithAnOlderTimeIsReadAgain() throws Exception {
        Path dir = Files.createTempDirectory("feats-hand-edit");
        ProgressStore store = new ProgressStore(dir, LOGGER);
        UUID uuid = UUID.randomUUID();
        PlayerProgress progress = new PlayerProgress(uuid, "Editor");
        progress.set("deaths", 12);
        store.save(progress);
        assertEquals(12, read(store, uuid));

        Path file = dir.resolve("players").resolve(uuid + ".json");
        FileTime before = Files.getLastModifiedTime(file);
        String json = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, json.replaceFirst("(\"deaths\"\\s*:\\s*)12", "$134"), StandardCharsets.UTF_8); // same size, written in place
        Files.setLastModifiedTime(file, FileTime.fromMillis(before.toMillis() - 86_400_000L));
        assertEquals(34, read(store, uuid));

        Files.writeString(file, json.replaceFirst("(\"deaths\"\\s*:\\s*)12", "$11234"), StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, FileTime.fromMillis(before.toMillis() - 2 * 86_400_000L));
        assertEquals(1234, read(store, uuid));
    }
}
