package dev.ryvrook.feats.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.FeatsPlugin;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.ui.FeatsPage;

/**
 * {@code /feats} opens the browser. Its sub commands are stats, top, reset, view, compare, title, notify,
 * recent, share and admin.
 */
public final class FeatsCommand extends AbstractPlayerCommand {
    private final FeatsPlugin plugin;

    public FeatsCommand(FeatsPlugin plugin) {
        super("feats", "Browse your achievements and statistics.");
        // Without this the engine generates ryvrook.feats.command.feats and only admins could run it.
        requireNoPermission();
        this.plugin = plugin;
        addAliases("feat", "achievements");
        addSubCommand(new StatsCommand(plugin));
        addSubCommand(new TopCommand(plugin));
        addSubCommand(new ResetCommand(plugin));
        addSubCommand(new ViewCommand(plugin));
        addSubCommand(new CompareCommand(plugin));
        addSubCommand(new TitleCommand(plugin));
        addSubCommand(new NotifyCommand(plugin));
        addSubCommand(new RecentCommand(plugin));
        addSubCommand(new ShareCommand(plugin));
        // No requireNoPermission here, so admin and each of its sub commands get generated admin only nodes.
        addSubCommand(new AdminCommand(plugin));
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        openPage(plugin, context, store, ref, playerRef, FeatsPage.Tab.ALL);
    }

    static void openPage(FeatsPlugin plugin, CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                         PlayerRef playerRef, FeatsPage.Tab tab) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Feats can only be opened by a player.").color("#ff6b6b"));
            return;
        }
        PlayerProgress progress = plugin.tracker().load(playerRef.getUuid(), playerRef.getUsername());
        FeatsPage page = new FeatsPage(playerRef, plugin, progress, tab);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    /** {@code /feats stats} opens the browser directly on the statistics tab. */
    static final class StatsCommand extends AbstractPlayerCommand {
        private final FeatsPlugin plugin;

        StatsCommand(FeatsPlugin plugin) {
            super("stats", "Show your lifetime statistics.");
            requireNoPermission();
            this.plugin = plugin;
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef playerRef, World world) {
            openPage(plugin, context, store, ref, playerRef, FeatsPage.Tab.STATS);
        }
    }
}
