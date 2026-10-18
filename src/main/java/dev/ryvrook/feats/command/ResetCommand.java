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

/** {@code /feats reset} explains, {@code /feats reset confirm} wipes the caller's own progress. */
final class ResetCommand extends AbstractPlayerCommand {

    ResetCommand(FeatsPlugin plugin) {
        super("reset", "Erase your own feats and statistics.");
        requireNoPermission();
        addSubCommand(new Confirm(plugin));
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        context.sendMessage(Message.translation(Lang.CMD_RESET_WARN)
                .param("command", Message.raw("/feats reset confirm").color("#ffffff").bold(true))
                .color("#ff9f43"));
    }

    static final class Confirm extends AbstractPlayerCommand {
        private final FeatsPlugin plugin;

        Confirm(FeatsPlugin plugin) {
            super("confirm", "Confirm erasing your feats.");
            requireNoPermission();
            this.plugin = plugin;
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef playerRef, World world) {
            plugin.tracker().reset(playerRef.getUuid());
            // After the reset so its save is queued on the IO thread ahead of the rewrite this asks for.
            plugin.exporter().forget(playerRef.getUuid());
            context.sendMessage(Message.translation(Lang.CMD_RESET_DONE).color(Targets.MUTED));
        }
    }
}
