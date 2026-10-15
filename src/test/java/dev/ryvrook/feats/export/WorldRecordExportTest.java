package dev.ryvrook.feats.export;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.api.WorldRecord;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.stats.WorldStore;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.UnlockNotifier;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRecordExportTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Test
    void milestonesAndFirstsCarryTheHiddenFlagNextToTheOldFields() throws Exception {
        Path dir = Files.createTempDirectory("feats-export-world");
        FeatRegistry registry = new FeatRegistry();
        ProgressStore store = new ProgressStore(dir, LOGGER);
        WorldStore world = new WorldStore(dir, LOGGER);
        UUID holder = UUID.randomUUID();
        world.putMilestoneIfAbsent("world_gateway", new WorldStore.Entry(1_000L, holder, "Builder"));
        world.putFirstIfAbsent("first_gobliterator", new WorldStore.Entry(2_000L, holder, "Builder"));
        FeatsTracker tracker = new FeatsTracker(registry, store, new UnlockNotifier(() -> null, LOGGER), LOGGER,
                Runnable::run, world);
        new LeaderboardExporter(tracker, store, dir, LOGGER).flush(0);

        Document root = Document.parse(Files.readString(dir.resolve("export").resolve("feats.json"), StandardCharsets.UTF_8));
        Document milestone = root.get("milestones", Document.class).get("world_gateway", Document.class);
        Document first = root.get("firsts", Document.class).get("first_gobliterator", Document.class);
        for (Document row : new Document[]{milestone, first}) {
            assertTrue(row.containsKey("at") && row.containsKey("by") && row.containsKey("byName")
                    && row.containsKey("featName") && row.containsKey("tier"), row.toJson());
            assertTrue(row.get("hidden") instanceof Boolean, row.toJson());
        }
        assertEquals(registry.get("world_gateway").hidden(), milestone.getBoolean("hidden"));
        assertEquals(registry.get("first_gobliterator").hidden(), first.getBoolean("hidden"));
        assertEquals(1_000L, milestone.get("at", Number.class).longValue());
        assertEquals("Builder", first.getString("byName"));
    }

    @Test
    void theOldWorldRecordConstructorReadsAsNotHidden() {
        WorldRecord old = new WorldRecord("world_gateway", 1L, null, null);
        assertFalse(old.hidden());
        assertTrue(new WorldRecord("x", 1L, null, null, true).hidden());
    }
}
