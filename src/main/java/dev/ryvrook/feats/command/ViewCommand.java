package dev.ryvrook.feats.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.FeatsPlugin;
import dev.ryvrook.feats.lang.Lang;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.ui.FeatsPage;

/**
 * {@code /feats view <player>} opens the browser read only on someone else's progress, when
 * AllowViewOthers is on. Hidden feats they have not earned stay {@code ???}.
 */
final class ViewCommand extends AbstractPlayerCommand {
    private final FeatsPlugin plugin;
    private final RequiredArg<String> player;

    ViewCommand(FeatsPlugin plugin) {
        super("view", "Browse another player's feats.");
        requireNoPermission();
        this.plugin = plugin;
        this.player = withRequiredArg("player", "Player name", ArgTypes.STRING);
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        if (!plugin.config().allowViewOthers()) {
            context.sendMessage(Message.translation(Lang.CMD_VIEW_OFF).color(Targets.MUTED));
            return;
        }
        open(plugin, context, context.get(player));
    }

    /**
     * Finds the player, reads their progress on the IO thread and opens the browser for the sender on
     * their world thread. Shared with {@code /feats admin view}.
     */
    static void open(FeatsPlugin plugin, CommandContext context, String name) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.translation(Lang.CMD_VIEW_PLAYER_ONLY).color(Targets.ERROR));
            return;
        }
        PlayerRef viewer = context.senderAs(PlayerRef.class);
        Targets.find(plugin, context, name, target -> {
            PlayerProgress progress = plugin.tracker().peek(target.uuid());
            if (progress == null) {
                Targets.reply(context, Message.translation(Lang.CMD_NO_FEATS).param("player", target.name()).color(Targets.MUTED));
                return;
            }
            Targets.onSenderWorld(context, () -> openPage(plugin, viewer, progress));
        });
    }

    /** Runs on the viewer's world thread. */
    static void openPage(FeatsPlugin plugin, PlayerRef viewer, PlayerProgress target) {
        if (!viewer.isValid()) {
            return;
        }
        Ref<EntityStore> ref = viewer.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new FeatsPage(viewer, plugin, target, FeatsPage.Tab.ALL));
    }
}
