package dev.ryvrook.feats.lang;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import dev.ryvrook.feats.model.Category;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.Tier;
import dev.ryvrook.feats.stats.StatKeys;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Every string a player sees, as a translation key with its English text. The engine's i18n module ships the
 * keys to each client in the client's own language, taken from {@code lang/Server/Languages/<language>/feats.lang}
 * in the data directory (see {@link LangPack}), and falls back to English key by key, so a language file that
 * lags a release shows the new feats in English. The engine puts the file name in front of every key of a
 * file, so the file holds {@code chat.label} and the code asks for {@code feats.chat.label}. Feat names and
 * descriptions stay in the registry as the English text and are keyed by id, {@code feats.feat.<id>.name} and
 * {@code .desc}.
 *
 * <p>The English table here is the source of the bundled {@code lang/en-US/feats.lang}, which {@code LangFileTest}
 * keeps in step. Console and log lines, the API, the export and the placeholders stay English and never come
 * through here.
 */
public final class Lang {
    private Lang() {
    }

    /** The prefix of every Feats key, so another mod's keys never collide with ours. */
    public static final String PREFIX = "feats.";

    private static final Map<String, String> ENGLISH = new LinkedHashMap<>();

    private static String def(String key, String english) {
        if (ENGLISH.put(key, english) != null) {
            throw new IllegalStateException("duplicate lang key " + key);
        }
        return key;
    }

    // ---- chat --------------------------------------------------------------

    public static final String CHAT_LABEL = def("feats.chat.label", "Feats");
    public static final String CHAT_UNLOCKED_LABEL = def("feats.chat.unlocked.label", "Feat unlocked");
    public static final String CHAT_UNLOCKED = def("feats.chat.unlocked",
            "{label} {feat}  ({tier}, +{points} pts, {unlocked}/{total})");
    public static final String CHAT_UNLOCKED_RARE = def("feats.chat.unlocked.rare",
            "{label} {feat}  ({tier}, +{points} pts, {unlocked}/{total})  (rare, {share} of players)");
    public static final String CHAT_MORE = def("feats.chat.more",
            "{label} {n, plural, one {1 more feat} other {{n} more feats}} unlocked  ({unlocked}/{total}), see /feats");
    public static final String CHAT_MILESTONE = def("feats.chat.milestone", "Server milestone reached, {feat}");
    public static final String CHAT_MILESTONE_BY = def("feats.chat.milestone.by",
            "Server milestone reached, {feat}, by {player}");
    public static final String CHAT_MORE_MILESTONES = def("feats.chat.milestone.more",
            "{n, plural, one {1 more server milestone} other {{n} more server milestones}} reached, see the World tab in /feats");
    public static final String CHAT_BROADCAST = def("feats.chat.broadcast", "{player} unlocked the feat {feat} [{tier}]");
    public static final String CHAT_WELCOME = def("feats.chat.welcome",
            "{label} {unlocked}/{total} unlocked · {command} to browse");
    public static final String CHAT_TITLE = def("feats.chat.title", "[{feat}]");

    // ---- rarity ------------------------------------------------------------

    public static final String RARITY_RARE = def("feats.rarity.rare", "rare, {share} of players");
    public static final String RARITY_SHARE = def("feats.rarity.share", "{share} of players");
    public static final String RARITY_UNDER_ONE = def("feats.rarity.under1", "under 1%");
    public static final String RARITY_PERCENT = def("feats.rarity.percent", "{percent}%");

    // ---- banner ------------------------------------------------------------

    public static final String BANNER_FIRST = def("feats.banner.first", "Server first");
    public static final String BANNER_FIRST_BY = def("feats.banner.first.by", "Server first, by {player}");
    public static final String BANNER_MILESTONE = def("feats.banner.milestone", "Server milestone");
    public static final String BANNER_MILESTONE_BY = def("feats.banner.milestone.by", "Server milestone, by {player}");
    public static final String BANNER_PLATINUM = def("feats.banner.platinum", "Platinum");
    public static final String BANNER_RARE = def("feats.banner.rare", "Rare, {percent}% of players");
    public static final String BANNER_RARE_UNDER_ONE = def("feats.banner.rare.under1", "Rare, under 1% of players");

