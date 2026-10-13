package dev.ryvrook.feats;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.IEventDispatcher;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.ChangeGameModeEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import dev.ryvrook.feats.api.FeatUnlockedEvent;
import dev.ryvrook.feats.api.FeatsApi;
import dev.ryvrook.feats.command.FeatsCommand;
import dev.ryvrook.feats.export.LeaderboardExporter;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.placeholders.FeatsExpansion;
import dev.ryvrook.feats.rewards.RewardService;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.stats.WorldStore;
import dev.ryvrook.feats.systems.BreakBlockSystem;
import dev.ryvrook.feats.systems.CraftSystem;
import dev.ryvrook.feats.systems.DamageSystem;
import dev.ryvrook.feats.systems.DeathSystem;
import dev.ryvrook.feats.systems.FlagWatcher;
import dev.ryvrook.feats.systems.InteractionSystem;
import dev.ryvrook.feats.systems.InventorySystem;
import dev.ryvrook.feats.systems.PickupSystem;
import dev.ryvrook.feats.systems.PlaceBlockSystem;
import dev.ryvrook.feats.systems.UseBlockSystem;
import dev.ryvrook.feats.systems.ZoneDiscoverySystem;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.MemoryTracker;
import dev.ryvrook.feats.track.PlacedBlocks;
import dev.ryvrook.feats.track.PortalTracker;
import dev.ryvrook.feats.track.RecentDrops;
import dev.ryvrook.feats.track.RecentPlacements;
import dev.ryvrook.feats.track.UnlockNotifier;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Feats adds achievements and lifetime statistics to Hytale.
 *
 * <p>Everything is server side. Progress is stored per player as JSON under the mod's data
 * folder, so it works in singleplayer worlds and on dedicated servers alike.
 */
public final class FeatsPlugin extends JavaPlugin {
    private static final double MAX_STEP_PER_SECOND = 64.0; // ignore teleports and world changes
    private static final long CHAT_GAP_MILLIS = 5_000L; // at most one counted chat message per 5 seconds
    private static final long OFFLINE_KEEP_MILLIS = 60L * 60 * 1000; // anti farming memory outlives a relog by an hour
    private static final long IO_DRAIN_SECONDS = 10L;

    private final Config<FeatsConfig> config;
    private FeatRegistry registry;
    private ProgressStore store;
    private FeatsTracker tracker;
    private MemoryTracker memories;
    private WorldStore world;
    private FlagWatcher flags;
    private final RecentPlacements placements = new RecentPlacements();
    private final PlacedBlocks placedBlocks = new PlacedBlocks();
    private final RecentDrops drops = new RecentDrops();
    private final Map<UUID, Long> lastChatAt = new ConcurrentHashMap<>();
    /** When each player left. Their placement ring, drops and chat timer are dropped an hour later. */
    private final Map<UUID, Long> offlineSince = new ConcurrentHashMap<>();

    private final Map<UUID, Vector3d> lastPositions = new ConcurrentHashMap<>();
    private final Map<UUID, Double> distanceRemainder = new ConcurrentHashMap<>();
    private ScheduledFuture<?> ticker;
    private ScheduledFuture<?> autosave;
    private LeaderboardExporter exporter;
    private ScheduledFuture<?> export;
    private volatile boolean exporting;
    /**
     * The one thread Feats does its file reads and writes on. Owned by this plugin instance, so a
     * /plugin reload stops the old one in shutdown and the new instance starts its own.
     */
    private ScheduledExecutorService io;
    private final AtomicBoolean autosaveQueued = new AtomicBoolean();
    private final AtomicBoolean exportQueued = new AtomicBoolean();

    private RewardService rewards;
    private FeatsApi api;
    /** Players in Creative mode right now, kept whether or not IgnoreCreative is on. */
    private final Set<UUID> creative = ConcurrentHashMap.newKeySet();
    /** The lower case name of the world each player is in. */
    private final Map<UUID, String> worldOf = new ConcurrentHashMap<>();
    private volatile Set<String> disabledWorlds = Set.of();
    private volatile boolean ignoreCreative = true;

    public FeatsPlugin(JavaPluginInit init) {
        super(init);
        this.config = withConfig("Feats", FeatsConfig.CODEC);
    }

