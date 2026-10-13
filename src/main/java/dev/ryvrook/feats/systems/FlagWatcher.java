package dev.ryvrook.feats.systems;

import com.hypixel.hytale.builtin.gameflags.GameFlagsResource;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.Trigger;
import dev.ryvrook.feats.stats.WorldStore;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Polls the server wide game flags behind the flag triggered world feats every 5 seconds on the engine
 * scheduler. The flags live in a universe resource backed by a concurrent map, so reading a level off
 * the world thread is safe. Once a flag reaches its level the milestone is recorded, naming the player
 * who used a Goblin Lab device in the minute before, when there was one.
 */
public final class FlagWatcher {
    private static final long PERIOD_SECONDS = 5L;
    private static final long INITIATOR_WINDOW_MILLIS = 60_000L;
    /** The device whose repair raises each Goblin Lab flag, so its user wins over other lab users. */
    private static final Map<String, String> DEVICE_OF_FLAG = Map.of(
            "goblin_lab_power", "GoblinLab_Generator",
            "goblin_lab_recipe_a", "GoblinLab_Tinkering_Bench_Recipeset_A",
            "goblin_lab_recipe_b", "GoblinLab_Tinkering_Bench_Recipeset_B",
            "goblin_lab_recipe_c", "GoblinLab_Tinkering_Bench_Recipeset_C");

    private final FeatsTracker tracker;
    private final UseBlockSystem uses;
    private final HytaleLogger logger;
    private final List<Feat> watched;
    private ScheduledFuture<?> task;
    private volatile boolean warned;

    public FlagWatcher(FeatsTracker tracker, UseBlockSystem uses, HytaleLogger logger) {
        this.tracker = tracker;
        this.uses = uses;
        this.logger = logger;
        this.watched = tracker.registry().flagTriggered();
    }

    /** Starts polling. Does nothing when no feat watches a flag or the server has no world store. */
    public void start() {
        if (watched.isEmpty() || tracker.world() == null || task != null) {
            return;
        }
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(this::poll, PERIOD_SECONDS, PERIOD_SECONDS,
                TimeUnit.SECONDS);
    }

    public void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    private void poll() {
        try {
            WorldStore world = tracker.world();
            GameFlagsResource flags = null;
            for (Feat feat : watched) {
                if (world.hasMilestone(feat.id())) {
                    continue;
                }
                if (flags == null) {
                    flags = GameFlagsResource.get();
                    if (flags == null) {
                        return;
                    }
                }
                Trigger trigger = feat.trigger();
                if (flags.getLevel(trigger.key()) < trigger.minLevel()) {
                    continue;
                }
                UUID by = uses == null ? null : uses.lastLabUser(DEVICE_OF_FLAG.get(trigger.key()), INITIATOR_WINDOW_MILLIS);
                tracker.recordMilestone(feat.id(), by);
            }
        } catch (IllegalStateException e) {
            // universe resources not loaded yet, try again next time
        } catch (RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                logger.at(Level.WARNING).withCause(e).log("Feats could not read the game flags for world milestones");
            }
        }
    }
}
