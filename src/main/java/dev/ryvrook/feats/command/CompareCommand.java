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
import dev.ryvrook.feats.model.Category;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.PlayerProgress;

import java.util.ArrayList;
import java.util.List;

/** {@code /feats compare <player>} prints points, unlocks and per category counts side by side. */
final class CompareCommand extends AbstractPlayerCommand {
    private final FeatsPlugin plugin;
    private final RequiredArg<String> player;

    CompareCommand(FeatsPlugin plugin) {
        super("compare", "Compare your feats with another player.");
        requireNoPermission();
        this.plugin = plugin;
        this.player = withRequiredArg("player", "Player name", ArgTypes.STRING);
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        if (!plugin.config().allowViewOthers()) {
            context.sendMessage(Message.raw("Viewing other players is turned off on this server.").color(Targets.MUTED));
            return;
        }
        PlayerProgress self = plugin.tracker().load(playerRef.getUuid(), playerRef.getUsername());
        Targets.find(plugin, context, context.get(player), target -> {
            PlayerProgress other = plugin.tracker().peek(target.uuid());
            if (other == null) {
                Targets.reply(context, Message.raw(target.name() + " has no feats yet.").color(Targets.MUTED));
                return;
            }
            String otherName = other.username().isEmpty() ? target.name() : other.username();
            for (Message line : lines(self, other, otherName)) {
                Targets.reply(context, line);
            }
        });
    }

    private List<Message> lines(PlayerProgress self, PlayerProgress other, String otherName) {
        FeatRegistry registry = plugin.tracker().registry();
        int total = registry.count();
        List<Message> out = new ArrayList<>();
        out.add(Message.join(
                Message.raw("You").color("#7fe3ff").bold(true),
                Message.raw("  vs  ").color(Targets.MUTED),
                Message.raw(otherName).color("#7a9cc6").bold(true)));
        out.add(row("Points", String.valueOf(plugin.tracker().points(self)),
                String.valueOf(plugin.tracker().points(other))));
        out.add(row("Unlocked", self.unlockedCount() + "/" + total, other.unlockedCount() + "/" + total));
        for (Category category : Category.values()) {
            List<Feat> feats = registry.inCategory(category);
            if (feats.isEmpty()) {
                continue;
            }
            int mine = 0;
            int theirs = 0;
            for (Feat f : feats) {
                if (self.isUnlocked(f.id())) {
                    mine++;
                }
                if (other.isUnlocked(f.id())) {
                    theirs++;
                }
            }
            out.add(row(category.displayName(), mine + "/" + feats.size(), theirs + "/" + feats.size()));
        }
        return out;
    }

    private static Message row(String label, String mine, String theirs) {
        return Message.join(
                Message.raw(" " + label + "  ").color("#d3d6db"),
                Message.raw(mine).color("#7fe3ff"),
                Message.raw("  vs  ").color(Targets.MUTED),
                Message.raw(theirs).color("#7a9cc6"));
    }
}
