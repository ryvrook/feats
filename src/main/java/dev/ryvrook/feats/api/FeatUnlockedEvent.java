package dev.ryvrook.feats.api;

import com.hypixel.hytale.event.IEvent;

import java.util.UUID;

/**
 * Fired once for every feat a player unlocks, on the engine event bus and to listeners added with
 * {@link FeatsApi#onUnlock}. It is a plain synchronous event without a key, so register for it like
 * any other global event, for example
 * {@code getEventRegistry().registerGlobal(FeatUnlockedEvent.class, event -> ...)}.
 *
 * <p>It is delivered on the thread that caused the unlock, which is usually a world thread. Keep
 * listeners short and move slow work elsewhere. The event is informational and cannot be cancelled.
 *
 * <p>This class is part of the stable Feats API. Fields are only ever added, never removed or renamed.
 */
public final class FeatUnlockedEvent implements IEvent<Void> {
    private final UUID uuid;
    private final String playerName;
    private final String featId;
    private final String featName;
    private final String tier;
    private final int points;
    private final int totalPoints;
    private final boolean catchUp;

    public FeatUnlockedEvent(UUID uuid, String playerName, String featId, String featName, String tier, int points,
                             int totalPoints, boolean catchUp) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.featId = featId;
        this.featName = featName;
        this.tier = tier;
        this.points = points;
        this.totalPoints = totalPoints;
        this.catchUp = catchUp;
    }

    /** The player who unlocked the feat. */
    public UUID uuid() {
        return uuid;
    }

    /** The player's name as Feats last saw it. */
    public String playerName() {
        return playerName;
    }

    /** The stable feat id, as stored in player files. */
    public String featId() {
        return featId;
    }

    /** The feat's display name. */
    public String featName() {
        return featName;
    }

    /** BRONZE, SILVER, GOLD or PLATINUM. */
    public String tier() {
        return tier;
    }

    /** Points this feat is worth. */
    public int points() {
        return points;
    }

    /** The player's points total including this feat. */
    public int totalPoints() {
        return totalPoints;
    }

    /**
     * True when the feat unlocked as the player joined, because their counters already met it after an
     * update or a lowered target, rather than through something they just did.
     */
    public boolean catchUp() {
        return catchUp;
    }

    @Override
    public String toString() {
        return "FeatUnlockedEvent{" + playerName + " " + featId + " " + tier + " total " + totalPoints
                + (catchUp ? " catch up" : "") + "}";
    }
}
