package dev.ryvrook.feats.systems;

import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSomnolence;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSleep;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSlumber;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSomnolence;
import com.hypixel.hytale.builtin.portals.resources.PortalWorld;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEventType;
import com.hypixel.hytale.builtin.triggervolumes.event.TriggerVolumeEvent;
import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerConfigData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerRespawnPointData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.event.events.permissions.PlayerGroupEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.modules.entity.component.PlayerLives;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.hardcore.HardcoreState;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.FeatsConfig;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.stats.WorldStore;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.GameDays;
import dev.ryvrook.feats.track.Hardcore;
import dev.ryvrook.feats.track.HookSet;
import dev.ryvrook.feats.track.Hooks;
import dev.ryvrook.feats.track.JoinCalendar;
import dev.ryvrook.feats.track.Presence;
import dev.ryvrook.feats.track.Vitals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The world, time, session and social counters. The once a second world sample reads the world's clock,
 * weather, sleep and portal state once and then each player's biome, vitals, respawn points and lives.
 * The tick keeps the calendar and who is online with whom. Joins drive the join streak and the dated
 * feats, and bus listeners cover referrals, permission groups and chunk generation.
 *
 * <p>Every engine read is guarded. A read whose classes are missing on this server is switched off with
 * one warning and leaves its counters unfed, a read that fails once is skipped for that sample. Per
 * player state lives in concurrent maps keyed by uuid and is dropped when the player leaves. Samples run
 * on the world thread, so component reads are safe there. The TrackerAPI path carries the biome, weather,
 * moon, instance, volume, world event, sleep and portal counters itself, so those only run on the own path.
 */
public final class WorldHooks implements HookSet {
    /** Samples in a row with the same weather before it counts as experienced. */
    static final int WEATHER_SAMPLES = 10;
    /** Respawn points and lives are read every this many samples. */
    static final int SLOW_EVERY = 5;
    /** Sunlight factor below which it is night for the moon phase. */
    static final double NIGHT_SUNLIGHT = 0.25;
    /** The server totals key holding the epoch millis Feats first ran here. */
    public static final String INSTALLED_AT = "installed_at";
    /** Void event stages checked, highest first. */
    private static final int VOID_STAGES = 5;
    private static final long DAY_MILLIS = 86_400_000L;

    private static final class WeatherRun {
        int index = Integer.MIN_VALUE;
        int samples;
    }

    private final GameDays days = new GameDays();
    private final Vitals vitals = new Vitals();
    private final SleepWatch sleep = new SleepWatch();
    private final WorldPortals portals = new WorldPortals();
    private final Map<UUID, WeatherRun> weather = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> samples = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> livesSeen = new ConcurrentHashMap<>();
    private final Set<UUID> referred = ConcurrentHashMap.newKeySet();
    private final Set<String> biomesLogged = ConcurrentHashMap.newKeySet();
    /** Reads switched off after a LinkageError, by name. */
    private final Set<String> off = ConcurrentHashMap.newKeySet();

    private Hooks.Context ctx;
    private Presence presence;
    private JoinCalendar calendar;
    private volatile Hardcore.Mode hardcore = Hardcore.Mode.NONE;
    private volatile int defaultLives;
    private volatile long installedAt;

    // ---- registration ------------------------------------------------------

    @Override
    public void registerOwn(Hooks.Context ctx, ComponentRegistryProxy<EntityStore> ecs, EventRegistry events) {
        this.ctx = ctx;
        try {
            ecs.registerSystem(new InstanceDiscoverySystem(ctx.tracker(), ctx.logger()));
        } catch (RuntimeException | LinkageError e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Feats instance discovery tracking is not supported on this server");
        }
        try {
            ecs.registerSystem(new WorldEventEndSystem(ctx.tracker()));
        } catch (RuntimeException | LinkageError e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Feats world event tracking is not supported on this server");
        }
        try {
            events.registerGlobal(TriggerVolumeEvent.class, this::onTriggerVolume);
        } catch (RuntimeException | LinkageError e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Feats trigger volume tracking is not supported on this server");
        }
    }

