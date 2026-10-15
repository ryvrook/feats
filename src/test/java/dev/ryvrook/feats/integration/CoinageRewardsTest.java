package dev.ryvrook.feats.integration;

import com.google.gson.JsonObject;
import dev.ryvrook.coinage.api.Accounts;
import dev.ryvrook.coinage.api.CoinageApi;
import dev.ryvrook.coinage.api.Currencies;
import dev.ryvrook.coinage.api.Currency;
import dev.ryvrook.coinage.api.EconomyListener;
import dev.ryvrook.coinage.api.Ledger;
import dev.ryvrook.coinage.api.Mail;
import dev.ryvrook.coinage.api.NetWorthParts;
import dev.ryvrook.coinage.api.Reports;
import dev.ryvrook.coinage.api.Request;
import dev.ryvrook.coinage.api.Result;
import dev.ryvrook.coinage.api.Subscription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinageRewardsTest {
    private final List<Request> deposits = new ArrayList<>();
    private StandIn coinage;
    private CoinageRewards rewards;

    @AfterEach
    void stop() {
        if (rewards != null) {
            rewards.close();
        }
        if (coinage != null) {
            coinage.stop();
        }
    }

    @Test
    void paysWholeUnitsAsMinorUnitsOfThePrimaryCurrency() {
        coinage = new StandIn();
        coinage.start();
        rewards = CoinageRewards.install();
        assertTrue(rewards.bound());
        UUID player = UUID.randomUUID();
        assertNull(rewards.pay(player, "", new BigDecimal("2.50"), "feats:digger"));
        assertEquals(1, deposits.size());
        Request r = deposits.get(0);
        assertEquals(player, r.to());
        assertEquals("coins", r.currency());
        assertEquals(250, r.amount());
        assertEquals("Feats", r.plugin());
        assertEquals("feats:digger", r.reason());
    }

    @Test
    void anUnknownCurrencyOrAStoppedCoinagePaysNothing() {
        coinage = new StandIn();
        coinage.start();
        rewards = CoinageRewards.install();
        assertNotNull(rewards.pay(UUID.randomUUID(), "gems", BigDecimal.TEN, "feats:x"));
        assertNotNull(rewards.pay(UUID.randomUUID(), "", new BigDecimal("0.001"), "feats:x"));
        coinage.stop();
        assertFalse(rewards.bound());
        assertNotNull(rewards.pay(UUID.randomUUID(), "", BigDecimal.TEN, "feats:x"));
        coinage.start();
        assertTrue(rewards.bound(), "a Coinage that starts again is picked up through the enable hook");
        assertTrue(deposits.isEmpty());
    }

    /** A Coinage with one currency of two decimals whose ledger records deposits. */
    private final class StandIn extends CoinageApi {
        private final Currency coins = Currency.coins();

        void start() {
            enable(this);
        }

        void stop() {
            disable(this);
        }

        @Override
        public Currencies currencies() {
            return new Currencies() {
                @Override
                public Currency primary() {
                    return coins;
                }

                @Override
                public Optional<Currency> get(String id) {
                    return coins.id().equals(id) ? Optional.of(coins) : Optional.empty();
                }

                @Override
                public List<Currency> all() {
                    return List.of(coins);
                }
            };
        }

        @Override
        public Ledger ledger() {
            return (Ledger) Proxy.newProxyInstance(Ledger.class.getClassLoader(), new Class<?>[]{Ledger.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("deposit")) {
                            deposits.add((Request) args[0]);
                            return Result.ok("ok", 0, 0, null);
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override
        public Accounts accounts() {
            return null;
        }

        @Override
        public Mail mail() {
            return null;
        }

        @Override
        public Reports reports() {
            return null;
        }

        @Override
        public <T> Optional<T> stats(Class<T> type) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> service(Class<T> type) {
            return Optional.empty();
        }

        @Override
        public JsonObject config() {
            return new JsonObject();
        }

        @Override
        public NetWorthParts netWorth() {
            return null;
        }

        @Override
        public Subscription subscribe(String owner, EconomyListener listener) {
            return null;
        }

        @Override
        public List<Subscription> subscriptions() {
            return List.of();
        }

        @Override
        public String version() {
            return "test";
        }
    }
}
