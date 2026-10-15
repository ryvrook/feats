package dev.ryvrook.feats.rewards;

import java.math.BigDecimal;
import java.util.UUID;

/** Pays the Coins part of a reward. Feats has one while Coinage is installed, see integration.CoinageRewards. */
public interface CoinPayer {
    /** What {@link #pay} returns when Coinage is stopped or reloading. Nothing moved, so the payment can wait. */
    String NOT_RUNNING = "Coinage is not running";

    /**
     * Deposits {@code amount} whole units of {@code currency}, the primary currency when it is empty, into the
     * player's account. Returns null when it was paid, {@link #NOT_RUNNING} when Coinage is away, else why not.
     * Never throws and never blocks.
     */
    String pay(UUID player, String currency, BigDecimal amount, String reason);

    /** True while Coinage is up and a payment would go through. */
    default boolean ready() {
        return true;
    }
}