    public FeatsConfig config() {
        return config.get();
    }

    public FeatsTracker tracker() {
        return tracker;
    }

    public ProgressStore store() {
        return store;
    }

    /** Server milestones and firsts from server.json. */
    public WorldStore world() {
        return world;
    }

    public LeaderboardExporter exporter() {
        return exporter;
    }

    /** The plugin's own file thread. Use {@link #runIo} to hand it work. */
    public ScheduledExecutorService io() {
        return io;
    }

    /**
     * Runs a blocking file task on the Feats IO thread. Failures are logged by the executor. Throws
     * {@link RejectedExecutionException} once the plugin is shutting down.
     */
    public void runIo(Runnable task) {
        io.execute(task);
    }

    /** The rewards from Rewards.json. Inert while RewardsEnabled is false. */
    public RewardService rewards() {
        return rewards;
    }

    /**
     * Re-reads Feats.json and, when rewards are on, Rewards.json. Runs the file work on the IO thread
     * and completes with a short summary. Timers such as AutosaveSeconds keep their old period until
     * the next restart.
     */
    public CompletableFuture<String> reload() {
        CompletableFuture<String> result = new CompletableFuture<>();
        try {
            runIo(() -> {
                try {
                    config.load().join();
                    applyConfig();
                    String rewardLine = rewards.reload();
                    result.complete("Feats.json reloaded. " + rewardLine + ".");
                } catch (RuntimeException e) {
                    getLogger().at(Level.WARNING).withCause(e).log("Feats could not reload its config");
                    result.complete("Feats.json could not be read, see the server log.");
                }
            });
        } catch (RejectedExecutionException e) {
            result.complete("Feats is shutting down.");
        }
        return result;
    }

    /** Copies the settings read on hot paths into fields and recomputes everyone's pause. */
    private void applyConfig() {
        FeatsConfig cfg = config();
        disabledWorlds = cfg.disabledWorlds();
        ignoreCreative = cfg.ignoreCreative();
        if (tracker != null) {
            tracker.setPausePlaytime(cfg.pausePlaytimeWhenDisabled());
            Set<UUID> everyone = new HashSet<>(worldOf.keySet());
            everyone.addAll(creative);
            for (UUID uuid : everyone) {
                refreshPause(uuid);
            }
        }
    }

    /** Pauses a player's counters while they are in Creative mode or in a disabled world. */
    private void refreshPause(UUID uuid) {
        boolean pause = ignoreCreative && creative.contains(uuid);
        if (!pause && !disabledWorlds.isEmpty()) {
            String world = worldOf.get(uuid);
            pause = world != null && disabledWorlds.contains(world);
        }
        tracker.setPaused(uuid, pause);
    }

    private void setCreative(UUID uuid, boolean isCreative) {
        boolean changed = isCreative ? creative.add(uuid) : creative.remove(uuid);
        if (changed) {
            refreshPause(uuid);
        }
    }

    private void setWorld(UUID uuid, String worldName) {
        if (worldName == null) {
            return;
        }
        String key = worldName.toLowerCase(Locale.ROOT);
        if (!key.equals(worldOf.put(uuid, key))) {
            refreshPause(uuid);
        }
    }

    /** True while the leaderboard export runs, which also keeps the ranking {@code /feats top} reads. */
    public boolean exporting() {
        return exporting;
    }

