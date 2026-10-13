package dev.ryvrook.feats.export;

import dev.ryvrook.feats.FeatsConfig;

import java.util.Locale;

/** Turns the owned fraction from {@link LeaderboardExporter#rarity} into short text for players. */
public final class Rarity {
    private Rarity() {
    }

    /**
     * The share of players as a percentage, such as {@code 12%} or {@code 3.5%}. Values under 10 keep
     * one decimal. Never rounds up, so a feat everyone but one player owns does not read as 100%.
     */
    public static String percent(double fraction) {
        double pct = Math.max(0, Math.min(1, fraction)) * 100.0;
        if (pct >= 10) {
            return (int) Math.floor(pct) + "%";
        }
        double tenths = Math.floor(pct * 10) / 10.0;
        if (tenths == 0 && pct > 0) {
            return "<0.1%";
        }
        if (tenths == Math.floor(tenths)) {
            return (int) tenths + "%";
        }
        return String.format(Locale.ROOT, "%.1f%%", tenths);
    }

    /** True when the share is below {@code RareBelowPercent}. */
    public static boolean isRare(double fraction, int rareBelowPercent) {
        return fraction >= 0 && fraction * 100.0 < rareBelowPercent;
    }

    /**
     * {@code "12% of players"}, or {@code "rare, 3% of players"} under the rare threshold. Empty when
     * the rarity is unknown.
     */
    public static String tag(double fraction, int rareBelowPercent) {
        if (fraction < 0) {
            return "";
        }
        String share = percent(fraction) + " of players";
        return isRare(fraction, rareBelowPercent) ? "rare, " + share : share;
    }

    /** The tag for one feat, honouring {@code ShowRarity}. Empty when it is off or rarity is unknown. */
    public static String tag(LeaderboardExporter exporter, FeatsConfig config, String featId) {
        if (exporter == null || config == null || !config.showRarity()) {
            return "";
        }
        return tag(exporter.rarity(featId), config.rareBelowPercent());
    }
}
