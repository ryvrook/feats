package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.projectile.event.ProjectileLaunchEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.UUID;

/**
 * Counts the projectiles a player fires. The engine invokes {@link ProjectileLaunchEvent} on the entity
 * that launched the projectile, so the chunk entity here is the shooter. Only launches by a weapon
 * count, which leaves out thrown items and ability projectiles that are not shots.
 */
public final class ProjectileLaunchSystem extends PlayerEventSystem<ProjectileLaunchEvent> {
    public ProjectileLaunchSystem(FeatsTracker tracker) {
        super(ProjectileLaunchEvent.class, tracker);
    }

    @Override
    protected void handlePlayer(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, ProjectileLaunchEvent event) {
        if (event.isLaunchedByWeapon()) {
            tracker.increment(uuid, StatKeys.SHOTS_FIRED, 1);
        }
    }
}
