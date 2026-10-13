package dev.ryvrook.feats.scan;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import com.hypixel.hytale.server.worldgen.zone.Zone;
import com.hypixel.hytale.server.worldgen.zone.ZoneDiscoveryConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps the region names the engine saves in a player's discovered zones to the zone name the live
 * discovery event reports. The engine saves {@code Zone.name()}, such as Zone1_Tier1, while
 * {@code DiscoverZoneEvent.Display} carries the discovery name, such as Emerald_Wilds, and Feats keys
 * zones by the latter, falling back to the region name when a zone has no discovery name.
 */
final class ZoneNames {
    private ZoneNames() {
    }

    /** Reads every loaded world's generator, each on its own world thread. Call it off the world threads. */
    static Map<String, String> collect() {
        Map<String, String> names = new HashMap<>();
        for (World world : Universe.get().getWorlds().values()) {
            try {
                Map<String, String> part = Threads.onWorld(world, () -> read(world));
                if (part != null) {
                    part.forEach(names::putIfAbsent);
                }
            } catch (Exception | LinkageError e) {
                // a world closing or a generator without zones, the others still count
            }
        }
        return names;
    }

    private static Map<String, String> read(World world) {
        Map<String, String> names = new HashMap<>();
        IWorldGen gen = world.getChunkStore().getGenerator();
        if (!(gen instanceof ChunkGenerator generator)) {
            return names;
        }
        for (Zone zone : generator.getZonePatternProvider().getZones()) {
            add(names, zone);
        }
        try {
            int seed = (int) world.getWorldConfig().getSeed();
            for (Zone.Unique unique : generator.getZonePatternGenerator(seed).getUniqueZones()) {
                add(names, unique.zone());
            }
        } catch (RuntimeException e) {
            // unique zones are optional, the regular ones above cover nearly every region
        }
        return names;
    }

    private static void add(Map<String, String> names, Zone zone) {
        if (zone == null || zone.name() == null || zone.name().isBlank()) {
            return;
        }
        ZoneDiscoveryConfig discovery = zone.discoveryConfig();
        String shown = discovery != null ? discovery.zone() : null;
        names.putIfAbsent(zone.name(), shown == null || shown.isBlank() ? zone.name() : shown);
    }
}
