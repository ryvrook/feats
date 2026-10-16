package dev.ryvrook.feats.export;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.Category;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Pack;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.stats.WorldStore;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.UnlockNotifier;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 1.7.2 additions to export/feats.json sit next to the old fields, which keep their names and order. */
class ExportFieldsTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final List<String> OLD_TOP_LEVEL = List.of("updatedAt", "totalFeats", "totalPoints", "online",
            "rarityPlayers", "rarity", "players", "recent", "milestones", "firsts");

    private record Written(Document root, FeatRegistry registry) {
    }

    private Written export(FeatRegistry registry, boolean withTotals) throws Exception {
        Path dir = Files.createTempDirectory("feats-export-fields");
        ProgressStore store = new ProgressStore(dir, LOGGER);
        WorldStore world = new WorldStore(dir, LOGGER);
        if (withTotals) {
            world.addTotal("blocks_broken", 1_234L);
            world.addTotal("creatures_killed", 56L);
        }
        FeatsTracker tracker = new FeatsTracker(registry, store, new UnlockNotifier(() -> null, LOGGER), LOGGER,
                Runnable::run, world);
        PlayerProgress p = new PlayerProgress(UUID.randomUUID(), "Ryv");
        p.unlock(registry.personal().getFirst().id(), 5_000L);
        store.save(p);
        new LeaderboardExporter(tracker, store, dir, LOGGER).flush(3);
        String json = Files.readString(dir.resolve("export").resolve("feats.json"), StandardCharsets.UTF_8);
        return new Written(Document.parse(json), registry);
    }

    @Test
    void theOldTopLevelFieldsKeepTheirOrderAndTheNewOnesFollow() throws Exception {
        Document root = export(new FeatRegistry(), true).root();
        List<String> keys = new ArrayList<>(root.keySet());
        assertEquals(OLD_TOP_LEVEL, keys.subList(0, OLD_TOP_LEVEL.size()));
        assertEquals(List.of("packs", "siblings", "totals", "feats"), keys.subList(OLD_TOP_LEVEL.size(), keys.size()));
    }

    @Test
    void packsSiblingsAndTotalsAreWritten() throws Exception {
        FeatRegistry registry = new FeatRegistry(EnumSet.of(Sibling.COINAGE, Sibling.DOMINION),
                EnumSet.of(Pack.SOCIAL, Pack.GRIND));
        Document root = export(registry, true).root();
        assertEquals(List.of("SOCIAL", "GRIND"), root.getList("packs", String.class));
        assertEquals(List.of("Coinage", "Dominion"), root.getList("siblings", String.class));
        Document totals = root.get("totals", Document.class);
        assertEquals(1_234L, totals.get("blocks_broken", Number.class).longValue());
        assertEquals(56L, totals.get("creatures_killed", Number.class).longValue());
    }

    @Test
    void anEmptyServerWritesEmptyListsNotNulls() throws Exception {
        Document root = export(new FeatRegistry(), false).root();
        assertEquals(List.of(), root.getList("packs", String.class));
        assertEquals(List.of(), root.getList("siblings", String.class));
        assertTrue(root.get("totals", Document.class).isEmpty());
    }

    @Test
    void everyFeatRowCarriesBannerPackAndRequires() throws Exception {
        FeatRegistry registry = new FeatRegistry(EnumSet.allOf(Sibling.class), EnumSet.allOf(Pack.class));
        Document root = export(registry, false).root();
        List<Document> feats = root.getList("feats", Document.class);
        assertEquals(registry.countAll(), feats.size());
        for (Document row : feats) {
            Feat feat = registry.get(row.getString("id"));
            assertNotNull(feat, row.toJson());
            assertEquals(feat.name(), row.getString("name"));
            assertEquals(feat.tier().displayName(), row.getString("tier"));
            assertEquals(feat.category().displayName(), row.getString("category"));
            assertEquals(feat.scope().name(), row.getString("scope"));
            assertEquals(feat.hidden(), row.getBoolean("hidden"));
            assertEquals(feat.banner(), row.getBoolean("banner"));
            assertTrue(row.containsKey("pack"), row.toJson());
            if (feat.pack() == null) {
                assertNull(row.get("pack"));
            } else {
                assertEquals(feat.pack().name(), row.getString("pack"));
            }
            List<String> requires = row.getList("requires", String.class);
            assertEquals(feat.requires().size(), requires.size());
            for (Sibling s : feat.requires()) {
                assertTrue(requires.contains(s.pluginName()), row.toJson());
            }
        }
    }

    /** The flag fields on feats built by hand, since the built in list may carry none of them yet. */
    @Test
    void theFlagFieldsSerialiseBannerPackAndRequires() {
        Feat plain = new FeatRegistry().personal().getFirst();
        Feat banner = plain.withBanner();
        Feat social = new Feat(plain.id(), plain.name(), plain.description(), Category.SOCIAL, plain.tier(), plain.icon(),
                plain.requirements(), plain.hidden(), plain.scope(), plain.trigger(), false, Set.of(), Pack.SOCIAL);
        Feat needs = plain.requiring(Sibling.CHRONICLE, Sibling.COINAGE);

        assertEquals(", \"banner\": false, \"pack\": null, \"requires\": []", flags(plain));
        assertEquals(", \"banner\": true, \"pack\": null, \"requires\": []", flags(banner));
        assertEquals(", \"banner\": false, \"pack\": \"SOCIAL\", \"requires\": []", flags(social));
        assertEquals(", \"banner\": false, \"pack\": null, \"requires\": [\"Coinage\", \"ChronicleStats\"]", flags(needs));

        Document parsed = Document.parse("{\"id\": \"x\"" + flags(needs) + "}");
        assertEquals(List.of("Coinage", "ChronicleStats"), parsed.getList("requires", String.class));
        assertNull(parsed.get("pack"));
    }

    private static String flags(Feat feat) {
        StringBuilder sb = new StringBuilder();
        LeaderboardExporter.appendFlags(sb, feat);
        return sb.toString();
    }

    @Test
    void recentRowsKeepTheOldFieldsFirstAndAddTheFlags() throws Exception {
        Written w = export(new FeatRegistry(), false);
        List<Document> recent = w.root().getList("recent", Document.class);
        assertFalse(recent.isEmpty());
        Document row = recent.getFirst();
        List<String> keys = new ArrayList<>(row.keySet());
        assertEquals(List.of("at", "uuid", "name", "feat", "featName", "tier", "points", "category", "hidden",
                "banner", "pack", "requires"), keys);
        Feat feat = w.registry().get(row.getString("feat"));
        assertEquals(feat.banner(), row.getBoolean("banner"));
        assertNull(row.get("pack"));
        assertEquals(List.of(), row.getList("requires", String.class));
    }

    @Test
    void milestoneRecordsCarryTheBannerFlagAfterHidden() throws Exception {
        Path dir = Files.createTempDirectory("feats-export-records");
        FeatRegistry registry = new FeatRegistry();
        ProgressStore store = new ProgressStore(dir, LOGGER);
        WorldStore world = new WorldStore(dir, LOGGER);
        world.putMilestoneIfAbsent("world_gateway", new WorldStore.Entry(1_000L, null, null));
        FeatsTracker tracker = new FeatsTracker(registry, store, new UnlockNotifier(() -> null, LOGGER), LOGGER,
                Runnable::run, world);
        new LeaderboardExporter(tracker, store, dir, LOGGER).flush(0);
        Document root = Document.parse(Files.readString(dir.resolve("export").resolve("feats.json"), StandardCharsets.UTF_8));
        Document row = root.get("milestones", Document.class).get("world_gateway", Document.class);
        assertEquals(List.of("at", "by", "byName", "featName", "tier", "hidden", "banner"), new ArrayList<>(row.keySet()));
        assertNull(row.get("by"));
        assertEquals(registry.get("world_gateway").banner(), row.getBoolean("banner"));
    }
}
