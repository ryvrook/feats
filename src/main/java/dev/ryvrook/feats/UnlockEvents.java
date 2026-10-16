package dev.ryvrook.feats;

import dev.ryvrook.feats.api.FeatUnlockedEvent;
import dev.ryvrook.feats.model.Feat;

import java.util.UUID;

/** Builds the {@link FeatUnlockedEvent} for one unlock from the feat definition. */
final class UnlockEvents {
    private UnlockEvents() {
    }

    /** The form from before banners. The banner flag reads as false. */
    static FeatUnlockedEvent of(UUID uuid, String playerName, Feat feat, int totalPoints, boolean catchUp,
                                boolean admin, double rarity) {
        return of(uuid, playerName, feat, totalPoints, catchUp, admin, rarity, false);
    }

    /** {@code banner} is the feat's banner flag, see {@link Feat#banner()}. */
    static FeatUnlockedEvent of(UUID uuid, String playerName, Feat feat, int totalPoints, boolean catchUp,
                                boolean admin, double rarity, boolean banner) {
        return new FeatUnlockedEvent(uuid, playerName == null ? "" : playerName, feat.id(), feat.name(),
                feat.tier().name(), feat.tier().points(), totalPoints, catchUp, admin, feat.hidden(), rarity,
                feat.category().displayName(), feat.description(), banner);
    }
}
