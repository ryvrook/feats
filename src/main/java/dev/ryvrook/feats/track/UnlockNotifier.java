package dev.ryvrook.feats.track;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.SoundCategory;
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
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.Tier;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.ui.FeatsBannerHud;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;

/**
 * Toast, sound, big title, chat and the top of screen banner when a feat unlocks. The banner is a keyed
 * {@link FeatsBannerHud} added on the player's world thread and removed after {@code BannerSeconds},
 * with the rules in {@link BannerQueue}. Nothing in here throws into the tracker, and a missing engine
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
            if (loud && cfg.showToasts()) {
                sendToast(player, feat);
            }
            String tag = rarityTag(cfg, feat);
            player.sendMessage(Message.join(
                    Message.raw("Feat unlocked ").color(GOLD).bold(true),
                    Message.raw(feat.name()).color(feat.tier().color()).bold(true),
                    Message.raw("  (" + feat.tier().displayName() + ", +" + feat.tier().points() + " pts, "
                            + unlockedCount + "/" + totalCount + ")" + tag).color(MUTED)));
            if (loud && cfg.playSound()) {
                playSound(player, cfg.unlockSound());
            }
            if (loud && meetsTitleTier(feat.tier(), cfg.bigTitleMinimumTier())) {
                EventTitleUtil.showEventTitleToPlayer(player,
                        Message.raw(feat.name()).color(feat.tier().color()),
                        Message.raw(feat.description()),
                        feat.tier() == Tier.PLATINUM ? EventTitleStyle.Major : EventTitleStyle.Default);
            }
            if (cfg.broadcastUnlocks() && meetsBroadcastTier(feat.tier(), cfg.broadcastMinimumTier())) {
                broadcast(player, feat);
            }
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Feats could not announce %s to %s", feat.id(), uuid);
        }
        if (progress.notifications()) {
            String why = bannerReason(cfg, feat);
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
            String why = BannerQueue.broadcastReason(feat, byName);
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
    String bannerReason(FeatsConfig cfg, Feat feat) {
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
     * Asks the queue for a slot and, when granted, adds the banner on the player's world thread, plays
     * the banner sound, spawns the particles for the holder and schedules the removal. Never throws.
     */
    private void showBanner(PlayerRef player, Feat feat, FeatsConfig cfg, String why, boolean holder) {
        try {
            UUID uuid = player.getUuid();
            long stay = cfg.bannerSeconds() * 1000L;
            BannerQueue.Shown shown = banners.offer(uuid, clock.getAsLong(), stay);
            if (shown == null) {
                return; // over the three in a row, the chat line covers it
            }
            FeatsBannerHud.Frame frame = new FeatsBannerHud.Frame(
                    feat.icon(), feat.name(), feat.tier().color(), feat.description(), why);
            String sound = cfg.bannerSound();
            boolean particles = holder && cfg.bannerParticles();
            onWorldThread(player, (ref, store, world) -> {
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
                world.scheduleAfter(() -> removeBanner(player, shown.generation()), stay, TimeUnit.MILLISECONDS);
            });
        } catch (RuntimeException | LinkageError e) {
            warnBannerOnce(e);
        }
    }

    /** The removal timer. Hops to the player's current world and removes the banner when it is still ours. */
    private void removeBanner(PlayerRef player, int generation) {
        try {
            if (!banners.expire(player.getUuid(), generation)) {
                return; // a later banner replaced it and has its own timer
            }
            onWorldThread(player, (ref, store, world) -> {
                HudManager hud = hudManager(ref, store);
                if (hud != null && hud.getCustomHud(FeatsBannerHud.KEY) != null) {
                    hud.removeCustomHud(player, FeatsBannerHud.KEY);
                }
            });
        } catch (RuntimeException | LinkageError e) {
            warnBannerOnce(e);
        }
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
        PlayerRef player = Universe.get().getPlayer(uuid);
        if (player == null || more <= 0) {
            return;
        }
        player.sendMessage(Message.join(
                Message.raw("Feats ").color(GOLD).bold(true),
                Message.raw(more + (more == 1 ? " more feat" : " more feats") + " unlocked  (" + unlockedCount + "/"
                        + totalCount + "), see /feats").color(MUTED)));
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
        player.sendMessage(Message.join(
                Message.raw("Server milestone reached, ").color(MUTED),
                Message.raw(feat.name()).color(feat.tier().color()).bold(true),
                Message.raw(byName == null || byName.isEmpty() ? "" : ", by " + byName).color(MUTED)));
    }

    /** One line for shared milestones beyond the few told one by one. */
    public void moreShared(UUID uuid, int more) {
        PlayerRef player = Universe.get().getPlayer(uuid);
        if (player == null || more <= 0) {
            return;
        }
        player.sendMessage(Message.raw(more + (more == 1 ? " more server milestone" : " more server milestones")
                + " reached, see the World tab in /feats").color(MUTED));
    }

    private void sendToast(PlayerRef player, Feat feat) {
        NotificationUtil.sendNotification(
                player.getPacketHandler(),
                Message.raw(feat.name()).color(GOLD),
                Message.raw(feat.description()),
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

    /** Worded like "  (rare, 3% of players)", or empty when the feat is not rare or rarity is off. */
    private String rarityTag(FeatsConfig cfg, Feat feat) {
        if (!cfg.showRarity()) {
            return "";
        }
        double fraction;
        try {
            fraction = rarity.applyAsDouble(feat.id());
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
        if (fraction < 0 || fraction * 100.0 >= cfg.rareBelowPercent()) {
            return "";
        }
        double percent = fraction * 100.0;
        String shown = percent < 1.0 ? "under 1%" : Math.round(percent) + "%";
        return "  (rare, " + shown + " of players)";
    }

    /** True when the tier is at or above the configured minimum. OFF, or an unknown value, shows none. */
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
        Message line = Message.join(
                Message.raw(player.getUsername()).color("#7a9cc6").bold(true),
                Message.raw(" unlocked the feat ").color(MUTED),
                Message.raw(feat.name()).color(feat.tier().color()).bold(true),
                Message.raw(" [" + feat.tier().displayName() + "]").color(MUTED));
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
