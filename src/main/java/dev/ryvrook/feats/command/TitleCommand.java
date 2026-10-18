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
import dev.ryvrook.feats.lang.Lang;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.Tier;
import dev.ryvrook.feats.stats.PlayerProgress;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /feats title} lists the Gold and Platinum feats a player may wear as a title,
 * {@code /feats title <feat>} picks one by id or name and {@code /feats title off} clears it.
 */
final class TitleCommand extends AbstractPlayerCommand {
    private final FeatsPlugin plugin;

    TitleCommand(FeatsPlugin plugin) {
        super("title", "Choose a Gold or Platinum feat as your title.");
        requireNoPermission();
        this.plugin = plugin;
        addUsageVariant(new Choose(plugin));
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        PlayerProgress progress = plugin.tracker().load(playerRef.getUuid(), playerRef.getUsername());
        List<Feat> choices = choices(progress);
        Feat current = progress.title() == null ? null : plugin.tracker().registry().get(progress.title());
        context.sendMessage(Message.translation(Lang.CMD_TITLE_CURRENT)
                .param("title", current == null ? Message.translation(Lang.CMD_TITLE_NONE).color(Targets.MUTED)
                        : Lang.colouredName(current).bold(true))
                .color(Targets.GOLD).bold(true));
        if (choices.isEmpty()) {
            context.sendMessage(Message.translation(Lang.CMD_TITLE_HINT).color(Targets.MUTED));
            return;
        }
        for (Feat f : choices) {
            context.sendMessage(Message.translation(Lang.CMD_TITLE_ROW)
                    .param("feat", Lang.colouredName(f))
                    .param("id", f.id())
                    .color(Targets.MUTED));
        }
        context.sendMessage(Message.translation(Lang.CMD_TITLE_CLEAR_HINT).color(Targets.MUTED));
    }

    private List<Feat> choices(PlayerProgress progress) {
        List<Feat> out = new ArrayList<>();
        for (Feat f : plugin.tracker().registry().all()) {
            if (eligible(f) && progress.isUnlocked(f.id())) {
                out.add(f);
            }
        }
        return out;
    }

    static boolean eligible(Feat feat) {
        return feat.tier() == Tier.GOLD || feat.tier() == Tier.PLATINUM;
    }

    /** The one argument form. */
    static final class Choose extends AbstractPlayerCommand {
        private final FeatsPlugin plugin;
        private final RequiredArg<String> feat;

        Choose(FeatsPlugin plugin) {
            super("Pick a title by feat id or name, or off to clear it.");
            requireNoPermission();
            this.plugin = plugin;
            this.feat = withRequiredArg("feat", "Feat id or name, or off", ArgTypes.GREEDY_STRING);
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef playerRef, World world) {
            PlayerProgress progress = plugin.tracker().load(playerRef.getUuid(), playerRef.getUsername());
            String wanted = context.get(feat).trim();
            if (wanted.equalsIgnoreCase("off") || wanted.equalsIgnoreCase("none")) {
                progress.setTitle(null);
                context.sendMessage(Message.translation(Lang.CMD_TITLE_CLEARED).color(Targets.MUTED));
                return;
            }
            Feat chosen = ShareCommand.find(plugin.tracker().registry(), wanted, playerRef.getLanguage());
            if (chosen == null) {
                context.sendMessage(Message.translation(Lang.CMD_TITLE_UNKNOWN).param("name", wanted).color(Targets.ERROR));
                return;
            }
            if (!progress.isUnlocked(chosen.id())) {
                context.sendMessage(Message.translation(Lang.CMD_TITLE_LOCKED).color(Targets.ERROR));
                return;
            }
            if (!eligible(chosen)) {
                context.sendMessage(Message.translation(Lang.CMD_TITLE_TIER).color(Targets.ERROR));
                return;
            }
            progress.setTitle(chosen.id());
            context.sendMessage(Message.translation(Lang.CMD_TITLE_SET)
                    .param("feat", Lang.colouredName(chosen).bold(true))
                    .color(Targets.MUTED));
        }
    }
}
