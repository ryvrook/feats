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
        context.sendMessage(Message.raw("Recent feats").color("#E8A93B").bold(true));
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
            context.sendMessage(Message.join(
                    Message.raw(" " + r.name()).color("#7a9cc6").bold(true),
                    Message.raw(" unlocked ").color(MUTED),
                    secret ? Message.raw("a hidden feat").color(MUTED)
                            : Message.raw(feat.name()).color(feat.tier().color()).bold(true),
                    Message.raw(" [" + feat.tier().displayName() + "]  " + ago(now - r.at())).color(MUTED)));
            shown++;
        }
        if (shown == 0) {
            context.sendMessage(Message.raw("Nobody has unlocked anything yet.").color(MUTED));
        }
    }

    /** {@code just now}, {@code 5 minutes ago}, {@code 3 hours ago} or {@code 2 days ago}. */
    static String ago(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds < 60) {
            return "just now";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return plural(minutes, "minute") + " ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return plural(hours, "hour") + " ago";
        }
        return plural(hours / 24, "day") + " ago";
    }

    private static String plural(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s");
    }
}
