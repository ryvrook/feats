package dev.ryvrook.feats.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.FeatsPlugin;
import dev.ryvrook.feats.lang.Lang;
import dev.ryvrook.feats.export.LeaderboardExporter.RecentUnlock;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.PlayerProgress;

import java.util.List;

/**
 * {@code /feats recent} lists the last 10 unlocks on the server, newest first, from the list the
 * leaderboard export keeps in memory. Hidden feats stay hidden for players who have not earned them.
 */
public final class RecentCommand extends AbstractPlayerCommand {
    private static final int SHOWN = 10;
    private static final String MUTED = "#96a9be";

    private final FeatsPlugin plugin;

    public RecentCommand(FeatsPlugin plugin) {
        super("recent", "Show the latest feats unlocked on this server.");
        requireNoPermission();
        this.plugin = plugin;
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        FeatRegistry registry = plugin.tracker().registry();
        PlayerProgress self = plugin.tracker().load(playerRef.getUuid(), playerRef.getUsername());
        List<RecentUnlock> recent = plugin.exporter().recentUnlocks();
        context.sendMessage(Message.translation(Lang.CMD_RECENT_TITLE).color(Targets.GOLD).bold(true));
        long now = System.currentTimeMillis();
        int shown = 0;
        for (RecentUnlock r : recent) {
            if (shown >= SHOWN) {
                break;
            }
            Feat feat = registry.get(r.featId());
            if (feat == null) {
                continue;
            }
            boolean secret = feat.hidden() && !self.isUnlocked(feat.id());
            context.sendMessage(Message.translation(Lang.CMD_RECENT_ROW)
                    .param("player", Message.raw(r.name()).color("#7a9cc6").bold(true))
                    .param("feat", secret ? Message.translation(Lang.CMD_RECENT_HIDDEN) : Lang.colouredName(feat).bold(true))
                    .param("tier", Lang.tier(feat.tier()))
                    .param("ago", ago(now - r.at()))
                    .color(MUTED));
            shown++;
        }
        if (shown == 0) {
            context.sendMessage(Message.translation(Lang.CMD_TOP_EMPTY).color(MUTED));
        }
    }

    /** {@code just now}, {@code 5 minutes ago}, {@code 3 hours ago} or {@code 2 days ago}, in the viewer's language. */
    static Message ago(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds < 60) {
            return Message.translation(Lang.CMD_RECENT_JUST_NOW);
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return Message.translation(Lang.CMD_RECENT_MINUTES).param("n", minutes);
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return Message.translation(Lang.CMD_RECENT_HOURS).param("n", hours);
        }
        return Message.translation(Lang.CMD_RECENT_DAYS).param("n", hours / 24);
    }
}