    @Override
    public void registerAlways(Hooks.Context ctx, ComponentRegistryProxy<EntityStore> ecs, EventRegistry events) {
        this.ctx = ctx;
        presence = new Presence(ctx.tracker());
        calendar = new JoinCalendar(ctx.tracker());
        try {
            events.registerGlobal(PlayerSetupConnectEvent.class, this::onSetupConnect);
        } catch (RuntimeException | LinkageError e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Feats cannot see referral connections on this server");
        }
        try {
            events.registerGlobal(PlayerGroupEvent.Added.class, this::onGroupAdded);
        } catch (RuntimeException | LinkageError e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Feats cannot see permission group changes on this server");
        }
        try {
            events.registerGlobal(ChunkPreLoadProcessEvent.class, this::onChunkLoad);
        } catch (RuntimeException | LinkageError e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Feats cannot count generated chunks on this server");
        }
        // Shared First is raised by FeatsTracker.claim, which knows whether the milestone is happening now.
    }

    @Override
    public void start(Hooks.Context ctx) {
        this.ctx = ctx;
        hardcore = Hardcore.mode(ctx.logger());
        defaultLives = hardcore == Hardcore.Mode.NONE ? 0 : Hardcore.defaultLives();
        WorldStore world = ctx.world();
        if (world != null) {
            long at = world.total(INSTALLED_AT);
            if (at <= 0) {
                at = System.currentTimeMillis();
                world.raiseTotal(INSTALLED_AT, at);
            }
            installedAt = at;
            ctx.tracker().seedPlayersKnown(); // only does anything while players_known is still 0
        }
    }

    @Override
    public void stop(Hooks.Context ctx) {
        weather.clear();
        samples.clear();
        livesSeen.clear();
        referred.clear();
    }

    // ---- the engine scheduler tick ------------------------------------------

    @Override
    public void tick(Hooks.Context ctx, long nowMillis, List<PlayerRef> online) {
        List<UUID> uuids = new ArrayList<>(online.size());
        for (PlayerRef ref : online) {
            UUID uuid = ref != null ? ref.getUuid() : null;
            if (uuid != null) {
                uuids.add(uuid);
            }
        }
        if (presence != null) {
            presence.tick(uuids);
        }
        if (calendar != null) {
            calendar.tick(uuids, nowMillis, zone(ctx), installedAt);
        }
        WorldStore world = ctx.world();
        if (world != null && serverTotals(ctx)) {
            boolean rose = world.raiseTotal(StatKeys.TOTAL_PEAK_ONLINE, uuids.size());
            if (installedAt > 0 && nowMillis > installedAt) {
                rose |= world.raiseTotal(StatKeys.TOTAL_SERVER_DAYS, (nowMillis - installedAt) / DAY_MILLIS);
            }
            if (rose) {
                ctx.tracker().checkTotals(null); // Full House and First Year, reached by nobody in particular
            }
        }
    }

    // ---- the world sample -----------------------------------------------------

