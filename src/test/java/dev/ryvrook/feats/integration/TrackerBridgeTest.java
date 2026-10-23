package dev.ryvrook.feats.integration;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.systems.CombatKinds;
import dev.ryvrook.feats.systems.CombatMemory;
import dev.ryvrook.feats.systems.DeepworksRuns;
import dev.ryvrook.feats.systems.LabUses;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.UnlockNotifier;
import dev.ryvrook.feats.track.Windows;
import dev.ryvrook.feats.track.WorldBounds;
import dev.ryvrook.trackerapi.api.Actor;
import dev.ryvrook.trackerapi.api.Counters;
import dev.ryvrook.trackerapi.api.Delivery;
import dev.ryvrook.trackerapi.api.EventKind;
import dev.ryvrook.trackerapi.api.Header;
import dev.ryvrook.trackerapi.api.Journal;
import dev.ryvrook.trackerapi.api.ModInfo;
import dev.ryvrook.trackerapi.api.PlayerSnapshot;
import dev.ryvrook.trackerapi.api.Stats;
import dev.ryvrook.trackerapi.api.Subscription;
import dev.ryvrook.trackerapi.api.TrackerApi;
import dev.ryvrook.trackerapi.api.TrackerEvent;
import dev.ryvrook.trackerapi.api.event.BlockBroken;
import dev.ryvrook.trackerapi.api.event.BlockPlaced;
import dev.ryvrook.trackerapi.api.event.BlockUsed;
import dev.ryvrook.trackerapi.api.event.CreatureTamed;
import dev.ryvrook.trackerapi.api.event.DamageDealt;
import dev.ryvrook.trackerapi.api.event.EntityDied;
import dev.ryvrook.trackerapi.api.event.EntityUsed;
import dev.ryvrook.trackerapi.api.event.GameModeChanged;
import dev.ryvrook.trackerapi.api.event.GameTimeSampled;
import dev.ryvrook.trackerapi.api.event.InstanceDiscovered;
import dev.ryvrook.trackerapi.api.event.InteractionStarted;
import dev.ryvrook.trackerapi.api.event.ItemBroke;
import dev.ryvrook.trackerapi.api.event.ItemConsumed;
import dev.ryvrook.trackerapi.api.event.ItemCrafted;
import dev.ryvrook.trackerapi.api.event.ItemEquipped;
import dev.ryvrook.trackerapi.api.event.ItemPickedUp;
import dev.ryvrook.trackerapi.api.event.ItemsGained;
import dev.ryvrook.trackerapi.api.event.MemoryRecorded;
import dev.ryvrook.trackerapi.api.event.MountChanged;
import dev.ryvrook.trackerapi.api.event.MovementEdge;
import dev.ryvrook.trackerapi.api.event.PermissionChanged;
import dev.ryvrook.trackerapi.api.event.PlayerChatted;
import dev.ryvrook.trackerapi.api.event.PlayerLeft;
import dev.ryvrook.trackerapi.api.event.WorldRemoved;
import dev.ryvrook.trackerapi.api.event.WorldAdded;
import dev.ryvrook.trackerapi.api.event.PlayerMoved;
import dev.ryvrook.trackerapi.api.event.PlayerSlept;
import dev.ryvrook.trackerapi.api.event.PlayerWorldChanged;
import dev.ryvrook.trackerapi.api.event.PortalEntered;
import dev.ryvrook.trackerapi.api.event.ProjectileLanded;
import dev.ryvrook.trackerapi.api.event.ProjectileLaunched;
import dev.ryvrook.trackerapi.api.event.RecipeLearned;
import dev.ryvrook.trackerapi.api.event.TriggerVolumeFired;
import dev.ryvrook.trackerapi.api.event.VitalsSampled;
import dev.ryvrook.trackerapi.api.event.WeatherChanged;
import dev.ryvrook.trackerapi.api.event.WorldEventEnded;
import dev.ryvrook.trackerapi.api.event.ZoneDiscovered;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feeds synthetic TrackerAPI records through the bridge, the way TrackerAPI delivers them, and checks the
 * Feats counters come out as the Feats systems made them.
 */
class TrackerBridgeTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static FeatRegistry registry;

    private FeatsTracker tracker;
    private FakeHost host;
    private FakeApi api;
    private TrackerBridge bridge;
    private UUID p;
    private PlayerProgress progress;
    private long seq;

    private static synchronized FeatRegistry registry() {
        if (registry == null) {
            registry = new FeatRegistry();
        }
        return registry;
    }

    @BeforeEach
    void setUp() throws IOException {
        ProgressStore store = new ProgressStore(Files.createTempDirectory("feats-bridge"), LOGGER);
        tracker = new FeatsTracker(registry(), store, new UnlockNotifier(() -> null, LOGGER), LOGGER, Runnable::run);
        host = new FakeHost(tracker);
        api = new FakeApi();
        bridge = new TrackerBridge(host);
        bridge.bind(api);
        p = UUID.randomUUID();
        progress = tracker.load(p, "Bridge");
        host.worlds.put(p, "default");
    }

    private Header h(UUID player) {
        return h(player, "default");
    }

    private Header h(UUID player, String world) {
        return new Header(System.currentTimeMillis(), ++seq, world, player);
    }

    /** A header at a chosen wall clock time, for the records whose timing matters. */
    private Header t(UUID player, String world, long at) {
        return new Header(at, ++seq, world, player);
    }

    /** Every counter of {@code keys} the tracker keeps, at {@code value}. */
    private void assertKeys(List<String> keys, long value) {
        assertKeys(progress, keys, value);
    }

    private void assertKeys(PlayerProgress of, List<String> keys, long value) {
        assertFalse(keys.isEmpty());
        for (String key : new LinkedHashSet<>(keys)) {
            if (tracker.isPersisted(key)) {
                assertEquals(value, of.get(key), key);
            }
        }
    }

    @Test
    void bindSubscribesEveryListenerAndUnbindClosesThem() {
        assertEquals(34, bridge.subscriptionCount());
        assertEquals(34, api.live().size());
        assertTrue(api.listeners.containsKey(BlockBroken.class));
        assertTrue(api.listeners.containsKey(EntityDied.class));
        assertTrue(api.listeners.containsKey(MovementEdge.class));
        assertFalse(api.listeners.containsKey(RecipeLearned.class), "the recipe poll runs on both paths");
        assertFalse(api.listeners.containsKey(PermissionChanged.class), "the group listener runs on both paths");
        assertFalse(api.listeners.containsKey(PortalEntered.class), "world changes already give the portal keys");
        bridge.bind(api); // a second enable replaces the first set
        assertEquals(34, api.live().size());
        bridge.unbind();
        assertEquals(0, api.live().size());
        bridge.unbind();
    }

    @Test
    void theModKindsJoinOnlyOnTrackerApi120() {
        api.version = "1.1.2";
        bridge.bind(api);
        assertEquals(34, bridge.subscriptionCount(), "1.1.x publishes no mod record");
        api.version = TrackerBridge.MOD_KINDS_MINIMUM;
        bridge.bind(api);
        assertEquals(34 + ModKinds.KINDS, bridge.subscriptionCount());
        assertEquals(34 + ModKinds.KINDS, api.live().size());
        assertTrue(api.listeners.containsKey(dev.ryvrook.trackerapi.api.event.DungeonRun.class));
        assertTrue(api.listeners.containsKey(dev.ryvrook.trackerapi.api.event.ModBound.class), "the bound set of the read back bests follows ModBound");
        assertTrue(api.listeners.containsKey(dev.ryvrook.trackerapi.api.event.EntityDied.class), "relic kills are read back on a kill");
        api.publish(new dev.ryvrook.trackerapi.api.event.ModLevelUp(h(p), "rpg", "player", 4, 5, 50));
        assertEquals(5, progress.get("rpg:level"), "the mirror reaches the tracker");
        api.version = "1.2.1";
        bridge.bind(api);
        assertEquals(34 + ModKinds.KINDS, api.live().size(), "newer than the minimum is fine");
        assertFalse(api.listeners.containsKey(dev.ryvrook.trackerapi.api.event.ModTally.class), "1.2.x publishes no tally");
        api.version = TrackerBridge.TALLY_KINDS_MINIMUM;
        bridge.bind(api);
        assertEquals(34 + ModKinds.KINDS + TallyKinds.KINDS, api.live().size(), "1.3.0 adds the tally kind");
        api.publish(new dev.ryvrook.trackerapi.api.event.ModTally(h(p), "fish", "caught", "Legendary_Whale", 1, 0));
        assertEquals(1, progress.get("fish:caught"), "the tally mirror reaches the tracker");
        bridge.unbind();
        assertEquals(0, api.live().size(), "the mod subscriptions close with the rest");
    }

    @Test
    void blockBreakKeysAndThePlacedRule() {
        String[] blocks = {"Rock_Stone", "Soil_Dirt", "Ore_Copper_Stone", "Wood_Oak_Trunk", "Rock_Crystal_Void_Large",
                "Soil_Grass", "Ore_Iron_Basalt", "Rock_Basalt"};
        for (int i = 0; i < 80; i++) {
            api.publish(new BlockBroken(h(p), i, 64, 0, blocks[i & 7], null, false, false));
        }
        api.publish(new BlockBroken(h(p), 0, 64, 0, "Ore_Copper_Stone", null, true, false));
        assertEquals(80, progress.get(StatKeys.BLOCKS_BROKEN));
        assertEquals(20, progress.get(StatKeys.ORE_MINED), "the player's own placement earns nothing");
        assertEquals(10, progress.get(StatKeys.WOOD_CHOPPED));
        assertKeys(Classify.blockBroken("Ore_Copper_Stone").subList(1, 2), 10);
    }

    @Test
    void placementSkipsARepeatSpot() {
        api.publish(new BlockPlaced(h(p), 1, 2, 3, "Rock_Stone_Cobble", "Rock_Stone_Cobble", false, false));
        api.publish(new BlockPlaced(h(p), 1, 2, 3, "Rock_Stone_Cobble", "Rock_Stone_Cobble", true, false));
        assertKeys(Classify.blockPlaced("Rock_Stone_Cobble"), 1);
    }

    @Test
    void craftUseAndTame() {
        api.publish(new ItemCrafted(h(p), "Recipe", "Weapon_Sword_Iron", 0));
        assertKeys(Classify.crafted("Weapon_Sword_Iron"), 1);
        api.publish(new ItemCrafted(h(p), "Recipe", "Weapon_Sword_Iron", 3));
        assertKeys(Classify.crafted("Weapon_Sword_Iron"), 4);

        api.publish(new BlockUsed(h(p), 0, 0, 0, "GoblinLab_Generator"));
        assertEquals(p, host.labUses.lastLabUser("GoblinLab_Generator", 60_000L));

        api.publish(new CreatureTamed(h(p), "Cow", UUID.randomUUID(), "Tamed_Cow"));
        assertEquals(1, progress.get(StatKeys.TAMED));
        assertKeys(Classify.tamed("Tamed_Cow"), 1);
    }

    @Test
    void pickupCountsOnlyTheFreshPart() {
        api.publish(new ItemPickedUp(h(p), "Plant_Crop_Wheat_Item", 5, 3));
        api.publish(new ItemPickedUp(h(p), "Plant_Crop_Wheat_Item", 4, 0));
        assertEquals(3, progress.get(StatKeys.PICKED_UP));
        assertKeys(Classify.pickedUp("Plant_Crop_Wheat_Item"), 3);
    }

    @Test
    void gainsCountTheFreshPartOutsideCreative() {
        List<String> keys = Classify.obtained("Ingredient_Stick");
        api.publish(new ItemsGained(h(p), "Ingredient_Stick", 10, 6, ItemsGained.Source.CONTAINER, false, 0, 0, 0));
        api.publish(new ItemsGained(h(p), "Ingredient_Stick", 2, 0, ItemsGained.Source.CONTAINER, true, 1, 2, 3));
        assertKeys(keys, 6);
        host.creative.add(p);
        api.publish(new ItemsGained(h(p), "Ingredient_Stick", 5, 5, ItemsGained.Source.PICKUP, false, 0, 0, 0));
        assertKeys(keys, 6);
    }

    @Test
    void consumedMealsAndPotions() {
        api.publish(new ItemConsumed(h(p), "Food_Apple"));
        api.publish(new ItemConsumed(h(p), "Potion_Health"));
        api.publish(new ItemConsumed(h(p), "Potion_Empty"));
        assertEquals(1, progress.get("food_eaten"));
        assertEquals(1, progress.get(StatKeys.POTIONS_DRUNK));
        host.creative.add(p);
        api.publish(new ItemConsumed(h(p), "Food_Apple"));
        assertEquals(1, progress.get("food_eaten"), "Creative meals are free");
    }

    @Test
    void damageDealtTakenAndBiggestHit() {
        UUID other = UUID.randomUUID();
        PlayerProgress victim = tracker.load(other, "Victim");
        Actor me = Actor.player(p, "Bridge");
        Actor them = Actor.player(other, "Victim");
        Actor goblin = Actor.creature(UUID.randomUUID(), "Goblin_Scrapper");
        api.publish(new DamageDealt(h(p), me, goblin, 7.4, "Physical", false, null, 0, 0, 0, false));
        api.publish(new DamageDealt(h(p), me, them, 12.6, "Physical", false, null, 0, 0, 0, false));
        api.publish(new DamageDealt(h(p), me, goblin, 0.4, "Physical", false, null, 0, 0, 0, false));
        api.publish(new DamageDealt(h(p), goblin, me, 3, "Physical", false, null, 0, 0, 0, false));
        api.publish(new DamageDealt(h(p), me, me, 5, "Physical", false, null, 0, 0, 0, false));
        assertEquals(20, progress.get(StatKeys.DAMAGE_DEALT));
        assertEquals(13, progress.get(StatKeys.BIGGEST_HIT));
        assertEquals(8, progress.get(StatKeys.DAMAGE_TAKEN), "own damage is taken, never dealt");
        assertEquals(13, victim.get(StatKeys.DAMAGE_TAKEN));
    }

    private EntityDied death(Actor victim, Actor killer, String cause, String effect, boolean ranged, String world,
                             List<UUID> participants, Set<UUID> untouched, boolean armourless, String portal) {
        return death(victim, killer, cause, effect, ranged, world, participants, untouched, armourless, portal,
                armourless && killer != null && killer.isPlayer() ? Set.of(killer.uuid()) : Set.of());
    }

    private EntityDied death(Actor victim, Actor killer, String cause, String effect, boolean ranged, String world,
                             List<UUID> participants, Set<UUID> untouched, boolean killerArmourless, String portal,
                             Set<UUID> armourless) {
        UUID about = victim.isPlayer() ? victim.uuid() : killer != null && killer.isPlayer() ? killer.uuid() : null;
        return new EntityDied(h(about, world), victim, killer, cause, effect, ranged, effect != null, 0, 0, 0,
                Classify.isBoss(victim.typeId()), participants, untouched, killerArmourless, portal, 0, armourless);
    }

    @Test
    void killKeysAndStreaks() {
        Actor me = Actor.player(p, "Bridge");
        Actor goblin = Actor.creature(UUID.randomUUID(), "Goblin_Scrapper");
        api.publish(death(goblin, me, "Physical", null, true, "default", List.of(), Set.of(), false, null));
        assertKeys(Classify.kill("Goblin_Scrapper", true), 1);
        api.publish(death(Actor.creature(null, "Cow"), me, "Physical", null, false, "default", List.of(), Set.of(), false, null));
        assertEquals(2, progress.get(StatKeys.KILLS));
        assertEquals(1, progress.get(StatKeys.KILLS_RANGED));
        assertEquals(2, progress.get(StatKeys.KILL_STREAK));

        api.publish(death(me, goblin, "Physical", null, false, "default", List.of(), Set.of(), false, null));
        assertKeys(Classify.death("Physical", null), 1);
        assertEquals(0, tracker.killStreaks().current(p));
        api.publish(death(me, me, "Fall", null, false, "default", List.of(), Set.of(), false, null));
        assertEquals(2, progress.get(StatKeys.KILLS), "a player is never their own kill");
        assertEquals(2, progress.get(StatKeys.DEATHS));
    }

    @Test
    void deathByEffectCountsTheEffectKey() {
        Actor me = Actor.player(p, "Bridge");
        api.publish(death(me, Actor.WORLD, "Fire", "Lava_Burn", false, "default", List.of(), Set.of(), false, null));
        assertKeys(Classify.death("Fire", "Lava_Burn"), 1);
    }

    @Test
    void bossCreditGoesToEveryParticipant() {
        UUID helper = UUID.randomUUID();
        UUID late = UUID.randomUUID();
        PlayerProgress helped = tracker.load(helper, "Helper");
        tracker.load(late, "Late");
        Actor me = Actor.player(p, "Bridge");
        Actor boss = Actor.creature(UUID.randomUUID(), "Bear_Voidtaken");
        api.publish(death(boss, me, "Physical", null, true, "default", List.of(p, helper), Set.of(helper), false, null));
        assertKeys(Classify.kill("Bear_Voidtaken", true), 1);
        assertKeys(helped, Classify.kill("Bear_Voidtaken", false), 1);
        assertEquals(0, helped.get(StatKeys.KILLS_RANGED), "only the killing blow is ranged");
        assertEquals(1, helped.get(StatKeys.BOSS_FLAWLESS));
        assertEquals(0, progress.get(StatKeys.BOSS_FLAWLESS));
        assertEquals(1, tracker.killStreaks().current(helper));
        assertEquals(0, tracker.loadedProgress(late).get(StatKeys.KILLS));

        // A boss that died to the world still credits whoever fought it.
        api.publish(death(boss, Actor.WORLD, "Fall", null, false, "default", List.of(helper), Set.of(), false, null));
        assertEquals(2, helped.get(StatKeys.KILLS_BOSS));
        assertEquals(1, progress.get(StatKeys.KILLS_BOSS));
    }

    @Test
    void gobliteratorFeatsForCreditedPlayersInTheWorld() {
        String world = "instance-The_Deepworks-1";
        UUID helper = UUID.randomUUID();
        UUID away = UUID.randomUUID();
        PlayerProgress helped = tracker.load(helper, "Helper");
        PlayerProgress gone = tracker.load(away, "Away");
        host.worlds.put(p, world.toLowerCase(Locale.ROOT));
        host.worlds.put(helper, world.toLowerCase(Locale.ROOT));
        host.worlds.put(away, "default");
        long now = System.currentTimeMillis();
        DeepworksRuns runs = DeepworksRuns.of(tracker);
        runs.entered(p, DeepworksRuns.PORTAL_TYPE, world, now);
        runs.entered(helper, DeepworksRuns.PORTAL_TYPE, world, now - DeepworksRuns.FAST_MILLIS - 1);
        runs.died(helper, world);

        Actor me = Actor.player(p, "Bridge");
        Actor boss = Actor.creature(UUID.randomUUID(), "Goblin_Gobliterator_Phase3_HM");
        api.publish(death(boss, me, "Physical", null, false, world, List.of(p, helper, away), Set.of(), true,
                DeepworksRuns.PORTAL_TYPE, Set.of(p, helper)));
        assertEquals(1, progress.get(StatKeys.GOBLITERATOR_DEATHLESS));
        assertEquals(1, progress.get(StatKeys.GOBLITERATOR_FAST));
        assertEquals(1, progress.get(StatKeys.GOBLITERATOR_HM_UNARMOURED));
        assertEquals(0, helped.get(StatKeys.GOBLITERATOR_DEATHLESS), "the helper died in this run");
        assertEquals(0, helped.get(StatKeys.GOBLITERATOR_FAST));
        assertEquals(1, helped.get(StatKeys.GOBLITERATOR_HM_UNARMOURED), "every credited player without armour earns it");
        assertEquals(1, gone.get(StatKeys.KILLS_BOSS), "credit still counts for a player who left");
        assertEquals(0, gone.get(StatKeys.GOBLITERATOR_DEATHLESS));
    }

    @Test
    void deathInTheDeepworksEndsTheCleanRun() {
        String world = "instance-The_Deepworks-2";
        DeepworksRuns runs = DeepworksRuns.of(tracker);
        api.publish(new PlayerWorldChanged(h(p, world), "default", DeepworksRuns.PORTAL_TYPE, true));
        assertTrue(runs.isClean(p, world));
        assertTrue(runs.isFast(p, world, System.currentTimeMillis()));
        api.publish(death(Actor.player(p, "Bridge"), Actor.WORLD, "Fall", null, false, world, List.of(), Set.of(),
                false, DeepworksRuns.PORTAL_TYPE));
        assertFalse(runs.isClean(p, world));
    }

    @Test
    void portalEntriesSkipTheFirstWorldOfASession() {
        api.publish(new PlayerWorldChanged(h(p, "instance-Goblin_Breach-1"), null, "Goblin_Breach", true));
        assertEquals(0, progress.get(StatKeys.PORTALS_ENTERED));
        api.publish(new PlayerWorldChanged(h(p, "instance-Goblin_Breach-2"), "default", "Goblin_Breach", true));
        api.publish(new PlayerWorldChanged(h(p, "default"), "instance-Goblin_Breach-2", null, false));
        assertKeys(Classify.portal("Goblin_Breach"), 1);
        assertEquals(1, progress.get(StatKeys.PORTAL_TYPES));
    }

    @Test
    void abilityCastsNeedToBeAffordable() {
        List<String> keys = Classify.interaction("Root_Ability_Fireball");
        api.publish(new InteractionStarted(h(p), "Root_Ability_Fireball", "Ability1", true));
        api.publish(new InteractionStarted(h(p), "Root_Ability_Fireball", "Ability1", false));
        api.publish(new InteractionStarted(h(p), "Root_Ability_Fireball", "ProjectileHit", true));
        if (!keys.isEmpty()) {
            assertKeys(keys, 1);
        }
        api.publish(new InteractionStarted(h(p), "Root_Spyglass_Zoom", "Secondary", false));
        assertKeys(Classify.interaction("Root_Spyglass_Zoom"), 1);
    }

    @Test
    void movementDistanceModesAndWorldBounds() {
        api.publish(new PlayerMoved(h(p), 0, 70, 0, 4, PlayerMoved.Mode.WALK, null, null));
        api.publish(new PlayerMoved(h(p), 0, 60, 0, 3, PlayerMoved.Mode.SWIM, null, null));
        api.publish(new PlayerMoved(h(p), 0, 90, 0, 5, PlayerMoved.Mode.GLIDE, null, null));
        api.publish(new PlayerMoved(h(p), 0, 70, 0, 6, PlayerMoved.Mode.RIDE, null, null));
        assertEquals(18, progress.get(StatKeys.DISTANCE));
        assertEquals(3, progress.get(StatKeys.SWIM_DISTANCE));
        assertEquals(5, progress.get(StatKeys.GLIDE_DISTANCE));
        assertEquals(6, progress.get(StatKeys.RIDE_DISTANCE));
        host.trackDistance = false;
        api.publish(new PlayerMoved(h(p), 0, 70, 0, 4, PlayerMoved.Mode.WALK, null, null));
        assertEquals(18, progress.get(StatKeys.DISTANCE));

        api.publish(new PlayerWorldChanged(h(p, "instance-x"), "default", "The_Deepworks", true));
        api.publish(new PlayerMoved(h(p, "instance-x"), 0, WorldBounds.FLOOR, 0, 1, PlayerMoved.Mode.WALK, null, null));
        assertEquals(0, progress.get(StatKeys.BOTTOM_REACHED), "instances have floors of their own");
        api.publish(new PlayerMoved(h(p), 0, WorldBounds.FLOOR, 0, 1, PlayerMoved.Mode.WALK, null, null));
        api.publish(new PlayerMoved(h(p), 0, WorldBounds.CEILING, 0, 1, PlayerMoved.Mode.WALK, null, null));
        assertEquals(1, progress.get(StatKeys.BOTTOM_REACHED));
        assertEquals(1, progress.get(StatKeys.TOP_REACHED));
    }

    @Test
    void zonesMemoriesChatAndGameMode() {
        api.publish(new ZoneDiscovered(h(p), "", "Emerald_Grove"));
        api.publish(new ZoneDiscovered(h(p), "Emerald_Grove", "Zone1"));
        api.publish(new ZoneDiscovered(h(p), null, null));
        assertEquals(1, progress.get(StatKeys.ZONES_DISCOVERED));

        api.publish(new MemoryRecorded(h(p), "Memory_A"));
        api.publish(new MemoryRecorded(h(p), "Memory_A"));
        api.publish(new MemoryRecorded(h(p), "Memory_B"));
        assertEquals(2, progress.get(StatKeys.MEMORIES));

        api.publish(new PlayerChatted(h(p), "Bridge", 5, false, null));
        api.publish(new PlayerChatted(h(p), "Bridge", 5, true, null));
        assertEquals(1, host.chats.size(), "a cancelled message never counts");

        api.publish(new GameModeChanged(h(p), "Adventure", "Creative"));
        assertTrue(host.creative.contains(p));
        api.publish(new GameModeChanged(h(p), "Creative", "Adventure"));
        assertFalse(host.creative.contains(p));
    }

    // ---- 1.7.2 kinds ----------------------------------------------------------------------------------------

    private MovementEdge edge(MovementEdge.Edge edge, double y, double fall, long at) {
        return new MovementEdge(t(p, "default", at), edge, y, fall);
    }

    @Test
    void movementEdgesCountJumpsGlidesAndWholeSeconds() {
        long t0 = 1_000_000L;
        api.publish(edge(MovementEdge.Edge.JUMP, 64, 0, t0));
        api.publish(edge(MovementEdge.Edge.JUMP, 64, 0, t0 + 500));
        api.publish(edge(MovementEdge.Edge.GLIDE_START, 90, 0, t0));
        api.publish(edge(MovementEdge.Edge.GLIDE_END, 70, 0, t0 + 3_500));
        api.publish(edge(MovementEdge.Edge.GLIDE_START, 90, 0, t0 + 4_000));
        api.publish(edge(MovementEdge.Edge.GLIDE_END, 70, 0, t0 + 5_200));
        // Sprints of 1.6 s and 0.6 s make 2 whole seconds, the fraction carried as the own path does.
        api.publish(edge(MovementEdge.Edge.SPRINT_START, 64, 0, t0));
        api.publish(edge(MovementEdge.Edge.SPRINT_END, 64, 0, t0 + 1_600));
        api.publish(edge(MovementEdge.Edge.SPRINT_START, 64, 0, t0 + 2_000));
        api.publish(edge(MovementEdge.Edge.SPRINT_END, 64, 0, t0 + 2_600));
        api.publish(edge(MovementEdge.Edge.CROUCH_START, 64, 0, t0));
        api.publish(edge(MovementEdge.Edge.CROUCH_END, 64, 0, t0 + 2_000));
        api.publish(edge(MovementEdge.Edge.CLIMB_START, 64, 0, t0));
        api.publish(edge(MovementEdge.Edge.CLIMB_END, 64, 0, t0 + 999));
        api.publish(edge(MovementEdge.Edge.CLIMB_END, 64, 0, t0 + 5_000)); // an end without a start adds nothing
        assertEquals(2, progress.get(StatKeys.JUMPS));
        assertEquals(2, progress.get(StatKeys.GLIDES));
        assertEquals(3, progress.get(StatKeys.LONGEST_GLIDE_SECONDS));
        assertEquals(2, progress.get(StatKeys.SPRINT_SECONDS));
        assertEquals(2, progress.get(StatKeys.CROUCH_SECONDS));
        assertEquals(0, progress.get(StatKeys.CLIMB_SECONDS));
        assertEquals(0, progress.get(StatKeys.ROLLS), "rolls have no edge, own path only");
    }

    @Test
    void waterLandingNeedsThirtyMetresOfFall() {
        long t0 = 2_000_000L;
        api.publish(edge(MovementEdge.Edge.FALL_START, 100, 0, t0));
        api.publish(edge(MovementEdge.Edge.FALL_FAR, 90, 0, t0 + 500));
        api.publish(edge(MovementEdge.Edge.SWIM_START, 65, 0, t0 + 1_500));
        assertEquals(1, progress.get(StatKeys.WATER_LANDINGS));
        api.publish(edge(MovementEdge.Edge.SWIM_END, 66, 0, t0 + 3_000));
        api.publish(edge(MovementEdge.Edge.FALL_START, 80, 0, t0 + 4_000));
        api.publish(edge(MovementEdge.Edge.SWIM_START, 60, 0, t0 + 5_000));
        assertEquals(1, progress.get(StatKeys.WATER_LANDINGS), "twenty metres is not enough");
        // A landing in the same moment as the swim start is the same fall.
        api.publish(edge(MovementEdge.Edge.FALL_START, 100, 0, t0 + 6_000));
        api.publish(edge(MovementEdge.Edge.LANDED, 64, 36, t0 + 7_000));
        api.publish(edge(MovementEdge.Edge.SWIM_START, 64, 0, t0 + 7_050));
        assertEquals(2, progress.get(StatKeys.WATER_LANDINGS));
        // A dry landing and a swim much later is not.
        api.publish(edge(MovementEdge.Edge.FALL_START, 100, 0, t0 + 8_000));
        api.publish(edge(MovementEdge.Edge.LANDED, 64, 36, t0 + 9_000));
        api.publish(edge(MovementEdge.Edge.SWIM_START, 63, 0, t0 + 20_000));
        assertEquals(2, progress.get(StatKeys.WATER_LANDINGS));
    }

    @Test
    void mountsMinecartsAndTheLongestRide() {
        long t0 = 3_000_000L;
        api.publish(new MountChanged(t(p, "default", t0), true, "Horse", UUID.randomUUID()));
        api.publish(new MountChanged(t(p, "default", t0 + 65_400), false, "Horse", UUID.randomUUID()));
        assertEquals(1, progress.get(StatKeys.MOUNTS));
        assertKeys(List.of(Classify.mountKey("Horse")), 1);
        assertEquals(65, progress.get(StatKeys.LONGEST_RIDE));
        api.publish(new MountChanged(t(p, "default", t0 + 70_000), true, "Minecart", UUID.randomUUID()));
        api.publish(new MountChanged(t(p, "default", t0 + 80_000), false, "Minecart", null));
        assertEquals(1, progress.get(StatKeys.MINECART_RIDES));
        assertEquals(1, progress.get(StatKeys.MOUNTS));
        assertEquals(65, progress.get(StatKeys.LONGEST_RIDE));
        api.publish(new MountChanged(t(p, "default", t0 + 90_000), true, "Seat", null));
        api.publish(new MountChanged(t(p, "default", t0 + 990_000), false, "Seat", null));
        assertEquals(0, progress.get(StatKeys.SEATS), "the seat block is not on the record");
        assertEquals(65, progress.get(StatKeys.LONGEST_RIDE), "sitting is not a ride");
    }

    private ItemEquipped wear(int slot, String itemId) {
        return new ItemEquipped(h(p), "Armor", slot, itemId, null);
    }

    @Test
    void armourSetsFromTheSlotsSeen() {
        api.publish(wear(0, "Armor_Iron_Head"));
        api.publish(wear(1, "Armor_Iron_Chest"));
        api.publish(wear(2, "Armor_Iron_Hands"));
        api.publish(new ItemEquipped(h(p), "Tool", 3, "Armor_Iron_Legs", null));
        assertEquals(0, progress.get(StatKeys.ARMOUR_SETS), "the tool section is not armour");
        api.publish(wear(3, "Armor_Iron_Legs"));
        assertEquals(1, progress.get(StatKeys.ARMOUR_SETS));
        api.publish(wear(3, null));
        api.publish(wear(3, "Armor_Iron_Legs"));
        assertEquals(1, progress.get(StatKeys.ARMOUR_SETS), "each set counts once");
        // Kweebec ships only a head and a chest.
        api.publish(wear(2, null));
        api.publish(wear(3, null));
        api.publish(wear(0, "Armor_Kweebec_Head"));
        api.publish(wear(1, "Armor_Kweebec_Chest"));
        assertEquals(2, progress.get(StatKeys.ARMOUR_SETS));
    }

    @Test
    void brokenToolsButNotArmour() {
        api.publish(new ItemBroke(h(p), "Tool_Pickaxe_Iron"));
        api.publish(new ItemBroke(h(p), "Armor_Iron_Chest"));
        api.publish(new ItemBroke(h(p), "Tool_Watering_Can"));
        assertKeys(Classify.toolBroken("Tool_Pickaxe_Iron"), 1);
        assertEquals(1, progress.get(StatKeys.TOOLS_BROKEN));
    }

    @Test
    void shotsFiredOnlyByWeapon() {
        Actor me = Actor.player(p, "Bridge");
        api.publish(new ProjectileLaunched(h(p), me, "Arrow", true));
        api.publish(new ProjectileLaunched(h(p), me, "Fireball", false));
        api.publish(new ProjectileLaunched(h(null), Actor.creature(null, "Goblin_Archer"), "Arrow", true));
        assertEquals(1, progress.get(StatKeys.SHOTS_FIRED));
    }

    @Test
    void longRangeKillsFromTheLandedHit() {
        UUID other = UUID.randomUUID();
        tracker.load(other, "Victim");
        Actor me = Actor.player(p, "Bridge");
        Actor them = Actor.player(other, "Victim");
        Actor goblin = Actor.creature(UUID.randomUUID(), "Goblin_Scrapper");
        api.publish(new PlayerMoved(h(p), 0, 64, 0, 0, PlayerMoved.Mode.WALK, null, null));
        api.publish(new ProjectileLanded(h(p), me, "Arrow", goblin, 0, 64, 60));
        api.publish(death(goblin, me, "Physical", null, true, "default", List.of(), Set.of(), false, null));
        assertEquals(1, progress.get(StatKeys.KILLS_FAR));
        api.publish(new ProjectileLanded(h(p), me, "Arrow", goblin, 0, 64, 20));
        api.publish(death(goblin, me, "Physical", null, true, "default", List.of(), Set.of(), false, null));
        assertEquals(1, progress.get(StatKeys.KILLS_FAR), "twenty metres is not far");
        api.publish(new ProjectileLanded(h(p), me, "Arrow", them, 35, 64, 0));
        api.publish(death(them, me, "Physical", null, true, "default", List.of(), Set.of(), false, null));
        assertEquals(1, progress.get(StatKeys.PLAYER_KILLS_FAR));
        assertEquals(1, progress.get(StatKeys.KILLS_FAR), "a player kill is counted on its own key");
    }

    @Test
    void killKindsAndMultikillWindowsMatchTheOwnPath() {
        Actor me = Actor.player(p, "Bridge");
        String[] roles = {"Skeleton_Scout_Patrol", "Skeleton_Scout_Wander", "Goblin_Scrapper", "Cow"};
        long[] times = {10_000L, 11_000L, 12_000L, 30_000L};
        Windows mirror = new Windows();
        int doubles = 0;
        int triples = 0;
        Set<String> kinds = new HashSet<>();
        for (int i = 0; i < roles.length; i++) {
            Actor victim = Actor.creature(UUID.randomUUID(), roles[i]);
            api.publish(new EntityDied(t(p, "default", times[i]), victim, me, "Physical", null, false, false, 0, 0, 0,
                    false, List.of(), Set.of(), false, null, 0, Set.of()));
            int filled = mirror.kill(p, times[i]);
            doubles += (filled & Windows.DOUBLE) != 0 ? 1 : 0;
            triples += (filled & Windows.TRIPLE) != 0 ? 1 : 0;
            kinds.add(CombatKinds.kind(roles[i]));
        }
        assertEquals(3, kinds.size(), "the patrol and wander scouts are one kind");
        assertEquals(kinds.size(), progress.get(StatKeys.KILL_KINDS));
        assertEquals(doubles, progress.get(StatKeys.MULTIKILL_2));
        assertEquals(triples, progress.get(StatKeys.MULTIKILL_3));
        assertEquals(1, progress.get(StatKeys.MULTIKILL_2));
        assertEquals(1, progress.get(StatKeys.MULTIKILL_3));
    }

    @Test
    void killContextFromTheKeptState() {
        Actor me = Actor.player(p, "Bridge");
        Actor goblin = Actor.creature(UUID.randomUUID(), "Goblin_Scrapper");
        api.publish(death(goblin, me, "Physical", null, false, "default", List.of(), Set.of(), false, null));
        for (String key : List.of(StatKeys.KILLS_UNDERWATER, StatKeys.KILLS_GLIDING, StatKeys.KILLS_MOUNTED,
                StatKeys.KILLS_LOW_HEALTH, StatKeys.KILLS_NIGHT, StatKeys.KILLS_BLOOD_MOON)) {
            assertEquals(0, progress.get(key), key);
        }
        long now = System.currentTimeMillis();
        api.publish(new GameTimeSampled(h(null), 22, 0.9f, 3, 4));
        api.publish(new WeatherChanged(h(null), "Blood_Moon", "Zone1_Sunny"));
        api.publish(new MovementEdge(t(p, "default", now), MovementEdge.Edge.SWIM_START, 60, 0));
        api.publish(new MovementEdge(t(p, "default", now), MovementEdge.Edge.GLIDE_START, 90, 0));
        api.publish(new MountChanged(h(p), true, "Horse", UUID.randomUUID()));
        api.publish(new VitalsSampled(h(p), 1, 20, 5, 10, 0, 0));
        api.publish(death(goblin, me, "Physical", null, false, "default", List.of(), Set.of(), false, null));
        for (String key : List.of(StatKeys.KILLS_UNDERWATER, StatKeys.KILLS_GLIDING, StatKeys.KILLS_MOUNTED,
                StatKeys.KILLS_LOW_HEALTH, StatKeys.KILLS_NIGHT, StatKeys.KILLS_BLOOD_MOON)) {
            assertEquals(1, progress.get(key), key);
        }
        api.publish(new GameTimeSampled(h(null), 9, 0.4f, 3, 5));
        api.publish(new VitalsSampled(h(p), 20, 20, 5, 10, 0, 0));
        api.publish(new MovementEdge(t(p, "default", now), MovementEdge.Edge.SWIM_END, 60, 0));
        api.publish(death(goblin, me, "Physical", null, false, "default", List.of(), Set.of(), false, null));
        assertEquals(1, progress.get(StatKeys.KILLS_NIGHT), "day by the clock");
        assertEquals(1, progress.get(StatKeys.KILLS_LOW_HEALTH));
        assertEquals(1, progress.get(StatKeys.KILLS_UNDERWATER));
        assertEquals(2, progress.get(StatKeys.KILLS_GLIDING));
    }

    @Test
    void bossCoopKeysAndKindForEveryCredited() {
        List<UUID> group = new ArrayList<>(List.of(p));
        for (int i = 0; i < 4; i++) {
            UUID helper = UUID.randomUUID();
            tracker.load(helper, "Helper" + i);
            group.add(helper);
        }
        Actor me = Actor.player(p, "Bridge");
        Actor boss = Actor.creature(UUID.randomUUID(), "Bear_Voidtaken");
        api.publish(death(boss, me, "Physical", null, false, "default", group.subList(0, 3), Set.of(), false, null));
        assertEquals(1, progress.get(StatKeys.BOSS_COOP_3));
        assertEquals(0, progress.get(StatKeys.BOSS_COOP_5));
        assertEquals(1, tracker.loadedProgress(group.get(2)).get(StatKeys.BOSS_COOP_3));
        assertEquals(1, tracker.loadedProgress(group.get(2)).get(StatKeys.KILL_KINDS));
        assertEquals(0, tracker.loadedProgress(group.get(4)).get(StatKeys.BOSS_COOP_3));
        api.publish(death(boss, me, "Physical", null, false, "default", group, Set.of(), false, null));
        assertEquals(2, progress.get(StatKeys.BOSS_COOP_3));
        assertEquals(1, progress.get(StatKeys.BOSS_COOP_5));
        assertEquals(1, tracker.loadedProgress(group.get(4)).get(StatKeys.BOSS_COOP_5));
        assertEquals(1, progress.get(StatKeys.KILL_KINDS), "one kind however often");
        assertEquals(1, progress.get(StatKeys.MULTIKILL_2), "the killing blow feeds the windows");
    }

    @Test
    void hardModeGobliteratorDeathlessAndFast() {
        String world = "instance-The_Deepworks-3";
        host.worlds.put(p, world.toLowerCase(Locale.ROOT));
        DeepworksRuns.of(tracker).entered(p, DeepworksRuns.PORTAL_TYPE, world, System.currentTimeMillis());
        Actor me = Actor.player(p, "Bridge");
        Actor boss = Actor.creature(UUID.randomUUID(), "Goblin_Gobliterator_Phase3_HM");
        api.publish(death(boss, me, "Physical", null, false, world, List.of(p), Set.of(), true,
                DeepworksRuns.PORTAL_TYPE, Set.of()));
        assertEquals(1, progress.get(StatKeys.GOBLITERATOR_HARD_DEATHLESS));
        assertEquals(1, progress.get(StatKeys.GOBLITERATOR_HARD_FAST));
        assertEquals(1, progress.get(StatKeys.GOBLITERATOR_HM_UNARMOURED), "killerArmourless stands in for the list");
    }

    @Test
    void playerKillStreakAndVendetta() {
        UUID other = UUID.randomUUID();
        PlayerProgress rival = tracker.load(other, "Rival");
        Actor me = Actor.player(p, "Bridge");
        Actor them = Actor.player(other, "Rival");
        api.publish(death(me, them, "Physical", null, false, "default", List.of(), Set.of(), false, null));
        api.publish(death(them, me, "Physical", null, false, "default", List.of(), Set.of(), false, null));
        assertEquals(1, progress.get(StatKeys.VENDETTAS));
        assertEquals(1, progress.get(StatKeys.PLAYER_KILL_STREAK));
        api.publish(death(them, me, "Physical", null, false, "default", List.of(), Set.of(), false, null));
        assertEquals(1, progress.get(StatKeys.VENDETTAS), "the score was settled");
        assertEquals(2, progress.get(StatKeys.PLAYER_KILL_STREAK));
        assertEquals(1, rival.get(StatKeys.PLAYER_KILL_STREAK));
        assertEquals(0, progress.get(StatKeys.KILL_KINDS), "players are no creature kind");
    }

    private DamageDealt hurt(String cause, double amount, long at) {
        return new DamageDealt(t(p, "default", at), Actor.WORLD, Actor.player(p, "Bridge"), amount, cause, false, null,
                0, 0, 0, false);
    }

    private void tick(long at) {
        api.publish(new PlayerMoved(t(p, "default", at), 0, 64, 0, 0, PlayerMoved.Mode.WALK, null, null));
    }

    @Test
    void damageTypesAndSurvivedFallsAndDrowning() {
        long t0 = 5_000_000L;
        api.publish(hurt("Fall", 7, t0));
        assertEquals(0, progress.get(StatKeys.FALL_SURVIVED_MAX), "a fall is settled a moment later");
        tick(t0 + CombatMemory.SETTLE_MILLIS + 1);
        assertEquals(7, progress.get(StatKeys.FALL_SURVIVED_MAX));
        assertEquals(1, progress.get(StatKeys.DAMAGE_TYPES));

        api.publish(hurt("Drowning", 2, t0 + 2_000));
        api.publish(hurt("Drowning", 2, t0 + 3_000));
        tick(t0 + 4_000);
        assertEquals(0, progress.get(StatKeys.DROWNING_SURVIVED), "the episode is still going");
        tick(t0 + 3_000 + CombatMemory.EPISODE_GAP_MILLIS);
        assertEquals(1, progress.get(StatKeys.DROWNING_SURVIVED));
        assertEquals(2, progress.get(StatKeys.DAMAGE_TYPES));

        api.publish(hurt("Fall", 30, t0 + 10_000));
        api.publish(new EntityDied(t(p, "default", t0 + 10_000), Actor.player(p, "Bridge"), Actor.WORLD, "Fall", null,
                false, false, 0, 0, 0, false, List.of(), Set.of(), false, null, 0, Set.of()));
        tick(t0 + 20_000);
        assertEquals(7, progress.get(StatKeys.FALL_SURVIVED_MAX), "a fatal fall was not survived");
        assertEquals(0, progress.get(StatKeys.BLOCKS), "blocks stay on the own path");
    }

    @Test
    void seedsTrapsPlacedKindsAndWoodSpecies() {
        api.publish(new BlockPlaced(h(p), 0, 0, 0, "Plant_Seeds_Wheat", null, false, false));
        api.publish(new BlockPlaced(h(p), 0, 0, 0, "Plant_Seeds_Wheat", null, true, false));
        api.publish(new BlockPlaced(h(p), 1, 0, 0, "Tool_Fishing_Trap", null, false, false));
        api.publish(new BlockPlaced(h(p), 2, 0, 0, "Rock_Stone_Cobble", null, false, false));
        assertKeys(Classify.seedPlanted("Plant_Seeds_Wheat"), 2);
        assertEquals(1, progress.get(StatKeys.TRAPS_PLACED));
        assertEquals(3, progress.get(StatKeys.PLACED_KINDS), "the repeat spot counts the seed but no kind");

        api.publish(new BlockBroken(h(p), 0, 0, 0, "Wood_Oak_Trunk", null, false, false));
        api.publish(new BlockBroken(h(p), 0, 0, 0, "Wood_Oak_Trunk_Half", null, false, false));
        api.publish(new BlockBroken(h(p), 0, 0, 0, "Wood_Birch_Trunk", null, true, false));
        api.publish(new BlockBroken(h(p), 0, 0, 0, "Wood_Birch_Planks", null, false, false));
        assertEquals(1, progress.get(StatKeys.WOOD_SPECIES), "own placements and planks are no new species");
        api.publish(new BlockBroken(h(p), 0, 0, 0, "Wood_Birch_Trunk", null, false, false));
        assertEquals(2, progress.get(StatKeys.WOOD_SPECIES));
    }

    @Test
    void instancesVolumesAndWorldEvents() {
        api.publish(new InstanceDiscovered(h(p), UUID.randomUUID(), "Forgotten_Temple"));
        api.publish(new InstanceDiscovered(h(p), UUID.randomUUID(), "Forgotten_Temple"));
        api.publish(new InstanceDiscovered(h(p), UUID.randomUUID(), null));
        assertEquals(1, progress.get(StatKeys.INSTANCES_DISCOVERED));
        assertKeys(List.of(StatKeys.INSTANCE_PREFIX + "Forgotten_Temple"), 1);

        Actor me = Actor.player(p, "Bridge");
        api.publish(new TriggerVolumeFired(h(p), "Dungeon_Checkpoint_3", "ENTER", me));
        api.publish(new TriggerVolumeFired(h(p), "Dungeon_Checkpoint_4", "EXIT", me));
        api.publish(new TriggerVolumeFired(h(null), "Dungeon_Checkpoint_5", "ENTER", Actor.creature(null, "Cow")));
        assertKeys(List.of(StatKeys.VOLUME_PREFIX + "Dungeon_Checkpoint"), 1);

        api.publish(new WorldEventEnded(h(null), "Hunt_Breach", "SUCCESS", List.of(p)));
        api.publish(new WorldEventEnded(h(null), "Hunt_Breach", "FAILURE", List.of(p)));
        api.publish(new WorldEventEnded(h(null), "Coffer_Goblin", "SUCCESS", List.of(UUID.randomUUID())));
        assertEquals(1, progress.get(StatKeys.WORLD_EVENTS_WON));
        assertKeys(List.of(StatKeys.WORLD_EVENT_PREFIX + "Hunt_Breach"), 1);
    }

    @Test
    void nightsSleptMoonsAndWeathers() {
        long t0 = 7_000_000L;
        api.publish(new GameTimeSampled(t(null, "default", t0), 23, 0.95f, 2, 1));
        assertEquals(1, progress.get(StatKeys.MOONS_SEEN));
        api.publish(new PlayerSlept(t(p, "default", t0 + 1_000), true, 0, 64, 0));
        api.publish(new PlayerSlept(t(p, "default", t0 + 5_000), false, 0, 64, 0));
        api.publish(new GameTimeSampled(t(null, "default", t0 + 60_000), 7, 0.3f, 2, 2));
        assertEquals(1, progress.get(StatKeys.NIGHTS_SLEPT));
        api.publish(new GameTimeSampled(t(null, "default", t0 + 120_000), 8, 0.35f, 2, 2));
        assertEquals(1, progress.get(StatKeys.NIGHTS_SLEPT), "one morning, one night");
        assertEquals(1, progress.get(StatKeys.MOONS_SEEN), "no moon by day");
        assertEquals(0, progress.get(StatKeys.GAME_DAYS), "game days stay with the world sample on both paths");

        api.publish(new WeatherChanged(t(null, "default", t0 + 200_000), "Zone1_Rain", "Zone1_Sunny"));
        api.publish(new GameTimeSampled(t(null, "default", t0 + 205_000), 9, 0.4f, 2, 2));
        assertEquals(0, progress.get(StatKeys.WEATHERS_SEEN), "five seconds of rain is not enough");
        api.publish(new GameTimeSampled(t(null, "default", t0 + 211_000), 9, 0.4f, 2, 2));
        assertEquals(1, progress.get(StatKeys.WEATHERS_SEEN));
        api.publish(new WeatherChanged(t(null, "default", t0 + 220_000), "Zone1_Storm", "Zone1_Rain"));
        api.publish(new WeatherChanged(t(null, "default", t0 + 222_000), "Zone1_Sunny", "Zone1_Storm"));
        assertEquals(1, progress.get(StatKeys.WEATHERS_SEEN), "a two second storm does not count");
        api.publish(new WeatherChanged(t(null, "default", t0 + 240_000), "Zone1_Rain", "Zone1_Sunny"));
        assertEquals(2, progress.get(StatKeys.WEATHERS_SEEN), "the sun that held counts when it ends");

        host.worlds.put(p, "elsewhere");
        api.publish(new GameTimeSampled(t(null, "default", t0 + 300_000), 1, 0.05f, 5, 3));
        assertEquals(1, progress.get(StatKeys.MOONS_SEEN), "only players in that world see its moon");
    }

    @Test
    void talkTamedFishBiomesAndPortalTime() {
        api.publish(new EntityUsed(h(p), Actor.creature(UUID.randomUUID(), "Kweebec_Elder"), "Use"));
        api.publish(new EntityUsed(h(p), Actor.player(UUID.randomUUID(), "Other"), "Use"));
        assertEquals(1, progress.get(Classify.talkKey("Kweebec_Elder")));

        api.publish(new ItemPickedUp(h(p), "Fish_Whale_Humpback_Item", 2, 1));
        api.publish(new ItemPickedUp(h(p), "Fish_Bluegill_Item", 1, 1));
        assertEquals(1, progress.get(StatKeys.FISH_RARE));

        api.publish(new PlayerMoved(h(p), 0, 70, 0, 1, PlayerMoved.Mode.WALK, null, "Forest"));
        api.publish(new PlayerMoved(h(p), 0, 70, 0, 1, PlayerMoved.Mode.WALK, null, "Forest"));
        api.publish(new PlayerMoved(h(p), 0, 70, 0, 1, PlayerMoved.Mode.WALK, null, " "));
        assertEquals(1, progress.get(StatKeys.BIOMES_VISITED));

        long t0 = 9_000_000L;
        api.publish(new PlayerWorldChanged(t(p, "instance-Goblin_Breach-9", t0), "default", "Goblin_Breach", true));
        api.publish(new PlayerWorldChanged(t(p, "default", t0 + 90_500), "instance-Goblin_Breach-9", null, false));
        assertEquals(90, progress.get(StatKeys.PORTAL_SECONDS));
        api.publish(new PlayerWorldChanged(t(p, "instance-Goblin_Breach-9", t0 + 100_000), "default", "Goblin_Breach", true));
        api.publish(new PlayerLeft(t(p, "instance-Goblin_Breach-9", t0 + 110_600), "Bridge", 1L));
        assertEquals(101, progress.get(StatKeys.PORTAL_SECONDS), "a leave ends the stay, with the half second carried");
    }

    @Test
    void theLastPortalStayAndRideOfASessionSurviveTheUnload() {
        long t0 = 12_000_000L;
        api.publish(new MountChanged(t(p, "default", t0), true, "Horse", UUID.randomUUID()));
        api.publish(new MountChanged(t(p, "default", t0 + 40_000), false, "Horse", UUID.randomUUID()));
        assertEquals(40, progress.get(StatKeys.LONGEST_RIDE));
        api.publish(new PlayerWorldChanged(t(p, "instance-Goblin_Breach-3", t0 + 50_000), "default", "Goblin_Breach", true));
        api.publish(new MountChanged(t(p, "instance-Goblin_Breach-3", t0 + 60_000), true, "Horse", UUID.randomUUID()));
        // Feats unloads on the disconnect event before TrackerAPI's queue hands over PlayerLeft
        tracker.unload(p);
        api.publish(new PlayerLeft(t(p, "instance-Goblin_Breach-3", t0 + 160_000), "Bridge", 1L));
        assertEquals(0, tracker.offlineAddsWaiting());
        PlayerProgress saved = tracker.peek(p);
        assertEquals(110, saved.get(StatKeys.PORTAL_SECONDS), "the stay from the portal entry to the leave");
        assertEquals(100, saved.get(StatKeys.LONGEST_RIDE), "the ride raised to its length, not added on top");
    }

    @Test
    void aShortLastRideAfterTheUnloadKeepsTheRecord() {
        long t0 = 13_000_000L;
        api.publish(new MountChanged(t(p, "default", t0), true, "Horse", UUID.randomUUID()));
        api.publish(new MountChanged(t(p, "default", t0 + 80_000), false, "Horse", UUID.randomUUID()));
        api.publish(new MountChanged(t(p, "default", t0 + 90_000), true, "Horse", UUID.randomUUID()));
        tracker.unload(p);
        api.publish(new PlayerLeft(t(p, "default", t0 + 100_000), "Bridge", 1L));
        assertEquals(80, tracker.peek(p).get(StatKeys.LONGEST_RIDE));
    }

    @Test
    void aRemovedWorldDropsItsSky() {
        api.publish(new WorldAdded(h(null, "instance-Goblin_Breach-7"), true, "Goblin_Breach"));
        api.publish(new WeatherChanged(h(null, "instance-Goblin_Breach-7"), "Zone1_Rain", "Zone1_Sunny"));
        assertEquals(1, bridge.skyCount());
        api.publish(new WorldRemoved(h(null, "instance-Goblin_Breach-7")));
        assertEquals(0, bridge.skyCount());
    }

    // ---- fakes ----------------------------------------------------------------------------------------------

    private static final class FakeHost implements TrackerHost {
        final FeatsTracker tracker;
        final LabUses labUses = new LabUses();
        final Set<UUID> creative = new HashSet<>();
        final Map<UUID, String> worlds = new HashMap<>();
        final List<UUID> chats = new ArrayList<>();
        boolean trackDistance = true;

        FakeHost(FeatsTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public FeatsTracker tracker() {
            return tracker;
        }

        @Override
        public LabUses labUses() {
            return labUses;
        }

        @Override
        public boolean trackDistance() {
            return trackDistance;
        }

        @Override
        public boolean isCreative(UUID uuid) {
            return creative.contains(uuid);
        }

        @Override
        public void setCreative(UUID uuid, boolean isCreative) {
            if (isCreative) {
                creative.add(uuid);
            } else {
                creative.remove(uuid);
            }
        }

        @Override
        public String worldOf(UUID uuid) {
            return worlds.get(uuid);
        }

        @Override
        public void countChat(UUID uuid, long atMillis) {
            chats.add(uuid);
        }

        @Override
        public HytaleLogger logger() {
            return LOGGER;
        }
    }

    /** Delivers each record straight to the listeners of its class, as one ASYNC batch of one. */
    private static final class FakeApi extends TrackerApi {
        final Map<Class<?>, List<FakeSub>> listeners = new HashMap<>();

        List<FakeSub> live() {
            List<FakeSub> out = new ArrayList<>();
            listeners.values().forEach(l -> l.stream().filter(FakeSub::isActive).forEach(out::add));
            return out;
        }

        void publish(TrackerEvent event) {
            for (FakeSub s : listeners.getOrDefault(event.getClass(), List.of())) {
                if (s.isActive()) {
                    s.deliver(event);
                }
            }
        }

        @Override
        public <E extends TrackerEvent> Subscription subscribe(String owner, Class<E> type, Delivery delivery,
                                                                Consumer<? super E> listener) {
            assertEquals(TrackerBridge.OWNER, owner);
            assertEquals(Delivery.ASYNC, delivery);
            FakeSub sub = new FakeSub(owner, delivery, e -> listener.accept(type.cast(e)));
            listeners.computeIfAbsent(type, t -> new ArrayList<>()).add(sub);
            return sub;
        }

        @Override
        public Subscription subscribeAll(String owner, Delivery delivery, Consumer<TrackerEvent> listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Subscription subscribeKinds(String owner, Set<EventKind> kinds, Delivery delivery,
                                           Consumer<TrackerEvent> listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Subscription> subscriptions() {
            return List.copyOf(live());
        }

        @Override
        public boolean isTracked(EventKind kind) {
            return true;
        }

        @Override
        public PlayerSnapshot snapshot(UUID player) {
            return null;
        }

        @Override
        public List<PlayerSnapshot> snapshots() {
            return List.of();
        }

        @Override
        public Counters counters() {
            return null;
        }

        @Override
        public Journal journal() {
            return null;
        }

        @Override
        public Stats stats() {
            return null;
        }

        @Override
        public List<ModInfo> mods() {
            return List.of();
        }

        /** The version the bridge sees. 1.0.0 keeps the 1.9.0 mod kinds out, see {@link ModKindsTest}. */
        String version = "1.0.0";

        @Override
        public String version() {
            return version;
        }
    }

    private static final class FakeSub implements Subscription {
        private final String owner;
        private final Delivery delivery;
        private final Consumer<TrackerEvent> listener;
        private boolean active = true;
        private long delivered;

        FakeSub(String owner, Delivery delivery, Consumer<TrackerEvent> listener) {
            this.owner = owner;
            this.delivery = delivery;
            this.listener = listener;
        }

        void deliver(TrackerEvent e) {
            delivered++;
            listener.accept(e);
        }

        @Override
        public EventKind kind() {
            return null;
        }

        @Override
        public Delivery delivery() {
            return delivery;
        }

        @Override
        public String owner() {
            return owner;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public long delivered() {
            return delivered;
        }

        @Override
        public long failures() {
            return 0;
        }

        @Override
        public void close() {
            active = false;
        }
    }
}