    // ---- the /feats page ---------------------------------------------------

    public static final String PAGE_UNLOCKED = def("feats.page.unlocked", "{unlocked} / {total} unlocked");
    public static final String PAGE_POINTS = def("feats.page.points",
            "{points, number} / {max, number} points  ·  {percent}% complete");
    public static final String PAGE_POINTS_OVER = def("feats.page.points.over",
            "{points, number} points  ·  {percent}% complete");
    public static final String PAGE_TAB_ALL = def("feats.page.tab.all", "All feats");
    public static final String PAGE_TAB_UNLOCKED = def("feats.page.tab.unlocked", "Unlocked");
    public static final String PAGE_TAB_STATS = def("feats.page.tab.stats", "Statistics");
    public static final String PAGE_EMPTY = def("feats.page.empty", "Nothing here yet");
    public static final String PAGE_COUNT = def("feats.page.count", "{done} / {total} unlocked  ·  {points} pts");
    public static final String PAGE_COUNT_WORLD = def("feats.page.count.world", "{done} / {total} achieved");
    public static final String PAGE_HIDDEN_NAME = def("feats.page.row.hidden.name", "???");
    public static final String PAGE_HIDDEN_DESC = def("feats.page.row.hidden",
            "Hidden feat. Its name shows once you unlock it.");
    public static final String PAGE_ROW_UNLOCKED = def("feats.page.row.unlocked", "Unlocked  ·  {date}  ·  +{points} pts{rarity}");
    public static final String PAGE_ROW_UNLOCKED_NO_DATE = def("feats.page.row.unlocked.nodate", "Unlocked  ·  +{points} pts{rarity}");
    public static final String PAGE_ROW_POINTS = def("feats.page.row.points", "+{points} pts");
    public static final String PAGE_ROW_PROGRESS = def("feats.page.row.progress",
            "{current, number} / {total, number}  ·  +{points} pts{rarity}");
    public static final String PAGE_ROW_STEPS = def("feats.page.row.steps", "{met} / {total} steps  ·  +{points} pts{rarity}");
    public static final String PAGE_ROW_TIER_BANNER = def("feats.page.row.tier.banner", "{tier}.");
    public static final String PAGE_WORLD_FIRST = def("feats.page.world.first", "First");
    public static final String PAGE_WORLD_MILESTONE = def("feats.page.world.milestone", "Milestone");
    public static final String PAGE_WORLD_UNCLAIMED = def("feats.page.world.unclaimed",
            "Unclaimed  ·  {tier}  ·  +{points} pts");
    public static final String PAGE_WORLD_NOT_YET = def("feats.page.world.notyet",
            "Not yet achieved  ·  {tier}  ·  +{points} pts");
    public static final String PAGE_WORLD_HELD_BY = def("feats.page.world.heldby", "Held by {player}  ·  {date}{points}");
    public static final String PAGE_WORLD_ACHIEVED_BY = def("feats.page.world.achievedby", "Achieved by {player}  ·  {date}{points}");
    public static final String PAGE_WORLD_ACHIEVED = def("feats.page.world.achieved", "Achieved  ·  {date}{points}");
    public static final String PAGE_WORLD_SOMEONE = def("feats.page.world.someone", "a player");
    public static final String PAGE_STATS_TITLE = def("feats.page.stats.title", "Statistics");
    public static final String PAGE_STATS_SUBTITLE = def("feats.page.stats.subtitle", "Lifetime totals");
    public static final String STAT_VALUE_COUNT = def("feats.stat.value.count", "{value, number}");
    public static final String STAT_VALUE_KM = def("feats.stat.value.km", "{km} km");
    public static final String STAT_VALUE_M = def("feats.stat.value.m", "{m, number} m");
    public static final String STAT_VALUE_HOURS = def("feats.stat.value.hours", "{h}h {m}m");
    public static final String STAT_VALUE_MINUTES = def("feats.stat.value.minutes", "{m}m {s}s");

    // ---- commands ----------------------------------------------------------

