package dev.ryvrook.feats.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.FeatsPlugin;
import dev.ryvrook.feats.export.Rarity;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.PlayerProgress;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /feats share <featId>} posts one chat line about a feat the player owns to everyone online,
 * with the tier, the unlock date and the rarity when it is known. Once every 30 seconds per player.
 */
public final class ShareCommand extends AbstractPlayerCommand {
    private static final long COOLDOWN_MILLIS = 30_000L;
    private static final String MUTED = "#96a9be";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final FeatsPlugin plugin;
    private final RequiredArg<String> featArg;
    private final Map<UUID, Long> lastShare = new ConcurrentHashMap<>();

    public ShareCommand(FeatsPlugin plugin) {
        super("share", "Show off one of your feats in chat.");
        requireNoPermission();
        this.plugin = plugin;
        this.featArg = withRequiredArg("feat", "Id of the feat to share, such as rock_bottom", ArgTypes.STRING);
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        FeatRegistry registry = plugin.tracker().registry();
        Feat feat = find(registry, featArg.get(context));
        PlayerProgress progress = plugin.tracker().load(playerRef.getUuid(), playerRef.getUsername());
        Long at = feat == null ? null : progress.unlockedAt(feat.id());
        if (feat == null || at == null) {
            // The same answer for unknown and locked feats, so share cannot be used to probe hidden ids.
            context.sendMessage(Message.raw("You can only share a feat you have unlocked. Use its id, as shown by /feats.")
                    .color("#ff6b6b"));
            return;
        }
        long wait = claim(playerRef.getUuid());
        if (wait > 0) {
            context.sendMessage(Message.raw("You can share again in " + ((wait + 999) / 1000) + " seconds.").color(MUTED));
            return;
        }
        String date = DATE.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()));
        String tag = Rarity.tag(plugin.exporter(), plugin.config(), feat.id());
        Message line = Message.join(
                Message.raw(playerRef.getUsername()).color("#7a9cc6").bold(true),
                Message.raw(" shares the feat ").color(MUTED),
                Message.raw(feat.name()).color(feat.tier().color()).bold(true),
                Message.raw(" [" + feat.tier().displayName() + "]  unlocked " + date
                        + (tag.isEmpty() ? "" : ", " + tag)).color(MUTED));
        // The same path the unlock broadcast uses, one message per online player.
        for (PlayerRef other : Universe.get().getPlayers()) {
            other.sendMessage(line);
        }
    }

    /** Milliseconds left on the cooldown, or 0 when the share may go out now, recording it if so. */
    private long claim(UUID uuid) {
        long now = System.currentTimeMillis();
        if (lastShare.size() > 64) {
            lastShare.values().removeIf(t -> now - t >= COOLDOWN_MILLIS);
        }
        long[] left = {0};
        lastShare.compute(uuid, (id, last) -> {
            if (last == null || now - last >= COOLDOWN_MILLIS) {
                return now;
            }
            left[0] = COOLDOWN_MILLIS - (now - last);
            return last;
        });
        return left[0];
    }

    /** Looks a feat up by id, ignoring case, or by its name with underscores for spaces. */
    static Feat find(FeatRegistry registry, String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String raw = input.trim();
        Feat feat = registry.get(raw);
        if (feat != null) {
            return feat;
        }
        feat = registry.get(raw.toLowerCase(Locale.ROOT));
        if (feat != null) {
            return feat;
        }
        String wanted = normalise(raw);
        for (Feat f : registry.all()) {
            if (normalise(f.id()).equals(wanted) || normalise(f.name()).equals(wanted)) {
                return f;
            }
        }
        return null;
    }

    private static String normalise(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
