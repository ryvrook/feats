package dev.ryvrook.feats.track;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.protocol.packets.interface_.EventTitleStyle;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import dev.ryvrook.feats.FeatsConfig;
import dev.ryvrook.feats.lang.Lang;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.Tier;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.ui.FeatsBannerHud;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;

/**
 * Toast, sound, big title, chat and the top of screen banner when a feat unlocks. The banner is a keyed
 * {@link FeatsBannerHud} added on the player's world thread and removed after {@code BannerSeconds},
 * with the rules in {@link BannerQueue}. The removal is timed on the engine's scheduler from the moment
 * the banner is granted and hops to the player's current world when it fires, so it never depends on the
 * add having worked. Nothing in here throws into the tracker, and a missing engine
 * class on the banner path is logged once.
 */
public final class UnlockNotifier {
    /** The sound used when {@code UnlockSound} is empty or names no sound. */
    public static final String DEFAULT_SOUND = "SFX_Memories_Unlock_Local";
    /** The particle burst at the player when {@code BannerParticles} is on. */
    public static final String BANNER_PARTICLE = "Memories/MemoryUnlock";
    private static final String GOLD = "#E8A93B";
    private static final String MUTED = "#96a9be";

    /** One looked up sound. The id the index belongs to is kept so a reload with a new id looks it up again. */
    private static final class SoundSlot {
        volatile String id;
        volatile int index = Integer.MIN_VALUE;
        /** True when the id was unknown and the Memories sound stands in for it. */
        volatile boolean fellBack;
    }

    /** Work on a player's world thread, with the player's live reference and store. */
    private interface WorldTask {
        void run(Ref<EntityStore> ref, Store<EntityStore> store, World world);
    }

    /** How often a removal is tried again when the player has no world yet or the last try did not land. */
    static final long RETRY_MILLIS = 1_000L;
    /** How many tries again before a removal gives up, so about 10 seconds. */
    static final int RETRIES = 10;

    /** A scheduled banner step that can be called off. */
    interface Timer {
        void cancel();
    }

    /** Runs a task after a delay, off any world thread. The engine's scheduled executor outside tests. */
    interface Scheduler {
        Timer schedule(Runnable task, long delayMillis);
    }

    /** What one try at taking a banner down did. */
    enum Attempt {
        /** The player is offline, the HUD went with them. */
        GONE,
        /** The player has no world right now, for example between worlds. */
        WAIT,
        /** A removal was posted to the player's world. It calls back once the HUD is really gone. */
        POSTED
    }

    /**
     * Takes a player's banner down on whichever world they are in at the time of the call. The player and
     * the world are looked up by uuid each time, never kept from when the banner was shown.
     */
    interface Remover {
        /**
         * @param uuid    the player
         * @param wanted  asked again on the world thread, the removal is skipped when it says no
         * @param removed run on the world thread once the HUD is gone
         */
        Attempt post(UUID uuid, BooleanSupplier wanted, Runnable removed);
    }

    private final Supplier<FeatsConfig> config;
    private final HytaleLogger logger;
    private final SoundSlot unlockSlot = new SoundSlot();
    private final SoundSlot bannerSlot = new SoundSlot();
    private final BannerQueue banners = new BannerQueue();
    private final AtomicBoolean bannerWarned = new AtomicBoolean();
    private final AtomicBoolean bannerSoundWarned = new AtomicBoolean();
    private final AtomicBoolean particleWarned = new AtomicBoolean();
    /** 0 unchecked, 1 present, -1 missing. */
    private volatile int particleState;
    private volatile LongSupplier clock = System::currentTimeMillis;
    private volatile ToDoubleFunction<String> rarity = id -> -1;
    private volatile Predicate<UUID> notifications = uuid -> true;
    private volatile Scheduler scheduler = UnlockNotifier::engineSchedule;
    private volatile Remover remover = this::engineRemove;
    /** The banner timers still waiting, per player, so stop can call them all off. */
    private final Map<UUID, Set<Timer>> timers = new ConcurrentHashMap<>();
    /** Set by {@link #stop}. No banner is shown or timed after it. */
    private volatile boolean stopped;

    public UnlockNotifier(Supplier<FeatsConfig> config, HytaleLogger logger) {
        this.config = config;
        this.logger = logger;
    }

