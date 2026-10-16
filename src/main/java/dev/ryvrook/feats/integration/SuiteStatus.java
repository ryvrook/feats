package dev.ryvrook.feats.integration;

import dev.ryvrook.feats.model.Sibling;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The lines of {@code /feats admin status}, one per suite mod, with its version, whether Feats is linked to it
 * and the setting that controls the link. Reads the plugin manager by name only, so it touches no sibling class.
 */
public final class SuiteStatus {
    /** What the status reads, kept apart from the plugin so the tests can fake it. */
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
        String name = s.pluginName();
        String mode = in.mode().apply(s);
        String setting = ", setting " + (s == Sibling.TRACKER_API ? "Tracker " : name.replace("Stats", "") + " ") + mode;
        boolean installed = in.installed().apply(name);
        String version = in.version().apply(name);
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
        } + setting;
    }

    private static String reader(String name, Inputs in, String what, String setting) {
        if (!in.installed().apply(name)) {
            return name + " is not installed";
        }
        String version = in.version().apply(name);
        return (version == null ? name : name + " " + version) + " " + what + (setting == null ? "" : ", setting " + setting);
    }
}
