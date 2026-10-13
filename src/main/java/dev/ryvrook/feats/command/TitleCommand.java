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
        context.sendMessage(Message.join(
                Message.raw("Your title ").color(Targets.GOLD).bold(true),
                current == null ? Message.raw("none").color(Targets.MUTED)
                        : Message.raw(current.name()).color(current.tier().color()).bold(true)));
        if (choices.isEmpty()) {
            context.sendMessage(Message.raw("Unlock a Gold or Platinum feat to wear it as a title.").color(Targets.MUTED));
            return;
        }
        for (Feat f : choices) {
            context.sendMessage(Message.join(
                    Message.raw(" " + f.name()).color(f.tier().color()),
                    Message.raw("  /feats title " + f.id()).color(Targets.MUTED)));
        }
        context.sendMessage(Message.raw("Use /feats title off to clear it.").color(Targets.MUTED));
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
                context.sendMessage(Message.raw("Your title is cleared.").color(Targets.MUTED));
                return;
            }
            Feat chosen = find(wanted);
            if (chosen == null) {
                context.sendMessage(Message.raw("There is no feat called " + wanted + ".").color(Targets.ERROR));
                return;
            }
            if (!progress.isUnlocked(chosen.id())) {
                context.sendMessage(Message.raw("You have not unlocked that feat yet.").color(Targets.ERROR));
                return;
            }
            if (!eligible(chosen)) {
                context.sendMessage(Message.raw("Only Gold and Platinum feats can be titles.").color(Targets.ERROR));
                return;
            }
            progress.setTitle(chosen.id());
            context.sendMessage(Message.join(
                    Message.raw("Your title is now ").color(Targets.MUTED),
                    Message.raw(chosen.name()).color(chosen.tier().color()).bold(true)));
        }

        private Feat find(String wanted) {
            Feat byId = plugin.tracker().registry().get(wanted);
            if (byId != null) {
                return byId;
            }
            for (Feat f : plugin.tracker().registry().all()) {
                if (f.id().equalsIgnoreCase(wanted) || f.name().equalsIgnoreCase(wanted)) {
                    return f;
                }
            }
            return null;
        }
    }
}
