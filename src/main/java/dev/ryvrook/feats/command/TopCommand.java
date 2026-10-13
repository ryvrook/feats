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
import dev.ryvrook.feats.export.LeaderboardExporter;
import dev.ryvrook.feats.export.LeaderboardExporter.Ranked;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.PlayerProgress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * {@code /feats top} prints the ten highest scoring players this server has seen.
 *
 * <p>Normally it answers from the ranking the leaderboard export keeps in memory, with online players
 * brought up to date. When the export is turned off it reads the player files on the Feats IO thread,
 * at most once every 10 seconds per player, and replies on the world thread. Both paths sort exactly
 * like the export does.
 */
final class TopCommand extends AbstractPlayerCommand {
    private static final int SHOWN = 10;
    private static final long COOLDOWN_MILLIS = 10_000L;

    private final FeatsPlugin plugin;
    private final Map<UUID, Long> lastDiskRead = new ConcurrentHashMap<>();

    TopCommand(FeatsPlugin plugin) {
        super("top", "Show the feats leaderboard.");
        requireNoPermission();
        this.plugin = plugin;
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        UUID self = playerRef.getUuid();
        FeatRegistry registry = plugin.tracker().registry();
        if (plugin.exporting()) {
            reply(context::sendMessage, merge(plugin.exporter().ranking(), plugin.tracker().loadedPlayers(), registry),
                    self, registry.count());
            return;
        }
        if (!claimDiskRead(self)) {
            context.sendMessage(Message.raw("The leaderboard was just checked. Try again in a few seconds.")
                    .color("#96a9be"));
            return;
        }
        try {
            plugin.runIo(() -> readAndReply(playerRef, world, self, registry));
        } catch (RejectedExecutionException e) {
            context.sendMessage(Message.raw("The leaderboard could not be read right now.").color("#ff6b6b"));
        }
    }

    /** Runs on the IO thread. Only the reply goes back to the world thread. */
    private void readAndReply(PlayerRef playerRef, World world, UUID self, FeatRegistry registry) {
        List<Ranked> rows;
        try {
            Collection<PlayerProgress> everyone = plugin.store().loadAll(plugin.tracker().loadedPlayers());
            rows = merge(List.of(), everyone, registry);
        } catch (RuntimeException e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("Feats could not read the leaderboard");
            rows = null;
        }
        List<Ranked> result = rows;
        try {
            world.execute(() -> {
                if (!playerRef.isValid()) {
                    return;
                }
                if (result == null) {
                    playerRef.sendMessage(Message.raw("The leaderboard could not be read right now.")
                            .color("#ff6b6b"));
                } else {
                    reply(playerRef::sendMessage, result, self, registry.count());
                }
            });
        } catch (RuntimeException e) {
            plugin.getLogger().at(Level.FINE).withCause(e).log("Feats leaderboard reply dropped");
        }
    }

    /** True when this player may trigger a disk read now, recording the time if so. */
    private boolean claimDiskRead(UUID uuid) {
        long now = System.currentTimeMillis();
        if (lastDiskRead.size() > 64) {
            lastDiskRead.values().removeIf(at -> now - at >= COOLDOWN_MILLIS);
        }
        boolean[] allowed = {false};
        lastDiskRead.compute(uuid, (id, last) -> {
            if (last == null || now - last >= COOLDOWN_MILLIS) {
                allowed[0] = true;
                return now;
            }
            return last;
        });
        return allowed[0];
    }

    /**
     * Combines ranked rows with players held in memory, whose current points win, and sorts them in
     * {@link LeaderboardExporter#ORDER}. Points are worked out once per player.
     */
    private static List<Ranked> merge(List<Ranked> cached, Collection<PlayerProgress> live, FeatRegistry registry) {
        Map<UUID, Ranked> byId = new HashMap<>();
        for (Ranked r : cached) {
            byId.put(r.uuid(), r);
        }
        for (PlayerProgress p : live) {
            byId.put(p.uuid(), LeaderboardExporter.rank(p, registry));
        }
        List<Ranked> rows = new ArrayList<>(byId.values());
        rows.sort(LeaderboardExporter.ORDER);
        return rows;
    }

    private static void reply(Consumer<Message> out, List<Ranked> rows, UUID self, int totalFeats) {
        out.accept(Message.raw("Feats leaderboard").color("#E8A93B").bold(true));
        int rank = 1;
        for (Ranked r : rows) {
            if (rank > SHOWN) {
                break;
            }
            boolean mine = r.uuid().equals(self);
            out.accept(Message.join(
                    Message.raw(" " + rank + ". ").color("#878e9c"),
                    Message.raw(r.name()).color(mine ? "#7fe3ff" : "#d3d6db").bold(mine),
                    Message.raw("  " + r.points() + " pts  ·  " + r.unlocked() + "/" + totalFeats).color("#96a9be")));
            rank++;
        }
        if (rows.isEmpty()) {
            out.accept(Message.raw("Nobody has unlocked anything yet.").color("#96a9be"));
        }
    }
}