    @Override
    public void sampleWorld(Hooks.Context ctx, World world, List<PlayerRef> players, boolean ownPath, long nowMillis) {
        FeatsTracker tracker = ctx.tracker();
        String worldName = world.getName();
        String worldKey = WorldKeys.worldKey(worldName);
        Store<EntityStore> store = world.getEntityStore().getStore();

        // World level reads, once per world task.
        WorldTimeResource time = time(store);
        boolean newDay = false;
        boolean night = false;
        int moon = -1;
        Instant gameTime = null;
        if (time != null) {
            try {
                LocalDateTime dateTime = time.getGameDateTime();
                LocalDate date = dateTime != null ? dateTime.toLocalDate() : null;
                newDay = days.rollover(worldKey, date);
                night = time.getSunlightFactor() < NIGHT_SUNLIGHT;
                moon = time.getMoonPhase();
                gameTime = time.getGameTime();
            } catch (RuntimeException e) {
                skipped(e);
            }
        }
        PortalWorld portal = ownPath ? portal(store) : null;
        boolean inPortal = false;
        boolean timed = false;
        double remaining = -1;
        int voidStage = 0;
        if (portal != null) {
            try {
                inPortal = portal.exists();
                if (inPortal) {
                    timed = portal.hasTimeLimit();
                    remaining = timed ? portal.getRemainingSeconds(world) : -1;
                    voidStage = voidStage(portal, world);
                }
            } catch (RuntimeException e) {
                skipped(e);
                inPortal = false;
            }
        }
        boolean worldAsleep = false;
        if (ownPath) {
            Instant target = slumberTarget(store);
            worldAsleep = target != null;
            for (UUID slept : sleep.world(worldKey, target, gameTime)) {
                tracker.increment(slept, StatKeys.NIGHTS_SLEPT, 1);
            }
        }

        for (int i = 0, n = players.size(); i < n; i++) {
            PlayerRef player = players.get(i);
            try {
                UUID uuid = player.getUuid();
                Ref<EntityStore> ref = player.getReference();
                if (uuid == null || ref == null || !ref.isValid()) {
                    continue;
                }
                if (!store.isInThread() || ref.getStore() != store) {
                    continue; // moved to another world since the tick grouped them, sampled there next time
                }
                PlayerProgress progress = tracker.loadedProgress(uuid);
                if (progress == null) {
                    continue;
                }
                int count = samples.merge(uuid, 1, Integer::sum);
                if (ownPath) {
                    biome(tracker, uuid, ref, store);
                    weather(tracker, uuid, ref, store);
                    if (night && moon >= 0) {
                        tracker.discover(uuid, StatKeys.MOON_PREFIX, WorldKeys.moonKey(moon), StatKeys.MOONS_SEEN);
                    }
                    if (inPortal) {
                        tracker.increment(uuid, StatKeys.PORTAL_SECONDS, 1);
                        if (voidStage > 0) {
                            tracker.raiseTo(uuid, StatKeys.VOID_STAGE_MAX, voidStage);
                        }
                        portals.inside(uuid, worldKey, timed, remaining, nowMillis);
                    } else if (portals.elsewhere(uuid, worldKey, nowMillis)) {
                        tracker.increment(uuid, StatKeys.PORTAL_CLOSE_EXITS, 1);
                    }
                    if (worldAsleep && asleep(ref, store)) {
                        sleep.sleeping(worldKey, uuid);
                    }
                }
                vitals(tracker, uuid, ref, store);
                if (count % SLOW_EVERY == 0) {
                    respawnPoints(tracker, uuid, ref, store, worldName);
                    lives(tracker, uuid, ref, store, progress);
                }
                if (days.died(uuid, progress.get(StatKeys.DEATHS))) {
                    progress.set(StatKeys.LIFE_DAYS, 0);
                }
                if (newDay) {
                    dayPassed(tracker, uuid, progress);
                }
            } catch (RuntimeException e) {
                skipped(e);
            }
        }
    }

    private void dayPassed(FeatsTracker tracker, UUID uuid, PlayerProgress progress) {
        tracker.increment(uuid, StatKeys.GAME_DAYS, 1);
        tracker.increment(uuid, StatKeys.LIFE_DAYS, 1);
        tracker.raiseTo(uuid, StatKeys.LONGEST_LIFE_DAYS, progress.get(StatKeys.LIFE_DAYS));
        if (hardcore != Hardcore.Mode.NONE) {
            tracker.increment(uuid, StatKeys.HARDCORE_DAYS, 1);
            tracker.increment(uuid, StatKeys.HARDCORE_CLEAN_DAYS, 1);
        }
    }

    // ---- per world reads --------------------------------------------------------

