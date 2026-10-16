package dev.ryvrook.feats.placeholders;

import at.helpch.placeholderapi.PlaceholderAPIPlugin;
import at.helpch.placeholderapi.expansion.PlaceholderExpansion;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ryvrook.feats.FeatsPlugin;
import dev.ryvrook.feats.export.LeaderboardExporter;
import dev.ryvrook.feats.export.Rarity;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Pack;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.stats.WorldStore;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Optional PlaceholderAPI support with the identifier {@code feats}.
 *
 * <p>This outer class never touches PlaceholderAPI types itself, so calling {@link #registerIfPresent}
 * is safe on a server without PlaceholderAPI. The expansion proper is the nested {@link Hook}, which
 * extends the PlaceholderAPI base class and is only loaded once that class was found.
 *
 * <p>Placeholders are {@code feats_points}, {@code feats_unlocked}, {@code feats_total},
 * {@code feats_total_points}, {@code feats_rank}, {@code feats_title}, {@code feats_last},
 * {@code feats_rarity_<featId>}, {@code feats_stat_<key>}, {@code feats_has_<featId>},
 * {@code feats_first_<featId>}, the holder's name or empty, and {@code feats_milestone_<featId>}, true or false.
 * Since 1.7.2 also {@code feats_streak} and {@code feats_best_streak}, the join streak in days,
 * {@code feats_server_<key>}, a server total, {@code feats_pack_<name>}, true while that pack is on, and
 * {@code feats_siblings}, the sibling plugins found at setup, comma separated.
 */
public final class FeatsExpansion {
    static final String BASE_CLASS = "at.helpch.placeholderapi.expansion.PlaceholderExpansion";
    /** The registered hook, typed as Object so this class never names a PlaceholderAPI type. */
    private static volatile Object registered;

    private FeatsExpansion() {
    }

    /**
     * Registers the placeholders when PlaceholderAPI is installed and returns true if it worked. Does
     * nothing and returns false otherwise. Never throws. Call it from the plugin's start.
     */
    public static boolean registerIfPresent(FeatsPlugin plugin) {
        try {
            Class.forName(BASE_CLASS, false, FeatsExpansion.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return false; // PlaceholderAPI is not installed
        }
        try {
            boolean ok = Hook.install(plugin) != null;
            if (ok) {
                plugin.getLogger().at(Level.INFO).log("Feats placeholders registered with PlaceholderAPI");
            } else {
                plugin.getLogger().at(Level.WARNING).log("PlaceholderAPI did not accept the Feats placeholders");
            }
            return ok;
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("Feats could not register its placeholders");
            return false;
        }
    }

    /**
     * Takes the placeholders back out of PlaceholderAPI, so a stopped or reloaded Feats never answers
     * with stale data. Does nothing when they were never registered. Never throws. Call it at shutdown.
     */
    public static void unregister(FeatsPlugin plugin) {
        Object hook = registered;
        registered = null;
        if (hook == null) {
            return;
        }
        try {
            Hook.remove(hook);
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().at(Level.WARNING).withCause(e).log("Feats could not unregister its placeholders");
        }
    }

    /** {@code true} when the pack is on, whatever the case of {@code name}, else {@code false}. */
    static String packOn(FeatRegistry registry, String name) {
        if (registry == null || name == null || name.isBlank()) {
            return "false";
        }
        for (Pack p : registry.packs()) {
            if (p.name().equalsIgnoreCase(name.trim())) {
                return "true";
            }
        }
        return "false";
    }

    /** The plugin names of the siblings found at setup, comma separated in enum order, empty for none. */
    static String siblingsOf(FeatRegistry registry) {
        if (registry == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Sibling s : Sibling.values()) {
            if (registry.present().contains(s)) {
                sb.append(sb.isEmpty() ? "" : ", ").append(s.pluginName());
            }
        }
        return sb.toString();
    }

    /** A server total by key as given, else by its lower case form, so keys ignore case. 0 when unknown. */
    static long serverTotal(WorldStore world, String key) {
        if (world == null || key == null || key.isEmpty()) {
            return 0L;
        }
        long value = world.total(key);
        return value != 0 ? value : world.total(key.toLowerCase(Locale.ROOT));
    }

    /** The PlaceholderAPI expansion. Only loaded after the base class was found. */
    static final class Hook extends PlaceholderExpansion {
        private static final List<String> PLACEHOLDERS = List.of(
                "%feats_points%", "%feats_unlocked%", "%feats_total%", "%feats_total_points%", "%feats_rank%",
                "%feats_title%", "%feats_last%", "%feats_rarity_<featId>%", "%feats_stat_<key>%",
                "%feats_has_<featId>%", "%feats_first_<featId>%", "%feats_milestone_<featId>%",
                "%feats_streak%", "%feats_best_streak%", "%feats_server_<key>%", "%feats_pack_<name>%",
                "%feats_siblings%");

        private final FeatsPlugin plugin;

        private Hook(FeatsPlugin plugin) {
            this.plugin = plugin;
        }

        /** Kept apart so the outer class never handles a PlaceholderAPI type. Null when not accepted. */
        static Object install(FeatsPlugin plugin) {
            if (PlaceholderAPIPlugin.instance() == null) {
                return null; // the classes are there but the plugin is not running
            }
            Hook hook = new Hook(plugin);
            if (!hook.register()) {
                return null;
            }
            registered = hook;
            return hook;
        }

        static void remove(Object hook) {
            if (hook instanceof Hook h && h.isRegistered()) {
                h.unregister();
            }
        }

        @Override
        public String getIdentifier() {
            return "feats";
        }

        @Override
        public String getAuthor() {
            return "ryvrook";
        }

        @Override
        public String getVersion() {
            try {
                return plugin.getManifest().getVersion().toString();
            } catch (RuntimeException e) {
                return "unknown";
            }
        }

        @Override
        public String getName() {
            return "Feats";
        }

        @Override
        public List<String> getPlaceholders() {
            return PLACEHOLDERS;
        }

        /** Stay registered across a PlaceholderAPI reload, since Feats registers only once at start. */
        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(PlayerRef player, String params) {
            if (params == null) {
                return null;
            }
            try {
                return resolve(player, params);
            } catch (RuntimeException | LinkageError e) {
                return "";
            }
        }

        private String resolve(PlayerRef player, String params) {
            FeatRegistry registry = plugin.tracker().registry();
            LeaderboardExporter exporter = plugin.exporter();
            String lower = params.toLowerCase(Locale.ROOT);

            switch (lower) {
                case "total":
                    return String.valueOf(registry.count());
                case "total_points":
                    return String.valueOf(registry.totalPoints());
                case "siblings":
                    return siblingsOf(registry);
                default:
                    break;
            }
            if (lower.startsWith("pack_")) {
                return packOn(registry, params.substring("pack_".length()));
            }
            if (lower.startsWith("server_")) {
                return String.valueOf(serverTotal(plugin.tracker().world(), params.substring("server_".length())));
            }
            if (lower.startsWith("rarity_")) {
                double rarity = exporter == null ? -1 : exporter.rarity(featIdOf(registry, params.substring("rarity_".length())));
                return rarity < 0 ? "" : Rarity.percent(rarity);
            }

            if (lower.startsWith("first_")) {
                WorldStore world = plugin.tracker().world();
                WorldStore.Entry entry = world == null ? null : world.first(featIdOf(registry, params.substring("first_".length())));
                return entry == null || entry.byName() == null ? "" : entry.byName();
            }
            if (lower.startsWith("milestone_")) {
                WorldStore world = plugin.tracker().world();
                return String.valueOf(world != null
                        && world.hasMilestone(featIdOf(registry, params.substring("milestone_".length()))));
            }

            boolean known = switch (lower) {
                case "points", "unlocked", "rank", "title", "last", "streak", "best_streak" -> true;
                default -> lower.startsWith("stat_") || lower.startsWith("has_");
            };
            if (!known) {
                return null; // not one of ours
            }
            if (player == null) {
                return "";
            }
            PlayerProgress progress = exporter == null ? null : exporter.progressOf(player.getUuid());
            if (progress == null) {
                progress = new PlayerProgress(player.getUuid(), player.getUsername());
            }

            switch (lower) {
                case "points":
                    return String.valueOf(plugin.tracker().points(progress));
                case "unlocked":
                    return String.valueOf(progress.unlockedCount());
                case "rank": {
                    int rank = exporter == null ? 0 : exporter.rankOf(player.getUuid());
                    return rank <= 0 ? "" : String.valueOf(rank);
                }
                case "title": {
                    String id = progress.title();
                    Feat feat = id == null ? null : registry.get(id);
                    return feat == null || !progress.isUnlocked(feat.id()) ? "" : feat.name();
                }
                case "last":
                    return lastUnlocked(progress, registry);
                case "streak":
                    return String.valueOf(progress.get(StatKeys.JOIN_STREAK));
                case "best_streak":
                    return String.valueOf(progress.get(StatKeys.BEST_JOIN_STREAK));
                default:
                    break;
            }
            if (lower.startsWith("stat_")) {
                return String.valueOf(progress.get(params.substring("stat_".length())));
            }
            String featId = params.substring("has_".length());
            Feat feat = registry.get(featId);
            if (feat == null) {
                feat = registry.get(featId.toLowerCase(Locale.ROOT));
            }
            return String.valueOf(feat != null && progress.isUnlocked(feat.id()));
        }

        /** The id as given when a feat has it, else the lower case form, so ids ignore case. */
        private static String featIdOf(FeatRegistry registry, String id) {
            return registry.get(id) != null ? id : id.toLowerCase(Locale.ROOT);
        }

        private static String lastUnlocked(PlayerProgress progress, FeatRegistry registry) {
            Feat best = null;
            long bestAt = Long.MIN_VALUE;
            for (Map.Entry<String, Long> e : progress.unlockedSnapshot().entrySet()) {
                Feat feat = registry.get(e.getKey());
                long at = e.getValue() == null ? 0 : e.getValue();
                if (feat != null && at >= bestAt) {
                    best = feat;
                    bestAt = at;
                }
            }
            return best == null ? "" : best.name();
        }
    }
}
