package dev.ryvrook.feats.integration;

import dev.ryvrook.coinage.api.CoinageApi;
import dev.ryvrook.coinage.api.Currency;
import dev.ryvrook.coinage.api.Initiator;
import dev.ryvrook.coinage.api.Request;
import dev.ryvrook.coinage.api.Result;
import dev.ryvrook.feats.rewards.CoinPayer;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Pays the Coins of Rewards.json into Coinage accounts. The only class of Feats that uses Coinage types, loaded
 * by name in start once the plugin manager listed Coinage. It follows Coinage through its enable and disable
 * hooks, so a Coinage that starts after Feats or stops for a while is picked up again.
 */
public final class CoinageRewards implements CoinPayer, AutoCloseable {
    static final String PLUGIN = "Feats";
    private static final Initiator BY = Initiator.plugin(PLUGIN);

    private final Consumer<CoinageApi> enable = this::bind;
    private final Runnable disable = this::unbind;
    private volatile CoinageApi api;
    private volatile boolean closed;

    private CoinageRewards() {
    }

    /** Called by name from FeatsPlugin. */
    public static CoinageRewards install() {
        CoinageRewards rewards = new CoinageRewards();
        CoinageApi.onDisable(rewards.disable);
        CoinageApi.onEnable(rewards.enable);
        return rewards;
    }

    private void bind(CoinageApi live) {
        if (!closed) {
            api = live;
        }
    }

    private void unbind() {
        api = null;
    }

    /** True while Coinage is up and linked. */
    public boolean bound() {
        return api != null;
    }

    @Override
    public boolean ready() {
        return !closed && api != null;
    }

    public String version() {
        CoinageApi a = api;
        try {
            return a == null ? "" : a.version();
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
    }

    @Override
    public String pay(UUID player, String currency, BigDecimal amount, String reason) {
        CoinageApi a = api;
        if (closed || a == null) {
            return NOT_RUNNING;
        }
        if (player == null || amount == null || amount.signum() <= 0) {
            return "nothing to pay";
        }
        try {
            Optional<Currency> found = a.currencies().resolve(currency);
            if (found.isEmpty()) {
                return "Coinage has no currency " + currency;
            }
            Currency c = found.get();
            long minor = c.toMinor(amount);
            if (minor <= 0) {
                return amount.toPlainString() + " rounds to nothing in " + c.id();
            }
            Result r = a.ledger().deposit(Request.deposit(player, c.id(), minor).by(BY).plugin(PLUGIN).reason(reason));
            return r.ok() ? null : r.message();
        } catch (ArithmeticException | IllegalArgumentException e) {
            return amount.toPlainString() + " is too large";
        } catch (RuntimeException | LinkageError e) {
            return "Coinage refused the deposit (" + e + ")";
        }
    }

    /** Removes the hooks, for the Feats shutdown. */
    @Override
    public void close() {
        closed = true;
        api = null;
        try {
            CoinageApi.removeHooks(enable, disable);
        } catch (RuntimeException | LinkageError ignored) {
            // Coinage is already gone
        }
    }
}
