package dev.ryvrook.feats.integration;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.systems.DeepworksRuns;
import dev.ryvrook.feats.systems.LabUses;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.UnlockNotifier;
import dev.ryvrook.feats.track.WorldBounds;
import dev.ryvrook.trackerapi.api.Actor;
import dev.ryvrook.trackerapi.api.Counters;
import dev.ryvrook.trackerapi.api.Delivery;
import dev.ryvrook.trackerapi.api.EventKind;
import dev.ryvrook.trackerapi.api.Header;
import dev.ryvrook.trackerapi.api.Journal;
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
import dev.ryvrook.trackerapi.api.event.GameModeChanged;
import dev.ryvrook.trackerapi.api.event.InteractionStarted;
import dev.ryvrook.trackerapi.api.event.ItemConsumed;
import dev.ryvrook.trackerapi.api.event.ItemCrafted;
import dev.ryvrook.trackerapi.api.event.ItemPickedUp;
import dev.ryvrook.trackerapi.api.event.ItemsGained;
import dev.ryvrook.trackerapi.api.event.MemoryRecorded;
import dev.ryvrook.trackerapi.api.event.PlayerChatted;
import dev.ryvrook.trackerapi.api.event.PlayerMoved;
import dev.ryvrook.trackerapi.api.event.PlayerWorldChanged;
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
        assertEquals(19, bridge.subscriptionCount());
        assertEquals(19, api.live().size());
        assertTrue(api.listeners.containsKey(BlockBroken.class));
        assertTrue(api.listeners.containsKey(EntityDied.class));
        bridge.bind(api); // a second enable replaces the first set
        assertEquals(19, api.live().size());
        bridge.unbind();
        assertEquals(0, api.live().size());
        bridge.unbind();
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
        public String version() {
            return "1.0.0";
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
