package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerConfigData;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.HookSet;
import dev.ryvrook.feats.track.Hooks;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The item, block, farming, trading, movement and mount hooks of 1.7.2. The counters that ride on the
 * systems the plugin already registers (inventory, placing, breaking, pickups, interactions, taming) live
 * in those systems. This set adds the systems that are new, each registered on its own so a server missing
 * one engine class loses one counter group and keeps the rest, and the recipe poll.
 *
 * <p>Recipes. The knowledge recipes a player learned are read from their PlayerConfigData every fifth
 * world sample, so once in five seconds, on both tracking paths since it is a poll of persisted data and a
 * discovery counts once however often it is seen. See {@link RecipePoll} for the 25 ids.
 */
public final class ItemHooks implements HookSet {
    private MovementSystem movement;
    private volatile boolean recipesOff;
    /** World samples seen per player, so the recipe read runs every fifth one. */
    private final Map<UUID, Integer> samples = new ConcurrentHashMap<>();

    @Override
    public void registerOwn(Hooks.Context ctx, ComponentRegistryProxy<EntityStore> ecs, EventRegistry events) {
        FeatsTracker tracker = ctx.tracker();
        try {
            ecs.registerSystem(new UseEntitySystem(tracker));
        } catch (RuntimeException | LinkageError e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Feats cannot see entity uses on this server, NPC talk tracking is off");
        }
        try {
            ecs.registerSystem(new MountSystem(tracker));
        } catch (RuntimeException | LinkageError e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Feats cannot see mounts on this server, seat, mount and minecart tracking is off");
        }
        try {
            MovementSystem system = new MovementSystem(tracker);
            ecs.registerSystem(system);
            movement = system;
        } catch (RuntimeException | LinkageError e) {
            ctx.logger().at(Level.WARNING).withCause(e).log("Feats cannot read movement states on this server, jump, roll, climb and glide tracking is off");
        }
    }

    @Override
    public void sampleWorld(Hooks.Context ctx, World world, List<PlayerRef> players, boolean ownPath, long nowMillis) {
        if (recipesOff) {
            return;
        }
        for (int i = 0, n = players.size(); i < n; i++) {
            PlayerRef player = players.get(i);
            UUID uuid = player.getUuid();
            if (uuid == null) {
                continue;
            }
            int count = samples.merge(uuid, 1, Integer::sum);
            if (count % RecipePoll.EVERY != 0) {
                continue;
            }
            try {
                readRecipes(ctx.tracker(), uuid, player);
            } catch (LinkageError e) {
                recipesOff = true;
                ctx.logger().at(Level.WARNING).withCause(e).log("Feats cannot read known recipes on this server, recipe tracking is off");
                return;
            } catch (RuntimeException e) {
                ctx.logger().at(Level.FINE).withCause(e).log("Feats skipped a recipe read");
            }
        }
    }

    private static void readRecipes(FeatsTracker tracker, UUID uuid, PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        if (!store.isInThread()) {
            return; // moved to another world since the tick grouped them
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerConfigData data = player != null ? player.getPlayerConfigData() : null;
        Set<String> known = data != null ? data.getKnownRecipes() : null;
        List<String> hits = RecipePoll.known(known);
        if (hits.isEmpty()) {
            return;
        }
        for (String id : hits) {
            tracker.discover(uuid, StatKeys.RECIPE_PREFIX, id, StatKeys.RECIPES_KNOWN);
        }
        tracker.raiseTo(uuid, StatKeys.RECIPES_KNOWN, hits.size());
    }

    @Override
    public void left(Hooks.Context ctx, UUID uuid, long nowMillis) {
        samples.remove(uuid);
        if (movement != null) {
            movement.forget(uuid);
        }
    }
}
