package dev.ryvrook.feats.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import dev.ryvrook.feats.FeatsPlugin;
import dev.ryvrook.feats.export.LeaderboardExporter;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Pack;
import dev.ryvrook.feats.model.Scope;
import dev.ryvrook.feats.model.Trigger;
import dev.ryvrook.feats.stats.WorldStore;
import dev.ryvrook.feats.scan.ScanJob;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.FeatsTracker.Lease;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.function.Consumer;

/**
 * {@code /feats admin ...} for server staff. None of these call requireNoPermission, so the engine
 * guards each one with its own generated node under {@code ryvrook.feats.command.feats.admin}, and
 * a sender needs both that node and the one of the sub command. Every sub command also works from the
 * console, except view, which opens a page.
 *
 * <p>Offline players are read and written on the Feats IO thread through the tracker's lease, so a
 * player who joins during an edit shares the same copy. Replies go back on the sender's world thread.
 */
final class AdminCommand extends AbstractCommandCollection {

    AdminCommand(FeatsPlugin plugin) {
        super("admin", "Feats tools for server staff.");
        addSubCommand(new View(plugin));
        addSubCommand(new Grant(plugin));
        addSubCommand(new Revoke(plugin));
        addSubCommand(new Reset(plugin));
        addSubCommand(new SetStat(plugin));
        addSubCommand(new Reload(plugin));
        addSubCommand(new Rates(plugin));
        addSubCommand(new Scan(plugin));
        addSubCommand(new Totals(plugin));
        addSubCommand(new Status(plugin));
    }

    private static Message ok(String text) {
        return Message.raw(text).color(Targets.MUTED);
    }

    private static Message error(String text) {
        return Message.raw(text).color(Targets.ERROR);
    }

    /**
     * Holds the target's progress on the IO thread while {@code edit} runs, then saves it when offline.
     * Refuses with a message when another edit of the same player is still running.
     */
    private static void edit(FeatsPlugin plugin, CommandContext context, Targets.Target target, Consumer<Lease> edit) {
        FeatsTracker tracker = plugin.tracker();
        Lease lease;
        try {
            lease = tracker.acquire(target.uuid());
        } catch (FeatsTracker.BusyException e) {
            Targets.reply(context, error(target.name() + " is being changed by another command. Try again in a moment."));
            return;
        }
        if (lease == null) {
            Targets.reply(context, error(target.name() + " has no feats file yet."));
            return;
        }
        try {
            edit.accept(lease);
        } finally {
            tracker.release(target.uuid(), lease);
        }
    }

    private static Feat feat(FeatsPlugin plugin, CommandContext context, String id) {
        FeatRegistry registry = plugin.tracker().registry();
        Feat feat = registry.get(id);
        if (feat == null) {
            context.sendMessage(error(unknownFeat(registry, id)));
        }
        return feat;
    }

    /**
     * Why a feat id cannot be used. A feat the registry left out because its pack is off or a sibling is missing
     * says so, such as "pack GRIND is off", anything else is unknown.
     */
    static String unknownFeat(FeatRegistry registry, String id) {
        String why = registry.whySkipped(id);
        if (why == null) {
            return "There is no feat with the id " + id + ".";
        }
        return "The feat " + id + " is not on this server, " + why + ". Packs and siblings are read at start.";
    }

    /** The active packs as one line, such as "Packs on: SOCIAL, CALENDAR", or "Packs on: none". */
    static String packsLine(FeatRegistry registry) {
        StringBuilder sb = new StringBuilder("Packs on: ");
        int n = 0;
        for (Pack p : Pack.values()) {
            if (registry.packs().contains(p)) {
                sb.append(n++ == 0 ? "" : ", ").append(p.name());
            }
        }
        return n == 0 ? sb.append("none").toString() : sb.toString();
    }

    private static String where(Lease lease) {
        return lease.online() ? "" : " (offline, saved)";
    }

