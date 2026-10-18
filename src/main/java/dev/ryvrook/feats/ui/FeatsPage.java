package dev.ryvrook.feats.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.FeatsConfig;
import dev.ryvrook.feats.FeatsPlugin;
import dev.ryvrook.feats.export.LeaderboardExporter;
import dev.ryvrook.feats.export.Rarity;
import dev.ryvrook.feats.lang.Lang;
import dev.ryvrook.feats.model.Category;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Scope;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.stats.WorldStore;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The /feats browser with a category sidebar on the left and feat rows (or stats) on the right. Every label
 * is a translation the viewer's client renders in its own language, dates and numbers are formatted for the
 * viewer's language on the server.
 */
public final class FeatsPage extends InteractiveCustomUIPage<FeatsPage.Event> {
    private static final String PAGE = "Pages/Feats_Page.ui";
    private static final String CATEGORY_BUTTON = "Pages/Feats_CategoryButton.ui";
    private static final String ROW = "Pages/Feats_Row.ui";
    private static final String STAT_ROW = "Pages/Feats_StatRow.ui";

    private static final String TAB_ALL = "all";
    private static final String TAB_UNLOCKED = "unlocked";
    private static final String TAB_STATS = "stats";
    private static final String HIDDEN_ICON = "Furniture_Crude_Chest_Small";
    private static final String SEPARATOR = "  ·  ";

    /** Tabs that commands can open the page on directly. */
    public enum Tab {
        ALL(TAB_ALL),
        UNLOCKED(TAB_UNLOCKED),
        STATS(TAB_STATS);

        private final String id;

        Tab(String id) {
            this.id = id;
        }
    }

    private final FeatsTracker tracker;
    private final PlayerProgress target;
    /** Gives the config and the rarity numbers. Null only when the plugin could not be found. */
    private final FeatsPlugin plugin;
    private String tab;

    public FeatsPage(PlayerRef viewer, FeatsPlugin plugin, PlayerProgress target, Tab initial) {
        this(viewer, plugin.tracker(), plugin, target, initial);
    }

    public FeatsPage(PlayerRef viewer, FeatsTracker tracker, PlayerProgress target, Tab initial) {
        this(viewer, tracker, findPlugin(), target, initial);
    }

    private FeatsPage(PlayerRef viewer, FeatsTracker tracker, FeatsPlugin plugin, PlayerProgress target, Tab initial) {
        super(viewer, CustomPageLifetime.CanDismiss, Event.CODEC);
        this.tracker = tracker;
        this.plugin = plugin;
        this.target = target;
        this.tab = (initial == null ? Tab.ALL : initial).id;
    }

