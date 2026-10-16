package dev.ryvrook.feats;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.IEventDispatcher;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.ChangeGameModeEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
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
import dev.ryvrook.feats.integration.TrackerBridge;
import dev.ryvrook.feats.integration.TrackerHost;
import dev.ryvrook.feats.integration.Siblings;
import dev.ryvrook.feats.integration.TrackerSwitch;
import dev.ryvrook.feats.model.Category;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Pack;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.integration.SiblingLinks;
import dev.ryvrook.feats.track.Hardcore;
import dev.ryvrook.feats.track.Hooks;
import dev.ryvrook.feats.placeholders.FeatsExpansion;
import dev.ryvrook.feats.rewards.CoinPayer;
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
import dev.ryvrook.feats.systems.LabUses;
import dev.ryvrook.feats.systems.PickupSystem;
import dev.ryvrook.feats.systems.PlaceBlockSystem;
import dev.ryvrook.feats.systems.TameSystem;
import dev.ryvrook.feats.systems.UseBlockSystem;
import dev.ryvrook.feats.systems.ZoneDiscoverySystem;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.MemoryTracker;
import dev.ryvrook.feats.track.PlacedBlocks;
import dev.ryvrook.feats.track.PlayerSampler;
import dev.ryvrook.feats.track.PortalTracker;
import dev.ryvrook.feats.track.RecentDrops;
import dev.ryvrook.feats.track.RecentPlacements;
import dev.ryvrook.feats.track.SessionClock;
import dev.ryvrook.feats.track.UnlockNotifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    private static final long CHAT_GAP_MILLIS = 5_000L; // at most one counted chat message per 5 seconds
    private static final long OFFLINE_KEEP_MILLIS = 60L * 60 * 1000; // anti farming memory outlives a relog by an hour
    private static final long IO_DRAIN_SECONDS = 10L;
    /** How often the running session of everyone online is written to the longest session record. */
    private static final int SESSION_RECORD_TICKS = 300;

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
    /** The Goblin Lab device uses the flag watcher reads, fed by whichever tracking path is active. */
    private final LabUses labUses = new LabUses();
    /** Which path feeds the counters, decided once in setup. */
    private TrackerSwitch trackerSwitch;
    /** The TrackerAPI hooks while Feats tracks through it, closed at shutdown. */
    private AutoCloseable trackerLink;
    /** The Coinage link that pays the Coins of Rewards.json, null without Coinage. */
    private AutoCloseable coinageLink;
    /** The bindings that write sibling counters, see SiblingLinks. */
    private SiblingLinks siblingLinks;
    /** The 1.7.2 tracking hooks and what they can reach. */
    private final Hooks hooks = new Hooks(getLogger());
    private Hooks.Context hookContext;
    private Set<Sibling> siblings = Set.of();
    /** The 5 second look at the TrackerAPI plugin object that notices a reload of it. */
    private ScheduledFuture<?> trackerWatch;
    private final Map<UUID, Long> lastChatAt = new ConcurrentHashMap<>();
    /** When each player left. Their placement ring, drops and chat timer are dropped an hour later. */
    private final Map<UUID, Long> offlineSince = new ConcurrentHashMap<>();

    /** Session start times for the longest session record. */
    private final SessionClock sessions = new SessionClock();
    /** The once a second distance, world bounds and Memories pass, one task per world. */
    private PlayerSampler sampler;
    private int ticks;
    private ScheduledFuture<?> ticker;
    private ScheduledFuture<?> autosave;
    private LeaderboardExporter exporter;
    /** The unlock announcer, kept so shutdown can stop its banner timers. */
    private UnlockNotifier notifier;
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
    /** Set first thing in shutdown, so a dependents pass still waiting does not start. */
    private volatile boolean stopping;
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

    @Override
    protected void setup() {
        AssetPackFix.dropStale(this);
        config.save();

        io = newIoExecutor();
        siblings = Siblings.present();
        boolean factions = siblings.contains(Sibling.DOMINION);
        Set<Pack> packs = config().packs(() -> Hardcore.detect(getLogger()));
        registry = new FeatRegistry(siblings, packs);
        store = new ProgressStore(getDataDirectory(), getLogger());
        world = new WorldStore(getDataDirectory(), getLogger());
        loadWorld();
        notifier = new UnlockNotifier(this::config, getLogger());
        tracker = new FeatsTracker(registry, store, notifier, getLogger(), io, world);
        tracker.setServerTotals(() -> config().serverTotals(), () -> config().fullHouse());
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
        runIo(rewards::loadWaiting); // Coins a shutdown kept because Coinage was away, owed even with rewards off now
        api = FeatsApi.install(this);

        getCommandRegistry().registerCommand(new FeatsCommand(this));

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        // Late, so moderation plugins have cancelled and chat plugins have set their formatter before the title looks.
        getEventRegistry().registerAsyncGlobal(EventPriority.LATE, PlayerChatEvent.class,
                future -> future.thenApply(this::onPlayerChat));
        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, this::onAddPlayerToWorld);

        TrackerSwitch.Mode mode = TrackerSwitch.Mode.parse(config().tracker());
        TrackerSwitch.Found found = mode == TrackerSwitch.Mode.NEVER ? TrackerSwitch.Found.MISSING
                : TrackerSwitch.ENGINE.find();
        TrackerSwitch.Path path = TrackerSwitch.decide(mode, found);
        trackerSwitch = new TrackerSwitch(mode, path, found, TrackerSwitch.ENGINE);
        hookContext = new Hooks.Context(this, tracker, registry, this::config, getLogger(), world, siblings,
                path == TrackerSwitch.Path.OWN, sessions);
        if (path == TrackerSwitch.Path.OWN) {
            registerOwnTracking();
            hooks.registerOwn(hookContext, getEntityStoreRegistry(), getEventRegistry());
        }
        hooks.registerAlways(hookContext, getEntityStoreRegistry(), getEventRegistry());
        memories = new MemoryTracker(tracker, getLogger());
        sampler = new PlayerSampler(tracker, memories, getLogger());
        try {
            flags = new FlagWatcher(tracker, labUses, getLogger());
        } catch (LinkageError | RuntimeException e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats cannot watch the Goblin Lab flags on this server");
        }

        getLogger().at(path == TrackerSwitch.Path.NONE ? Level.WARNING : Level.INFO)
                .log("%s", TrackerSwitch.describe(mode, path, found));
        getLogger().at(Level.INFO).log("Feats loaded %d feats worth %d points, plus %d world feats", registry.count(),
                registry.totalPoints(), registry.countServer());
        if (factions) {
            getLogger().at(Level.INFO).log("Dominion is installed, so the %d Factions feats are in", registry.inCategory(Category.FACTIONS).size());
        }
        for (Sibling s : siblings) {
            if (s != Sibling.DOMINION && s != Sibling.TRACKER_API) {
                getLogger().at(Level.INFO).log("%s is installed, so its %d feats are in", s.pluginName(),
                        registry.personal().stream().filter(f -> f.requires().contains(s)).count());
            }
        }
        if (!packs.isEmpty()) {
            getLogger().at(Level.INFO).log("Packs on: %s", packs);
        }
    }

    /** The siblings the plugin manager listed at setup. */
    public Set<Sibling> siblings() {
        return siblings;
    }

    /** What the tracking hooks and sibling bindings can reach. Null before setup. */
    public Hooks.Context hookContext() {
        return hookContext;
    }

    /**
     * The Feats engine systems and the portal listener, registered only when Feats tracks on its own. With
     * TrackerAPI in charge none of them exist and {@link TrackerBridge} feeds the same counters.
     */
    private void registerOwnTracking() {
        var ecs = getEntityStoreRegistry();
        ecs.registerSystem(new BreakBlockSystem(tracker, placedBlocks));
        ecs.registerSystem(new PlaceBlockSystem(tracker, placements, placedBlocks));
        ecs.registerSystem(new CraftSystem(tracker));
        ecs.registerSystem(new PickupSystem(tracker, drops));
        ecs.registerSystem(new UseBlockSystem(tracker, labUses));
        ecs.registerSystem(new ZoneDiscoverySystem(tracker));
        ecs.registerSystem(new DamageSystem(tracker));
        ecs.registerSystem(new DeathSystem(tracker));
        try {
            ecs.registerSystem(new TameSystem(tracker));
        } catch (LinkageError | RuntimeException e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats taming tracking is not supported on this server");
        }
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
        AssetPackFix.report(this);
        loadOnlinePlayers();
        FeatsExpansion.registerIfPresent(this); // does nothing without PlaceholderAPI
        if (flags != null) {
            try {
                flags.start();
            } catch (LinkageError | RuntimeException e) {
                getLogger().at(Level.WARNING).withCause(e).log("Feats cannot watch the Goblin Lab flags on this server");
            }
        }
        startTrackerLink();
        startCoinageLink();
        siblingLinks = SiblingLinks.start(hookContext);
        hooks.start(hookContext);
        logDiscordPath();
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
        FeatsApi.enable(api);
        reloadDependentsAfterReload();
    }

    /**
     * After a {@code /plugin reload} of Feats the plugins that list it still hold the old FeatsApi class, so they
     * are reloaded two seconds after this start, on a thread of their own since the pass may wait for another
     * reloader's lock and for each plugin. On a fresh boot none of them is enabled yet and nothing happens.
     */
    private void reloadDependentsAfterReload() {
        List<Dependents.Dependent> dependents = Dependents.enabled(getIdentifier(), getLogger());
        if (dependents.isEmpty()) {
            return;
        }
        getLogger().at(Level.INFO).log("Feats found %d plugins linked to its previous instance and reloads them in two seconds", dependents.size());
        try {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                if (stopping) {
                    return;
                }
                Thread pass = new Thread(() -> {
                    try {
                        Dependents.reload(dependents, getLogger());
                    } catch (RuntimeException | LinkageError e) {
                        getLogger().at(Level.WARNING).withCause(e).log("Feats could not reload the plugins linked to it");
                    }
                }, "Feats-dependents");
                pass.setDaemon(true);
                pass.start();
            }, 2, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            // the server is stopping, nothing to relink
        }
    }

    /**
     * Hooks the bridge to TrackerAPI when setup chose it, and starts the 5 second watch that notices a reload
     * of TrackerAPI. The bridge class is only loaded here, after setup found TrackerAPI installed.
     */
    private void startTrackerLink() {
        if (trackerSwitch == null) {
            return;
        }
        if (trackerSwitch.path() == TrackerSwitch.Path.TRACKER_API) {
            try {
                trackerLink = TrackerBridge.install(new Host());
            } catch (LinkageError | RuntimeException e) {
                getLogger().at(Level.WARNING).withCause(e).log("Feats could not link to TrackerAPI and counts nothing until it is reloaded");
            }
        }
        if (trackerSwitch.mode() != TrackerSwitch.Mode.NEVER) {
            trackerWatch = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(() -> {
                String line = trackerSwitch.poll();
                if (line != null) {
                    getLogger().at(Level.WARNING).log("%s", line);
                }
            }, 5, 5, TimeUnit.SECONDS);
        }
    }

    /**
     * Links the reward queue to Coinage when it is installed, so the Coins of Rewards.json are paid. The link
     * class is only loaded after the plugin manager listed Coinage. Rewards are off by default, so nothing is
     * paid until RewardsEnabled is on and a reward has Coins.
     */
    private void startCoinageLink() {
        if (!Siblings.installed(Siblings.COINAGE)) {
            return;
        }
        try {
            Class<?> type = Class.forName("dev.ryvrook.feats.integration.CoinageRewards", true, getClass().getClassLoader());
            Object link = type.getMethod("install").invoke(null);
            rewards.setCoinPayer((CoinPayer) link);
            coinageLink = (AutoCloseable) link;
            getLogger().at(Level.INFO).log("Coinage is installed, so rewards with Coins pay through it");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats could not link to Coinage, rewards with Coins pay nothing");
        }
    }

    /** One line when a webhook is set but SocialBridge takes the unlock posts on {@code DiscordVia} auto. */
    private void logDiscordPath() {
        FeatsConfig cfg = config();
        String url = cfg.discordWebhookUrl();
        if (url == null || url.isBlank() || !DiscordVia.AUTO.equals(cfg.discordVia())) {
            return;
        }
        if (Siblings.installed(Siblings.SOCIALBRIDGE)) {
            getLogger().at(Level.INFO).log("SocialBridge is installed, so it posts the unlocks and DiscordWebhookUrl is not used. Set DiscordVia to webhook to post through both");
        }
    }

    /** What the TrackerAPI bridge reads and calls in Feats. */
    private final class Host implements TrackerHost {
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
            return config().trackDistance();
        }

        @Override
        public boolean isCreative(UUID uuid) {
            return uuid != null && creative.contains(uuid);
        }

        @Override
        public void setCreative(UUID uuid, boolean isCreative) {
            FeatsPlugin.this.setCreative(uuid, isCreative);
        }

        @Override
        public String worldOf(UUID uuid) {
            return uuid == null ? null : worldOf.get(uuid);
        }

        @Override
        public void countChat(UUID uuid, long atMillis) {
            FeatsPlugin.this.countChat(uuid, atMillis);
        }

        @Override
        public HytaleLogger logger() {
            return getLogger();
        }

        @Override
        public Hooks.Context context() {
            return hookContext;
        }
    }

    @Override
    protected void shutdown() {
        stopping = true;
        FeatsApi.disable(api); // first, so dependents let go while every Feats service still answers
        if (trackerWatch != null) {
            trackerWatch.cancel(false);
        }
        if (trackerLink != null) {
            try {
                trackerLink.close();
            } catch (Exception | LinkageError e) {
                getLogger().at(Level.FINE).withCause(e).log("Feats could not unlink from TrackerAPI");
            }
            trackerLink = null;
        }
        FeatsExpansion.unregister(this);
        FeatsApi.uninstall(api);
        if (rewards != null) {
            rewards.stop(); // runs the waiting steps, Coins included, so the Coinage link closes after it
        }
        if (coinageLink != null) {
            try {
                coinageLink.close();
            } catch (Exception | LinkageError e) {
                getLogger().at(Level.FINE).withCause(e).log("Feats could not unlink from Coinage");
            }
            coinageLink = null;
        }
        if (ticker != null) {
            ticker.cancel(false);
        }
        if (siblingLinks != null) {
            try {
                siblingLinks.close();
            } catch (Exception | LinkageError e) {
                getLogger().at(Level.FINE).withCause(e).log("Feats could not unlink from a sibling");
            }
            siblingLinks = null;
        }
        if (hookContext != null) {
            hooks.stop(hookContext);
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
        if (notifier != null) notifier.stop(); // banner timers off and banners down before a reload's new instance
        if (tracker != null) {
            recordSessions(System.currentTimeMillis()); // the record so far, and the start stays in the file for a reload
            tracker.saveAll();
        }
        if (world != null && world.isDirty()) {
            world.save();
        }
        if (export != null && exporter != null) {
            exporter.flush(LeaderboardExporter.onlineNow());
        }
        stopIo();
        AssetPackFix.release(this);
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
        sampler.resetPosition(uuid);
        // The player is in the world now, so feats already met when their file loaded can announce themselves.
        tracker.catchUp(uuid);
        if (!firstReadyThisSession) {
            return; // PlayerReady fires again on world changes, so only the first one counts
        }
        long joinedAt = System.currentTimeMillis();
        sessions.start(uuid, joinedAt);
        progress.setSessionStart(joinedAt); // read back only if Feats reloads during this session
        progress.setFirstSeenIfAbsent(joinedAt);
        tracker.increment(uuid, StatKeys.JOINS, 1);
        hooks.joined(hookContext, uuid, playerRef, progress, joinedAt);
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
                    PlayerProgress progress = tracker.load(ref.getUuid(), ref.getUsername());
                    // Carry on the session the old instance saved, so a reload does not split it.
                    progress.setSessionStart(sessions.resume(ref.getUuid(), progress.sessionStart(),
                            System.currentTimeMillis()));
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
        long leftAt = System.currentTimeMillis();
        hooks.left(hookContext, uuid, leftAt);
        tracker.raiseTo(uuid, StatKeys.LONGEST_SESSION, sessions.end(uuid, leftAt));
        PlayerProgress leaving = tracker.loadedProgress(uuid);
        if (leaving != null) {
            leaving.setSessionStart(0); // the session is over, the leaving save clears it from the file
        }
        tracker.unload(uuid);
        creative.remove(uuid);
        worldOf.remove(uuid);
        tracker.setPaused(uuid, false);
        sampler.forget(uuid);
        placements.forget(uuid);
        // The placement ring, the drop memory, the kill streak and the chat timer survive a relog, see pruneOffline.
        offlineSince.put(uuid, System.currentTimeMillis());
    }

    private PlayerChatEvent onPlayerChat(PlayerChatEvent event) {
        try {
            if (!event.isCancelled() && event.getSender() != null) {
                UUID uuid = event.getSender().getUuid();
                if (ownTracking()) {
                    countChat(uuid, System.currentTimeMillis()); // TrackerAPI counts it through PlayerChatted otherwise
                }
                applyTitle(event, uuid);
            }
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats chat tracking failed");
        }
        return event;
    }

    /** Counts one chat message unless the last counted one was under 5 seconds before {@code now}. */
    private void countChat(UUID uuid, long now) {
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
    }

    /** True when the Feats systems and samplers feed the counters, false when TrackerAPI does or nothing does. */
    private boolean ownTracking() {
        return trackerSwitch == null || trackerSwitch.path() == TrackerSwitch.Path.OWN;
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
        LeaderboardExporter ranks = exporter;
        double rarity = ranks == null ? -1 : ranks.rarity(feat.id());
        FeatUnlockedEvent event = UnlockEvents.of(unlock.uuid(), name, feat, tracker.points(progress),
                unlock.catchUp(), unlock.admin(), rarity, feat.banner());
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

    /**
     * Runs once a second on the engine scheduler. Playtime is counted here. Everything that reads the
     * player entity is handed to the world thread, one task per world for all of its players, rather than
     * one task per player.
     */
    private void tick() {
        try {
            rewards.payWaiting();
            boolean trackDistance = config().trackDistance();
            boolean recordSessions = ++ticks % SESSION_RECORD_TICKS == 0;
            boolean sample = ownTracking(); // with TrackerAPI the samples arrive as PlayerMoved and MemoryRecorded
            long now = System.currentTimeMillis();
            Map<World, List<PlayerRef>> byWorld = null;
            List<PlayerRef> online = new ArrayList<>();
            for (PlayerRef ref : Universe.get().getPlayers()) {
                UUID uuid = ref.getUuid();
                if (!tracker.isLoaded(uuid)) {
                    continue;
                }
                online.add(ref);
                tracker.increment(uuid, StatKeys.PLAYTIME, 1);
                if (recordSessions) {
                    tracker.raiseTo(uuid, StatKeys.LONGEST_SESSION, sessions.seconds(uuid, now));
                }
                World world = worldOf(ref);
                if (world != null) {
                    if (byWorld == null) {
                        byWorld = new HashMap<>(4);
                    }
                    byWorld.computeIfAbsent(world, w -> new ArrayList<>()).add(ref);
                }
            }
            hooks.tick(hookContext, now, online);
            if (byWorld != null) {
                for (Map.Entry<World, List<PlayerRef>> e : byWorld.entrySet()) {
                    List<PlayerRef> players = e.getValue();
                    World world = e.getKey();
                    try {
                        world.execute(() -> {
                            if (sample) {
                                sampler.sampleWorld(players, trackDistance);
                            }
                            hooks.sampleWorld(hookContext, world, players, sample, now);
                        });
                    } catch (RuntimeException rejected) {
                        // the world is closing, its players are sampled again once they are elsewhere
                    }
                }
            }
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Feats tick failed");
        }
    }

    /** The world a player's entity lives in, or null while they are between worlds. */
    private static World worldOf(PlayerRef ref) {
        try {
            Ref<EntityStore> entity = ref.getReference();
            EntityStore store = entity != null ? entity.getStore().getExternalData() : null;
            return store != null ? store.getWorld() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Raises the longest session record of everyone online to their running session. */
    private void recordSessions(long now) {
        for (PlayerProgress progress : tracker.loadedPlayers()) {
            UUID uuid = progress.uuid();
            if (sessions.isRunning(uuid)) {
                tracker.raiseTo(uuid, StatKeys.LONGEST_SESSION, sessions.seconds(uuid, now));
            }
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
                        if (world != null && world.isDirty()) {
                            world.save(); // the server totals move on hot paths and only flush here and at shutdown
                        }
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
            tracker.killStreaks().forget(uuid);
            lastChatAt.remove(uuid);
            return true;
        });
    }
}
