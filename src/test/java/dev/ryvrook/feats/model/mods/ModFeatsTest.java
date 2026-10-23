package dev.ryvrook.feats.model.mods;

import dev.ryvrook.feats.model.Category;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Pack;
import dev.ryvrook.feats.model.Requirement;
import dev.ryvrook.feats.model.Scope;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.model.Tier;
import dev.ryvrook.feats.model.Trigger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nine mod sections of Feats 1.9.0 against docs/MODS-PLAN.md section 2: the counts per tab, the counters each
 * feat reads against the keys {@code integration/ModKinds} writes, the id prefixes, and the sibling gate.
 */
class ModFeatsTest {
    /** Every sibling and every pack with a TrackerAPI that publishes the mod records, so every mod feat is in. */
    private static final FeatRegistry FULL = new FeatRegistry(EnumSet.allOf(Sibling.class), EnumSet.allOf(Pack.class),
            FeatRegistry.MODS_MINIMUM);

    /** Planned count per tab, the tab's id prefix and the mod whose sibling gates it. */
    private record Section(Category tab, int count, String prefix, Sibling mod) {
    }

    private static final List<Section> SECTIONS = List.of(
            new Section(Category.RPG, 12, "rpg_", Sibling.RPG_LEVELING),
            new Section(Category.DUNGEONS, 12, "dungeons_", Sibling.MAJOR_DUNGEONS),
            new Section(Category.PARRIES, 8, "parries_", Sibling.PERFECT_PARRIES),
            new Section(Category.RELICS, 8, "relics_", Sibling.WONDER_WEAPONS),
            new Section(Category.ZEPHYR, 8, "zephyr_", Sibling.ZEPHYR),
            new Section(Category.ENCHANTING, 6, "enchant_", Sibling.SIMPLE_ENCHANTMENTS),
            new Section(Category.VEIN_MINING, 6, "vein_", Sibling.VEIN_MINING),
            new Section(Category.LUCKY_MINING, 5, "lucky_", Sibling.LUCKY_MINING),
            new Section(Category.TAMEWORK, 8, "tamework_", Sibling.TAMEWORK));

    /**
     * The keys the mirror writes, from the ModKinds javadoc. A key that ends in a colon is a per id prefix and
     * matches any id under it.
     */
    private static final Set<String> KEYS = Set.of(
            "rpg:level", "rpg:level_ups", "rpg:max_level_reached", "rpg:xp", "rpg:xp:", "rpg:class:", "rpg:classes",
            "dungeons:runs", "dungeons:runs:", "dungeons:cleared", "dungeons:cleared:", "dungeons:cleared_mode:",
            "dungeons:failed", "dungeons:failed:", "dungeons:bosses", "dungeons:bosses:", "dungeons:boss_type:",
            "dungeons:boss_types", "dungeons:dungeon_type:", "dungeons:dungeon_types", "dungeons:fast_clear",
            "dungeons:hardcore_clear", "dungeons:solo_clear",
            "parries:parry", "parries:parry:pvp", "parries:counterattack", "parries:counterattack:pvp", "parries:streak_best",
            "relics:kills", "relics:kills:", "relics:abilities", "relics:abilities:", "relics:relic_kind:", "relics:relic_kinds",
            "relics:form_switch",
            "zephyr:dodge", "zephyr:perfect_dodge", "zephyr:perfect_dodge:pvp", "zephyr:double_jump", "zephyr:wall_jump",
            "zephyr:grapple", "zephyr:vanish", "zephyr:zipline", "zephyr:move_kind:", "zephyr:move_kinds",
            "enchant:items", "enchant:items:", "enchant:highest_level", "enchant:procs", "enchant:procs:",
            "enchant:enchant_kind:", "enchant:enchant_kinds",
            "vein:veins", "vein:blocks", "vein:blocks:", "vein:largest",
            "lucky:drops", "lucky:drops:", "lucky:streak_best",
            "tamework:tamed", "tamework:tamed:", "tamework:species:", "tamework:species", "tamework:bred", "tamework:bred:",
            "tamework:companion_level_ups", "tamework:companion_level_best", "tamework:companions_lost", "tamework:xp",
            "tamework:xp:", "tamework:harvest", "tamework:cull");

    /** The world feats the nine sections add, by id, with the personal feat each follows. */
    private static final Map<String, String> WORLD = Map.of(
            "first_rpg_level_cap", "rpg_max_level",
            "first_dungeons_hardcore", "dungeons_hardcore",
            "world_dungeons_fifty_clears", "dungeons_fifty_clears",
            "world_relics_every_relic", "relics_every_relic");

    @AfterAll
    static void restore() {
        new FeatRegistry(false); // put the shared feat predicates back to the plain list for the other tests
    }

    private static boolean allowed(String key) {
        if (KEYS.contains(key)) {
            return true;
        }
        int cut = key.lastIndexOf(':');
        return cut > 0 && KEYS.contains(key.substring(0, cut + 1));
    }

    @Test
    void everyTabHasItsPlannedCount() {
        int total = 0;
        for (Section s : SECTIONS) {
            List<Feat> feats = FULL.inCategory(s.tab());
            assertEquals(s.count(), feats.size(), s.tab() + " count");
            total += feats.size();
        }
        assertEquals(73, total);
    }

