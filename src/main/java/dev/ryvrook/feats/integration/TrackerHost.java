package dev.ryvrook.feats.integration;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.systems.LabUses;
import dev.ryvrook.feats.track.FeatsTracker;

import java.util.UUID;

/**
 * What {@link TrackerBridge} needs from Feats. Kept free of TrackerAPI types so the plugin can implement it
 * without loading any of them.
 */
public interface TrackerHost {
    FeatsTracker tracker();

    /** The Goblin Lab device uses {@code FlagWatcher} reads. */
    LabUses labUses();

    /** The {@code TrackDistance} setting. */
    boolean trackDistance();

    /** True while the player is in Creative mode, whatever {@code IgnoreCreative} says. */
    boolean isCreative(UUID uuid);

    /** Follows a game mode change, which also pauses or resumes the player's counters. */
    void setCreative(UUID uuid, boolean creative);

    /** The lower case name of the world the player is in now, or null. */
    String worldOf(UUID uuid);

    /** Counts one chat message unless the last counted one was under 5 seconds before {@code atMillis}. */
    void countChat(UUID uuid, long atMillis);

    HytaleLogger logger();

    /** What the 1.7.2 hooks reach, so the bridge can feed the same in memory state they do. */
    default dev.ryvrook.feats.track.Hooks.Context context() {
        return null;
    }
}
