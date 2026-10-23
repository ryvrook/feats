package dev.ryvrook.feats.integration;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.UnlockNotifier;
import dev.ryvrook.trackerapi.api.Header;
import dev.ryvrook.trackerapi.api.event.CompanionChanged;
import dev.ryvrook.trackerapi.api.event.DungeonRun;
import dev.ryvrook.trackerapi.api.event.EnchantmentTriggered;
import dev.ryvrook.trackerapi.api.event.ItemEnchanted;
import dev.ryvrook.trackerapi.api.event.LuckyDrop;
import dev.ryvrook.trackerapi.api.event.ModAbilityUsed;
import dev.ryvrook.trackerapi.api.event.ModAction;
import dev.ryvrook.trackerapi.api.event.ModBossDefeated;
import dev.ryvrook.trackerapi.api.event.ModChoiceChanged;
import dev.ryvrook.trackerapi.api.event.ModLevelUp;
import dev.ryvrook.trackerapi.api.event.ModMilestone;
import dev.ryvrook.trackerapi.api.event.ModXpGained;
import dev.ryvrook.trackerapi.api.event.VeinMined;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feeds one synthetic record per TrackerAPI 1.2.0 mod kind into {@link ModKinds} and checks every counter write
 * against the rules of TrackerAPI's CounterSink and the Feats only derivations in docs/MODS-PLAN.md.
 */
class ModKindsTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** A tracker over a plain registry with the IO inline, so a save happens on the calling thread. */
    private static FeatsTracker tracker() throws IOException {
        ProgressStore store = new ProgressStore(Files.createTempDirectory("feats-modkinds"), LOGGER);
        return new FeatsTracker(new FeatRegistry(), store, new UnlockNotifier(() -> null, LOGGER), LOGGER, Runnable::run);
    }

    /** Every write, as {@code add key amount}, {@code raise key value} or {@code mark prefix+id total}. */
    private final List<String> writes = new ArrayList<>();
    private final UUID p = UUID.randomUUID();
    private final ModKinds kinds = new ModKinds(new ModKinds.Sink() {
        @Override
        public void add(UUID player, String key, long amount) {
            assertEquals(p, player);
            writes.add("add " + key + " " + amount);
        }

        @Override
        public void raise(UUID player, String key, long value) {
            assertEquals(p, player);
            writes.add("raise " + key + " " + value);
        }

        @Override
        public void discover(UUID player, String prefix, String id, String total) {
            assertEquals(p, player);
            assertTrue(prefix.endsWith(":"), prefix);
            writes.add("mark " + prefix + id + " " + total);
        }
    });
    private long seq;

    private Header h() {
        return new Header(System.currentTimeMillis(), ++seq, "default", p);
    }

    private Header nobody() {
        return new Header(System.currentTimeMillis(), ++seq, "default", null);
    }

    @Test
    void mainLevelIsABestWithLevelUpsAndTheCapFlag() {
        kinds.onLevelUp(new ModLevelUp(h(), "rpg", "player", 3, 5, 50));
        assertEquals(List.of("raise rpg:level 5", "add rpg:level_ups 2"), writes);
        writes.clear();
        kinds.onLevelUp(new ModLevelUp(h(), "rpg", null, 49, 50, 50));
        assertEquals(List.of("raise rpg:level 50", "add rpg:level_ups 1", "raise rpg:max_level_reached 1"), writes);
        writes.clear();
        kinds.onLevelUp(new ModLevelUp(h(), "rpg", "", 5, 5, 0));
        assertEquals(List.of("raise rpg:level 5", "add rpg:level_ups 1"), writes, "a level up of nothing still counts once");
        writes.clear();
        kinds.onLevelUp(new ModLevelUp(nobody(), "rpg", "player", 3, 5, 50));
        kinds.onLevelUp(new ModLevelUp(h(), null, "player", 3, 5, 50));
        assertTrue(writes.isEmpty(), "no player or no mod counts nothing");
    }

    @Test
    void skillLevelIsABestPerTrackAndOverEverySkill() {
        kinds.onLevelUp(new ModLevelUp(h(), "mmo", "mining", 9, 10, 0));
        assertEquals(List.of("raise mmo:skill_level:mining 10", "raise mmo:skill_level_best 10", "add mmo:skill_level_ups 1"), writes);
        assertTrue(StatKeys.isPerId("mmo:skill_level:mining"), "a skill level is kept only while a feat reads it");
        assertFalse(StatKeys.isPerId("mmo:skill_level_best"));
    }

    @Test
    void experienceIsRoundedAndKeyedBySourceOrSkill() {
        kinds.onXpGained(new ModXpGained(h(), "rpg", "player", 12.6, "kill"));
        kinds.onXpGained(new ModXpGained(h(), "mmo", "mining", 3.2, null));
        kinds.onXpGained(new ModXpGained(h(), "rpg", "player", 7, null));
        kinds.onXpGained(new ModXpGained(h(), "rpg", "player", 0.4, "block"));
        assertEquals(List.of("add rpg:xp 13", "add rpg:xp:kill 13", "add mmo:xp 3", "add mmo:xp:mining 3", "add rpg:xp 7"), writes);
    }

    @Test
    void aPickIsAMarkUnderItsSlotWithThePluralTotal() {
        kinds.onChoiceChanged(new ModChoiceChanged(h(), "rpg", "class", "warrior", "Warrior", 1));
        kinds.onChoiceChanged(new ModChoiceChanged(h(), "mmo", "class", "mage", null, 0));
        kinds.onChoiceChanged(new ModChoiceChanged(h(), "rpg", "class", null, null, 0));
        assertEquals(List.of("mark rpg:class:warrior rpg:classes", "mark mmo:class:mage mmo:classes"), writes);
    }

    @Test
    void abilitiesCountPerIdAndARelicIsAKind() {
        kinds.onAbilityUsed(new ModAbilityUsed(h(), "mmo", "fireball", null, "Trork"));
        kinds.onAbilityUsed(new ModAbilityUsed(h(), "relics", "storm", "Relic_Hammer", null));
        kinds.onAbilityUsed(new ModAbilityUsed(h(), "relics", null, null, null));
        assertEquals(List.of("add mmo:abilities 1", "add mmo:abilities:fireball 1",
                "add relics:abilities 1", "add relics:abilities:storm 1", "mark relics:relic_kind:Relic_Hammer relics:relic_kinds",
                "add relics:abilities 1"), writes);
    }

    @Test
    void anActionCountsUnderItsNameWithPvpApartAndZephyrMovesAreKinds() {
        kinds.onAction(new ModAction(h(), "parries", "parry", true, "Player", 0));
        kinds.onAction(new ModAction(h(), "parries", "counterattack", false, "Trork", 12));
        kinds.onAction(new ModAction(h(), "zephyr", "wall_jump", false, null, 0));
        kinds.onAction(new ModAction(h(), "tamework", null, false, null, 0));
        assertEquals(List.of("add parries:parry 1", "add parries:parry:pvp 1", "add parries:counterattack 1",
                "add zephyr:wall_jump 1", "mark zephyr:move_kind:wall_jump zephyr:move_kinds"), writes);
    }

    @Test
    void aMilestoneIsAMarkUnderItsCategoryWithThePluralTotal() {
        kinds.onMilestone(new ModMilestone(h(), "mmo", "quest", "q1", "First quest"));
        kinds.onMilestone(new ModMilestone(h(), "mmo", "bounty", "b1", null));
        kinds.onMilestone(new ModMilestone(h(), "endgame", "achievement", "slayer", null));
        kinds.onMilestone(new ModMilestone(h(), "endgame", "bestiary", "Trork", null));
        kinds.onMilestone(new ModMilestone(h(), "endgame", "wave", "arena/5", "arena wave 5"));
        kinds.onMilestone(new ModMilestone(h(), "endgame", null, "x", null));
        assertEquals(List.of("mark mmo:quest:q1 mmo:quests", "mark mmo:bounty:b1 mmo:bounties",
                "mark endgame:achievement:slayer endgame:achievements", "mark endgame:bestiary:Trork endgame:bestiaries",
                "mark endgame:wave:arena/5 endgame:waves"), writes);
    }

    @Test
    void aDungeonRunCountsEntriesClearsFailuresAndTheClearFlags() {
        kinds.onDungeonRun(new DungeonRun(h(), "dungeons", "Crypt", DungeonRun.Phase.ENTERED, "Normal", 3, 0));
        assertEquals(List.of("add dungeons:runs 1", "add dungeons:runs:Crypt 1"), writes);
        writes.clear();
        kinds.onDungeonRun(new DungeonRun(h(), "dungeons", "Crypt", DungeonRun.Phase.COMPLETED, "Hardcore", 1, 9 * 60_000L));
        assertEquals(List.of("add dungeons:cleared 1", "add dungeons:cleared:Crypt 1", "add dungeons:cleared_mode:Hardcore 1",
                "add dungeons:hardcore_clear 1", "add dungeons:solo_clear 1", "mark dungeons:dungeon_type:Crypt dungeons:dungeon_types",
                "add dungeons:fast_clear 1"), writes);
        writes.clear();
        kinds.onDungeonRun(new DungeonRun(h(), "dungeons", "Crypt", DungeonRun.Phase.COMPLETED, "normal", 4, 10 * 60_000L));
        assertEquals(List.of("add dungeons:cleared 1", "add dungeons:cleared:Crypt 1", "add dungeons:cleared_mode:normal 1",
                "mark dungeons:dungeon_type:Crypt dungeons:dungeon_types"), writes, "ten minutes is not under ten");
        writes.clear();
        kinds.onDungeonRun(new DungeonRun(h(), "dungeons", "Crypt", DungeonRun.Phase.FAILED, "Normal", 2, 30_000L));
        assertEquals(List.of("add dungeons:failed 1", "add dungeons:failed:Crypt 1"), writes);
        writes.clear();
        kinds.onDungeonRun(new DungeonRun(h(), "dungeons", "Crypt", DungeonRun.Phase.LEFT, null, 0, 0));
        kinds.onDungeonRun(new DungeonRun(h(), "dungeons", "Crypt", null, null, 0, 0));
        assertTrue(writes.isEmpty());
    }

    @Test
    void anEndgameTrialUnderFiveMinutesIsAFastTrial() {
        kinds.onDungeonRun(new DungeonRun(h(), "endgame", "Arena_Fire", DungeonRun.Phase.COMPLETED, null, 0, 4 * 60_000L));
        assertEquals(List.of("add endgame:cleared 1", "add endgame:cleared:Arena_Fire 1",
                "mark endgame:dungeon_type:Arena_Fire endgame:dungeon_types", "add endgame:fast_trial 1"), writes);
        writes.clear();
        kinds.onDungeonRun(new DungeonRun(h(), "endgame", "Arena_Fire", DungeonRun.Phase.COMPLETED, null, 0, 6 * 60_000L));
        assertFalse(writes.contains("add endgame:fast_trial 1"), writes.toString());
        assertFalse(writes.contains("add endgame:fast_clear 1"), "the ten minute rule is Major Dungeons only");
        writes.clear();
        kinds.onDungeonRun(new DungeonRun(h(), "endgame", "Dungeon_Ice", DungeonRun.Phase.ENTERED, null, 0, 0));
        assertEquals(List.of("add endgame:runs 1", "add endgame:runs:Dungeon_Ice 1"), writes);
    }

    @Test
    void aBossCountsForTheRecordsPlayerAndIsAKind() {
        UUID other = UUID.randomUUID();
        kinds.onBossDefeated(new ModBossDefeated(h(), "dungeons", "Lich", "Crypt", List.of(p, other)));
        kinds.onBossDefeated(new ModBossDefeated(h(), "endgame", "Dragon", null, List.of(p)));
        kinds.onBossDefeated(new ModBossDefeated(h(), "endgame", null, null, List.of(p)));
        assertEquals(List.of("add dungeons:bosses 1", "add dungeons:bosses:Lich 1", "mark dungeons:boss_type:Lich dungeons:boss_types",
                "add endgame:bosses 1", "add endgame:bosses:Dragon 1", "mark endgame:boss_type:Dragon endgame:boss_types",
                "add endgame:bosses 1"), writes);
    }

    @Test
    void anEnchantmentCountsItemsTheHighestLevelAndTheKind() {
        kinds.onItemEnchanted(new ItemEnchanted(h(), "enchant", "Weapon_Sword_Iron", "sharpness", 3));
        kinds.onItemEnchanted(new ItemEnchanted(h(), "enchant", "Weapon_Sword_Iron", "sharpness", 0));
        assertEquals(List.of("add enchant:items 1", "add enchant:items:sharpness 1", "raise enchant:highest_level 3",
                "mark enchant:enchant_kind:sharpness enchant:enchant_kinds",
                "add enchant:items 1", "add enchant:items:sharpness 1", "mark enchant:enchant_kind:sharpness enchant:enchant_kinds"), writes);
    }

    @Test
    void aTriggeredEnchantmentCountsProcs() {
        kinds.onEnchantmentTriggered(new EnchantmentTriggered(h(), "enchant", "fire_aspect", "Weapon_Sword_Iron"));
        kinds.onEnchantmentTriggered(new EnchantmentTriggered(h(), "enchant", null, null));
        assertEquals(List.of("add enchant:procs 1", "add enchant:procs:fire_aspect 1", "add enchant:procs 1"), writes);
    }

    @Test
    void aVeinCountsOnceWithItsBlocksAndTheLargest() {
        kinds.onVeinMined(new VeinMined(h(), "vein", "Ore_Iron", 7));
        kinds.onVeinMined(new VeinMined(h(), "vein", "Ore_Iron", 0));
        assertEquals(List.of("add vein:veins 1", "add vein:blocks 7", "add vein:blocks:Ore_Iron 7", "raise vein:largest 7"), writes);
    }

    @Test
    void aLuckyDropCountsPerOreWithTheBestStreak() {
        kinds.onLuckyDrop(new LuckyDrop(h(), "lucky", "Ore_Gold", "Ore_Gold", 4));
        kinds.onLuckyDrop(new LuckyDrop(h(), "lucky", "Ore_Gold", "Ore_Gold", 0));
        assertEquals(List.of("add lucky:drops 1", "add lucky:drops:Ore_Gold 1", "raise lucky:streak_best 4",
                "add lucky:drops 1", "add lucky:drops:Ore_Gold 1"), writes);
    }

    @Test
    void aCompanionChangeCountsByItsKind() {
        kinds.onCompanionChanged(new CompanionChanged(h(), "tamework", "c1", "Wolf", CompanionChanged.Change.TAMED, 0));
        kinds.onCompanionChanged(new CompanionChanged(h(), "tamework", null, "Wolf", CompanionChanged.Change.BRED, 0));
        kinds.onCompanionChanged(new CompanionChanged(h(), "tamework", "c1", "Wolf", CompanionChanged.Change.LEVEL_UP, 6));
        kinds.onCompanionChanged(new CompanionChanged(h(), "tamework", "c1", "Wolf", CompanionChanged.Change.LEVEL_UP, 0));
        kinds.onCompanionChanged(new CompanionChanged(h(), "tamework", "c1", "Wolf", CompanionChanged.Change.DIED, 0));
        kinds.onCompanionChanged(new CompanionChanged(h(), "tamework", "c1", "Wolf", CompanionChanged.Change.RENAMED, 0));
        kinds.onCompanionChanged(new CompanionChanged(h(), "tamework", "c1", "Wolf", CompanionChanged.Change.MOUNTED, 0));
        kinds.onCompanionChanged(new CompanionChanged(h(), "tamework", "c1", "Wolf", CompanionChanged.Change.RELEASED, 0));
        kinds.onCompanionChanged(new CompanionChanged(h(), "tamework", "c1", "Wolf", null, 0));
        assertEquals(List.of("add tamework:tamed 1", "add tamework:tamed:Wolf 1", "mark tamework:species:Wolf tamework:species",
                "add tamework:bred 1", "add tamework:bred:Wolf 1",
                "add tamework:companion_level_ups 1", "raise tamework:companion_level_best 6",
                "add tamework:companion_level_ups 1",
                "add tamework:companions_lost 1"), writes);
    }

    // ---- the keys in the player file -----------------------------------------------------------------------

    @Test
    void perIdSumsAreKeptOnlyWhenWatchedAndMarkersAlways() throws IOException {
        FeatsTracker tracker = tracker();
        for (String perId : List.of("dungeons:cleared:Crypt", "rpg:xp:kill", "mmo:abilities:fireball", "vein:blocks:Ore_Iron",
                "lucky:drops:Ore_Gold", "tamework:tamed:Wolf", "enchant:procs:fire_aspect", "endgame:runs:Dungeon_Ice",
                "dungeons:cleared_mode:Hardcore", "mmo:skill_level:mining")) {
            assertTrue(StatKeys.isPerId(perId), perId);
            assertFalse(tracker.isPersisted(perId), perId + " is read by no feat yet");
        }
        for (String kept : List.of("rpg:level", "rpg:level_ups", "rpg:max_level_reached", "mmo:skill_level_best", "dungeons:cleared",
                "dungeons:fast_clear", "endgame:fast_trial", "vein:largest", "lucky:streak_best", "enchant:highest_level",
                "parries:parry", "parries:parry:pvp", "zephyr:wall_jump", "tamework:companion_level_best",
                "rpg:class:warrior", "rpg:classes", "mmo:quest:q1", "mmo:quests", "endgame:achievement:slayer", "endgame:achievements",
                "endgame:bestiary:Trork", "endgame:bestiaries", "endgame:wave:arena/5", "endgame:waves",
                "dungeons:boss_type:Lich", "dungeons:boss_types", "dungeons:dungeon_type:Crypt", "dungeons:dungeon_types",
                "relics:relic_kind:Relic_Hammer", "relics:relic_kinds", "zephyr:move_kind:wall_jump", "zephyr:move_kinds",
                "enchant:enchant_kind:sharpness", "enchant:enchant_kinds", "tamework:species:Wolf", "tamework:species")) {
            assertFalse(StatKeys.isPerId(kept), kept);
            assertTrue(tracker.isPersisted(kept), kept + " is a total, a best or a marker");
        }
        assertFalse(StatKeys.isModPerId("dominion:claims"), "the Dominion counters are not mod keys");
        assertFalse(StatKeys.isModPerId("rpg:cleared:"), "an empty id is no per id key");
        Set<String> ids = new HashSet<>();
        for (Sibling s : Sibling.values()) {
            if (s.isMod()) {
                ids.add(s.modId());
            }
        }
        assertEquals(ids, StatKeys.MOD_PREFIXES, "StatKeys lists the same prefixes as Sibling.modId");
    }

    @Test
    void theMirrorReachesTheTrackerThroughTheSink() throws IOException {
        FeatsTracker tracker = tracker();
        UUID player = UUID.randomUUID();
        var progress = tracker.load(player, "Modder");
        ModKinds live = new ModKinds(ModKinds.sink(tracker));
        Header header = new Header(System.currentTimeMillis(), 1, "default", player);
        live.onLevelUp(new ModLevelUp(header, "rpg", "player", 1, 7, 50));
        live.onLevelUp(new ModLevelUp(header, "rpg", "player", 7, 5, 50));
        live.onMilestone(new ModMilestone(header, "mmo", "quest", "q1", null));
        live.onMilestone(new ModMilestone(header, "mmo", "quest", "q1", null));
        live.onMilestone(new ModMilestone(header, "mmo", "quest", "q2", null));
        assertEquals(7, progress.get("rpg:level"), "a best never goes down");
        assertEquals(7, progress.get("rpg:level_ups"), "6 then 1 for a level up of nothing");
        assertEquals(2, progress.get("mmo:quests"), "the same quest counts once");
        assertEquals(1, progress.get("mmo:quest:q1"));
        tracker.unload(player);
    }
}