    @Test
    void everyFeatReadsAKeyTheMirrorWrites() {
        for (Section s : SECTIONS) {
            for (Feat feat : FULL.inCategory(s.tab())) {
                assertTrue(feat.isPersonal(), feat.id());
                assertFalse(feat.requirements().isEmpty(), feat.id());
                for (Requirement r : feat.requirements()) {
                    assertTrue(allowed(r.key()), feat.id() + " reads " + r.key());
                    assertTrue(r.key().startsWith(s.prefix().replace('_', ':')), feat.id() + " reads another mod's key " + r.key());
                    assertTrue(r.target() > 0, feat.id());
                }
            }
        }
    }

    @Test
    void idsArePrefixedUniqueAndTheTiersStayOnTheScale() {
        Set<String> ids = new HashSet<>();
        for (Section s : SECTIONS) {
            int platinum = 0;
            int hidden = 0;
            for (Feat feat : FULL.inCategory(s.tab())) {
                assertTrue(feat.id().startsWith(s.prefix()), feat.id() + " starts with " + s.prefix());
                assertTrue(ids.add(feat.id()), "duplicate " + feat.id());
                assertEquals(Set.of(s.mod(), Sibling.TRACKER_API), feat.requires(), feat.id());
                assertTrue(feat.description().endsWith("."), feat.id());
                assertFalse(feat.description().contains("—") || feat.description().contains(";"), feat.id());
                assertFalse(feat.icon().isBlank(), feat.id());
                if (feat.tier() == Tier.PLATINUM) {
                    platinum++;
                }
                if (feat.hidden()) {
                    hidden++;
                }
            }
            assertTrue(platinum <= 2, s.tab() + " has " + platinum + " platinum feats");
            assertTrue(hidden <= 2, s.tab() + " has " + hidden + " hidden feats");
        }
        // Every id in the registry is unique by construction, the registry throws on a duplicate. The check here is
        // that no mod id reuses one of the other sections' ids.
        assertEquals(73, ids.size());
    }

    @Test
    void theWorldFeatsFollowPersonalFeatsOfTheirSection() {
        for (Map.Entry<String, String> e : WORLD.entrySet()) {
            Feat world = FULL.get(e.getKey());
            assertNotNull(world, e.getKey());
            assertEquals(Category.WORLD, world.category(), e.getKey());
            assertEquals(Trigger.Kind.AFTER, world.trigger().kind(), e.getKey());
            assertEquals(e.getValue(), world.trigger().key(), e.getKey());
            Feat source = FULL.get(e.getValue());
            assertNotNull(source, e.getValue());
            assertTrue(source.isPersonal(), e.getValue());
            assertEquals(source.requires(), world.requires(), e.getKey() + " needs what its source needs");
            assertEquals(List.of(world), FULL.triggeredBy(e.getValue()));
            assertEquals(e.getKey().startsWith("first_") ? Scope.FIRST : Scope.WORLD, world.scope(), e.getKey());
        }
    }

    @Test
    void aMissingModLeavesItsTabAndWorldFeatsOut() {
        for (Section s : SECTIONS) {
            EnumSet<Sibling> without = EnumSet.allOf(Sibling.class);
            without.remove(s.mod());
            FeatRegistry registry = new FeatRegistry(without, EnumSet.allOf(Pack.class), FeatRegistry.MODS_MINIMUM);
            assertTrue(registry.inCategory(s.tab()).isEmpty(), s.tab() + " is empty without " + s.mod());
            assertFalse(registry.tabs().contains(s.tab()), s.tab() + " is not a tab");
            for (Feat feat : FULL.inCategory(s.tab())) {
                assertTrue(registry.whySkipped(feat.id()).startsWith("needs "), feat.id());
            }
            for (Map.Entry<String, String> e : WORLD.entrySet()) {
                if (FULL.get(e.getValue()).category() == s.tab()) {
                    assertTrue(registry.whySkipped(e.getKey()).startsWith("needs "), e.getKey());
                }
            }
            // The other eight tabs are untouched.
            for (Section other : SECTIONS) {
                if (other != s) {
                    assertEquals(other.count(), registry.inCategory(other.tab()).size(), other.tab() + " without " + s.mod());
                }
            }
        }
    }

    @Test
    void noTrackerApiOrAnOldOneLeavesEveryModTabOut() {
        EnumSet<Sibling> noTracker = EnumSet.allOf(Sibling.class);
        noTracker.remove(Sibling.TRACKER_API);
        FeatRegistry without = new FeatRegistry(noTracker, EnumSet.allOf(Pack.class), FeatRegistry.MODS_MINIMUM);
        FeatRegistry old = new FeatRegistry(EnumSet.allOf(Sibling.class), EnumSet.allOf(Pack.class), "1.1.2");
        for (Section s : SECTIONS) {
            assertTrue(without.inCategory(s.tab()).isEmpty(), s.tab() + " without TrackerAPI");
            assertTrue(old.inCategory(s.tab()).isEmpty(), s.tab() + " with TrackerAPI 1.1.2");
        }
        assertNotNull(old.modsNote());
        for (String id : WORLD.keySet()) {
            assertFalse(without.all().stream().anyMatch(f -> f.id().equals(id)), id);
        }
    }
}
