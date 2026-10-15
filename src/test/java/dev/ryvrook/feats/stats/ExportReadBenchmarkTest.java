package dev.ryvrook.feats.stats;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The read the leaderboard export and the rarity scan make once a minute, over 2,000 player files with 150
 * counters and 120 unlocks each. Prints the time of a first read and of a read with nothing changed, and
 * checks that a file saved in between is read again. The numbers also land in build/benchmark-export.txt.
 */
class ExportReadBenchmarkTest {
    private static final int PLAYERS = 2_000;

    @Test
    void secondReadOfUnchangedFiles() throws IOException {
        HytaleLogger logger = HytaleLogger.forEnclosingClass();
        Path dir = Files.createTempDirectory("feats-export-bench");
        ProgressStore store = new ProgressStore(dir, logger);
        List<Feat> feats = new FeatRegistry().personal();
        List<UUID> ids = new ArrayList<>();
        for (int p = 0; p < PLAYERS; p++) {
            UUID uuid = UUID.randomUUID();
            ids.add(uuid);
            PlayerProgress progress = new PlayerProgress(uuid, "Player" + p);
            for (int k = 0; k < 150; k++) {
                progress.set("kill:Creature_" + k, k * 7L + p);
            }
            for (int f = 0; f < 120; f++) {
                progress.unlock(feats.get((f + p) % feats.size()).id(), 1_700_000_000_000L + f);
            }
            store.save(progress);
        }
        long first = time(() -> assertEquals(PLAYERS, store.loadAll(List.of()).size()));
        long again = time(() -> assertEquals(PLAYERS, store.loadAll(List.of()).size()));

        PlayerProgress changed = store.load(ids.get(7), "Player7");
        changed.set("kill:Creature_0", 999_999);
        store.save(changed);
        Collection<PlayerProgress> after = store.loadAll(List.of());
        long seen = after.stream().filter(p -> p.uuid().equals(ids.get(7))).findFirst().orElseThrow()
                .get("kill:Creature_0");
        assertEquals(999_999, seen, "a file saved since the last read is read again");
        Files.delete(dir.resolve("players").resolve(ids.get(9) + ".json"));
        assertEquals(PLAYERS - 1, store.loadAll(List.of()).size(), "a deleted file drops out");

        String line = String.format(Locale.ROOT, "Feats export read of %,d player files, first %,d ms, unchanged %,d ms%n",
                PLAYERS, first / 1_000_000, again / 1_000_000);
        if (Boolean.getBoolean("feats.bench")) {
            System.out.print(line);
        }
        Path out = Path.of("build", "benchmark-export.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, line);
    }

    private static long time(Runnable task) {
        long start = System.nanoTime();
        task.run();
        return System.nanoTime() - start;
    }
}