    public static final String CMD_PLAYER_ONLY = def("feats.cmd.playerOnly", "Feats can only be opened by a player.");
    public static final String CMD_VIEW_PLAYER_ONLY = def("feats.cmd.view.playerOnly", "Only a player can open the feats browser.");
    public static final String CMD_VIEW_OFF = def("feats.cmd.view.off", "Viewing other players is turned off on this server.");
    public static final String CMD_NO_FEATS = def("feats.cmd.noFeats", "{player} has no feats yet.");
    public static final String CMD_NO_PLAYER = def("feats.cmd.noPlayer", "No player named {name} has played on this server.");
    public static final String CMD_FAILED = def("feats.cmd.failed", "That did not work, see the server log.");
    public static final String CMD_STOPPING = def("feats.cmd.stopping", "Feats is shutting down.");
    public static final String CMD_TOP_TITLE = def("feats.cmd.top.title", "Feats leaderboard");
    public static final String CMD_TOP_ROW = def("feats.cmd.top.row", "{rank}. {player}  {points} pts  ·  {unlocked}/{total}");
    public static final String CMD_TOP_EMPTY = def("feats.cmd.top.empty", "Nobody has unlocked anything yet.");
    public static final String CMD_TOP_WAIT = def("feats.cmd.top.wait",
            "The leaderboard was just checked. Try again in a few seconds.");
    public static final String CMD_TOP_FAILED = def("feats.cmd.top.failed", "The leaderboard could not be read right now.");
    public static final String CMD_RECENT_TITLE = def("feats.cmd.recent.title", "Recent feats");
    public static final String CMD_RECENT_ROW = def("feats.cmd.recent.row", "{player} unlocked {feat} [{tier}]  {ago}");
    public static final String CMD_RECENT_HIDDEN = def("feats.cmd.recent.hidden", "a hidden feat");
    public static final String CMD_RECENT_JUST_NOW = def("feats.cmd.recent.ago.now", "just now");
    public static final String CMD_RECENT_MINUTES = def("feats.cmd.recent.ago.minutes",
            "{n, plural, one {1 minute ago} other {{n} minutes ago}}");
    public static final String CMD_RECENT_HOURS = def("feats.cmd.recent.ago.hours",
            "{n, plural, one {1 hour ago} other {{n} hours ago}}");
    public static final String CMD_RECENT_DAYS = def("feats.cmd.recent.ago.days",
            "{n, plural, one {1 day ago} other {{n} days ago}}");
    public static final String CMD_SHARE_LOCKED = def("feats.cmd.share.locked",
            "You can only share a feat you have unlocked. Use its id, as shown by /feats.");
    public static final String CMD_SHARE_WAIT = def("feats.cmd.share.wait",
            "You can share again in {n, plural, one {1 second} other {{n} seconds}}.");
    public static final String CMD_SHARE_LINE = def("feats.cmd.share.line",
            "{player} shares the feat {feat} [{tier}]  unlocked {date}");
    public static final String CMD_TITLE_CURRENT = def("feats.cmd.title.current", "Your title {title}");
    public static final String CMD_TITLE_NONE = def("feats.cmd.title.none", "none");
    public static final String CMD_TITLE_HINT = def("feats.cmd.title.hint",
            "Unlock a Gold or Platinum feat to wear it as a title.");
    public static final String CMD_TITLE_ROW = def("feats.cmd.title.row", "{feat}  /feats title {id}");
    public static final String CMD_TITLE_CLEAR_HINT = def("feats.cmd.title.clearHint", "Use /feats title off to clear it.");
    public static final String CMD_TITLE_CLEARED = def("feats.cmd.title.cleared", "Your title is cleared.");
    public static final String CMD_TITLE_UNKNOWN = def("feats.cmd.title.unknown", "There is no feat called {name}.");
    public static final String CMD_TITLE_LOCKED = def("feats.cmd.title.locked", "You have not unlocked that feat yet.");
    public static final String CMD_TITLE_TIER = def("feats.cmd.title.tier", "Only Gold and Platinum feats can be titles.");
    public static final String CMD_TITLE_SET = def("feats.cmd.title.set", "Your title is now {feat}");
    public static final String CMD_COMPARE_HEAD = def("feats.cmd.compare.head", "{you}  vs  {other}");
    public static final String CMD_COMPARE_YOU = def("feats.cmd.compare.you", "You");
    public static final String CMD_COMPARE_POINTS = def("feats.cmd.compare.points", "Points");
    public static final String CMD_COMPARE_UNLOCKED = def("feats.cmd.compare.unlocked", "Unlocked");
    public static final String CMD_COMPARE_ROW = def("feats.cmd.compare.row", "{label}  {mine}  vs  {theirs}");
    public static final String CMD_NOTIFY_STATE = def("feats.cmd.notify.state",
            "Unlock toasts, sounds and titles are {state}. Use /feats notify on or /feats notify off.");
    public static final String CMD_NOTIFY_ON = def("feats.cmd.notify.on", "on");
    public static final String CMD_NOTIFY_OFF = def("feats.cmd.notify.off", "off");
    public static final String CMD_NOTIFY_USAGE = def("feats.cmd.notify.usage", "Use /feats notify on or /feats notify off.");
    public static final String CMD_NOTIFY_NOW_ON = def("feats.cmd.notify.nowOn", "Unlock toasts, sounds and titles are on.");
    public static final String CMD_NOTIFY_NOW_OFF = def("feats.cmd.notify.nowOff",
            "Unlock toasts, sounds and titles are off. Unlocks still show in chat.");
    public static final String CMD_RESET_WARN = def("feats.cmd.reset.warn",
            "This erases ALL of your feats and statistics. Type {command} to continue.");
    public static final String CMD_RESET_DONE = def("feats.cmd.reset.done", "Your feats and statistics have been reset.");