    /** {@code view <player>} opens the browser read only on that player. */
    static final class View extends CommandBase {
        private final FeatsPlugin plugin;
        private final RequiredArg<String> player;

        View(FeatsPlugin plugin) {
            super("view", "Open the feats browser on any player.");
            this.plugin = plugin;
            this.player = withRequiredArg("player", "Player name", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(CommandContext context) {
            ViewCommand.open(plugin, context, context.get(player));
        }
    }

    /** {@code grant <player> <featId> [--rewards]} unlocks a feat whatever its requirements. */
    static final class Grant extends CommandBase {
        private final FeatsPlugin plugin;
        private final RequiredArg<String> player;
        private final RequiredArg<String> featId;
        private final FlagArg rewards;

        Grant(FeatsPlugin plugin) {
            super("grant", "Unlock a feat for a player.");
            this.plugin = plugin;
            this.player = withRequiredArg("player", "Player name", ArgTypes.STRING);
            this.featId = withRequiredArg("feat", "Feat id", ArgTypes.STRING);
            this.rewards = withFlagArg("rewards", "Also hand out the rewards for it");
        }

        @Override
        protected void executeSync(CommandContext context) {
            Feat feat = feat(plugin, context, context.get(featId));
            if (feat == null) {
                return;
            }
            boolean withRewards = Boolean.TRUE.equals(context.get(rewards));
            Targets.find(plugin, context, context.get(player), target -> edit(plugin, context, target, lease -> {
                boolean done = plugin.tracker().adminGrant(lease.progress(), feat, withRewards);
                Targets.reply(context, done
                        ? ok("Granted " + feat.name() + " to " + target.name()
                        + (withRewards ? " with rewards" : "") + where(lease) + ".")
                        : lease.progress().isUnlocked(feat.id()) || feat.isPersonal()
                        ? ok(target.name() + " already has " + feat.name() + ".")
                        : feat.scope() == Scope.FIRST
                        ? ok(feat.name() + " is already held, revoke it from its holder to hand it on.")
                        : ok(feat.name() + " was already reached on this server."));
            }));
        }
    }

    /** {@code revoke <player> <featId>} takes a feat away, and meta feats that are no longer met. */
    static final class Revoke extends CommandBase {
        private final FeatsPlugin plugin;
        private final RequiredArg<String> player;
        private final RequiredArg<String> featId;

        Revoke(FeatsPlugin plugin) {
            super("revoke", "Take a feat away from a player.");
            this.plugin = plugin;
            this.player = withRequiredArg("player", "Player name", ArgTypes.STRING);
            this.featId = withRequiredArg("feat", "Feat id", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(CommandContext context) {
            Feat feat = feat(plugin, context, context.get(featId));
            if (feat == null) {
                return;
            }
            Targets.find(plugin, context, context.get(player), target -> edit(plugin, context, target, lease -> {
                List<String> removed = plugin.tracker().revoke(lease.progress(), feat.id());
                if (removed.isEmpty()) {
                    Targets.reply(context, ok(target.name() + " does not have " + feat.name() + "."));
                    return;
                }
                String also = removed.size() > 1 ? ", plus " + (removed.size() - 1) + " meta feats no longer met" : "";
                Targets.reply(context, ok("Revoked " + feat.name() + " from " + target.name() + also + where(lease)
                        + ". It unlocks again if their counters still meet it."));
            }));
        }
    }

    /** {@code reset <player>} explains, {@code reset <player> confirm} wipes that player. */
    static final class Reset extends CommandBase {
        private final RequiredArg<String> player;

        Reset(FeatsPlugin plugin) {
            super("reset", "Erase a player's feats and statistics.");
            this.player = withRequiredArg("player", "Player name", ArgTypes.STRING);
            addUsageVariant(new Confirm(plugin));
        }

        @Override
        protected void executeSync(CommandContext context) {
            String name = context.get(player);
            context.sendMessage(Message.join(
                    Message.raw("This erases ALL feats and statistics of " + name + ". Type ").color("#ff9f43"),
                    Message.raw("/feats admin reset " + name + " confirm").color("#ffffff").bold(true),
                    Message.raw(" to continue.").color("#ff9f43")));
        }

        static final class Confirm extends CommandBase {
            private final FeatsPlugin plugin;
            private final RequiredArg<String> player;
            private final RequiredArg<String> word;

            Confirm(FeatsPlugin plugin) {
                super("Erase a player's feats and statistics.");
                this.plugin = plugin;
                this.player = withRequiredArg("player", "Player name", ArgTypes.STRING);
                this.word = withRequiredArg("confirm", "Type confirm", ArgTypes.STRING);
            }

            @Override
            protected void executeSync(CommandContext context) {
                if (!"confirm".equalsIgnoreCase(context.get(word))) {
                    context.sendMessage(error("Type confirm after the player name to reset them."));
                    return;
                }
                Targets.find(plugin, context, context.get(player), target -> {
                    if (!target.online() && !plugin.store().exists(target.uuid())) {
                        Targets.reply(context, error(target.name() + " has no feats file yet."));
                        return;
                    }
                    plugin.tracker().reset(target.uuid());
                    // After the reset so its save is queued on the IO thread ahead of the rewrite this asks for.
                    plugin.exporter().forget(target.uuid());
                    Targets.reply(context, ok("Reset the feats and statistics of " + target.name()
                            + ". Rewards they already had are still remembered."));
                });
            }
        }
    }

    /** {@code setstat <player> <key> <value>} sets one counter and checks the feats that read it. */
    static final class SetStat extends CommandBase {
        private final FeatsPlugin plugin;
        private final RequiredArg<String> player;
        private final RequiredArg<String> key;
        private final RequiredArg<String> value;

        SetStat(FeatsPlugin plugin) {
            super("setstat", "Set one of a player's counters.");
            this.plugin = plugin;
            this.player = withRequiredArg("player", "Player name", ArgTypes.STRING);
            this.key = withRequiredArg("key", "Counter key, for example blocks_broken", ArgTypes.STRING);
            this.value = withRequiredArg("value", "New value", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(CommandContext context) {
            long parsed;
            try {
                parsed = Long.parseLong(context.get(value).trim());
            } catch (NumberFormatException e) {
                context.sendMessage(error(context.get(value) + " is not a whole number."));
                return;
            }
            if (parsed < 0) {
                context.sendMessage(error("Counters cannot be negative."));
                return;
            }
            String stat = context.get(key).trim();
            if (!plugin.tracker().isPersisted(stat)) {
                context.sendMessage(error(stat + " is not a counter Feats keeps."));
                return;
            }
            long target = parsed;
            Targets.find(plugin, context, context.get(player), found -> edit(plugin, context, found, lease -> {
                long before = lease.progress().get(stat);
                plugin.tracker().adminSetStat(lease.progress(), stat, target);
                Targets.reply(context, ok("Set " + stat + " of " + found.name() + " from " + before + " to " + target
                        + where(lease) + "."));
            }));
        }
    }

    /** {@code reload} re-reads Feats.json and Rewards.json. */
    static final class Reload extends CommandBase {
        private final FeatsPlugin plugin;

        Reload(FeatsPlugin plugin) {
            super("reload", "Reload Feats.json and Rewards.json.");
            this.plugin = plugin;
        }

        @Override
        protected void executeSync(CommandContext context) {
            plugin.reload().thenAccept(summary -> {
                Targets.reply(context, ok(summary));
                Targets.reply(context, ok(packsLine(plugin.tracker().registry())
                        + ". A changed Pack setting needs a restart or /plugin reload to take effect."));
            });
        }
    }

    /** {@code status} prints the Feats version and one line per suite mod with its link and setting. */
    static final class Status extends CommandBase {
        private final FeatsPlugin plugin;

        Status(FeatsPlugin plugin) {
            super("status", "Show the Feats version and its links to the other mods.");
            this.plugin = plugin;
        }

        @Override
        protected void executeSync(CommandContext context) {
            FeatRegistry registry = plugin.tracker().registry();
            Targets.reply(context, ok("Feats " + plugin.getManifest().getVersion() + " with " + registry.count()
                    + " feats worth " + registry.totalPoints() + " points. " + packsLine(registry) + "."));
            for (String line : plugin.suiteStatus()) {
                Targets.reply(context, ok(line));
            }
        }
    }

    /** {@code totals} lists every server total and which total milestones the server has reached. */
    static final class Totals extends CommandBase {
        private final FeatsPlugin plugin;

        Totals(FeatsPlugin plugin) {
            super("totals", "Show the server totals and their milestones.");
            this.plugin = plugin;
        }

        @Override
        protected void executeSync(CommandContext context) {
            WorldStore world = plugin.world();
            FeatRegistry registry = plugin.tracker().registry();
            if (world == null) {
                context.sendMessage(ok("Server totals are not loaded."));
                return;
            }
            for (String line : totalLines(world, registry, plugin.tracker()::targetOf)) {
                context.sendMessage(line.startsWith(" ") ? ok(line) : Message.raw(line).color(Targets.GOLD).bold(true));
            }
        }

        /**
         * The report, one entry per line. Headings have no leading space, rows start with one. Totals come first,
         * sorted by key, then every total milestone with its current value, target and whether it is reached.
         * {@code target} is the tracker's target, so Full House shows the FullHouse setting.
         */
        static List<String> totalLines(WorldStore world, FeatRegistry registry,
                                       java.util.function.ToLongFunction<Feat> target) {
            List<String> out = new ArrayList<>();
            java.util.Map<String, Long> totals = world.totals();
            out.add("Server totals");
            if (totals.isEmpty()) {
                out.add(" None counted yet.");
            }
            for (java.util.Map.Entry<String, Long> e : totals.entrySet()) {
                out.add(String.format(" %s  %,d", e.getKey(), e.getValue()));
            }
            List<Feat> feats = registry.totalTriggered();
            out.add("Total milestones");
            if (feats.isEmpty()) {
                out.add(" None on this server.");
            }
            for (Feat f : feats) {
                Trigger t = f.trigger();
                long now = world.total(t.key());
                long goal = target.applyAsLong(f);
                boolean reached = world.hasMilestone(f.id()) || now >= goal;
                out.add(String.format(" %s  %s %,d / %,d  %s", f.name(), t.key(), now, goal,
                        reached ? "reached" : "not yet"));
            }
            return out;
        }
    }

    /** {@code rates} lists the ten least and ten most earned feats from the rarity data. */
    static final class Rates extends CommandBase {
        private static final int SHOWN = 10;
        private final FeatsPlugin plugin;

        Rates(FeatsPlugin plugin) {
            super("rates", "Show the rarest and most common feats.");
            this.plugin = plugin;
        }

        private record Rate(Feat feat, double fraction) {
        }

        @Override
        protected void executeSync(CommandContext context) {
            LeaderboardExporter exporter = plugin.exporter();
            List<Rate> rates = new ArrayList<>();
            for (Feat f : plugin.tracker().registry().all()) {
                double fraction = exporter.rarity(f.id());
                if (fraction >= 0) {
                    rates.add(new Rate(f, fraction));
                }
            }
            int players = exporter.rarityPlayers();
            if (rates.isEmpty() || players <= 0) {
                context.sendMessage(ok("No rarity data yet. It needs at least " + plugin.config().rarityMinimumPlayers()
                        + " counted players and a first read of the player files."));
                return;
            }
            rates.sort(Comparator.comparingDouble(Rate::fraction).thenComparing(r -> r.feat().id()));
            context.sendMessage(Message.raw("Least earned feats, over " + players + " players").color(Targets.GOLD).bold(true));
            for (int i = 0; i < Math.min(SHOWN, rates.size()); i++) {
                context.sendMessage(line(rates.get(i)));
            }
            context.sendMessage(Message.raw("Most earned feats").color(Targets.GOLD).bold(true));
            for (int i = rates.size() - 1; i >= Math.max(0, rates.size() - SHOWN); i--) {
                context.sendMessage(line(rates.get(i)));
            }
        }

        private static Message line(Rate r) {
            return Message.join(
                    Message.raw(" " + r.feat().name()).color(r.feat().tier().color()),
                    Message.raw("  " + r.feat().id() + "  " + String.format("%.1f%%", r.fraction() * 100.0))
                            .color(Targets.MUTED));
        }
    }

    /**
     * {@code scan <player|all> [--dry-run]} raises counters from what the engine itself recorded, such as
     * discovered zones, death markers and held Memories, for players who played before Feats was
     * installed. Counters only go up, so running it again changes nothing. Offline players unlock what
     * they now meet right away, silently, and get the rewards for it at their next join.
     */
    static final class Scan extends CommandBase {
        /** Chat gets at most this many lines, the console always gets the whole report. */
        private static final int CHAT_LINES = 20;
        private final FeatsPlugin plugin;
        private final RequiredArg<String> player;
        private final FlagArg dryRun;

        Scan(FeatsPlugin plugin) {
            super("scan", "Catch counters up from what the game recorded.");
            this.plugin = plugin;
            this.player = withRequiredArg("player", "Player name, a uuid or all", ArgTypes.STRING);
            this.dryRun = withFlagArg("dry-run", "Only show what would change");
        }

        @Override
        protected void executeSync(CommandContext context) {
            String who = context.get(player).trim();
            boolean dry = Boolean.TRUE.equals(context.get(dryRun));
            ScanJob.Reporter reporter = reporter(context, dry);
            try {
                if ("all".equalsIgnoreCase(who)) {
                    ScanJob.everyone(plugin, dry, reporter);
                    return;
                }
                UUID direct = parseUuid(who);
                if (direct != null) {
                    plugin.runIo(() -> ScanJob.one(plugin, direct, null, dry, reporter));
                    return;
                }
            } catch (RejectedExecutionException e) {
                context.sendMessage(error("Feats is shutting down."));
                return;
            }
            Targets.find(plugin, context, who, target -> ScanJob.one(plugin, target.uuid(), target.name(), dry, reporter));
        }

        private ScanJob.Reporter reporter(CommandContext context, boolean dry) {
            return new ScanJob.Reporter() {
                @Override
                public void progress(String text) {
                    Targets.reply(context, ok(text));
                }

                @Override
                public void done(List<String> lines, String totals) {
                    String title = dry ? "Feats scan dry run" : "Feats scan";
                    if (context.isPlayer() || lines.size() > CHAT_LINES) {
                        plugin.getLogger().at(Level.INFO).log("%s report, %d lines", title, lines.size());
                        for (String line : lines) {
                            plugin.getLogger().at(Level.INFO).log("  %s", line);
                        }
                        plugin.getLogger().at(Level.INFO).log("%s", totals);
                    }
                    int shown = lines.size() <= CHAT_LINES ? lines.size() : CHAT_LINES - 1;
                    for (int i = 0; i < shown; i++) {
                        Targets.reply(context, ok(lines.get(i)));
                    }
                    if (shown < lines.size()) {
                        Targets.reply(context, ok("And " + (lines.size() - shown)
                                + " more players, the full report is in the server console."));
                    }
                    Targets.reply(context, Message.raw(totals).color(Targets.GOLD));
                }
            };
        }

        private static UUID parseUuid(String text) {
            if (text.length() != 36) {
                return null;
            }
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
