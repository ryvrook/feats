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
            context.sendMessage(Message.translation(Lang.CMD_VIEW_OFF).color(Targets.MUTED));
            return;
        }
        PlayerProgress self = plugin.tracker().load(playerRef.getUuid(), playerRef.getUsername());
        Targets.find(plugin, context, context.get(player), target -> {
            PlayerProgress other = plugin.tracker().peek(target.uuid());
            if (other == null) {
                Targets.reply(context, Message.translation(Lang.CMD_NO_FEATS).param("player", target.name()).color(Targets.MUTED));
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
        out.add(Message.translation(Lang.CMD_COMPARE_HEAD)
                .param("you", Message.translation(Lang.CMD_COMPARE_YOU).color("#7fe3ff").bold(true))
                .param("other", Message.raw(otherName).color("#7a9cc6").bold(true))
                .color(Targets.MUTED));
        out.add(row(Message.translation(Lang.CMD_COMPARE_POINTS), String.valueOf(plugin.tracker().points(self)),
                String.valueOf(plugin.tracker().points(other))));
        out.add(row(Message.translation(Lang.CMD_COMPARE_UNLOCKED), self.unlockedCount() + "/" + total, other.unlockedCount() + "/" + total));
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
            out.add(row(Lang.category(category), mine + "/" + feats.size(), theirs + "/" + feats.size()));
        }
        return out;
    }

    private static Message row(Message label, String mine, String theirs) {
        return Message.translation(Lang.CMD_COMPARE_ROW)
                .param("label", label.color("#d3d6db"))
                .param("mine", Message.raw(mine).color("#7fe3ff"))
                .param("theirs", Message.raw(theirs).color("#7a9cc6"))
                .color(Targets.MUTED);
    }
}
