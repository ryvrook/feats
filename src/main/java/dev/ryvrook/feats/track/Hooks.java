package dev.ryvrook.feats.track;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.FeatsConfig;
import dev.ryvrook.feats.FeatsPlugin;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.WorldStore;
import dev.ryvrook.feats.systems.CombatHooks;
import dev.ryvrook.feats.systems.ItemHooks;
import dev.ryvrook.feats.systems.WorldHooks;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Fans the plugin's life out to every {@link HookSet}. The sets are {@link CombatHooks}, {@link WorldHooks} and
 * {@link ItemHooks}, each owning its own systems and in memory state. A set that throws is logged and the
 * others still run.
 */
public final class Hooks {
    /**
     * What every hook can reach. {@code ownPath} is true when Feats registers its own tracking systems, false
     * when TrackerAPI feeds the counters. {@code siblings} is what the plugin manager listed at setup.
     */
    public record Context(FeatsPlugin plugin, FeatsTracker tracker, FeatRegistry registry, Supplier<FeatsConfig> config,
                          HytaleLogger logger, WorldStore world, Set<Sibling> siblings, boolean ownPath,
                          SessionClock sessions) {
    }

    private final List<HookSet> sets;
    private final HytaleLogger logger;

    public Hooks(HytaleLogger logger) {
        this(logger, List.of(new CombatHooks(), new WorldHooks(), new ItemHooks()));
    }

    public Hooks(HytaleLogger logger, List<HookSet> sets) {
        this.logger = logger;
        this.sets = List.copyOf(sets);
    }

    public List<HookSet> sets() {
        return sets;
    }

    public void registerOwn(Context ctx, ComponentRegistryProxy<EntityStore> ecs, EventRegistry events) {
        for (HookSet set : sets) {
            guard(set, "register", () -> set.registerOwn(ctx, ecs, events));
        }
    }

    public void registerAlways(Context ctx, ComponentRegistryProxy<EntityStore> ecs, EventRegistry events) {
        for (HookSet set : sets) {
            guard(set, "register", () -> set.registerAlways(ctx, ecs, events));
        }
    }

    public void start(Context ctx) {
        for (HookSet set : sets) {
            guard(set, "start", () -> set.start(ctx));
        }
    }

    public void stop(Context ctx) {
        for (HookSet set : sets) {
            guard(set, "stop", () -> set.stop(ctx));
        }
    }

    public void tick(Context ctx, long nowMillis, List<PlayerRef> online) {
        for (HookSet set : sets) {
            guard(set, "tick", () -> set.tick(ctx, nowMillis, online));
        }
    }

    public void sampleWorld(Context ctx, World world, List<PlayerRef> players, boolean ownPath, long nowMillis) {
        for (HookSet set : sets) {
            guard(set, "sample", () -> set.sampleWorld(ctx, world, players, ownPath, nowMillis));
        }
    }

    public void joined(Context ctx, UUID uuid, PlayerRef player, PlayerProgress progress, long nowMillis) {
        for (HookSet set : sets) {
            guard(set, "join", () -> set.joined(ctx, uuid, player, progress, nowMillis));
        }
    }

    public void left(Context ctx, UUID uuid, long nowMillis) {
        for (HookSet set : sets) {
            guard(set, "leave", () -> set.left(ctx, uuid, nowMillis));
        }
    }

    private void guard(HookSet set, String what, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException | LinkageError e) {
            logger.at(Level.WARNING).withCause(e).log("Feats %s hooks failed at %s", set.getClass().getSimpleName(), what);
        }
    }
}
