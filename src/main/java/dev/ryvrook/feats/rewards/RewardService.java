package dev.ryvrook.feats.rewards;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.ryvrook.feats.FeatsConfig;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.track.FeatsTracker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Hands out the rewards in {@code Rewards.json} when feats unlock. Does nothing, and never reads the
 * file, while {@code RewardsEnabled} is false.
 *
 * <p>Each reward is recorded in the player's progress before it runs, under {@code feat:<id>},
 * {@code points:<at>} or {@code count:<at>}, so it is never given twice, not even after a reset.
 * The work itself goes into one queue that the engine scheduler drains one step every 100 ms, so a
 * burst of unlocks at join never runs a pile of commands in one tick. Console commands go through the
 * engine's command manager, which parses on its own pool and moves world work to the world thread
 * itself, so no step here ever waits on a world.
 */
public final class RewardService {
    public static final String FILE_NAME = "Rewards.json";
    private static final long STEP_MILLIS = 100L;
    private static final long QUIET_MILLIS = 60_000L;

    private final Supplier<FeatsConfig> config;
    private final FeatsTracker tracker;
    private final HytaleLogger logger;
    private final Path file;
    private volatile RewardTable table = RewardTable.EMPTY;
    private volatile boolean stopped;
    /** Pays the Coins part while Coinage is installed, null without it. */
    private volatile CoinPayer coins;

    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean draining = new AtomicBoolean();
    /** When each failing command, or other failing step, was last logged. */
    private final Map<String, Long> lastFailureLog = new ConcurrentHashMap<>();

    public RewardService(Supplier<FeatsConfig> config, FeatsTracker tracker, Path dataDirectory, HytaleLogger logger) {
        this.config = config;
        this.tracker = tracker;
        this.logger = logger;
        this.file = dataDirectory.resolve(FILE_NAME);
    }

