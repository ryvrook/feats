package dev.ryvrook.feats.model.mods;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.Category;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Pack;
import dev.ryvrook.feats.model.Requirement;
import dev.ryvrook.feats.model.Scope;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.model.Tier;
import dev.ryvrook.feats.model.Trigger;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.UnlockNotifier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seven tabs of Feats 1.10.0: Fishing, Arcanum and Music for the mods TrackerAPI 1.3.0 reads, Storage and
 * Machines for two plugins without a binding, Lost Garden and Decor for six asset packs. Counts per tab, the keys
 * each feat reads, the requires sets, the TrackerAPI gates and a missing pack taking only its own feats out.
 */
class PackFeatsTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Every sibling and pack with TrackerAPI 1.3.0, so every feat of the seven tabs is in. */
    private static final FeatRegistry FULL = new FeatRegistry(EnumSet.allOf(Sibling.class), EnumSet.allOf(Pack.class),
            FeatRegistry.TALLY_MINIMUM);

    /** A tab, its planned count, its id prefix, the siblings every feat needs (null for Decor) and its keys. */
    private record Section(Category tab, int count, String prefix, Set<Sibling> requires, List<String> keys) {
    }

    private static final List<Section> SECTIONS = List.of(
            new Section(Category.FISHING, 15, "fish_", Set.of(Sibling.FISHING, Sibling.TRACKER_API), FishingFeats.KEYS),
            new Section(Category.ARCANUM, 10, "arcanum_", Set.of(Sibling.ARCANUM, Sibling.TRACKER_API), ArcanumFeats.KEYS),
            new Section(Category.MUSIC, 8, "melodies_", Set.of(Sibling.MELODIES, Sibling.TRACKER_API), MusicFeats.KEYS),
            new Section(Category.STORAGE, 8, "storage_", Set.of(Sibling.STORAGE), StorageFeats.KEYS),
            new Section(Category.MACHINES, 10, "machines_", Set.of(Sibling.MACHINES), MachinesFeats.KEYS),
            new Section(Category.GARDEN, 12, "garden_", Set.of(Sibling.SIGS_GARDEN), GardenFeats.KEYS),
            new Section(Category.DECOR, 15, "decor_", null, DecorFeats.KEYS));

    private static final List<Category> TALLY_TABS = List.of(Category.FISHING, Category.ARCANUM, Category.MUSIC);
    private static final List<Category> COUNTED_TABS = List.of(Category.STORAGE, Category.MACHINES, Category.GARDEN, Category.DECOR);

    /** The Decor feats per pack. */
    private static final Map<Sibling, Integer> DECOR_PACKS = Map.of(Sibling.FANCY_STORAGE, 4, Sibling.POTION_SHOP, 4,
            Sibling.BLAHAJ, 3, Sibling.INFINITE_WATER, 2, Sibling.INFINITE_TELEPORTERS, 2);

    /** The world feats of the seven tabs, with the personal feat each follows. */
    private static final Map<String, String> WORLD = Map.of(
            "first_fish_legendary", "fish_legendary",
            "first_fish_almanac", "fish_pages_35",
            "first_machines_ultimate", "machines_ultimate_crusher",
            "first_garden_phoenix", "garden_phoenix");

    @AfterAll
    static void restore() {
        new FeatRegistry(false);
    }

    private static boolean allowed(List<String> keys, String key) {
        if (keys.contains(key)) {
            return true;
        }
        int cut = key.lastIndexOf(':');
        return cut > 0 && keys.contains(key.substring(0, cut + 1));
    }

    @Test
    void everyTabHasItsPlannedCount() {
        int total = 0;
        for (Section s : SECTIONS) {
            assertEquals(s.count(), FULL.inCategory(s.tab()).size(), s.tab() + " count");
            assertTrue(FULL.tabs().contains(s.tab()), s.tab() + " is a tab");
            total += s.count();
        }
        assertEquals(78, total);
    }

    @Test
    void everyFeatReadsAKeyOfItsSection() {
        for (Section s : SECTIONS) {
            for (Feat feat : FULL.inCategory(s.tab())) {
                assertTrue(feat.isPersonal(), feat.id());
                for (Requirement r : feat.requirements()) {
                    assertTrue(allowed(s.keys(), r.key()), feat.id() + " reads " + r.key());
                    assertTrue(r.target() > 0, feat.id());
                }
            }
        }
        for (String key : FishingFeats.KEYS) {
            assertTrue(key.startsWith(StatKeys.FISH_PREFIX), key);
        }
        for (String key : ArcanumFeats.KEYS) {
            assertTrue(key.startsWith(StatKeys.ARCANUM_PREFIX), key);
        }
        for (String key : MusicFeats.KEYS) {
            assertTrue(key.startsWith(StatKeys.MELODIES_PREFIX), key);
        }
        // The counted tabs read only keys Feats keeps on either tracking path.
        for (Category tab : COUNTED_TABS) {
            for (Feat feat : FULL.inCategory(tab)) {
                for (Requirement r : feat.requirements()) {
                    String k = r.key();
                    assertTrue(k.startsWith(StatKeys.CRAFTED_PREFIX) || k.startsWith(StatKeys.PLACED_PREFIX)
                            || k.startsWith(StatKeys.OBTAINED_PREFIX) || k.startsWith(StatKeys.USE_PREFIX)
                            || k.startsWith(StatKeys.KILL_PREFIX) || k.startsWith(StatKeys.PORTAL_PREFIX), feat.id() + " reads " + k);
                }
            }
        }
    }

    @Test
    void namesDescriptionsTiersAndIconsFollowTheRules() {
        Set<String> ids = new HashSet<>();
        for (Section s : SECTIONS) {
            int platinum = 0;
            int hidden = 0;
            for (Feat feat : FULL.inCategory(s.tab())) {
                assertTrue(feat.id().startsWith(s.prefix()), feat.id() + " starts with " + s.prefix());
                assertTrue(ids.add(feat.id()), "duplicate " + feat.id());
                int words = feat.name().split(" ").length;
                assertTrue(words >= 2 && words <= 3, feat.id() + " is named in two or three words, " + feat.name());
                String d = feat.description();
                assertTrue(d.endsWith(".") && d.indexOf('.') == d.length() - 1, feat.id() + " is one sentence");
                assertFalse(d.contains("—") || d.contains(";") || d.contains(":"), feat.id());
                if (feat.isSingleCounter() && feat.totalTarget() > 1) {
                    assertTrue(d.contains(String.format("%,d", feat.totalTarget())), feat.id() + " names its number, " + d);
                }
                if (!feat.isSingleCounter()) {
                    assertTrue(d.contains(String.valueOf(feat.requirements().size())), feat.id() + " names its number, " + d);
                }
                if (s.tab() != Category.GARDEN) {
                    for (String prefix : List.of("Sig_", "ModernStorage_", "Ymmersive_", "AA_", "Arcanum_", "MachineBlocks_")) {
                        assertFalse(feat.icon().startsWith(prefix), feat.id() + " has a vanilla icon");
                    }
                    for (String key : s.keys()) {
                        assertFalse(key.endsWith(":" + feat.icon()) && !key.equals(StatKeys.PLACED_PREFIX + "Teleporter"),
                                feat.id() + " has a vanilla icon, not the mod item " + feat.icon());
                    }
                }
                if (feat.tier() == Tier.PLATINUM) {
                    platinum++;
                }
                if (feat.hidden()) {
                    hidden++;
                }
            }
            assertTrue(platinum >= 1 && platinum <= 2, s.tab() + " has " + platinum + " platinum feats");
            assertTrue(hidden <= 2, s.tab() + " has " + hidden + " hidden feats");
            assertFalse(s.tab().icon().startsWith("Sig_"), s.tab() + " tab icon");
        }
        assertEquals(78, ids.size());
    }

    @Test
    void theRequiresSetsAndTheDecorPacks() {
        for (Section s : SECTIONS) {
            for (Feat feat : FULL.inCategory(s.tab())) {
                if (s.requires() != null) {
                    assertEquals(s.requires(), feat.requires(), feat.id());
                } else {
                    assertEquals(1, feat.requires().size(), feat.id() + " names exactly its own pack");
                    Sibling pack = feat.requires().iterator().next();
                    assertTrue(pack.isAssetPack(), feat.id());
                    assertEquals(Category.DECOR, Category.ofSibling(pack), feat.id());
                }
            }
        }
        Map<Sibling, Integer> perPack = new HashMap<>();
        for (Feat feat : FULL.inCategory(Category.DECOR)) {
            perPack.merge(feat.requires().iterator().next(), 1, Integer::sum);
        }
        assertEquals(DECOR_PACKS, perPack);
        assertEquals(Category.GARDEN, Category.ofSibling(Sibling.SIGS_GARDEN));
        assertEquals(Category.STORAGE, Category.ofSibling(Sibling.STORAGE));
        assertEquals(Category.MACHINES, Category.ofSibling(Sibling.MACHINES));
        assertEquals(Category.MUSIC, Category.ofMod(Sibling.MELODIES));
        assertNull(Category.ofSibling(Sibling.COINAGE));
    }

    @Test
    void theWorldFeatsFollowPersonalFeatsOfTheirSection() {
        for (Map.Entry<String, String> e : WORLD.entrySet()) {
            Feat world = FULL.get(e.getKey());
            assertNotNull(world, e.getKey());
            assertEquals(Category.WORLD, world.category());
            assertEquals(Scope.FIRST, world.scope(), e.getKey());
            assertEquals(Trigger.Kind.AFTER, world.trigger().kind());
            assertEquals(e.getValue(), world.trigger().key());
            assertEquals(FULL.get(e.getValue()).requires(), world.requires(), e.getKey() + " needs what its source needs");
            assertEquals(List.of(world), FULL.triggeredBy(e.getValue()));
        }
    }

    @Test
    void trackerApi12LeavesTheThreeTallyTabsOutAndKeepsTheRest() {
        FeatRegistry older = new FeatRegistry(EnumSet.allOf(Sibling.class), EnumSet.allOf(Pack.class), "1.2.1");
        for (Category tab : TALLY_TABS) {
            assertTrue(older.inCategory(tab).isEmpty(), tab + " needs TrackerAPI 1.3.0");
            assertFalse(older.tabs().contains(tab), tab.name());
        }
        assertNull(older.modsNote(), "the eleven tabs of 1.9.0 stay in");
        assertEquals(12, older.inCategory(Category.RPG).size());
        assertNotNull(older.tallyNote());
        assertTrue(older.tallyNote().contains("1.3.0") && older.tallyNote().contains("1.2.1"), older.tallyNote());
        assertEquals(older.tallyNote(), older.modNote(Sibling.FISHING));
        assertNull(older.modNote(Sibling.RPG_LEVELING));
        assertNull(older.modNote(Sibling.STORAGE));
        assertNull(older.get("first_fish_legendary"));
        for (Section s : SECTIONS) {
            if (COUNTED_TABS.contains(s.tab())) {
                assertEquals(s.count(), older.inCategory(s.tab()).size(), s.tab() + " needs no TrackerAPI");
            }
        }
        assertNull(FULL.tallyNote());
        assertNull(FULL.modNote(Sibling.FISHING));
        EnumSet<Sibling> noTallyMods = EnumSet.allOf(Sibling.class);
        noTallyMods.removeAll(List.of(Sibling.FISHING, Sibling.ARCANUM, Sibling.MELODIES));
        assertNull(new FeatRegistry(noTallyMods, EnumSet.noneOf(Pack.class), "1.2.1").tallyNote(),
                "no line when none of the three is installed");
    }

    @Test
    void withoutTrackerApiOnlyTheCountedTabsStay() {
        EnumSet<Sibling> noTracker = EnumSet.allOf(Sibling.class);
        noTracker.remove(Sibling.TRACKER_API);
        FeatRegistry registry = new FeatRegistry(noTracker, EnumSet.noneOf(Pack.class), FeatRegistry.TALLY_MINIMUM);
        for (Category tab : TALLY_TABS) {
            assertTrue(registry.inCategory(tab).isEmpty(), tab.name());
        }
        assertNotNull(registry.modNote(Sibling.ARCANUM));
        for (Section s : SECTIONS) {
            if (COUNTED_TABS.contains(s.tab())) {
                assertEquals(s.count(), registry.inCategory(s.tab()).size(), s.tab().name());
            }
        }
        assertNotNull(registry.get("first_machines_ultimate"));
        assertNotNull(registry.get("first_garden_phoenix"));
    }

    @Test
    void aMissingSiblingOrPackTakesOnlyItsOwnFeatsOut() {
        for (Sibling s : List.of(Sibling.FISHING, Sibling.ARCANUM, Sibling.MELODIES, Sibling.STORAGE, Sibling.MACHINES,
                Sibling.SIGS_GARDEN)) {
            EnumSet<Sibling> without = EnumSet.allOf(Sibling.class);
            without.remove(s);
            FeatRegistry registry = new FeatRegistry(without, EnumSet.allOf(Pack.class), FeatRegistry.TALLY_MINIMUM);
            Category tab = Category.ofSibling(s);
            assertTrue(registry.inCategory(tab).isEmpty(), tab + " without " + s);
            assertFalse(registry.tabs().contains(tab));
            for (Feat feat : FULL.inCategory(tab)) {
                assertTrue(registry.whySkipped(feat.id()).startsWith("needs "), feat.id());
            }
            assertEquals(FULL.count() - FULL.inCategory(tab).size(), registry.count(), "only " + tab + " goes");
        }
        for (Map.Entry<Sibling, Integer> e : DECOR_PACKS.entrySet()) {
            EnumSet<Sibling> without = EnumSet.allOf(Sibling.class);
            without.remove(e.getKey());
            FeatRegistry registry = new FeatRegistry(without, EnumSet.allOf(Pack.class), FeatRegistry.TALLY_MINIMUM);
            assertEquals(15 - e.getValue(), registry.inCategory(Category.DECOR).size(), "Decor without " + e.getKey());
            assertTrue(registry.tabs().contains(Category.DECOR));
            for (Feat feat : registry.inCategory(Category.DECOR)) {
                assertFalse(feat.requires().contains(e.getKey()), feat.id());
            }
        }
        EnumSet<Sibling> noDecor = EnumSet.allOf(Sibling.class);
        noDecor.removeAll(DECOR_PACKS.keySet());
        FeatRegistry bare = new FeatRegistry(noDecor, EnumSet.allOf(Pack.class), FeatRegistry.TALLY_MINIMUM);
        assertFalse(bare.tabs().contains(Category.DECOR), "no decoration pack, no Decor tab");
        assertEquals(12, bare.inCategory(Category.GARDEN).size());
        FeatRegistry gardenOnly = new FeatRegistry(EnumSet.of(Sibling.SIGS_GARDEN), EnumSet.noneOf(Pack.class));
        assertEquals(12, gardenOnly.inCategory(Category.GARDEN).size(), "Sig's Garden alone, no TrackerAPI");
        assertTrue(gardenOnly.whySkipped("first_garden_phoenix") == null);
        assertTrue(new FeatRegistry().inCategory(Category.GARDEN).isEmpty(), "a plain server has no Lost Garden");
    }

    @Test
    void everyKeyAFeatReadsIsKeptInThePlayerFile() throws IOException {
        ProgressStore store = new ProgressStore(Files.createTempDirectory("feats-packs"), LOGGER);
        FeatsTracker tracker = new FeatsTracker(FULL, store, new UnlockNotifier(() -> null, LOGGER), LOGGER, Runnable::run);
        int perId = 0;
        for (Section s : SECTIONS) {
            for (Feat feat : FULL.inCategory(s.tab())) {
                for (Requirement r : feat.requirements()) {
                    assertTrue(tracker.isPersisted(r.key()), feat.id() + " reads " + r.key() + ", which must be kept");
                    if (StatKeys.isPerId(r.key())) {
                        perId++;
                    }
                }
            }
        }
        assertTrue(perId > 100, "most of these feats read per id keys, " + perId);
        assertTrue(StatKeys.isPerId("fish:caught:Legendary_Whale"));
        assertFalse(tracker.isPersisted("fish:caught:Common_Minnow"), "an unwatched per id sum is dropped");
        assertFalse(tracker.isPersisted(StatKeys.CRAFTED_PREFIX + "Sig_Crate_Onion"), "an unwatched crate is dropped");
    }
}
