package dev.ryvrook.feats.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.ecs.UseEntityEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.ryvrook.feats.track.Classify;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.UUID;

/**
 * Counts the NPCs a player uses, by NPC type id, such as {@code talk:Kweebec_Elder}. The engine posts
 * UseEntityEvent.Post after a use interaction ran on an entity. Players and creatures without an NPC
 * entity are skipped. The key is per id, so it is only kept when a feat reads it.
 */
public final class UseEntitySystem extends PlayerEventSystem<UseEntityEvent.Post> {
    public UseEntitySystem(FeatsTracker tracker) {
        super(UseEntityEvent.Post.class, tracker);
    }

    @Override
    protected void handlePlayer(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store, UseEntityEvent.Post event) {
        Ref<EntityStore> target = event.getTargetEntity();
        if (target == null || !target.isValid() || target.getStore() != store) {
            return;
        }
        NPCEntity npc = store.getComponent(target, NPCEntity.getComponentType());
        String typeId = npc != null ? npc.getNPCTypeId() : null;
        if (typeId == null || typeId.isEmpty()) {
            return;
        }
        tracker.increment(uuid, Classify.talkKey(typeId), 1);
    }
}