    private WorldTimeResource time(Store<EntityStore> store) {
        if (off.contains("time")) {
            return null;
        }
        try {
            return store.getResource(WorldTimeResource.getResourceType());
        } catch (LinkageError e) {
            disable("time", e, "the world clock", "game days, moon phases and nights slept");
            return null;
        } catch (RuntimeException e) {
            skipped(e);
            return null;
        }
    }

    private PortalWorld portal(Store<EntityStore> store) {
        if (off.contains("portal")) {
            return null;
        }
        try {
            return store.getResource(PortalWorld.getResourceType());
        } catch (LinkageError e) {
            disable("portal", e, "portal worlds", "portal time, void stages and close exits");
            return null;
        } catch (RuntimeException e) {
            skipped(e);
            return null;
        }
    }

    /** The highest void event stage the portal world has reached, 0 for none. */
    private static int voidStage(PortalWorld portal, World world) {
        if (!portal.isVoidInvasionEnabled()) {
            return 0;
        }
        for (int stage = VOID_STAGES; stage >= 1; stage--) {
            if (portal.hasVoidEventReached(world, stage)) {
                return stage;
            }
        }
        return 0;
    }

    /** The game time a slumbering world is heading for, null while it is awake or cannot be read. */
    private Instant slumberTarget(Store<EntityStore> store) {
        if (off.contains("sleep")) {
            return null;
        }
        try {
            WorldSomnolence somnolence = store.getResource(WorldSomnolence.getResourceType());
            WorldSleep state = somnolence != null ? somnolence.getState() : null;
            return state instanceof WorldSlumber slumber ? slumber.getTargetInstant() : null;
        } catch (LinkageError e) {
            disable("sleep", e, "the sleep state", "nights slept");
            return null;
        } catch (RuntimeException e) {
            skipped(e);
            return null;
        }
    }

    private boolean asleep(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (off.contains("sleep")) {
            return false;
        }
        try {
            PlayerSomnolence somnolence = store.getComponent(ref, PlayerSomnolence.getComponentType());
            return somnolence != null && somnolence.getSleepState() instanceof PlayerSleep.Slumber;
        } catch (LinkageError e) {
            disable("sleep", e, "the sleep state", "nights slept");
            return false;
        }
    }

    // ---- per player reads ---------------------------------------------------------

