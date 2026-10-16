package dev.ryvrook.feats.systems;

import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.feats.stats.StatKeys;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Reads the state a player is in when they land a kill, for the kill context counters. Each read is
 * guarded on its own. A read that fails once is skipped for that kill, a read whose class is missing on
 * this server logs one warning and stays off. The counter keys for every combination of flags are built
 * once and shared, so a kill allocates nothing here.
 */
final class CombatContext {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    static final int UNDERWATER = 1;
    static final int GLIDING = 2;
    static final int MOUNTED = 4;
    static final int LOW_HEALTH = 8;
    static final int NIGHT = 16;
    static final int BLOOD_MOON = 32;
    /** Set by the caller for a projectile kill from far enough away, see {@link DeathSystem}. */
    static final int FAR = 64;

    private static final String[] KEYS = {StatKeys.KILLS_UNDERWATER, StatKeys.KILLS_GLIDING, StatKeys.KILLS_MOUNTED,
            StatKeys.KILLS_LOW_HEALTH, StatKeys.KILLS_NIGHT, StatKeys.KILLS_BLOOD_MOON, StatKeys.KILLS_FAR};
    private static final List<List<String>> LISTS = buildLists();

    /** Health under this share of the maximum is low. */
    static final float LOW_HEALTH_SHARE = 0.1f;
    /**
     * Night by the engine's clock. Its day runs from 0.2 to 0.8 of the 24 game hours, sunrise at 4:48
     * and sunset at 19:12, so the hours 5 to 18 are day and the rest is night.
     */
    static final int SUNRISE_HOUR = 5;
    static final int SUNSET_HOUR = 19;
    /** Weather asset id of the blood moon, Server/Weathers in 0.7.1. */
    static final String BLOOD_MOON_WEATHER = "Blood_Moon";

    private boolean movementOff;
    private boolean healthOff;
    private boolean timeOff;
    private boolean weatherOff;
    private boolean positionOff;

    private static List<List<String>> buildLists() {
        List<List<String>> lists = new ArrayList<>(128);
        for (int mask = 0; mask < 128; mask++) {
            List<String> keys = new ArrayList<>(KEYS.length);
            for (int i = 0; i < KEYS.length; i++) {
                if ((mask & (1 << i)) != 0) {
                    keys.add(KEYS[i]);
                }
            }
            lists.add(List.copyOf(keys));
        }
        return List.copyOf(lists);
    }

    /** The counter keys for a set of flags, an immutable shared list. */
    static List<String> keys(int flags) {
        return LISTS.get(flags & 127);
    }

    /** True when the engine's clock says night, see {@link #SUNRISE_HOUR}. */
    static boolean isNight(int hour) {
        return hour < SUNRISE_HOUR || hour >= SUNSET_HOUR;
    }

    /** The context flags of the killer, without {@link #FAR}. */
    int flags(Ref<EntityStore> killer, Store<EntityStore> store) {
        int flags = 0;
        flags |= movement(killer, store);
        flags |= health(killer, store);
        flags |= night(store);
        flags |= weather(killer, store);
        return flags;
    }

    private int movement(Ref<EntityStore> killer, Store<EntityStore> store) {
        if (movementOff) {
            return 0;
        }
        try {
            MovementStatesComponent component = store.getComponent(killer, MovementStatesComponent.getComponentType());
            MovementStates states = component != null ? component.getMovementStates() : null;
            if (states == null) {
                return 0;
            }
            int flags = 0;
            if (states.swimming || states.inFluid) {
                flags |= UNDERWATER;
            }
            if (states.gliding) {
                flags |= GLIDING;
            }
            if (states.mounting) {
                flags |= MOUNTED;
            }
            return flags;
        } catch (LinkageError e) {
            movementOff = true;
            LOGGER.at(Level.WARNING).withCause(e).log("Feats cannot read movement states on this server, underwater, gliding and mounted kills are off");
        } catch (RuntimeException e) {
            PlayerEventSystem.failed(e);
        }
        return 0;
    }

    private int health(Ref<EntityStore> killer, Store<EntityStore> store) {
        if (healthOff) {
            return 0;
        }
        try {
            EntityStatMap stats = store.getComponent(killer, EntityStatMap.getComponentType());
            EntityStatValue health = stats != null ? stats.get(DefaultEntityStatTypes.getHealth()) : null;
            if (health == null) {
                return 0;
            }
            float max = health.getMax();
            return max > 0 && health.get() / max < LOW_HEALTH_SHARE ? LOW_HEALTH : 0;
        } catch (LinkageError e) {
            healthOff = true;
            LOGGER.at(Level.WARNING).withCause(e).log("Feats cannot read health on this server, low health kills are off");
        } catch (RuntimeException e) {
            PlayerEventSystem.failed(e);
        }
        return 0;
    }

    private int night(Store<EntityStore> store) {
        if (timeOff) {
            return 0;
        }
        try {
            WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
            return time != null && isNight(time.getCurrentHour()) ? NIGHT : 0;
        } catch (LinkageError e) {
            timeOff = true;
            LOGGER.at(Level.WARNING).withCause(e).log("Feats cannot read the game clock on this server, night kills are off");
        } catch (RuntimeException e) {
            PlayerEventSystem.failed(e);
        }
        return 0;
    }

    private int weather(Ref<EntityStore> killer, Store<EntityStore> store) {
        if (weatherOff) {
            return 0;
        }
        try {
            WeatherTracker weather = store.getComponent(killer, WeatherTracker.getComponentType());
            int index = weather != null ? weather.getWeatherIndex() : -1;
            Weather asset = index < 0 ? null : Weather.getAssetMap().getAsset(index);
            return asset != null && BLOOD_MOON_WEATHER.equals(asset.getId()) ? BLOOD_MOON : 0;
        } catch (LinkageError e) {
            weatherOff = true;
            LOGGER.at(Level.WARNING).withCause(e).log("Feats cannot read the weather on this server, blood moon kills are off");
        } catch (RuntimeException e) {
            PlayerEventSystem.failed(e);
        }
        return 0;
    }

    /**
     * The distance in metres between two entities of one store, or -1 when either has no position or
     * the read fails. Both refs must be valid in {@code store}.
     */
    double distance(Ref<EntityStore> a, Ref<EntityStore> b, Store<EntityStore> store) {
        if (positionOff) {
            return -1;
        }
        try {
            TransformComponent first = store.getComponent(a, TransformComponent.getComponentType());
            TransformComponent second = store.getComponent(b, TransformComponent.getComponentType());
            Vector3d from = first != null ? first.getPosition() : null;
            Vector3d to = second != null ? second.getPosition() : null;
            return from != null && to != null ? from.distance(to) : -1;
        } catch (LinkageError e) {
            positionOff = true;
            LOGGER.at(Level.WARNING).withCause(e).log("Feats cannot read positions on this server, long range kills are off");
        } catch (RuntimeException e) {
            PlayerEventSystem.failed(e);
        }
        return -1;
    }
}
