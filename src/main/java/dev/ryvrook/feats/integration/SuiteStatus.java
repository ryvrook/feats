package dev.ryvrook.feats.integration;

import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Sibling;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The lines of {@code /feats admin status}, one per suite mod and one per mod TrackerAPI reads, with its version,
 * whether Feats is linked to it and the setting that controls the link. Reads the plugin manager by name only, so
 * it touches no sibling class.
 */
public final class SuiteStatus {
    /**
     * What the status reads, kept apart from the plugin so the tests can fake it. {@code version} and
     * {@code installed} take a plugin name, see {@link Siblings#installed(String)}.
     */
    public record Inputs(Set<Sibling> present, List<String> linked, boolean trackerFeeds, boolean coinRewards,
                         Function<Sibling, String> mode, String discordVia, Function<String, String> version,
                         Function<String, Boolean> installed) {
    }

    private SuiteStatus() {
    }

    public static List<String> lines(Inputs in) {
        List<String> out = new ArrayList<>();
        for (Sibling s : Sibling.values()) {
            out.add(line(s, in));
        }
        out.add(reader(Siblings.SOCIALBRIDGE, in, in.installed().apply(Siblings.SOCIALBRIDGE)
                ? (dev.ryvrook.feats.DiscordVia.useWebhook(in.discordVia(), true) ? "reads Feats, the webhook posts too"
                : "reads Feats and posts the unlocks") : null, "DiscordVia " + in.discordVia()));
        out.add(reader("RestAPI", in, "reads Feats under /api/feats", null));
        out.add(reader("Lullaby", in, "installed, the two do not link", null));
        return out;
    }

    private static String line(Sibling s, Inputs in) {
        String plugin = s.pluginName();
        String name = s.displayName();
        String mode = in.mode().apply(s);
        String setting = ", setting " + s.settingKey() + " " + mode;
        boolean installed = in.installed().apply(plugin);
        String version = in.version().apply(plugin);
        String head = version == null ? name : name + " " + version;
        if ("never".equals(mode)) {
            return head + (installed ? " is installed and ignored" : " is ignored") + setting;
        }
        if (!installed) {
            return name + " is not installed" + setting;
        }
        if (!in.present().contains(s)) {
            return head + " is older than " + s.minimum() + ", so its feats are out" + setting;
        }
        return head + " " + switch (s) {
            case TRACKER_API -> in.trackerFeeds() ? "feeds the counters" : "is installed, Feats tracks on its own";
            case DOMINION -> "sends the Factions counters";
            case COINAGE -> in.linked().contains("Coinage")
                    ? (in.coinRewards() ? "is linked, with coin rewards" : "is linked") : "is not linked yet";
            case CHRONICLE -> in.linked().contains("Chronicle Stats") ? "is linked" : "is not linked yet";
            case ATLAS -> in.linked().contains("Atlas") ? "is linked" : "is not linked yet";
            default -> mod(in);
        } + setting;
    }

    /**
     * A mod TrackerAPI reads is linked through TrackerAPI alone. Its feats are in when TrackerAPI feeds the counters
     * and is at least {@link FeatRegistry#MODS_MINIMUM}, which is what {@code FeatRegistry.defineMods} checks.
     */
    private static String mod(Inputs in) {
        String tracker = in.version().apply(Sibling.TRACKER_API.pluginName());
        boolean newEnough = tracker != null && !VersionCompare.below(tracker, FeatRegistry.MODS_MINIMUM);
        if (in.trackerFeeds() && newEnough) {
            return "feeds its counters through TrackerAPI";
        }
        return "is installed, its feats need TrackerAPI " + FeatRegistry.MODS_MINIMUM + " feeding the counters";
    }

    private static String reader(String name, Inputs in, String what, String setting) {
        if (!in.installed().apply(name)) {
            return name + " is not installed";
        }
        String version = in.version().apply(name);
        return (version == null ? name : name + " " + version) + " " + what + (setting == null ? "" : ", setting " + setting);
    }
}