    /** Where the rarity tag comes from, the fraction of players owning a feat or -1 when unknown. */
    public void setRarity(ToDoubleFunction<String> rarity) {
        this.rarity = rarity == null ? id -> -1 : rarity;
    }

    /**
     * Whether a player wants to be told, the {@code /feats notify} switch, for banners that do not pass
     * through {@link #unlocked}. {@link #recordBanner} asks it for every viewer. Everyone is told until
     * the tracker wires its loaded progress in here.
     */
    public void setNotifications(Predicate<UUID> notifications) {
        this.notifications = notifications == null ? uuid -> true : notifications;
    }

    /** The banner rules, for tests and for a leave hook to call {@link BannerQueue#clear}. */
    public BannerQueue banners() {
        return banners;
    }

    /** The clock the banner timing runs on, for tests. */
    void setClock(LongSupplier clock) {
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    /** Where the banner timers run, for tests. */
    void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler == null ? UnlockNotifier::engineSchedule : scheduler;
    }

    /** How a banner is taken down, for tests. */
    void setRemover(Remover remover) {
        this.remover = remover == null ? this::engineRemove : remover;
    }

    /**
     * The plugin is shutting down. Calls off every banner timer and takes down every banner still up, so
     * an old timer can never remove a banner the next instance shows under the same key after a reload.
     * The removals are posted to the players' worlds ahead of anything the next instance posts. A player
     * between worlds at this moment keeps the banner until the next one replaces it. Never throws.
     */
    public void stop() {
        stopped = true;
        try {
            for (Set<Timer> set : timers.values()) {
                for (Timer timer : set) {
                    cancelQuietly(timer);
                }
            }
            timers.clear();
            for (UUID uuid : banners.withBannerUp()) {
                try {
                    remover.post(uuid, () -> true, () -> {
                    });
                } catch (RuntimeException | LinkageError e) {
                    warnBannerOnce(e);
                }
            }
            banners.clearAll();
        } catch (RuntimeException | LinkageError e) {
            warnBannerOnce(e);
        }
    }

    /** How many banner timers are waiting, for tests. */
    int timersWaiting() {
        int n = 0;
        for (Set<Timer> set : timers.values()) {
            n += set.size();
        }
        return n;
    }

    public void unlocked(PlayerProgress progress, Feat feat, int unlockedCount, int totalCount) {
        Universe universe = Universe.get();
        if (universe == null) {
            return; // no server running, as in the tests
        }
        FeatsConfig cfg = config.get();
        UUID uuid = progress.uuid();
        PlayerRef player = universe.getPlayer(uuid);
        if (player == null) {
            return;
        }
        try {
            boolean loud = progress.notifications(); // false after /feats notify off
            boolean bigTitle = showsBigTitle(loud, feat.tier(), cfg.bigTitleMinimumTier());
            if (sendsToast(loud, cfg.showToasts(), bigTitle)) {
                sendToast(player, feat);
            }
            Message share = rareShare(cfg, feat);
            Message line = Message.translation(share == null ? Lang.CHAT_UNLOCKED : Lang.CHAT_UNLOCKED_RARE)
                    .param("label", Message.translation(Lang.CHAT_UNLOCKED_LABEL).color(GOLD).bold(true))
                    .param("feat", Lang.colouredName(feat).bold(true))
                    .param("tier", Lang.tier(feat.tier()))
                    .param("points", feat.tier().points())
                    .param("unlocked", unlockedCount)
                    .param("total", totalCount)
                    .color(MUTED);
            if (share != null) {
                line.param("share", share);
            }
            if (cfg.chatLine()) {
                player.sendMessage(line);
            }
            if (loud && cfg.playSound()) {
                playSound(player, cfg.unlockSound());
            }
            if (bigTitle) {
                EventTitleUtil.showEventTitleToPlayer(player,
                        Lang.colouredName(feat),
                        Lang.description(feat),
                        feat.tier() == Tier.PLATINUM ? EventTitleStyle.Major : EventTitleStyle.Default);
            }
            if (cfg.broadcastUnlocks() && meetsBroadcastTier(feat.tier(), cfg.broadcastMinimumTier())) {
                broadcast(player, feat);
            }
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Feats could not announce %s to %s", feat.id(), uuid);
        }
        if (progress.notifications()) {
            Message why = bannerReason(cfg, feat);
            if (why != null) {
                showBanner(player, feat, cfg, why, true);
            }
        }
    }

