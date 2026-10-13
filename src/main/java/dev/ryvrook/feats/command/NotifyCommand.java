package dev.ryvrook.feats.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.FeatsPlugin;
import dev.ryvrook.feats.stats.PlayerProgress;

/**
 * {@code /feats notify on|off} turns a player's own unlock toasts, sounds and big titles on or off.
 * The chat line always stays. Without an argument it shows the current setting.
 */
final class NotifyCommand extends AbstractPlayerCommand {
    private final FeatsPlugin plugin;

    NotifyCommand(FeatsPlugin plugin) {
        super("notify", "Turn your unlock toasts, sounds and titles on or off.");
        requireNoPermission();
        this.plugin = plugin;
        addUsageVariant(new Toggle(plugin));
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        PlayerProgress progress = plugin.tracker().load(playerRef.getUuid(), playerRef.getUsername());
        context.sendMessage(Message.raw("Unlock toasts, sounds and titles are "
                + (progress.notifications() ? "on" : "off") + ". Use /feats notify on or /feats notify off.")
                .color(Targets.MUTED));
    }

    static final class Toggle extends AbstractPlayerCommand {
        private final FeatsPlugin plugin;
        private final RequiredArg<String> state;

        Toggle(FeatsPlugin plugin) {
            super("Turn unlock toasts, sounds and titles on or off.");
            requireNoPermission();
            this.plugin = plugin;
            this.state = withRequiredArg("state", "on or off", ArgTypes.STRING);
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef playerRef, World world) {
            String value = context.get(state).trim().toLowerCase(java.util.Locale.ROOT);
            boolean on;
            if (value.equals("on")) {
                on = true;
            } else if (value.equals("off")) {
                on = false;
            } else {
                context.sendMessage(Message.raw("Use /feats notify on or /feats notify off.").color(Targets.ERROR));
                return;
            }
            PlayerProgress progress = plugin.tracker().load(playerRef.getUuid(), playerRef.getUsername());
            progress.setNotifications(on);
            context.sendMessage(Message.raw(on
                    ? "Unlock toasts, sounds and titles are on."
                    : "Unlock toasts, sounds and titles are off. Unlocks still show in chat.").color(Targets.MUTED));
        }
    }
}