    // ---- categories, tiers and statistics -----------------------------------

    static {
        for (Category c : Category.values()) {
            def(categoryKey(c), c.displayName());
        }
        for (Tier t : Tier.values()) {
            def(tierKey(t), t.displayName());
        }
        for (StatKeys.StatRow row : StatKeys.DISPLAY) {
            def(statKey(row.key()), row.label());
        }
    }

    /**
     * The keys the {@code /feats} page resolves on the server through {@link #text} and passes into a UI slot as a
     * string, an entry ending in a dot meaning every key under it. {@link #text} fills only plain {@code {name}}
     * and {@code {name, number}} placeholders, so a plural or select in one of these would show raw, and
     * {@code LangFiles.check} reports it.
     */
    public static final java.util.List<String> SERVER_FILLED = java.util.List.of(
            PREFIX + "tier.", PAGE_WORLD_SOMEONE, PAGE_ROW_POINTS, PREFIX + "rarity.");

    /** True for a key in {@link #SERVER_FILLED}. */
    public static boolean serverFilled(String key) {
        for (String k : SERVER_FILLED) {
            if (k.endsWith(".") ? key.startsWith(k) : key.equals(k)) {
                return true;
            }
        }
        return false;
    }

    /** The English text of every key except the feats, in definition order. */
    public static Map<String, String> english() {
        return Collections.unmodifiableMap(ENGLISH);
    }

    public static String nameKey(String featId) {
        return PREFIX + "feat." + featId + ".name";
    }

    public static String descKey(String featId) {
        return PREFIX + "feat." + featId + ".desc";
    }

    public static String categoryKey(Category category) {
        return PREFIX + "category." + category.name().toLowerCase(Locale.ROOT);
    }

    public static String tierKey(Tier tier) {
        return PREFIX + "tier." + tier.name().toLowerCase(Locale.ROOT);
    }

    public static String statKey(String statKey) {
        return PREFIX + "stat." + statKey;
    }

    // ---- messages ----------------------------------------------------------

    /** The message for a key, rendered by each client in its own language. */
    public static Message t(String key) {
        return Message.translation(key);
    }

    /** The feat's name in the viewer's language. */
    public static Message name(Feat feat) {
        return Message.translation(nameKey(feat.id()));
    }

    /** The feat's name in its tier colour, as every chat line shows it. */
    public static Message colouredName(Feat feat) {
        return name(feat).color(feat.tier().color());
    }

    public static Message description(Feat feat) {
        return Message.translation(descKey(feat.id()));
    }

    public static Message category(Category category) {
        return Message.translation(categoryKey(category));
    }

