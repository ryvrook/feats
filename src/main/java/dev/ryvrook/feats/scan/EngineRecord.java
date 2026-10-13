package dev.ryvrook.feats.scan;

import com.hypixel.hytale.builtin.adventure.memories.component.PlayerMemories;
import com.hypixel.hytale.builtin.adventure.memories.memories.Memory;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerConfigData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerDeathPositionData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the engine itself recorded about one player, copied out of the engine objects into plain values
 * so it can travel between threads.
 *
 * @param regions      worldgen region names from {@link PlayerConfigData#getDiscoveredZones()}
 * @param deathMarkers death markers kept per world, at most five per world, so a lower bound on deaths
 * @param spawned      true when any world says this player has made their first spawn there
 * @param memories     ids of the Memories the player has recorded and not handed in yet
 */
public record EngineRecord(Set<String> regions, int deathMarkers, boolean spawned, Set<String> memories) {

    /** Reads an online player. Call it on the world thread that owns the store. */
    static EngineRecord ofLive(Store<EntityStore> store, Ref<EntityStore> ref) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return null;
        }
        return of(player.getPlayerConfigData(), Memories.live(store, ref));
    }

    /** Reads a player holder the engine loaded from its player storage. Not attached to any world. */
    static EngineRecord ofSaved(Holder<EntityStore> holder) {
        if (holder == null) {
            return null;
        }
        Player player = holder.getComponent(Player.getComponentType());
        if (player == null) {
            return null;
        }
        return of(player.getPlayerConfigData(), Memories.saved(holder));
    }

    private static EngineRecord of(PlayerConfigData data, Set<String> memories) {
        if (data == null) {
            return null;
        }
        Set<String> regions = new TreeSet<>();
        Set<String> zones = data.getDiscoveredZones();
        if (zones != null) {
            for (String zone : zones) {
                if (zone != null && !zone.isBlank()) {
                    regions.add(zone);
                }
            }
        }
        int deaths = 0;
        boolean spawned = false;
        if (data.getPerWorldData() != null) {
            for (PlayerWorldData world : data.getPerWorldData().values()) {
                if (world == null) {
                    continue;
                }
                List<PlayerDeathPositionData> positions = world.getDeathPositions();
                if (positions != null) {
                    deaths += positions.size();
                }
                if (!world.isFirstSpawn()) {
                    spawned = true;
                }
            }
        }
        return new EngineRecord(Set.copyOf(regions), deaths, spawned, Set.copyOf(memories));
    }

    /**
     * Memories live in their own builtin plugin. Kept apart so a server without it only loses this
     * source and the zone and death reads still work.
     */
    private static final class Memories {
        static Set<String> live(Store<EntityStore> store, Ref<EntityStore> ref) {
            try {
                return ids(store.getComponent(ref, PlayerMemories.getComponentType()));
            } catch (LinkageError | RuntimeException e) {
                return Set.of();
            }
        }

        static Set<String> saved(Holder<EntityStore> holder) {
            try {
                return ids(holder.getComponent(PlayerMemories.getComponentType()));
            } catch (LinkageError | RuntimeException e) {
                return Set.of();
            }
        }

        private static Set<String> ids(PlayerMemories memories) {
            if (memories == null) {
                return Set.of();
            }
            Set<String> ids = new TreeSet<>();
            for (Memory memory : memories.getRecordedMemories()) {
                if (memory != null && memory.getId() != null && !memory.getId().isEmpty()) {
                    ids.add(memory.getId());
                }
            }
            return ids;
        }
    }
}