    /**
     * The banner for a server milestone or first, called by the tracker when one is recorded. With
     * {@code BannerBroadcast} on, everyone online except the holder sees the banner with the why line
     * "Server first, by Name" or "Server milestone, by Name", or without the name when it is null, each
     * on their own world thread. The holder gets the normal unlock banner through {@link #unlocked} when
     * the feat is forced onto them, so they are skipped here. Never throws.
     *
     * @param feat   the WORLD or FIRST feat
     * @param by     the player who raised it, or null when nobody did
     * @param byName the name to show, or null
     */
    public void recordBanner(Feat feat, UUID by, String byName) {
        try {
            FeatsConfig cfg = config.get();
            Universe universe = Universe.get();
            if (feat == null || cfg == null || universe == null || !cfg.bannerBroadcast()
                    || "OFF".equals(cfg.banner())) {
                return;
            }
            Message why = BannerQueue.broadcastReason(feat, byName);
            for (PlayerRef other : universe.getPlayers()) {
                UUID uuid = other.getUuid();
                if (uuid == null || uuid.equals(by) || !wantsNotifications(uuid)) {
                    continue;
                }
                showBanner(other, feat, cfg, why, false);
            }
        } catch (RuntimeException | LinkageError e) {
            warnBannerOnce(e);
        }
    }

