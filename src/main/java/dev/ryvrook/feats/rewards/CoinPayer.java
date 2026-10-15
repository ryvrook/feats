package dev.ryvrook.feats.rewards;

import java.math.BigDecimal;
import java.util.UUID;

/** Pays the Coins part of a reward. Feats has one while Coinage is installed, see integration.CoinageRewards. */
public interface CoinPayer {
    /**
     * Deposits {@code amount} whole units of {@code currency}, the primary currency when it is empty, into the
     * player's account. Returns null when it was paid, else why not. Never throws and never blocks.
     */
    String pay(UUID player, String currency, BigDecimal amount, String reason);
}