    /**
     * The engine registers the UI asset pack of this jar right after setup and refuses it when a pack with
     * the same name is already registered. On a boot load that is harmless because the earlier registration
     * points at this very file. After the jar was replaced by a file with another name, or when the old
     * file is gone, the earlier registration is stale and would block every load until a restart, so
     * it is removed here first.
     */
    private void dropStaleAssetPack() {
        AssetModule assets;
        AssetPack stale;
        try {
            assets = AssetModule.get();
            if (assets == null) {
                return;
            }
            stale = findPack(assets, getIdentifier().toString());
            if (stale == null) {
                return;
            }
            Path mine = getFile();
            Path theirs = stale.getPackLocation();
            boolean sameFile = false;
            if (mine != null && theirs != null && Files.exists(theirs)) {
                try {
                    sameFile = Files.isSameFile(mine, theirs);
                } catch (IOException ignored) {
                    // treat it as a different file
                }
            }
            // A pack the boot scan registered from this very jar is left alone, the engine reuses it. One
            // registered by a runtime load is always replaced, because the engine compares the path text and
            // its runtime path never matches the one it checks on reload.
            if (sameFile && stale.getSource() != AssetPack.PackSource.RUNTIME) {
                return;
            }
        } catch (RuntimeException | LinkageError e) {
            getLogger().at(Level.FINE).withCause(e).log("Feats could not check the asset pack registration");
            return;
        }
        String name = stale.getName();
        Path location = stale.getPackLocation();
        try {
            assets.unregisterPack(name);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            // The engine throws checked exceptions here without declaring them, for example when the old
            // jar no longer exists. The registration is usually gone by then, so check instead of giving up.
            getLogger().at(Level.FINE).withCause(e).log("Feats hit an error while removing the stale asset pack");
        }
        if (findPack(assets, name) == null) {
            getLogger().at(Level.INFO).log("Feats refreshed the asset pack registration from %s so this jar can register its own", location);
            return;
        }
        if (forgetPack(assets, name)) {
            getLogger().at(Level.INFO).log("Feats dropped a stale asset pack registration from %s so this jar can register its own", location);
        } else {
            getLogger().at(Level.WARNING).log("Feats found a stale asset pack registration from %s and could not remove it. Restart the server if the Feats pages do not open.", location);
        }
    }


    /** One line at start saying whether the UI pack of this jar is registered, which the /feats page needs. */
    private void reportAssetPack() {
        try {
            AssetModule assets = AssetModule.get();
            AssetPack pack = assets == null ? null : findPack(assets, getIdentifier().toString());
            if (pack == null) {
                getLogger().at(Level.WARNING).log("Feats UI pack is not registered, the /feats page will not open until the server restarts");
            } else {
                getLogger().at(Level.INFO).log("Feats UI pack registered from %s", pack.getPackLocation());
            }
        } catch (RuntimeException | LinkageError e) {
            getLogger().at(Level.FINE).withCause(e).log("Feats could not report the asset pack");
        }
    }

    private static AssetPack findPack(AssetModule assets, String name) {
        for (AssetPack pack : assets.getAssetPacks()) {
            if (pack != null && name.equalsIgnoreCase(pack.getName())) {
                return pack;
            }
        }
        return null;
    }

    /** Last resort when the engine's own removal failed partway, drops the entry from its pack list directly. */
    private static boolean forgetPack(AssetModule assets, String name) {
        try {
            java.lang.reflect.Field field = AssetModule.class.getDeclaredField("assetPacks");
            field.setAccessible(true);
            Object value = field.get(assets);
            if (value instanceof java.util.List<?> list) {
                return list.removeIf(o -> o instanceof AssetPack pack && name.equalsIgnoreCase(pack.getName()));
            }
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable ignored) {
            // the engine changed, leave it to a restart
        }
        return false;
    }

    @Override
    protected void setup() {
        dropStaleAssetPack();
        config.save();

        io = newIoExecutor();
        registry = new FeatRegistry();
        store = new ProgressStore(getDataDirectory(), getLogger());
        world = new WorldStore(getDataDirectory(), getLogger());
        loadWorld();
        UnlockNotifier notifier = new UnlockNotifier(this::config, getLogger());
        tracker = new FeatsTracker(registry, store, notifier, getLogger(), io, world);
        exporter = new LeaderboardExporter(this);
        notifier.setRarity(exporter::rarity);
        tracker.setUnlockListener(exporter::recordUnlock);
        rewards = new RewardService(this::config, tracker, getDataDirectory(), getLogger());
        tracker.addUnlockHook(rewards::onUnlock);
        tracker.setDeferredRewards(rewards::onUnlock); // feats unlocked while offline, rewarded at the next join
        tracker.addUnlockHook(this::publishUnlock);
        applyConfig();
        if (config().rewardsEnabled()) {
            runIo(rewards::reload); // nothing reads Rewards.json while rewards are off
        }
        api = FeatsApi.install(this);

        getCommandRegistry().registerCommand(new FeatsCommand(this));

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        // Late, so moderation plugins have cancelled and chat plugins have set their formatter before the title looks.
        getEventRegistry().registerAsyncGlobal(EventPriority.LATE, PlayerChatEvent.class,
                future -> future.thenApply(this::onPlayerChat));
        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, this::onAddPlayerToWorld);