    private boolean wantsNotifications(UUID uuid) {
        try {
            return notifications.test(uuid);
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Why the unlock earns a banner under the current settings, or null. */
    Message bannerReason(FeatsConfig cfg, Feat feat) {
        if (cfg == null || feat == null) {
            return null;
        }
        double fraction;
        try {
            fraction = rarity.applyAsDouble(feat.id());
        } catch (RuntimeException | LinkageError e) {
            fraction = -1;
        }
        return BannerQueue.reason(cfg.banner(), feat, fraction, cfg.bannerRareBelowPercent());
    }

    /**
     * Asks the queue for a slot and, when granted, schedules the removal straight away and then adds the
     * banner on the player's world thread, plays the banner sound and spawns the particles for the holder.
     * The removal does not depend on the add, so a banner shown while the player changes world still has a
     * timer that takes whatever is on screen down. Never throws.
     */
    private void showBanner(PlayerRef player, Feat feat, FeatsConfig cfg, Message why, boolean holder) {
        try {
            UUID uuid = player.getUuid();
            BannerQueue.Shown shown = grant(uuid, cfg.bannerSeconds() * 1000L);
            if (shown == null) {
                return; // over the three in a row, the chat line covers it
            }
            long generation = shown.generation();
            FeatsBannerHud.Frame frame = new FeatsBannerHud.Frame(
                    feat.icon(), Lang.name(feat), feat.tier().color(), Lang.description(feat), why);
            String sound = cfg.bannerSound();
            boolean particles = holder && cfg.bannerParticles();
            onWorldThread(player, (ref, store, world) -> {
                if (stopped || !banners.pending(uuid, generation)) {
                    return; // already timed out and taken down, or a later banner replaced it
                }
                HudManager hud = hudManager(ref, store);
                if (hud == null) {
                    return;
                }
                hud.addCustomHud(player, new FeatsBannerHud(player, frame));
                if (!sound.isBlank()) {
                    playBannerSound(player, sound);
                }
                if (particles) {
                    spawnParticles(player, ref, store);
                }
            });
        } catch (RuntimeException | LinkageError e) {
            warnBannerOnce(e);
        }
    }

    /**
     * Asks the queue for a slot and, when granted, schedules the removal for the time the queue gives.
     * Returns the show, or null when there is no slot or the plugin stopped. Never throws.
     */
    BannerQueue.Shown grant(UUID uuid, long stayMillis) {
        if (stopped || uuid == null) {
            return null;
        }
        try {
            long now = clock.getAsLong();
            BannerQueue.Shown shown = banners.offer(uuid, now, stayMillis);
            if (shown != null) {
                long generation = shown.generation();
                schedule(uuid, () -> fire(uuid, generation, 0), Math.max(0, shown.removeAt() - now));
            }
            return shown;
        } catch (RuntimeException | LinkageError e) {
            warnBannerOnce(e);
            return null;
        }
    }

    /**
     * The removal timer for one show. Stops when a later show replaced it or it was already taken down.
     * Otherwise it tries to take the banner down on the player's current world and checks again a second
     * later, since the player may have no world yet or the world may drop the task while it closes. The
     * banner is marked as gone only once the HUD really went, or the player went offline, or after
     * {@link #RETRIES} tries again, where it gives up.
     */
    private void fire(UUID uuid, long generation, int tries) {
        if (stopped) {
            return;
        }
        try {
            if (!banners.pending(uuid, generation)) {
                return; // taken down, or a later banner has its own timer
            }
            if (tries > RETRIES) {
                banners.removed(uuid, generation);
                logger.at(Level.FINE).log("Feats gave up taking the banner down for %s", uuid);
                return;
            }
            Attempt attempt;
            try {
                attempt = remover.post(uuid,
                        () -> !stopped && banners.pending(uuid, generation),
                        () -> banners.removed(uuid, generation));
            } catch (RuntimeException | LinkageError e) {
                logger.at(Level.FINE).withCause(e).log("Feats banner removal failed, trying again");
                attempt = Attempt.WAIT;
            }
            if (attempt == Attempt.GONE) {
                banners.removed(uuid, generation);
                return;
            }
            schedule(uuid, () -> fire(uuid, generation, tries + 1), RETRY_MILLIS);
        } catch (RuntimeException | LinkageError e) {
            warnBannerOnce(e);
        }
    }

    /** Schedules a banner step and keeps it under the player until it ran, so stop can call it off. */
    private void schedule(UUID uuid, Runnable task, long delayMillis) {
        AtomicReference<Timer> self = new AtomicReference<>();
        AtomicBoolean ran = new AtomicBoolean();
        Timer timer = scheduler.schedule(() -> {
            ran.set(true);
            Timer me = self.get();
            if (me != null) {
                forget(uuid, me);
            }
            try {
                task.run();
            } catch (RuntimeException | LinkageError e) {
                warnBannerOnce(e);
            }
        }, delayMillis);
        if (timer == null) {
            return;
        }
        self.set(timer);
        if (!ran.get()) {
            timers.compute(uuid, (k, set) -> {
                Set<Timer> out = set == null ? ConcurrentHashMap.newKeySet() : set;
                out.add(timer);
                return out;
            });
            if (ran.get()) {
                forget(uuid, timer); // it ran while being added
            }
        }
        if (stopped) {
            cancelQuietly(timer); // stop ran while this was being scheduled
        }
    }

    private void forget(UUID uuid, Timer timer) {
        timers.computeIfPresent(uuid, (k, set) -> {
            set.remove(timer);
            return set.isEmpty() ? null : set;
        });
    }

    private static void cancelQuietly(Timer timer) {
        try {
            timer.cancel();
        } catch (RuntimeException | LinkageError ignored) {
            // a timer that cannot be called off finds the plugin stopped and does nothing
        }
    }

    private static Timer engineSchedule(Runnable task, long delayMillis) {
        ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    /**
     * The engine removal. Looks the player up by uuid, finds the world they are in now and posts the
     * removal there, where the player and the reference are looked up again before the HUD is touched.
     */
    private Attempt engineRemove(UUID uuid, BooleanSupplier wanted, Runnable removed) {
        Universe universe = Universe.get();
        PlayerRef player = universe == null ? null : universe.getPlayer(uuid);
        if (player == null) {
            return Attempt.GONE;
        }
        World world = worldOf(player);
        if (world == null) {
            return Attempt.WAIT;
        }
        world.execute(() -> {
            try {
                if (!wanted.getAsBoolean()) {
                    return;
                }
                PlayerRef current = Universe.get().getPlayer(uuid);
                Ref<EntityStore> ref = current == null ? null : current.getReference();
                if (ref == null || !ref.isValid()) {
                    return; // moved on, the next try finds the new world
                }
                HudManager hud = hudManager(ref, ref.getStore());
                if (hud == null) {
                    return;
                }
                if (hud.getCustomHud(FeatsBannerHud.KEY) != null) {
                    hud.removeCustomHud(current, FeatsBannerHud.KEY);
                }
                removed.run();
            } catch (RuntimeException | LinkageError e) {
                warnBannerOnce(e);
            }
        });
        return Attempt.POSTED;
    }

    /**
     * Posts the task to the world the player's entity lives in, found through the reference's store, and
     * runs it with a reference checked again on that thread. Nothing happens for a player without an
     * entity, which is the case between worlds.
     */
    private void onWorldThread(PlayerRef player, WorldTask task) {
        World world = worldOf(player);
        if (world == null) {
            return;
        }
        world.execute(() -> {
            try {
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) {
                    return;
                }
                task.run(ref, ref.getStore(), world);
            } catch (RuntimeException | LinkageError e) {
                warnBannerOnce(e);
            }
        });
    }

    private static World worldOf(PlayerRef player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref != null && ref.isValid()) {
            Store<EntityStore> store = ref.getStore();
            World world = store == null ? null : store.getExternalData().getWorld();
            if (world != null) {
                return world;
            }
        }
        UUID worldId = player.getWorldUuid();
        Universe universe = worldId == null ? null : Universe.get();
        return universe == null ? null : universe.getWorld(worldId);
    }

    private static HudManager hudManager(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player entity = store.getComponent(ref, Player.getComponentType());
        return entity == null ? null : entity.getHudManager();
    }

    /** The configured banner sound, or the Memories sound when the id is unknown, which is logged once. */
    private void playBannerSound(PlayerRef player, String configured) {
        int index = resolveSound(bannerSlot, configured);
        if (bannerSlot.fellBack && bannerSoundWarned.compareAndSet(false, true)) {
            logger.at(Level.WARNING).log("Feats BannerSound %s is not a known sound event, using %s",
                    configured.trim(), DEFAULT_SOUND);
        }
        if (index > 0) {
            SoundUtil.playSoundEvent2dToPlayer(player, index, SoundCategory.UI);
        }
    }

    /** World thread. The burst at the player's feet, visible to that player only. */
    private void spawnParticles(PlayerRef player, Ref<EntityStore> ref, Store<EntityStore> store) {
        int state = particleState;
        if (state == 0) {
            state = ParticleSystem.getAssetMap().getAsset(BANNER_PARTICLE) != null ? 1 : -1;
            particleState = state;
            if (state < 0 && particleWarned.compareAndSet(false, true)) {
                logger.at(Level.WARNING).log("Feats BannerParticles is on but %s is not a known particle system",
                        BANNER_PARTICLE);
            }
        }
        if (state < 0) {
            return;
        }
        Transform transform = player.getTransform();
        Vector3d position = transform == null ? null : transform.getPosition();
        if (position == null) {
            return;
        }
        ParticleUtil.spawnParticleEffect(BANNER_PARTICLE, position, List.of(ref), store);
    }

    private void warnBannerOnce(Throwable e) {
        if (bannerWarned.compareAndSet(false, true)) {
            logger.at(Level.WARNING).withCause(e).log("Feats could not show the unlock banner, banners are off until a restart");
        } else {
            logger.at(Level.FINE).withCause(e).log("Feats banner failed again");
        }
    }

    /** One chat line for unlocks that were not announced one by one, used when many unlock at once. */
    public void moreUnlocked(UUID uuid, int more, int unlockedCount, int totalCount) {
        FeatsConfig cfg = config.get();
        if (more <= 0 || (cfg != null && !cfg.chatLine())) {
            return;
        }
        PlayerRef player = Universe.get().getPlayer(uuid);
        if (player == null) {
            return;
        }
        player.sendMessage(Message.translation(Lang.CHAT_MORE)
                .param("label", Message.translation(Lang.CHAT_LABEL).color(GOLD).bold(true))
                .param("n", more)
                .param("unlocked", unlockedCount)
                .param("total", totalCount)
                .color(MUTED));
    }

    /**
     * The quiet chat line for a server milestone this player shares in without having raised it. No
     * toast, no sound and no points. Worded "Server milestone reached, Lights On, by Name".
     */
    public void sharedMilestone(UUID uuid, Feat feat, String byName) {
        PlayerRef player = Universe.get().getPlayer(uuid);
        if (player == null) {
            return;
        }
        boolean named = byName != null && !byName.isEmpty();
        Message line = Message.translation(named ? Lang.CHAT_MILESTONE_BY : Lang.CHAT_MILESTONE)
                .param("feat", Lang.colouredName(feat).bold(true))
                .color(MUTED);
        if (named) {
            line.param("player", byName);
        }
        player.sendMessage(line);
    }

    /** One line for shared milestones beyond the few told one by one. */
    public void moreShared(UUID uuid, int more) {
        PlayerRef player = Universe.get().getPlayer(uuid);
        if (player == null || more <= 0) {
            return;
        }
        player.sendMessage(Message.translation(Lang.CHAT_MORE_MILESTONES).param("n", more).color(MUTED));
    }

    private void sendToast(PlayerRef player, Feat feat) {
        NotificationUtil.sendNotification(
                player.getPacketHandler(),
                Lang.name(feat).color(GOLD),
                Lang.description(feat),
                new ItemStack(feat.icon(), 1).toPacket(),
                NotificationStyle.Success);
    }

    /**
     * Plays the configured sound, or the Memories unlock sound when the configured id is empty or
     * unknown. Nothing plays when neither exists. The index is looked up once per id.
     */
    private void playSound(PlayerRef player, String configured) {
        int index = resolveSound(unlockSlot, configured);
        if (index > 0) {
            SoundUtil.playSoundEvent2dToPlayer(player, index, SoundCategory.UI);
        }
    }

    /** The index of the wanted sound, or of the Memories sound when it is unknown, cached per slot. */
    private static int resolveSound(SoundSlot slot, String configured) {
        String wanted = configured == null || configured.isBlank() ? DEFAULT_SOUND : configured.trim();
        int index = slot.index;
        if (index == Integer.MIN_VALUE || !wanted.equals(slot.id)) {
            index = lookupSound(wanted);
            boolean fellBack = false;
            if (index <= 0 && !wanted.equals(DEFAULT_SOUND)) {
                index = lookupSound(DEFAULT_SOUND);
                fellBack = true;
            }
            slot.id = wanted;
            slot.index = index;
            slot.fellBack = fellBack;
        }
        return index;
    }

    private static int lookupSound(String id) {
        try {
            return SoundEvent.getAssetMap().getIndex(id);
        } catch (Exception e) {
            return -1;
        }
    }

    /** The share of players owning a rare feat, "under 1%" or "3%", or null when it is not rare or rarity is off. */
    private Message rareShare(FeatsConfig cfg, Feat feat) {
        if (!cfg.showRarity()) {
            return null;
        }
        double fraction;
        try {
            fraction = rarity.applyAsDouble(feat.id());
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
        if (fraction < 0 || fraction * 100.0 >= cfg.rareBelowPercent()) {
            return null;
        }
        return Lang.share(fraction);
    }

    /** True when the tier is at or above the configured minimum. OFF, or an unknown value, shows none. */
    /** Whether the unlock puts Hytale's big event title on screen, a loud unlock at or above the minimum tier. */
    static boolean showsBigTitle(boolean loud, Tier tier, String minimum) {
        return loud && meetsTitleTier(tier, minimum);
    }

    /** Whether the unlock gets the toast. The big title replaces it, so one unlock never shows both. */
    static boolean sendsToast(boolean loud, boolean showToasts, boolean bigTitle) {
        return loud && showToasts && !bigTitle;
    }

    private static boolean meetsTitleTier(Tier tier, String minimum) {
        if (minimum == null) {
            return false;
        }
        try {
            return tier.ordinal() >= Tier.valueOf(minimum.trim().toUpperCase()).ordinal();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void broadcast(PlayerRef player, Feat feat) {
        Universe universe = Universe.get();
        if (universe.getPlayerCount() <= 1) {
            return;
        }
        Message line = Message.translation(Lang.CHAT_BROADCAST)
                .param("player", Message.raw(player.getUsername()).color("#7a9cc6").bold(true))
                .param("feat", Lang.colouredName(feat).bold(true))
                .param("tier", Lang.tier(feat.tier()))
                .color(MUTED);
        for (PlayerRef other : universe.getPlayers()) {
            if (!other.getUuid().equals(player.getUuid())) {
                other.sendMessage(line);
            }
        }
    }

    private static boolean meetsBroadcastTier(Tier tier, String minimum) {
        Tier min;
        try {
            min = Tier.valueOf(minimum == null ? "SILVER" : minimum.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            min = Tier.SILVER;
        }
        return tier.ordinal() >= min.ordinal();
    }
}
