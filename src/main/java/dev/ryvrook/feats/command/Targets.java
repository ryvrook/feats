package dev.ryvrook.feats.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.ryvrook.feats.FeatsPlugin;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Finds players by name for the commands that act on someone else, and sends replies back. */
final class Targets {
    static final String MUTED = "#96a9be";
    static final String ERROR = "#ff6b6b";
    static final String GOLD = "#E8A93B";

    private Targets() {
    }

    /** A player found by name. {@code online} is true when they are on the server right now. */
    record Target(UUID uuid, String name, boolean online) {
    }

    /**
     * Looks the name up among online players first, then in the stored names of the player files, and
     * runs {@code then} on the Feats IO thread with the result, or replies that nobody matched.
     */
    static void find(FeatsPlugin plugin, CommandContext context, String name, Consumer<Target> then) {
        PlayerRef online = null;
        try {
            online = Universe.get().getPlayerByUsername(name, NameMatching.EXACT_IGNORE_CASE);
        } catch (RuntimeException e) {
            // fall back to the files
        }
        Target direct = online == null ? null : new Target(online.getUuid(), online.getUsername(), true);
        try {
            plugin.runIo(() -> {
                Target target = direct;
                if (target == null) {
                    UUID uuid = plugin.store().findByName(name);
                    if (uuid != null) {
                        target = new Target(uuid, name, false);
                    }
                }
                if (target == null) {
                    reply(context, Message.raw("No player named " + name + " has played on this server.").color(ERROR));
                    return;
                }
                try {
                    then.accept(target);
                } catch (RuntimeException e) {
                    plugin.getLogger().at(Level.WARNING).withCause(e).log("Feats command failed for %s", name);
                    reply(context, Message.raw("That did not work, see the server log.").color(ERROR));
                }
            });
        } catch (RejectedExecutionException e) {
            context.sendMessage(Message.raw("Feats is shutting down.").color(ERROR));
        }
    }

    /** Sends a reply on the sender's world thread when the sender is a player, directly otherwise. */
    static void reply(CommandContext context, Message message) {
        World world = senderWorld(context);
        if (world == null) {
            context.sendMessage(message);
            return;
        }
        try {
            world.execute(() -> context.sendMessage(message));
        } catch (RuntimeException e) {
            context.sendMessage(message);
        }
    }

    /** Runs {@code task} on the sender's world thread, or not at all when the sender is not in a world. */
    static boolean onSenderWorld(CommandContext context, Runnable task) {
        World world = senderWorld(context);
        if (world == null) {
            return false;
        }
        try {
            world.execute(task);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static World senderWorld(CommandContext context) {
        if (!context.isPlayer()) {
            return null;
        }
        try {
            PlayerRef sender = context.senderAs(PlayerRef.class);
            UUID worldId = sender == null ? null : sender.getWorldUuid();
            return worldId == null ? null : Universe.get().getWorld(worldId);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
