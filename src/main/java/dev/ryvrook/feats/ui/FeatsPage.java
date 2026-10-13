package dev.ryvrook.feats.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
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
import dev.ryvrook.feats.model.Category;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Scope;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.stats.WorldStore;
import dev.ryvrook.feats.track.FeatsTracker;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The /feats browser with a category sidebar on the left and feat rows (or stats) on the right. */
public final class FeatsPage extends InteractiveCustomUIPage<FeatsPage.Event> {
    private static final String PAGE = "Pages/Feats_Page.ui";
    private static final String CATEGORY_BUTTON = "Pages/Feats_CategoryButton.ui";
    private static final String ROW = "Pages/Feats_Row.ui";
    private static final String STAT_ROW = "Pages/Feats_StatRow.ui";

    private static final String TAB_ALL = "all";
    private static final String TAB_UNLOCKED = "unlocked";
    private static final String TAB_STATS = "stats";
    private static final String HIDDEN_ICON = "Furniture_Crude_Chest_Small";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

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

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        FeatRegistry registry = tracker.registry();
        commands.append(PAGE);

        int unlocked = target.unlockedCount();
        int total = registry.count();
        int points = tracker.points(target);
        int maxPoints = registry.totalPoints();

        commands.set("#PlayerName.Text", target.username().isEmpty() ? "Feats" : target.username());
        commands.set("#UnlockedText.Text", unlocked + " / " + total + " unlocked");
        commands.set("#TotalBar.Value", total == 0 ? 0f : (float) unlocked / (float) total);
        // Points include world and first feats, the maximum and the percentage cover personal feats only,
        // so past the maximum the points read on their own.
        String pointsText = points <= maxPoints ? String.format("%,d / %,d points", points, maxPoints)
                : String.format("%,d points", points);
        commands.set("#PointsText.Text", String.format("%s  ·  %d%% complete",
                pointsText, total == 0 ? 0 : (100 * unlocked) / total));

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
        index = addTab(commands, events, index, TAB_ALL, "All feats", unlocked + "/" + total);
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
            index = addTab(commands, events, index, category.name(), category.displayName(), done + "/" + feats.size());
        }
        index = addTab(commands, events, index, TAB_UNLOCKED, "Unlocked", String.valueOf(unlocked));
        addTab(commands, events, index, TAB_STATS, "Statistics", "");
    }

    private int addTab(UICommandBuilder commands, UIEventBuilder events, int index, String id, String label, String count) {
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
        String title;
        if (Category.WORLD.name().equals(tab)) {
            buildWorldList(commands, registry);
            return;
        }
        if (TAB_ALL.equals(tab)) {
            feats = registry.personal();
            title = "All feats";
        } else if (TAB_UNLOCKED.equals(tab)) {
            feats = new ArrayList<>();
            for (Feat f : registry.personal()) {
                if (target.isUnlocked(f.id())) {
                    feats.add(f);
                }
            }
            feats.sort(Comparator.comparingLong((Feat f) -> target.unlockedAt(f.id())).reversed());
            title = "Unlocked";
        } else {
            Category category;
            try {
                category = Category.valueOf(tab);
            } catch (IllegalArgumentException e) {
                category = Category.EXPLORATION;
                tab = category.name();
            }
            feats = registry.inCategory(category);
            title = category.displayName();
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
                ? "Nothing here yet"
                : done + " / " + feats.size() + " unlocked  ·  " + points + " pts");

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
        commands.set("#CategoryTitle.Text", Category.WORLD.displayName());
        commands.set("#CategoryCount.Text", feats.isEmpty() ? "Nothing here yet" : done + " / " + feats.size() + " achieved");
        commands.clear("#List");
        int index = 0;
        for (Feat feat : feats) {
            WorldStore.Entry entry = world == null ? null
                    : feat.scope() == Scope.FIRST ? world.first(feat.id()) : world.milestone(feat.id());
            String selector = "#List[" + index++ + "]";
            commands.append("#List", ROW);
            commands.set(selector + " #Icon.ItemId", feat.icon());
            commands.set(selector + " #Name.Text", feat.name());
            commands.set(selector + " #Desc.Text", feat.description());
            commands.set(selector + " #Tier.Text", feat.scope() == Scope.FIRST ? "First" : "Milestone");
            commands.set(selector + " #Check.Visible", entry != null);
            commands.set(selector + " #Bar.Value", entry == null ? 0f : 1f);
            commands.set(selector + " #Progress.Text", worldStatus(feat, entry));
        }
    }

    private String worldStatus(Feat feat, WorldStore.Entry entry) {
        String points = feat.tier().displayName() + "  ·  +" + feat.tier().points() + " pts";
        if (entry == null) {
            return (feat.scope() == Scope.FIRST ? "Unclaimed" : "Not yet achieved") + "  ·  " + points;
        }
        StringBuilder sb = new StringBuilder(feat.scope() == Scope.FIRST ? "Held by " : "Achieved");
        if (feat.scope() == Scope.FIRST) {
            sb.append(entry.byName() == null ? "a player" : entry.byName());
        } else if (entry.byName() != null) {
            sb.append(" by ").append(entry.byName());
        }
        sb.append("  ·  ").append(DATE.format(Instant.ofEpochMilli(entry.at()).atZone(ZoneId.systemDefault())));
        if (target.isUnlocked(feat.id())) {
            sb.append("  ·  +").append(feat.tier().points()).append(" pts");
        }
        return sb.toString();
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

    /** Appends the rarity tag, leaving hidden feats untagged until the viewer has earned them. */
    private static String withRarity(String text, Feat feat, RarityView rarity) {
        if (rarity == null) {
            return text;
        }
        if (feat.hidden() && (rarity.viewer() == null || !rarity.viewer().isUnlocked(feat.id()))) {
            return text;
        }
        String tag = Rarity.tag(rarity.exporter(), rarity.config(), feat.id());
        return tag.isEmpty() ? text : text + ", " + tag;
    }

    private void addRow(UICommandBuilder commands, int index, Feat feat, RarityView rarity) {
        String selector = "#List[" + index + "]";
        commands.append("#List", ROW);
        boolean unlocked = target.isUnlocked(feat.id());
        boolean secret = feat.hidden() && !unlocked;

        commands.set(selector + " #Icon.ItemId", secret ? HIDDEN_ICON : feat.icon());
        commands.set(selector + " #Name.Text", secret ? "???" : feat.name());
        commands.set(selector + " #Desc.Text", secret ? "Hidden feat. Keep playing to discover it." : feat.description());
        commands.set(selector + " #Tier.Text", feat.tier().displayName());
        commands.set(selector + " #Check.Visible", unlocked);

        if (unlocked) {
            commands.set(selector + " #Bar.Value", 1f);
            Long at = target.unlockedAt(feat.id());
            String when = at == null ? "" : "  ·  " + DATE.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()));
            commands.set(selector + " #Progress.Text",
                    withRarity("Unlocked" + when + "  ·  +" + feat.tier().points() + " pts", feat, rarity));
        } else if (secret) {
            commands.set(selector + " #Bar.Value", 0f);
            commands.set(selector + " #Progress.Text", "+" + feat.tier().points() + " pts");
        } else {
            long current = feat.currentProgress(target);
            long total = feat.totalTarget();
            commands.set(selector + " #Bar.Value", (float) feat.fraction(target));
            String label = feat.isSingleCounter()
                    ? String.format("%,d / %,d", current, total)
                    : String.format("%d / %d steps", countMet(feat), feat.requirements().size());
            commands.set(selector + " #Progress.Text",
                    withRarity(label + "  ·  +" + feat.tier().points() + " pts", feat, rarity));
        }
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
        commands.set("#CategoryTitle.Text", "Statistics");
        commands.set("#CategoryCount.Text", "Lifetime totals");
        commands.clear("#List");
        int index = 0;
        for (StatKeys.StatRow row : StatKeys.DISPLAY) {
            String selector = "#List[" + index++ + "]";
            commands.append("#List", STAT_ROW);
            commands.set(selector + " #Name.Text", row.label());
            commands.set(selector + " #Value.Text", StatKeys.format(target.get(row.key()), row.format()));
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