    private static FeatsPlugin findPlugin() {
        try {
            PluginManager manager = PluginManager.get();
            return manager == null ? null : manager.getPlugin(FeatsPlugin.class);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** The viewer's language, as the client reports it, for dates and numbers. */
    private String language() {
        try {
            return playerRef == null ? null : playerRef.getLanguage();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        FeatRegistry registry = tracker.registry();
        commands.append(PAGE);

        int unlocked = target.unlockedCount();
        int total = registry.count();
        int points = tracker.points(target);
        int maxPoints = registry.totalPoints();

        commands.set("#PlayerName.Text", target.username().isEmpty() ? "Feats" : target.username());
        commands.set("#UnlockedText.Text", Message.translation(Lang.PAGE_UNLOCKED)
                .param("unlocked", unlocked).param("total", total));
        commands.set("#TotalBar.Value", total == 0 ? 0f : (float) unlocked / (float) total);
        // Points include world and first feats, the maximum and the percentage cover personal feats only,
        // so past the maximum the points read on their own.
        int percent = total == 0 ? 0 : (100 * unlocked) / total;
        Message pointsText = points <= maxPoints
                ? Message.translation(Lang.PAGE_POINTS).param("points", points).param("max", maxPoints).param("percent", percent)
                : Message.translation(Lang.PAGE_POINTS_OVER).param("points", points).param("percent", percent);
        commands.set("#PointsText.Text", pointsText);

        buildSidebar(commands, events, registry, unlocked, total);

        if (TAB_STATS.equals(tab)) {
            buildStats(commands);
        } else {
            buildFeatList(commands, registry);
        }
    }

    private void buildSidebar(UICommandBuilder commands, UIEventBuilder events, FeatRegistry registry, int unlocked, int total) {
        commands.clear("#Categories");
        int index = 0;
        index = addTab(commands, events, index, TAB_ALL, Message.translation(Lang.PAGE_TAB_ALL), unlocked + "/" + total);
        for (Category category : Category.values()) {
            List<Feat> feats = registry.inCategory(category);
            if (feats.isEmpty()) {
                continue;
            }
            int done = 0;
            for (Feat f : feats) {
                if (category == Category.WORLD ? isAchieved(f) : target.isUnlocked(f.id())) {
                    done++;
                }
            }
            index = addTab(commands, events, index, category.name(), Lang.category(category), done + "/" + feats.size());
        }
        index = addTab(commands, events, index, TAB_UNLOCKED, Message.translation(Lang.PAGE_TAB_UNLOCKED), String.valueOf(unlocked));
        addTab(commands, events, index, TAB_STATS, Message.translation(Lang.PAGE_TAB_STATS), "");
    }

    private int addTab(UICommandBuilder commands, UIEventBuilder events, int index, String id, Message label, String count) {
        String selector = "#Categories[" + index + "]";
        commands.append("#Categories", CATEGORY_BUTTON);
        boolean selected = id.equals(tab);
        commands.set(selector + " #Button.Text", label);
        commands.set(selector + " #Selected.Text", label);
        commands.set(selector + " #Button.Visible", !selected);
        commands.set(selector + " #Selected.Visible", selected);
        commands.set(selector + " #Count.Text", count);
        events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #Button",
                EventData.of("Action", "tab").append("Value", id), false);
        return index + 1;
    }

    private void buildFeatList(UICommandBuilder commands, FeatRegistry registry) {
        List<Feat> feats;
        Message title;
        if (Category.WORLD.name().equals(tab)) {
            buildWorldList(commands, registry);
            return;
        }
        if (TAB_ALL.equals(tab)) {
            feats = registry.personal();
            title = Message.translation(Lang.PAGE_TAB_ALL);
        } else if (TAB_UNLOCKED.equals(tab)) {
            feats = new ArrayList<>();
            for (Feat f : registry.personal()) {
                if (target.isUnlocked(f.id())) {
                    feats.add(f);
                }
            }
            feats.sort(Comparator.comparingLong((Feat f) -> target.unlockedAt(f.id())).reversed());
            title = Message.translation(Lang.PAGE_TAB_UNLOCKED);
        } else {
            Category category;
            try {
                category = Category.valueOf(tab);
            } catch (IllegalArgumentException e) {
                category = Category.EXPLORATION;
                tab = category.name();
            }
            feats = registry.inCategory(category);
            title = Lang.category(category);
        }

        int done = 0;
        int points = 0;
        for (Feat f : feats) {
            if (target.isUnlocked(f.id())) {
                done++;
                points += f.tier().points();
            }
        }
        commands.set("#CategoryTitle.Text", title);
        commands.set("#CategoryCount.Text", feats.isEmpty()
                ? Message.translation(Lang.PAGE_EMPTY)
                : Message.translation(Lang.PAGE_COUNT).param("done", done).param("total", feats.size()).param("points", points));

        commands.clear("#List");
        RarityView rarity = rarityView();
        int index = 0;
        for (Feat feat : feats) {
            addRow(commands, index++, feat, rarity);
        }
    }

    /** True when a world milestone was reached or a first has a holder. */
    private boolean isAchieved(Feat feat) {
        WorldStore world = tracker.world();
        if (world == null) {
            return false;
        }
        return feat.scope() == Scope.FIRST ? world.hasFirst(feat.id()) : world.hasMilestone(feat.id());
    }

    /**
     * The World tab. Every row says whether the server reached the milestone or someone holds the first,
     * who did it and when. Built from the same row as the other tabs, text only.
     */
    private void buildWorldList(UICommandBuilder commands, FeatRegistry registry) {
        List<Feat> feats = registry.inCategory(Category.WORLD);
        WorldStore world = tracker.world();
        int done = 0;
        for (Feat f : feats) {
            if (isAchieved(f)) {
                done++;
            }
        }
        commands.set("#CategoryTitle.Text", Lang.category(Category.WORLD));
        commands.set("#CategoryCount.Text", feats.isEmpty()
                ? Message.translation(Lang.PAGE_EMPTY)
                : Message.translation(Lang.PAGE_COUNT_WORLD).param("done", done).param("total", feats.size()));
        commands.clear("#List");
        int index = 0;
        for (Feat feat : feats) {
            WorldStore.Entry entry = world == null ? null
                    : feat.scope() == Scope.FIRST ? world.first(feat.id()) : world.milestone(feat.id());
            String selector = "#List[" + index++ + "]";
            commands.append("#List", ROW);
            commands.set(selector + " #Icon.ItemId", feat.icon());
            commands.set(selector + " #Name.Text", Lang.name(feat));
            commands.set(selector + " #Desc.Text", Lang.description(feat));
            commands.set(selector + " #Tier.Text", tierLabel(feat,
                    feat.scope() == Scope.FIRST ? Lang.PAGE_WORLD_FIRST : Lang.PAGE_WORLD_MILESTONE, language()));
            commands.set(selector + " #Check.Visible", entry != null);
            commands.set(selector + " #Bar.Value", entry == null ? 0f : 1f);
            commands.set(selector + " #Progress.Text", worldStatus(feat, entry));
        }
    }

    /**
     * The status line of a World row. Only a translation with string and number params goes into a Text slot,
     * the client disconnects on a joined message or a message param, so the tier name, the holder's stand in
     * and the points suffix are resolved to strings first.
     */
    private Message worldStatus(Feat feat, WorldStore.Entry entry) {
        int points = feat.tier().points();
        String language = language();
        if (entry == null) {
            return Message.translation(feat.scope() == Scope.FIRST ? Lang.PAGE_WORLD_UNCLAIMED : Lang.PAGE_WORLD_NOT_YET)
                    .param("tier", Lang.text(language, Lang.tierKey(feat.tier()))).param("points", points);
        }
        String date = Lang.date(entry.at(), language);
        String suffix = target.isUnlocked(feat.id())
                ? SEPARATOR + Lang.text(language, Lang.PAGE_ROW_POINTS, "points", points) : "";
        if (feat.scope() == Scope.FIRST) {
            String holder = entry.byName() == null ? Lang.text(language, Lang.PAGE_WORLD_SOMEONE) : entry.byName();
            return Message.translation(Lang.PAGE_WORLD_HELD_BY).param("player", holder).param("date", date).param("points", suffix);
        }
        if (entry.byName() != null) {
            return Message.translation(Lang.PAGE_WORLD_ACHIEVED_BY).param("player", entry.byName()).param("date", date)
                    .param("points", suffix);
        }
        return Message.translation(Lang.PAGE_WORLD_ACHIEVED).param("date", date).param("points", suffix);
    }

    /** What the rows need to show a rarity tag, or null when tags are off or not known yet. */
    private record RarityView(LeaderboardExporter exporter, FeatsConfig config, PlayerProgress viewer) {
    }

    private RarityView rarityView() {
        if (plugin == null) {
            return null;
        }
        try {
            FeatsConfig config = plugin.config();
            LeaderboardExporter exporter = plugin.exporter();
            if (config == null || exporter == null || !config.showRarity() || exporter.rarityPlayers() < 0) {
                return null;
            }
            return new RarityView(exporter, config, viewerProgress());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The viewer's own progress, which is the target unless someone else's page is open. */
    private PlayerProgress viewerProgress() {
        if (playerRef == null || target.uuid().equals(playerRef.getUuid())) {
            return target;
        }
        for (PlayerProgress p : tracker.loadedPlayers()) {
            if (p.uuid().equals(playerRef.getUuid())) {
                return p;
            }
        }
        return null;
    }

    /**
     * The {@code {rarity}} param of a row line, ", rare, 3% of players" as a plain string, or empty for hidden
     * feats until the viewer has earned them, when tags are off or when the share is not known yet.
     */
    private String rarityParam(Feat feat, RarityView rarity) {
        if (rarity == null) {
            return "";
        }
        if (feat.hidden() && (rarity.viewer() == null || !rarity.viewer().isUnlocked(feat.id()))) {
            return "";
        }
        double fraction = rarity.exporter().rarity(feat.id());
        if (fraction < 0) {
            return "";
        }
        boolean rare = Rarity.isRare(fraction, rarity.config().rareBelowPercent());
        return ", " + Lang.text(language(), rare ? Lang.RARITY_RARE : Lang.RARITY_SHARE, "share", Rarity.percent(fraction));
    }

    private void addRow(UICommandBuilder commands, int index, Feat feat, RarityView rarity) {
        String selector = "#List[" + index + "]";
        commands.append("#List", ROW);
        boolean unlocked = target.isUnlocked(feat.id());
        boolean secret = feat.hidden() && !unlocked;
        int points = feat.tier().points();

        commands.set(selector + " #Icon.ItemId", secret ? HIDDEN_ICON : feat.icon());
        commands.set(selector + " #Name.Text", secret ? Message.translation(Lang.PAGE_HIDDEN_NAME) : Lang.name(feat));
        commands.set(selector + " #Desc.Text", secret ? Message.translation(Lang.PAGE_HIDDEN_DESC) : Lang.description(feat));
        commands.set(selector + " #Tier.Text", tierLabel(feat, Lang.tierKey(feat.tier()), language()));
        commands.set(selector + " #Check.Visible", unlocked);

        if (unlocked) {
            commands.set(selector + " #Bar.Value", 1f);
            Long at = target.unlockedAt(feat.id());
            Message line = at == null
                    ? Message.translation(Lang.PAGE_ROW_UNLOCKED_NO_DATE).param("points", points)
                    : Message.translation(Lang.PAGE_ROW_UNLOCKED).param("date", Lang.date(at, language())).param("points", points);
            commands.set(selector + " #Progress.Text", line.param("rarity", rarityParam(feat, rarity)));
        } else if (secret) {
            commands.set(selector + " #Bar.Value", 0f);
            commands.set(selector + " #Progress.Text", Message.translation(Lang.PAGE_ROW_POINTS).param("points", points));
        } else {
            long current = feat.currentProgress(target);
            long total = feat.totalTarget();
            commands.set(selector + " #Bar.Value", (float) feat.fraction(target));
            Message label = feat.isSingleCounter()
                    ? Message.translation(Lang.PAGE_ROW_PROGRESS).param("current", current).param("total", total).param("points", points)
                    : Message.translation(Lang.PAGE_ROW_STEPS).param("met", countMet(feat))
                            .param("total", feat.requirements().size()).param("points", points);
            commands.set(selector + " #Progress.Text", label.param("rarity", rarityParam(feat, rarity)));
        }
    }

    /**
     * The tier label of a row. A feat whose unlock shows the banner carries a trailing dot as its marker. The
     * dotted form takes the tier as a string, since a joined message in a Text slot disconnects the client.
     */
    static Message tierLabel(Feat feat, String tierKey, String language) {
        return feat.banner()
                ? Message.translation(Lang.PAGE_ROW_TIER_BANNER).param("tier", Lang.text(language, tierKey))
                : Message.translation(tierKey);
    }

    private int countMet(Feat feat) {
        int met = 0;
        for (var r : feat.requirements()) {
            if (target.get(r.key()) >= r.target()) {
                met++;
            }
        }
        return met;
    }

    private void buildStats(UICommandBuilder commands) {
        commands.set("#CategoryTitle.Text", Message.translation(Lang.PAGE_STATS_TITLE));
        commands.set("#CategoryCount.Text", Message.translation(Lang.PAGE_STATS_SUBTITLE));
        commands.clear("#List");
        int index = 0;
        var locale = Lang.locale(language());
        for (StatKeys.StatRow row : StatKeys.DISPLAY) {
            String selector = "#List[" + index++ + "]";
            commands.append("#List", STAT_ROW);
            commands.set(selector + " #Name.Text", Message.translation(Lang.statKey(row.key())));
            commands.set(selector + " #Value.Text", Lang.statValue(target.get(row.key()), row.format(), locale));
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Event data) {
        if (data == null || data.action == null) {
            return;
        }
        if ("tab".equals(data.action) && data.value != null) {
            tab = data.value;
            rebuild();
        }
    }

    /** Payload sent back by UI buttons. */
    public static final class Event {
        public static final BuilderCodec<Event> CODEC = BuilderCodec.builder(Event.class, Event::new)
                .append(new KeyedCodec<>("Action", Codec.STRING),
                        (e, v, extra) -> e.action = v,
                        (e, extra) -> e.action)
                .add()
                .append(new KeyedCodec<>("Value", Codec.STRING),
                        (e, v, extra) -> e.value = v,
                        (e, extra) -> e.value)
                .add()
                .build();

        public String action;
        public String value;
    }
}