    private void biome(FeatsTracker tracker, UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (off.contains("biome")) {
            return;
        }
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            WorldMapTracker map = player != null ? player.getWorldMapTracker() : null;
            String biome = map != null ? map.getCurrentBiomeName() : null;
            if (biome == null || biome.isBlank()) {
                return;
            }
            if (biomesLogged.size() < 512 && biomesLogged.add(biome)) {
                ctx.logger().at(Level.INFO).log("Feats saw biome %s", biome);
            }
            tracker.discover(uuid, StatKeys.BIOME_PREFIX, biome, StatKeys.BIOMES_VISITED);
        } catch (LinkageError e) {
            disable("biome", e, "the current biome", "biomes visited");
        }
    }

    private void weather(FeatsTracker tracker, UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (off.contains("weather")) {
            return;
        }
        try {
            WeatherTracker component = store.getComponent(ref, WeatherTracker.getComponentType());
            if (component == null) {
                return;
            }
            int index = component.getWeatherIndex();
            WeatherRun run = weather.computeIfAbsent(uuid, u -> new WeatherRun());
            if (run.index != index) {
                run.index = index;
                run.samples = 0;
            }
            run.samples++;
            if (run.samples != WEATHER_SAMPLES) {
                return; // counted once at the tenth sample, a later change starts a new run
            }
            Weather asset = index >= 0 ? Weather.getAssetMap().getAsset(index) : null;
            String id = asset != null ? asset.getId() : null;
            if (id != null && !id.isBlank()) {
                tracker.discover(uuid, StatKeys.WEATHER_PREFIX, id, StatKeys.WEATHERS_SEEN);
            }
        } catch (LinkageError e) {
            disable("weather", e, "the weather", "weathers seen");
        }
    }

    private void vitals(FeatsTracker tracker, UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (off.contains("stats")) {
            return;
        }
        try {
            EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
            if (stats == null) {
                return;
            }
            EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
            EntityStatValue oxygen = stats.get(DefaultEntityStatTypes.getOxygen());
            EntityStatValue mana = stats.get(DefaultEntityStatTypes.getMana());
            Vitals.Events events = vitals.sample(uuid,
                    value(health), max(health), value(oxygen), max(oxygen), value(mana), max(mana));
            if (events.closeCall()) {
                tracker.increment(uuid, StatKeys.CLOSE_CALLS, 1);
            }
            if (events.oxygenLow()) {
                tracker.increment(uuid, StatKeys.OXYGEN_LOW, 1);
            }
            if (events.manaEmptied()) {
                tracker.increment(uuid, StatKeys.MANA_EMPTIED, 1);
            }
        } catch (LinkageError e) {
            disable("stats", e, "entity stats", "close calls, low oxygen and emptied mana");
        }
    }

    private static float value(EntityStatValue stat) {
        return stat == null ? -1f : stat.get();
    }

    private static float max(EntityStatValue stat) {
        return stat == null ? -1f : stat.getMax();
    }

    private void respawnPoints(FeatsTracker tracker, UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, String worldName) {
        if (off.contains("respawn") || worldName == null) {
            return;
        }
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerConfigData config = player != null ? player.getPlayerConfigData() : null;
            PlayerWorldData data = config != null ? config.getPerWorldData(worldName) : null;
            PlayerRespawnPointData[] points = data != null ? data.getRespawnPoints() : null;
            if (points != null && points.length > 0) {
                tracker.raiseTo(uuid, StatKeys.RESPAWN_POINTS, points.length);
            }
        } catch (LinkageError e) {
            disable("respawn", e, "respawn points", "respawn points held");
        }
    }

    private void lives(FeatsTracker tracker, UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, PlayerProgress progress) {
        if (hardcore == Hardcore.Mode.NONE || off.contains("lives")) {
            return;
        }
        try {
            int lives;
            if (hardcore == Hardcore.Mode.GLOBAL) {
                HardcoreState state = Universe.get().getResource(HardcoreState.getResourceType());
                if (state == null) {
                    return;
                }
                lives = state.getPoolRemaining(defaultLives);
            } else {
                lives = PlayerLives.getRemainingOrDefault(ref, store, defaultLives);
            }
            if (lives == 1) {
                tracker.raiseTo(uuid, StatKeys.LIVES_LEFT_MIN, 1);
            }
            Integer last = livesSeen.put(uuid, lives);
            if (last != null && lives < last) {
                progress.set(StatKeys.HARDCORE_CLEAN_DAYS, 0); // a life was lost, the clean run starts over
            }
        } catch (LinkageError e) {
            disable("lives", e, "hardcore lives", "last life and clean days");
        }
    }

    // ---- bus listeners ------------------------------------------------------------

    private void onTriggerVolume(TriggerVolumeEvent event) {
        try {
            if (event.getTriggerEventType() != TriggerEventType.ENTER) {
                return;
            }
            String prefix = WorldKeys.volumePrefix(event.getVolumeId());
            if (prefix == null) {
                return;
            }
            UUID uuid = event.getEntityUuid();
            if (uuid == null || !ctx.tracker().isLoaded(uuid)) {
                uuid = playerOf(event.getEntityRef());
            }
            if (uuid != null) {
                ctx.tracker().increment(uuid, StatKeys.VOLUME_PREFIX + prefix, 1);
            }
        } catch (RuntimeException | LinkageError e) {
            PlayerEventSystem.failed(e);
        }
    }

    /** The player behind an entity ref, read only when this is the store's thread. */
    private static UUID playerOf(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        if (!store.isInThread()) {
            return null;
        }
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        return player != null ? player.getUuid() : null;
    }

    private void onSetupConnect(PlayerSetupConnectEvent event) {
        try {
            UUID uuid = event.getUuid();
            if (uuid != null && event.isReferralConnection()) {
                referred.add(uuid);
            }
        } catch (RuntimeException | LinkageError e) {
            PlayerEventSystem.failed(e);
        }
    }

    private void onGroupAdded(PlayerGroupEvent.Added event) {
        try {
            UUID uuid = event.getPlayerUuid();
            String group = event.getGroupName();
            if (uuid == null || group == null || group.isBlank()) {
                return;
            }
            FeatsTracker tracker = ctx.tracker();
            if (tracker.isLoaded(uuid)) {
                tracker.increment(uuid, StatKeys.GROUP_ADDED_PREFIX + group, 1);
                tracker.increment(uuid, StatKeys.GROUPS_ADDED, 1);
            } else {
                tracker.addOffline(uuid, StatKeys.GROUP_ADDED_PREFIX + group, 1);
                tracker.addOffline(uuid, StatKeys.GROUPS_ADDED, 1);
            }
        } catch (RuntimeException | LinkageError e) {
            PlayerEventSystem.failed(e);
        }
    }

    private void onChunkLoad(ChunkPreLoadProcessEvent event) {
        try {
            WorldStore world = ctx.world();
            if (world != null && event.isNewlyGenerated() && serverTotals(ctx)) {
                long total = world.addTotal(StatKeys.TOTAL_CHUNKS_GENERATED, 1);
                if (total % 1000 == 0) {
                    ctx.tracker().checkTotals(null); // Chunk Factory, checked once per thousand so the chunk thread stays cheap
                }
            }
        } catch (RuntimeException | LinkageError e) {
            PlayerEventSystem.failed(e);
        }
    }

    // ---- joins and leaves -----------------------------------------------------------

    @Override
    public void joined(Hooks.Context ctx, UUID uuid, PlayerRef player, PlayerProgress progress, long nowMillis) {
        if (calendar != null) {
            calendar.joined(uuid, progress, nowMillis, zone(ctx), installedAt);
        }
        if (referred.remove(uuid)) {
            ctx.tracker().increment(uuid, StatKeys.REFERRAL_JOINS, 1);
        }
        WorldStore world = ctx.world();
        if (world != null && serverTotals(ctx) && progress.get(StatKeys.JOINS) == 1) {
            world.addTotal(StatKeys.TOTAL_PLAYERS_KNOWN, 1);
            ctx.tracker().checkTotals(uuid); // A Hundred Players and A Thousand Players name the player who arrived
        }
    }

    @Override
    public void left(Hooks.Context ctx, UUID uuid, long nowMillis) {
        days.forget(uuid);
        vitals.forget(uuid);
        sleep.forget(uuid);
        portals.forget(uuid);
        weather.remove(uuid);
        samples.remove(uuid);
        livesSeen.remove(uuid);
        referred.remove(uuid);
        if (presence != null) {
            presence.forget(uuid);
        }
    }

    // ---- small helpers --------------------------------------------------------------

    private static ZoneId zone(Hooks.Context ctx) {
        FeatsConfig config = ctx.config() != null ? ctx.config().get() : null;
        return config != null ? config.calendarZone() : ZoneId.systemDefault();
    }

    private static boolean serverTotals(Hooks.Context ctx) {
        FeatsConfig config = ctx.config() != null ? ctx.config().get() : null;
        return config == null || config.serverTotals();
    }

    private void disable(String read, Throwable cause, String what, String counters) {
        if (off.add(read)) {
            HytaleLogger logger = ctx != null ? ctx.logger() : null;
            if (logger != null) {
                logger.at(Level.WARNING).withCause(cause).log("Feats cannot read %s on this server, %s are off", what, counters);
            }
        }
    }

    private void skipped(RuntimeException cause) {
        HytaleLogger logger = ctx != null ? ctx.logger() : null;
        if (logger != null) {
            logger.at(Level.FINE).withCause(cause).log("Feats skipped a world sample read");
        }
    }
}
