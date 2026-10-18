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
import dev.ryvrook.feats.lang.Lang;
import dev.ryvrook.feats.export.Rarity;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.PlayerProgress;

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

    private final FeatsPlugin plugin;
    private final RequiredArg<String> featArg;
    private final Map<UUID, Long> lastShare = new ConcurrentHashMap<>();

    public ShareCommand(FeatsPlugin plugin) {
        super("share", "Post one of your feats to chat.");
        requireNoPermission();
        this.plugin = plugin;
        this.featArg = withRequiredArg("feat", "Id of the feat to share, such as rock_bottom", ArgTypes.STRING);
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        FeatRegistry registry = plugin.tracker().registry();
        Feat feat = find(registry, featArg.get(context), playerRef.getLanguage());
        PlayerProgress progress = plugin.tracker().load(playerRef.getUuid(), playerRef.getUsername());
        Long at = feat == null ? null : progress.unlockedAt(feat.id());
        if (feat == null || at == null) {
            // The same answer for unknown and locked feats, so share cannot be used to probe hidden ids.
            context.sendMessage(Message.translation(Lang.CMD_SHARE_LOCKED).color(Targets.ERROR));
            return;
        }
        long wait = claim(playerRef.getUuid());
        if (wait > 0) {
            context.sendMessage(Message.translation(Lang.CMD_SHARE_WAIT).param("n", (wait + 999) / 1000).color(MUTED));
            return;
        }
        Message tag = Rarity.message(plugin.exporter(), plugin.config(), feat.id());
        // The same path the unlock broadcast uses, one message per online player, with the date in each
        // reader's language.
        for (PlayerRef other : Universe.get().getPlayers()) {
            Message line = Message.translation(Lang.CMD_SHARE_LINE)
                    .param("player", Message.raw(playerRef.getUsername()).color("#7a9cc6").bold(true))
                    .param("feat", Lang.colouredName(feat).bold(true))
                    .param("tier", Lang.tier(feat.tier()))
                    .param("date", Lang.date(at, other.getLanguage()))
                    .color(MUTED);
            other.sendMessage(tag == null ? line : Message.join(line, Message.raw(", ").color(MUTED), tag.color(MUTED)));
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

    /** Looks a feat up by id, ignoring case, or by its English name with underscores for spaces. */
    static Feat find(FeatRegistry registry, String input) {
        return find(registry, input, null);
    }

    /**
     * Looks a feat up by id, ignoring case, or by its name with underscores for spaces, the English name or
     * the name in the player's language, which is what they read on the page.
     */
    static Feat find(FeatRegistry registry, String input, String language) {
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
        if (language != null) {
            for (Feat f : registry.all()) {
                if (normalise(Lang.nameFor(f, language)).equals(wanted)) {
                    return f;
                }
            }
        }
        return null;
    }

    /** Lower case letters and digits only, so accents, spaces and punctuation never matter. */
    private static String normalise(String s) {
        String plain = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