    /**
     * Reads {@code Rewards.json}, writing the example first when it does not exist. Clears the table
     * when rewards are off. A file that fails to parse keeps the previous table and logs why. Blocks on
     * the file, so call it on the IO thread. Returns a short summary for the reload command.
     */
    public String reload() {
        if (!config.get().rewardsEnabled()) {
            table = RewardTable.EMPTY;
            return "Rewards are off";
        }
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, RewardTable.EXAMPLE, StandardCharsets.UTF_8);
                logger.at(Level.INFO).log("Feats wrote an empty %s to fill in", FILE_NAME);
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<String> problems = new ArrayList<>();
            RewardTable parsed = RewardTable.parse(json, id -> tracker.registry().get(id) != null, problems::add);
            for (String problem : problems) {
                logger.at(Level.WARNING).log("Feats %s, %s", FILE_NAME, problem);
            }
            table = parsed;
            logger.at(Level.INFO).log("Feats loaded %d rewards from %s", parsed.size(), FILE_NAME);
            return "Loaded " + parsed.size() + " rewards";
        } catch (IOException | RuntimeException e) {
            logger.at(Level.WARNING).withCause(e).log("Feats could not read %s, keeping the rewards it had", FILE_NAME);
            return FILE_NAME + " could not be read, see the server log";
        }
    }

    /**
     * Stops the queue and runs the steps still waiting straight away. Each of those rewards is already
     * recorded as given, so dropping them would lose them for good.
     */
    public void stop() {
        stopped = true;
        int ran = 0;
        Runnable next;
        while ((next = queue.poll()) != null) {
            try {
                next.run();
            } catch (RuntimeException | LinkageError e) {
                failed("reward step", e);
            }
            ran++;
        }
        if (ran > 0) {
            logger.at(Level.INFO).log("Feats ran %d waiting reward steps at shutdown", ran);
        }
    }

    /** Hands the Coins part of each reward to Coinage. Null leaves Coins unpaid with a warning. */
    public void setCoinPayer(CoinPayer payer) {
        this.coins = payer;
    }

    /** Tracker hook, runs on the thread that caused the unlock. Only records and queues. */
    public void onUnlock(FeatsTracker.Unlock unlock) {
        if (stopped || !unlock.rewards() || !config.get().rewardsEnabled()) {
            return;
        }
        RewardTable rewards = table;
        if (rewards.isEmpty()) {
            return;
        }
        PlayerProgress progress = unlock.progress();
        Feat feat = unlock.feat();
        int points = tracker.points(progress);
        int count = progress.unlockedCount();
        Context ctx = new Context(progress.uuid(), name(progress), feat, points);

        Reward forFeat = rewards.feats().get(feat.id());
        Reward forTier = rewards.tiers().get(feat.tier());
        if ((forFeat != null || forTier != null) && progress.markRewarded("feat:" + feat.id())) {
            give(forFeat, ctx);
            give(forTier, ctx);
        }
        for (RewardTable.Threshold t : rewards.points()) {
            if (t.at() > points) {
                break; // sorted by At
            }
            if (progress.markRewarded("points:" + t.at())) {
                give(t.reward(), ctx);
            }
        }
        for (RewardTable.Threshold t : rewards.count()) {
            if (t.at() > count) {
                break;
            }
            if (progress.markRewarded("count:" + t.at())) {
                give(t.reward(), ctx);
            }
        }
    }

    /** The values placeholders are filled from, taken at the moment of the unlock. */
    private record Context(UUID uuid, String player, Feat feat, int points) {
        String fill(String text) {
            return text.replace("{player}", player)
                    .replace("{uuid}", uuid.toString())
                    .replace("{feat}", feat.id())
                    .replace("{name}", feat.name())
                    .replace("{tier}", feat.tier().displayName())
                    .replace("{points}", Integer.toString(points));
        }
    }

    /** Queues one step per command, then one step for the permissions, the group and the message. */
    private void give(Reward reward, Context ctx) {
        if (reward == null || reward.isEmpty()) {
            return;
        }
        for (String command : reward.commands()) {
            String line = ctx.fill(command);
            enqueue(() -> runCommand(command, line));
        }
        if (reward.coins() != null) {
            String reason = "feats:" + ctx.feat().id();
            enqueue(() -> payCoins(ctx.uuid(), reward, reason));
        }
        if (!reward.permissions().isEmpty() || !reward.group().isEmpty() || !reward.message().isEmpty()) {
            String message = reward.message().isEmpty() ? "" : ctx.fill(reward.message());
            enqueue(() -> grantAccess(ctx.uuid(), reward, message));
        }
    }

    /** Deposits the Coins through Coinage. Coinage takes deposits for offline players as well. */
    private void payCoins(UUID uuid, Reward reward, String reason) {
        CoinPayer payer = coins;
        String what = "coins " + reward.coins().toPlainString() + (reward.currency().isEmpty() ? "" : " " + reward.currency());
        if (payer == null) {
            failed(what, new IllegalStateException("Coinage is not installed"));
            return;
        }
        String problem = payer.pay(uuid, reward.currency(), reward.coins(), reason);
        if (problem != null) {
            failed(what, new IllegalStateException(problem));
        }
    }

    private void runCommand(String template, String line) {
        String command = line.startsWith("/") ? line.substring(1) : line;
        try {
            CommandManager.get().handleCommand(ConsoleSender.INSTANCE, command).whenComplete((done, error) -> {
                if (error != null) {
                    failed("command " + template, error);
                }
            });
        } catch (RuntimeException | LinkageError e) {
            failed("command " + template, e);
        }
    }

    private void grantAccess(UUID uuid, Reward reward, String message) {
        if (!reward.permissions().isEmpty()) {
            try {
                PermissionsModule.get().addUserPermission(uuid, new LinkedHashSet<>(reward.permissions()));
            } catch (RuntimeException | LinkageError e) {
                failed("permissions " + reward.permissions(), e);
            }
        }
        if (!reward.group().isEmpty()) {
            try {
                PermissionsModule.get().addUserToGroup(uuid, reward.group());
            } catch (RuntimeException | LinkageError e) {
                failed("group " + reward.group(), e);
            }
        }
        if (!message.isEmpty()) {
            try {
                PlayerRef player = Universe.get().getPlayer(uuid);
                if (player != null) {
                    player.sendMessage(Message.raw(message).color("#E8A93B"));
                }
            } catch (RuntimeException e) {
                failed("message", e);
            }
        }
    }

    private void enqueue(Runnable step) {
        queue.add(step);
        if (draining.compareAndSet(false, true)) {
            schedule(0);
        }
    }

    private void schedule(long delayMillis) {
        try {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(this::step, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            draining.set(false);
            logger.at(Level.WARNING).withCause(e).log("Feats could not schedule its reward queue");
        }
    }

    /** Runs one queued step and schedules the next one 100 ms later. */
    private void step() {
        if (stopped) {
            draining.set(false);
            return;
        }
        Runnable next = queue.poll();
        if (next == null) {
            draining.set(false);
            // Something may have been queued between the poll and the flag going down.
            if (!queue.isEmpty() && draining.compareAndSet(false, true)) {
                schedule(STEP_MILLIS);
            }
            return;
        }
        try {
            next.run();
        } catch (RuntimeException | LinkageError e) {
            failed("reward step", e);
        }
        schedule(STEP_MILLIS);
    }

    /** Logs a failure at most once a minute for the same command string or step. */
    private void failed(String what, Throwable cause) {
        long now = System.currentTimeMillis();
        boolean[] log = {false};
        lastFailureLog.compute(what, (key, last) -> {
            if (last == null || now - last >= QUIET_MILLIS) {
                log[0] = true;
                return now;
            }
            return last;
        });
        if (lastFailureLog.size() > 256) {
            lastFailureLog.values().removeIf(at -> now - at >= QUIET_MILLIS);
        }
        if (log[0]) {
            logger.at(Level.WARNING).withCause(cause).log("Feats reward failed, %s", what);
        }
    }

    private static String name(PlayerProgress progress) {
        String name = progress.username();
        return name == null || name.isEmpty() ? progress.uuid().toString() : name;
    }
}
