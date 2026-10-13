package dev.ryvrook.feats.track;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import dev.ryvrook.feats.FeatsConfig;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.Tier;
import dev.ryvrook.feats.stats.PlayerProgress;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;

/** Toast, sound, big title and chat feedback when a feat unlocks. */
public final class UnlockNotifier {
    /** The sound used when {@code UnlockSound} is empty or names no sound. */
    public static final String DEFAULT_SOUND = "SFX_Memories_Unlock_Local";
    private static final String GOLD = "#E8A93B";
    private static final String MUTED = "#96a9be";

    private final Supplier<FeatsConfig> config;
    private final HytaleLogger logger;
    /** The sound id the cached index belongs to, so a reload with a new id looks it up again. */
    private volatile String soundId;
    private volatile int soundIndex = Integer.MIN_VALUE;
    private volatile ToDoubleFunction<String> rarity = id -> -1;

    public UnlockNotifier(Supplier<FeatsConfig> config, HytaleLogger logger) {
        this.config = config;
        this.logger = logger;
    }

    /** Where the rarity tag comes from, the fraction of players owning a feat or -1 when unknown. */
    public void setRarity(ToDoubleFunction<String> rarity) {
        this.rarity = rarity == null ? id -> -1 : rarity;
    }

    public void unlocked(PlayerProgress progress, Feat feat, int unlockedCount, int totalCount) {
        FeatsConfig cfg = config.get();
        UUID uuid = progress.uuid();
        PlayerRef player = Universe.get().getPlayer(uuid);
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
                        feat.tier() == Tier.PLATINUM);
            }
            if (cfg.broadcastUnlocks() && meetsBroadcastTier(feat.tier(), cfg.broadcastMinimumTier())) {
                broadcast(player, feat);
            }
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Failed to announce feat %s for %s", feat.id(), uuid);
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
        String wanted = configured == null || configured.isBlank() ? DEFAULT_SOUND : configured.trim();
        int index = soundIndex;
        if (index == Integer.MIN_VALUE || !wanted.equals(soundId)) {
            index = lookupSound(wanted);
            if (index <= 0 && !wanted.equals(DEFAULT_SOUND)) {
                index = lookupSound(DEFAULT_SOUND);
            }
            soundId = wanted;
            soundIndex = index;
        }
        if (index > 0) {
            SoundUtil.playSoundEvent2dToPlayer(player, index, SoundCategory.UI);
        }
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
