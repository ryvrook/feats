package dev.ryvrook.feats;

import dev.ryvrook.feats.api.FeatUnlockedEvent;
import dev.ryvrook.feats.model.Feat;

import java.util.UUID;

/** Builds the {@link FeatUnlockedEvent} for one unlock from the feat definition. */
final class UnlockEvents {
    private UnlockEvents() {
    }

    static FeatUnlockedEvent of(UUID uuid, String playerName, Feat feat, int totalPoints, boolean catchUp,
                                boolean admin, double rarity) {
        return new FeatUnlockedEvent(uuid, playerName == null ? "" : playerName, feat.id(), feat.name(),
                feat.tier().name(), feat.tier().points(), totalPoints, catchUp, admin, feat.hidden(), rarity,
                feat.category().displayName(), feat.description());
    }
}