    public static Message tier(Tier tier) {
        return Message.translation(tierKey(tier));
    }

    /** "under 1%" or "3%", the share of players a rare unlock line and the banner show. */
    public static Message share(double fraction) {
        double percent = fraction * 100.0;
        return percent < 1.0 ? Message.translation(RARITY_UNDER_ONE)
                : Message.translation(RARITY_PERCENT).param("percent", (int) Math.round(percent));
    }

    /**
     * A statistics value, "1,234", "1.2 km", "3h 05m", in the viewer's language. Whole numbers go through the
     * client's own number format, the one decimal of a distance is formatted here with the viewer's locale.
     */
    public static Message statValue(long value, StatKeys.Format format, Locale locale) {
        return switch (format) {
            case COUNT -> Message.translation(STAT_VALUE_COUNT).param("value", value);
            case DISTANCE -> value >= 1000
                    ? Message.translation(STAT_VALUE_KM).param("km", String.format(locale, "%.1f", value / 1000.0))
                    : Message.translation(STAT_VALUE_M).param("m", value);
            case DURATION -> {
                long h = value / 3600;
                long m = (value % 3600) / 60;
                if (h > 0) {
                    yield Message.translation(STAT_VALUE_HOURS).param("h", h).param("m", String.format("%02d", m));
                }
                yield Message.translation(STAT_VALUE_MINUTES).param("m", m).param("s", String.format("%02d", value % 60));
            }
        };
    }

    // ---- the viewer's language ----------------------------------------------

    /** The Java locale for an engine language tag such as {@code fr-FR}. English when the tag is unknown. */
    public static Locale locale(String language) {
        if (language == null || language.isBlank()) {
            return Locale.ENGLISH;
        }
        Locale locale = Locale.forLanguageTag(language.trim());
        return locale.getLanguage().isEmpty() ? Locale.ENGLISH : locale;
    }

    /** "18 Oct 2026" in the viewer's language, with the server's zone. */
    public static String date(long epochMillis, String language) {
        return DateTimeFormatter.ofPattern("d MMM yyyy", locale(language))
                .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    /**
     * The text of a key in a language, as the engine has it right now, or null when the engine is not running
     * or the key is unknown. Used where a player types a feat's name, so the name they see on screen matches.
     */
    public static String resolve(String language, String key) {
        try {
            I18nModule i18n = I18nModule.get();
            if (i18n == null) {
                return null;
            }
            String text = i18n.getMessage(language == null ? I18nModule.DEFAULT_LANGUAGE : language, key);
            return text == null || text.isEmpty() || text.equals(key) ? null : text;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /**
     * A key's text in a language with its placeholders filled in, as a plain string, for the slots of a UI page.
     * The client takes a translation with number and string params in a Text slot, but not a joined message
     * or a message param, it disconnects on either, so the small pieces a page line is built from, a tier
     * name or the rarity tag, are resolved here and passed as strings. {@code kv} is name, value pairs.
     * {@code {name}} and {@code {name, number}} are filled, a number with the locale's grouping. The engine's
     * map for the language, English under it, is used when the engine runs, the English table otherwise.
     */
    public static String text(String language, String key, Object... kv) {
        String template = resolve(language, key);
        if (template == null) {
            template = ENGLISH.getOrDefault(key, key);
        }
        if (kv.length == 0 || template.indexOf('{') < 0) {
            return template;
        }
        Locale locale = locale(language);
        String out = template;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String name = String.valueOf(kv[i]);
            Object value = kv[i + 1];
            String plain = String.valueOf(value);
            String grouped = value instanceof Number n && !(value instanceof Double) && !(value instanceof Float)
                    ? String.format(locale, "%,d", n.longValue()) : plain;
            out = out.replace("{" + name + ", number}", grouped).replace("{" + name + ",number}", grouped)
                    .replace("{" + name + "}", plain);
        }
        return out;
    }

    /** The feat's name as a player with this language sees it, or the English name when the engine cannot say. */
    public static String nameFor(Feat feat, String language) {
        String text = resolve(language, nameKey(feat.id()));
        return text == null ? feat.name() : text;
    }
}
