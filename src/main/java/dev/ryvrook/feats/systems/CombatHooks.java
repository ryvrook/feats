package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.track.HookSet;
import dev.ryvrook.feats.track.Hooks;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The combat hooks of the own tracking path. {@link DamageSystem} and {@link DeathSystem} are registered
 * by the plugin with the other core systems and feed every combat counter. This set adds the projectile
 * launch system for {@code shots_fired}, settles survived falls and drowning episodes from the
 * {@link CombatMemory} once a second, and drops a player's short lived combat notes when they leave.
 * See docs/EXPANSION-PLAN.md section 3.4 for the counters.
 */
public final class CombatHooks implements HookSet {
    @Override
    public void registerOwn(Hooks.Context ctx, ComponentRegistryProxy<EntityStore> ecs, EventRegistry events) {
        try {
            ecs.registerSystem(new ProjectileLaunchSystem(ctx.tracker()));
        } catch (RuntimeException | LinkageError e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Feats cannot count projectiles on this server, shots fired is off");
        }
    }

    @Override
    public void tick(Hooks.Context ctx, long nowMillis, List<PlayerRef> online) {
        if (ctx.ownPath()) {
            CombatMemory.of(ctx.tracker()).flush(nowMillis);
        }
    }

    @Override
    public void left(Hooks.Context ctx, UUID uuid, long nowMillis) {
        CombatMemory.of(ctx.tracker()).forget(uuid);
    }
}