        var ecs = getEntityStoreRegistry();
        ecs.registerSystem(new BreakBlockSystem(tracker, placedBlocks));
        ecs.registerSystem(new PlaceBlockSystem(tracker, placements, placedBlocks));
        ecs.registerSystem(new CraftSystem(tracker));
        ecs.registerSystem(new PickupSystem(tracker, drops));
        UseBlockSystem useBlocks = new UseBlockSystem(tracker);
        ecs.registerSystem(useBlocks);
        ecs.registerSystem(new ZoneDiscoverySystem(tracker));
        ecs.registerSystem(new DamageSystem(tracker));
        ecs.registerSystem(new DeathSystem(tracker));
        try {
            ecs.registerSystem(new GameModeSystem());
        } catch (LinkageError | RuntimeException e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats cannot see game mode changes on this server");
        }

        // Chapter I hooks. Each one is optional so a server without them still loads every other feat.
        try {
            ecs.registerSystem(new InteractionSystem(tracker));
        } catch (LinkageError | RuntimeException e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats ability tracking is not supported on this server");
        }
        try {
            ecs.registerSystem(new InventorySystem(tracker, placements, drops));
        } catch (LinkageError | RuntimeException e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats inventory tracking is not supported on this server");
        }
        try {
            PortalTracker portals = new PortalTracker(tracker, getLogger());
            getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, portals::onAddPlayerToWorld);
        } catch (LinkageError | RuntimeException e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats portal tracking is not supported on this server");
        }
        memories = new MemoryTracker(tracker, getLogger());
        try {
            flags = new FlagWatcher(tracker, useBlocks, getLogger());
        } catch (LinkageError | RuntimeException e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats cannot watch the Goblin Lab flags on this server");
        }

        getLogger().at(Level.INFO).log("Feats loaded %d feats worth %d points, plus %d world feats", registry.count(),
                registry.totalPoints(), registry.countServer());
    }

    /** Reads server.json on the IO thread and waits for it, so milestones are known before anyone joins. */
    private void loadWorld() {
        try {
            io.submit(world::load).get(IO_DRAIN_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | java.util.concurrent.TimeoutException | RejectedExecutionException e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats could not read %s", world.file());
        }
    }

    @Override
    protected void start() {
        reportAssetPack();
        loadOnlinePlayers();
        FeatsExpansion.registerIfPresent(this); // does nothing without PlaceholderAPI
        if (flags != null) {
            try {
                flags.start();
            } catch (LinkageError | RuntimeException e) {
                getLogger().at(Level.WARNING).withCause(e).log("Feats cannot watch the Goblin Lab flags on this server");
            }
        }
        ticker = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        int every = config().autosaveSeconds();
        autosave = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(this::autosave, every, every, TimeUnit.SECONDS);
        int exportEvery = config().exportSeconds();
        if (exportEvery > 0) {
            // Same split as every later export, the online count on the engine scheduler and the file work on IO.
            HytaleServer.SCHEDULED_EXECUTOR.execute(() -> {
                int online = LeaderboardExporter.onlineNow();
                try {
                    runIo(() -> exporter.flush(online));
                } catch (RejectedExecutionException ignored) {
                    // already shutting down, the shutdown export covers it
                }
            });
            export = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(this::scheduleExport, exportEvery, exportEvery,
                    TimeUnit.SECONDS);
            exporting = true;
        }
    }

    @Override
    protected void shutdown() {
        FeatsExpansion.unregister(this);
        FeatsApi.uninstall(api);
        if (rewards != null) {
            rewards.stop();
        }
        if (ticker != null) {
            ticker.cancel(false);
        }
        if (flags != null) {
            flags.stop();
        }
        if (autosave != null) {
            autosave.cancel(false);
        }
        if (export != null) {
            export.cancel(false);
        }
        exporting = false;
        if (tracker != null) {
            tracker.saveAll();
        }
        if (world != null && world.isDirty()) {
            world.save();
        }
        if (export != null && exporter != null) {
            exporter.flush(LeaderboardExporter.onlineNow());
        }
        stopIo();
    }

    /** Lets queued saves finish, then stops the IO thread. Waits up to 10 seconds. */
    private void stopIo() {
        if (io == null) {
            return;
        }
        io.shutdown();
        try {
            if (!io.awaitTermination(IO_DRAIN_SECONDS, TimeUnit.SECONDS)) {
                getLogger().at(Level.WARNING).log("Feats file thread did not finish within %d seconds", IO_DRAIN_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            getLogger().at(Level.WARNING).log("Feats was interrupted while waiting for its file thread");
        }
    }

    /**
     * A single daemon thread named Feats-IO. Tasks given to {@code execute} keep their exception in a
     * future, so afterExecute digs it out and logs it. The uncaught handler covers anything else.
     */
    private ScheduledExecutorService newIoExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "Feats-IO");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) ->
                    getLogger().at(Level.SEVERE).withCause(e).log("Feats file thread failed"));
            return thread;
        }) {
            @Override
            protected void afterExecute(Runnable task, Throwable failure) {
                super.afterExecute(task, failure);
                if (failure == null && task instanceof Future<?> future && future.isDone() && !future.isCancelled()) {
                    try {
                        future.get();
                    } catch (ExecutionException e) {
                        failure = e.getCause();
                    } catch (CancellationException | InterruptedException ignored) {
                        // nothing to report
                    }
                }
                if (failure != null) {
                    getLogger().at(Level.WARNING).withCause(failure).log("Feats file task failed");
                }
            }
        };
        executor.setRemoveOnCancelPolicy(true);
        // Delayed work such as a Discord post waiting out a rate limit is dropped at shutdown, so draining
        // the queue never waits on it and nothing runs after the plugin is gone.
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    // ---- player lifecycle ------------------------------------------------

    private void onPlayerReady(PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        offlineSince.remove(uuid);
        readScope(uuid, ref, store);
        boolean firstReadyThisSession = !tracker.isLoaded(uuid);
        PlayerProgress progress = tracker.load(uuid, playerRef.getUsername());
        lastPositions.remove(uuid);
        // The player is in the world now, so feats already met when their file loaded can announce themselves.
        tracker.catchUp(uuid);
        if (!firstReadyThisSession) {
            return; // PlayerReady fires again on world changes, so only the first one counts
        }
        tracker.increment(uuid, StatKeys.JOINS, 1);
        if (config().welcomeHint()) {
            int unlocked = progress.unlockedCount();
            playerRef.sendMessage(Message.join(
                    Message.raw("Feats ").color("#E8A93B").bold(true),
                    Message.raw(unlocked + "/" + registry.count() + " unlocked · ").color("#96a9be"),
                    Message.raw("/feats").color("#d3d6db"),
                    Message.raw(" to browse").color("#96a9be")));
        }
    }

    /** Reads the player's game mode and world. Runs on the world thread that owns the store. */
    private void readScope(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                setCreative(uuid, player.getGameMode() == GameMode.Creative);
            }
            EntityStore entities = store.getExternalData();
            World world = entities != null ? entities.getWorld() : null;
            if (world != null) {
                setWorld(uuid, world.getName());
            }
        } catch (RuntimeException | LinkageError e) {
            getLogger().at(Level.FINE).withCause(e).log("Feats could not read the game mode of %s", uuid);
        }
    }

    /** Every world entry, including the first, records the world name for DisabledWorlds. */
    private void onAddPlayerToWorld(AddPlayerToWorldEvent event) {
        try {
            World world = event.getWorld();
            if (world == null || event.getHolder() == null) {
                return;
            }
            PlayerRef playerRef = event.getHolder().getComponent(PlayerRef.getComponentType());
            if (playerRef != null) {
                setWorld(playerRef.getUuid(), world.getName());
            }
        } catch (RuntimeException | LinkageError e) {
            getLogger().at(Level.FINE).withCause(e).log("Feats could not read a world change");
        }
    }

    /**
     * Follows game mode changes. The event fires before the change and a later system may still cancel
     * it, so the mode is read back from the player on the world thread straight after.
     */
    private final class GameModeSystem extends EntityEventSystem<EntityStore, ChangeGameModeEvent> {
        GameModeSystem() {
            super(ChangeGameModeEvent.class);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Player.getComponentType();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> commandBuffer, ChangeGameModeEvent event) {
            try {
                if (event.isCancelled()) {
                    return;
                }
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) {
                    return;
                }
                UUID uuid = playerRef.getUuid();
                setCreative(uuid, event.getGameMode() == GameMode.Creative);
                World world = store.getExternalData().getWorld();
                world.execute(() -> {
                    if (ref.isValid()) {
                        readScope(uuid, ref, ref.getStore());
                    }
                });
            } catch (RuntimeException | LinkageError e) {
                getLogger().at(Level.FINE).withCause(e).log("Feats could not follow a game mode change");
            }
        }
    }

    /**
     * After a /plugin reload nobody fires PlayerReady for the players already online, so they are
     * loaded here. On a normal boot nobody is online yet and this does nothing.
     */
    private void loadOnlinePlayers() {
        try {
            Universe universe = Universe.get();
            if (universe == null) {
                return;
            }
            for (PlayerRef ref : universe.getPlayers()) {
                try {
                    tracker.load(ref.getUuid(), ref.getUsername());
                    tracker.catchUp(ref.getUuid()); // already in a world, so toasts can go out straight away
                    World world = ref.getWorldUuid() == null ? null : universe.getWorld(ref.getWorldUuid());
                    if (world != null) {
                        setWorld(ref.getUuid(), world.getName());
                        world.execute(() -> {
                            Ref<EntityStore> entity = ref.getReference();
                            if (entity != null && entity.isValid()) {
                                readScope(ref.getUuid(), entity, entity.getStore());
                            }
                        });
                    }
                } catch (Exception e) {
                    getLogger().at(Level.WARNING).withCause(e).log("Feats could not load online player %s", ref.getUuid());
                }
            }
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats could not load the players already online");
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        tracker.unload(uuid);
        creative.remove(uuid);
        worldOf.remove(uuid);
        tracker.setPaused(uuid, false);
        lastPositions.remove(uuid);
        distanceRemainder.remove(uuid);
        placements.forget(uuid);
        // The placement ring, the drop memory and the chat timer survive a relog, see pruneOffline.
        offlineSince.put(uuid, System.currentTimeMillis());
    }

    private PlayerChatEvent onPlayerChat(PlayerChatEvent event) {
        try {
            if (!event.isCancelled() && event.getSender() != null) {
                UUID uuid = event.getSender().getUuid();
                long now = System.currentTimeMillis();
                boolean[] counts = {false};
                lastChatAt.compute(uuid, (id, last) -> {
                    if (last == null || now - last >= CHAT_GAP_MILLIS) {
                        counts[0] = true;
                        return now;
                    }
                    return last;
                });
                if (counts[0]) {
                    tracker.increment(uuid, StatKeys.CHAT, 1);
                }
                applyTitle(event, uuid);
            }
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats chat tracking failed");
        }
        return event;
    }

    /**
     * Puts the player's title in front of their chat line when Titles is PREFIX. Only touches events
     * still using the engine's default formatter, so a chat plugin's own format always wins.
     */
    private void applyTitle(PlayerChatEvent event, UUID uuid) {
        if (!"PREFIX".equalsIgnoreCase(config().titles())) {
            return;
        }
        PlayerProgress progress = tracker.loadedProgress(uuid);
        String titleId = progress == null ? null : progress.title();
        if (titleId == null || !progress.isUnlocked(titleId)) {
            return;
        }
        Feat feat = registry.get(titleId);
        PlayerChatEvent.Formatter base = event.getFormatter();
        if (feat == null || base != PlayerChatEvent.DEFAULT_FORMATTER) {
            return;
        }
        Message prefix = Message.raw("[" + feat.name() + "] ").color(feat.tier().color());
        event.setFormatter((sender, content) -> Message.join(prefix, base.format(sender, content)));
    }

    /** Sends every unlock to the engine event bus and the API listeners. */
    private void publishUnlock(FeatsTracker.Unlock unlock) {
        FeatsApi current = api;
        Feat feat = unlock.feat();
        PlayerProgress progress = unlock.progress();
        String name = progress.username();
        FeatUnlockedEvent event = new FeatUnlockedEvent(unlock.uuid(), name == null ? "" : name, feat.id(),
                feat.name(), feat.tier().name(), feat.tier().points(), tracker.points(progress), unlock.catchUp());
        try {
            IEventDispatcher<FeatUnlockedEvent, FeatUnlockedEvent> dispatcher =
                    HytaleServer.get().getEventBus().dispatchFor(FeatUnlockedEvent.class, null);
            if (dispatcher.hasListener()) {
                dispatcher.dispatch(event);
            }
        } catch (RuntimeException | LinkageError e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats unlock event failed for %s", feat.id());
        }
        if (current != null) {
            current.fire(event, e -> getLogger().at(Level.WARNING).withCause(e)
                    .log("A Feats API listener failed for %s", feat.id()));
        }
    }

    // ---- periodic work ---------------------------------------------------

    private void tick() {
        try {
            boolean trackDistance = config().trackDistance();
            for (PlayerRef ref : Universe.get().getPlayers()) {
                UUID uuid = ref.getUuid();
                if (!tracker.isLoaded(uuid)) {
                    continue;
                }
                tracker.increment(uuid, StatKeys.PLAYTIME, 1);
                if (trackDistance) {
                    trackDistance(ref, uuid);
                }
                memories.poll(ref);
            }
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats tick failed");
        }
    }

    private void trackDistance(PlayerRef ref, UUID uuid) {
        Transform transform = ref.getTransform();
        if (transform == null || transform.getPosition() == null) {
            return;
        }
        Vector3d now = new Vector3d(transform.getPosition());
        Vector3d last = lastPositions.put(uuid, now);
        if (last == null) {
            return;
        }
        double step = last.distance(now);
        if (step < 0.25 || step > MAX_STEP_PER_SECOND || Double.isNaN(step)) {
            return;
        }
        double total = distanceRemainder.merge(uuid, step, Double::sum);
        long whole = (long) Math.floor(total);
        if (whole >= 1) {
            distanceRemainder.put(uuid, total - whole);
            tracker.increment(uuid, StatKeys.DISTANCE, whole);
        }
    }

    /**
     * Runs on the engine scheduler and only hands the save to the IO thread. A save still queued from
     * the last run is not queued twice.
     */
    private void autosave() {
        if (autosaveQueued.compareAndSet(false, true)) {
            try {
                runIo(() -> {
                    try {
                        tracker.saveDirty();
                    } finally {
                        autosaveQueued.set(false);
                    }
                });
            } catch (RejectedExecutionException e) {
                autosaveQueued.set(false); // shutting down, the shutdown save covers it
            }
        }
        try {
            pruneOffline(System.currentTimeMillis());
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats could not prune offline players");
        }
    }

    /**
     * Runs on the engine scheduler. The online count is read here, where it always was, and the
     * export itself, which reads and writes files, runs on the IO thread.
     */
    private void scheduleExport() {
        try {
            if (!exportQueued.compareAndSet(false, true)) {
                return; // the previous export has not run yet
            }
            int online = LeaderboardExporter.onlineNow();
            runIo(() -> {
                try {
                    exporter.tick(online);
                } finally {
                    exportQueued.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            exportQueued.set(false); // shutting down, the shutdown export covers it
        } catch (Exception e) {
            exportQueued.set(false);
            getLogger().at(Level.WARNING).withCause(e).log("Feats could not schedule the export");
        }
    }

    /** Forgets the anti farming memory of players who have been offline for more than an hour. */
    private void pruneOffline(long now) {
        offlineSince.entrySet().removeIf(e -> {
            if (now - e.getValue() <= OFFLINE_KEEP_MILLIS) {
                return false;
            }
            UUID uuid = e.getKey();
            placedBlocks.forget(uuid);
            drops.forget(uuid);
            lastChatAt.remove(uuid);
            return true;
        });
    }
}
