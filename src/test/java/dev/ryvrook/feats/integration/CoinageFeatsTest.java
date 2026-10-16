package dev.ryvrook.feats.integration;

import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.coinage.api.Accounts;
import dev.ryvrook.coinage.api.CoinageApi;
import dev.ryvrook.coinage.api.Currencies;
import dev.ryvrook.coinage.api.Currency;
import dev.ryvrook.coinage.api.EconomyListener;
import dev.ryvrook.coinage.api.Initiator;
import dev.ryvrook.coinage.api.Ledger;
import dev.ryvrook.coinage.api.Mail;
import dev.ryvrook.coinage.api.NetWorth;
import dev.ryvrook.coinage.api.NetWorthParts;
import dev.ryvrook.coinage.api.Reports;
import dev.ryvrook.coinage.api.Subscription;
import dev.ryvrook.coinage.api.Transaction;
import dev.ryvrook.coinage.api.event.BetSettled;
import dev.ryvrook.coinage.api.event.DailyClaimed;
import dev.ryvrook.coinage.api.event.InterestPaid;
import dev.ryvrook.coinage.api.event.ListingSold;
import dev.ryvrook.coinage.api.event.LoanDefaulted;
import dev.ryvrook.coinage.api.event.LoanPaid;
import dev.ryvrook.coinage.api.event.LoanTaken;
import dev.ryvrook.coinage.api.event.LotteryDrawn;
import dev.ryvrook.coinage.api.event.NoteIssued;
import dev.ryvrook.coinage.api.event.Payment;
import dev.ryvrook.coinage.api.event.SavingsMoved;
import dev.ryvrook.coinage.api.event.ShopSale;
import dev.ryvrook.coinage.api.event.TradeSettled;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.UnlockNotifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Feeds Coinage records through the listener CoinageFeats subscribes, the way Coinage delivers them, and checks
 * the {@code coinage:} counters land in whole coins on online and offline players.
 */
class CoinageFeatsTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static FeatRegistry registry;

    private FeatsTracker tracker;
    private StandIn coinage;
    private CoinageFeats feats;
    private FakeFacts facts;
    private UUID a;
    private UUID b;
    private PlayerProgress pa;
    private PlayerProgress pb;

    private static synchronized FeatRegistry registry() {
        if (registry == null) {
            registry = new FeatRegistry(EnumSet.allOf(Sibling.class), Set.of());
        }
        return registry;
    }

    @BeforeEach
    void setUp() throws IOException {
        ProgressStore store = new ProgressStore(Files.createTempDirectory("feats-coinage"), LOGGER);
        tracker = new FeatsTracker(registry(), store, new UnlockNotifier(() -> null, LOGGER), LOGGER, Runnable::run);
        a = UUID.randomUUID();
        b = UUID.randomUUID();
        pa = tracker.load(a, "Alpha");
        pb = tracker.load(b, "Beta");
        facts = new FakeFacts();
        coinage = new StandIn();
    }

    @AfterEach
    void tearDown() {
        if (feats != null) {
            feats.close();
        }
        coinage.stop();
    }

    private EconomyListener up() {
        coinage.start();
        feats = CoinageFeats.install(tracker, LOGGER, null, facts);
        assertTrue(feats.bound());
        assertNotNull(coinage.listener);
        return coinage.listener;
    }

    @Test
    void subscribesUnderFeatsAndFollowsCoinageThroughAReload() {
        feats = CoinageFeats.install(tracker, LOGGER, null, facts);
        assertFalse(feats.bound(), "nothing to bind before Coinage starts");
        coinage.start();
        assertTrue(feats.bound(), "a Coinage that starts after Feats is picked up");
        assertEquals("Feats", coinage.owner);
        Sub first = coinage.subscription;
        coinage.stop();
        assertFalse(feats.bound());
        assertFalse(first.open, "the subscription is closed on disable");
        coinage.start();
        assertTrue(feats.bound(), "a Coinage that starts again is picked up");
        assertTrue(coinage.subscription.open);
        feats.close();
        assertFalse(coinage.subscription.open, "close ends the subscription");
        coinage.stop();
        coinage.start();
        assertFalse(feats.bound(), "a closed binding stays away");
    }

    @Test
    void aCloseDuringABindLetsGoOfTheNewSubscription() {
        feats = CoinageFeats.install(tracker, LOGGER, null, facts);
        coinage.onSubscribe = feats::close; // the shutdown arrives while the enable hook is subscribing
        coinage.start();
        assertFalse(feats.bound(), "a bind that saw the close after subscribing stays unbound");
        assertFalse(coinage.subscription.open, "and closes what it subscribed");
    }

    @Test
    void paymentsCountReceivedPaidAndSentInWholeCoinsWithTheRestCarried() {
        EconomyListener l = up();
        // 2.99 Coins paid, 2.50 net after the tax
        l.payment(new Payment(1, "Coinage", a, b, "coins", 299, 250, 49, 7));
        l.payment(new Payment(2, "Coinage", a, b, "coins", 1_000, 1_000, 0, 8));
        assertEquals(12, pb.get(CoinageFeats.RECEIVED), "2.50 then 10.00, the half still carried");
        assertEquals(12, pa.get(CoinageFeats.PAID), "2.99 then 10.00, 0.99 still carried");
        assertEquals(2, pa.get(CoinageFeats.PAYMENTS_SENT));
        assertEquals(0, pb.get(CoinageFeats.PAYMENTS_SENT));
    }

    @Test
    void shopListingsAndTradesCountTheRightSide() {
        EconomyListener l = up();
        l.shopSale(new ShopSale(1, "Coinage", "s", "Shop", true, null, "Ore_Iron", 16, 100, 1_600, 0, "coins", a, true));
        l.shopSale(new ShopSale(2, "Coinage", "s", "Shop", true, null, "Ore_Iron", 5, 100, 500, 0, "coins", a, false));
        assertEquals(16, pa.get(CoinageFeats.SHOP_BOUGHT));
        assertEquals(5, pa.get(CoinageFeats.SHOP_SOLD));

        UUID listing = UUID.randomUUID();
        l.listingSold(new ListingSold(1, "Coinage", listing, "sale", a, a, b, "Ore_Iron", 1, 100, 100, 0, 0, "coins"));
        l.listingSold(new ListingSold(2, "Coinage", listing, "auction", a, a, b, "Ore_Iron", 1, 100, 100, 0, 0, "coins"));
        assertEquals(2, pa.get(CoinageFeats.LISTINGS_SOLD));
        assertEquals(1, pb.get(CoinageFeats.AUCTIONS_WON), "only the auction is a win");
        assertEquals(0, pa.get(CoinageFeats.AUCTIONS_WON));

        l.tradeSettled(new TradeSettled(1, "Coinage", "t1", a, "Alpha", b, "Beta", 0, 0, List.of(), List.of(), "coins"));
        assertEquals(1, pa.get(CoinageFeats.TRADES));
        assertEquals(1, pb.get(CoinageFeats.TRADES));
        assertEquals(0, pa.get(CoinageFeats.ALLIED_TRADES));
        facts.allies.add(a);
        facts.allies.add(b);
        l.tradeSettled(new TradeSettled(2, "Coinage", "t2", a, "Alpha", b, "Beta", 0, 0, List.of(), List.of(), "coins"));
        assertEquals(1, pa.get(CoinageFeats.ALLIED_TRADES));
        assertEquals(1, pb.get(CoinageFeats.ALLIED_TRADES));
    }

    @Test
    void alliedTradesNeedDominion() {
        coinage.start();
        feats = CoinageFeats.install(tracker, LOGGER, null, null);
        coinage.listener.tradeSettled(new TradeSettled(1, "Coinage", "t1", a, "Alpha", b, "Beta", 0, 0, List.of(), List.of(), "coins"));
        assertEquals(1, pa.get(CoinageFeats.TRADES));
        assertEquals(0, pa.get(CoinageFeats.ALLIED_TRADES));
    }

    @Test
    void bankAndLoanRecords() {
        EconomyListener l = up();
        l.savingsMoved(new SavingsMoved(1, "Coinage", "bank", a, "coins", 500, 0, true, 500));
        l.savingsMoved(new SavingsMoved(2, "Coinage", "bank", a, "coins", 100, 0, false, 400));
        assertEquals(1, pa.get(CoinageFeats.SAVINGS_DEPOSITS), "a withdrawal is not a deposit");
        l.interestPaid(new InterestPaid(1, "Coinage", "bank", a, "coins", 150, 550));
        l.interestPaid(new InterestPaid(2, "Coinage", "bank", a, "coins", 75, 625));
        assertEquals(2, pa.get(CoinageFeats.INTEREST), "1.50 then 0.75 make 2.25, the half carried to the 0.75");

        l.loanTaken(new LoanTaken(1, "Coinage", "L1", a, null, "bank", "coins", 10_000, 11_000, 7));
        assertEquals(1, pa.get(CoinageFeats.LOANS));
        l.loanPaid(new LoanPaid(2, "Coinage", "L1", a, "bank", "coins", 5_000, 6_000, false));
        assertEquals(0, pa.get(CoinageFeats.LOANS_REPAID), "a part payment does not repay");
        l.loanPaid(new LoanPaid(3, "Coinage", "L1", a, "bank", "coins", 6_000, 0, false));
        assertEquals(1, pa.get(CoinageFeats.LOANS_REPAID));
        l.loanDefaulted(new LoanDefaulted(4, "Coinage", "L2", b, null, "bank", "coins", 100, 900));
        assertEquals(1, pb.get(CoinageFeats.LOANS_DEFAULTED));
    }

    @Test
    void casinoLotteryDailyAndNotes() {
        EconomyListener l = up();
        l.betSettled(new BetSettled(1, "Coinage", a, "roulette", 1_000, 0, false, "coins"));
        l.betSettled(new BetSettled(2, "Coinage", a, "roulette", 1_000, 2_000, true, "coins"));
        l.betSettled(new BetSettled(3, "Coinage", a, "blackjack", 1_000, 500, false, "coins"));
        assertEquals(3, pa.get(CoinageFeats.BETS));
        assertEquals(1, pa.get(CoinageFeats.BETS_WON));
        assertEquals(30, pa.get(CoinageFeats.STAKED));
        assertEquals(15, pa.get(CoinageFeats.LOST), "10 lost, nothing on the win, 5 on the half payout");

        l.lotteryDrawn(new LotteryDrawn(1, "Coinage", 1, b, "Beta", 10_000, 9_000, 1_000, 10, 3, false, "coins"));
        l.lotteryDrawn(new LotteryDrawn(2, "Coinage", 2, null, null, 10_000, 0, 0, 0, 0, true, "coins"));
        assertEquals(1, pb.get(CoinageFeats.LOTTERY_WINS), "a rollover has no winner");

        l.dailyClaimed(new DailyClaimed(1, "Coinage", a, 100, 5));
        l.dailyClaimed(new DailyClaimed(2, "Coinage", a, 100, 1));
        assertEquals(5, pa.get(CoinageFeats.DAILY_BEST_STREAK), "the best streak stays after a reset");

        l.noteIssued(new NoteIssued(1, "Coinage", a, "coins", 5_000, 0, "N1", 0));
        assertEquals(1, pa.get(CoinageFeats.NOTES_ISSUED));
    }

    @Test
    void balanceMaxRaisesFromThePlayerAccountsThatMovedInThePrimaryCurrency() {
        EconomyListener l = up();
        l.afterTransaction(tx(Transaction.Type.TRANSFER, "coins", a, b, 1_000, 500_000, 1_234_567));
        assertEquals(5_000, pa.get(CoinageFeats.BALANCE_MAX));
        assertEquals(12_345, pb.get(CoinageFeats.BALANCE_MAX));
        l.afterTransaction(tx(Transaction.Type.WITHDRAW, "coins", a, null, 1_000, 100, 0));
        assertEquals(5_000, pa.get(CoinageFeats.BALANCE_MAX), "a lower balance keeps the record");
        l.afterTransaction(tx(Transaction.Type.DEPOSIT, "gems", null, a, 1_000, 0, 99_999_999));
        assertEquals(5_000, pa.get(CoinageFeats.BALANCE_MAX), "another currency does not count");
        l.afterTransaction(tx(Transaction.Type.SET, "coins", a, null, 0, 900_000, 0));
        assertEquals(5_000, pa.get(CoinageFeats.BALANCE_MAX), "the balance a set replaced is not a balance now");
    }

    @Test
    void netWorthAndWarChestOnTheTimer() {
        up();
        coinage.worth.put(a, 4_000_000L); // 40,000 coins
        coinage.worth.put(b, 6_000_000L); // 60,000 coins
        feats.sampleNetWorth();
        assertEquals(40_000, pa.get(CoinageFeats.NET_WORTH_MAX));
        assertEquals(60_000, pb.get(CoinageFeats.NET_WORTH_MAX));
        assertEquals(0, pb.get(CoinageFeats.WAR_CHEST), "rich but not at war");
        facts.atWar.add(a);
        facts.atWar.add(b);
        feats.sampleNetWorth();
        assertEquals(0, pa.get(CoinageFeats.WAR_CHEST), "at war but under 50,000");
        assertEquals(1, pb.get(CoinageFeats.WAR_CHEST));
        coinage.worth.put(b, 100L);
        feats.sampleNetWorth();
        assertEquals(60_000, pb.get(CoinageFeats.NET_WORTH_MAX), "the max keeps the best");
    }

    @Test
    void smallAmountsAddUpInsteadOfFlooringToNothing() {
        EconomyListener l = up();
        // 1 percent interest on 30 coins is 0.30, four times over
        for (int i = 0; i < 4; i++) {
            l.interestPaid(new InterestPaid(i, "Coinage", "bank", a, "coins", 30, 3_000));
        }
        assertEquals(1, pa.get(CoinageFeats.INTEREST), "1.20 in all is one whole coin");
        for (int i = 0; i < 3; i++) {
            l.interestPaid(new InterestPaid(10 + i, "Coinage", "bank", a, "coins", 30, 3_000));
        }
        assertEquals(2, pa.get(CoinageFeats.INTEREST), "2.10 in all");
    }

    @Test
    void anyPaymentAboveZeroGivesTheFirstCoinAndTheTotalStaysTrue() {
        EconomyListener l = up();
        l.payment(new Payment(1, "Coinage", a, b, "coins", 40, 40, 0, 1));
        assertEquals(1, pb.get(CoinageFeats.RECEIVED), "0.40 received still counts for First Coin");
        assertEquals(0, pa.get(CoinageFeats.PAID), "paying keeps the floor");
        l.payment(new Payment(2, "Coinage", a, b, "coins", 40, 40, 0, 2));
        assertEquals(1, pb.get(CoinageFeats.RECEIVED), "0.80 in all, the early coin is paid back first");
        l.payment(new Payment(3, "Coinage", a, b, "coins", 150, 150, 0, 3));
        assertEquals(2, pb.get(CoinageFeats.RECEIVED), "2.30 in all");
        assertEquals(2, pa.get(CoinageFeats.PAID));
    }

    @Test
    void theCarryIsDroppedWhenCoinageStops() {
        EconomyListener l = up();
        l.interestPaid(new InterestPaid(1, "Coinage", "bank", a, "coins", 60, 3_000));
        coinage.stop();
        coinage.start();
        coinage.listener.interestPaid(new InterestPaid(2, "Coinage", "bank", a, "coins", 60, 3_000));
        assertEquals(0, pa.get(CoinageFeats.INTEREST), "the 0.60 before the stop is not carried over");
    }

    @Test
    void currencyDecimalsAreReadLiveSoACoinageReloadIsSeen() {
        EconomyListener l = up();
        coinage.coins = new Currency("coins", "Coin", "Coins", "", false, 0, 0, Currency.DEFAULT_MAX_BALANCE, true, true, 0);
        l.payment(new Payment(1, "Coinage", a, b, "coins", 7, 7, 0, 1));
        assertEquals(7, pb.get(CoinageFeats.RECEIVED), "no decimals now, so 7 minor units are 7 coins");
    }

    @Test
    void offlinePlayersGetTheAddsInTheirFile() {
        EconomyListener l = up();
        tracker.unload(b);
        l.payment(new Payment(1, "Coinage", a, b, "coins", 5_000, 5_000, 0, 1));
        l.tradeSettled(new TradeSettled(2, "Coinage", "t", a, "Alpha", b, "Beta", 0, 0, List.of(), List.of(), "coins"));
        PlayerProgress file = tracker.peek(b);
        assertNotNull(file);
        assertEquals(50, file.get(CoinageFeats.RECEIVED));
        assertEquals(1, file.get(CoinageFeats.TRADES));
        assertEquals(50, pa.get(CoinageFeats.PAID));
    }

    @Test
    void aFailingRecordIsSwallowed() {
        EconomyListener l = up();
        l.payment(null); // a record Feats cannot read warns once and does not reach Coinage
        l.noteIssued(new NoteIssued(1, "Coinage", a, "coins", 5_000, 0, "N1", 0));
        assertEquals(1, pa.get(CoinageFeats.NOTES_ISSUED));
    }

    @Test
    void dominionHandlesResolveAgainstTheDominionJar() throws Exception {
        Path jar = Path.of("..", "dominion", "build", "libs", "DominionFactions-0.7.1.jar");
        assumeTrue(Files.isRegularFile(jar), "no Dominion jar next to this checkout");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, getClass().getClassLoader())) {
            CrossModFeats.Handles h = CrossModFeats.handles(null, loader);
            assertNotNull(h.get());
            assertNotNull(h.factionOf());
            assertNotNull(h.relationBetween());
            assertNotNull(h.relations());
            assertNotNull(h.isSystem());
        }
    }

    private static Transaction tx(Transaction.Type type, String currency, UUID from, UUID to, long amount, long fromAfter, long toAfter) {
        return new Transaction(1, 1, type, currency, from, to, amount, 0, null, fromAfter, toAfter, Initiator.server(), "Coinage", "", 0);
    }

    private static final class FakeFacts implements CoinageFeats.Facts {
        final Set<UUID> allies = new HashSet<>();
        final Set<UUID> atWar = new HashSet<>();

        @Override
        public boolean allied(UUID x, UUID y) {
            return allies.contains(x) && allies.contains(y);
        }

        @Override
        public boolean atWar(UUID player) {
            return atWar.contains(player);
        }
    }

    static final class Sub implements Subscription {
        boolean open = true;

        @Override
        public String owner() {
            return "Feats";
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }

    /** A Coinage with Coins of two decimals that hands its subscriber out and answers net worth from a map. */
    static final class StandIn extends CoinageApi {
        volatile Currency coins = Currency.coins();
        final Map<UUID, Long> worth = new HashMap<>();
        final List<Sub> subscriptions = new ArrayList<>();
        EconomyListener listener;
        String owner;
        Sub subscription;
        boolean running;
        /** Runs inside subscribe, so a test can close Feats while a bind is in the middle of it. */
        Runnable onSubscribe;

        void start() {
            running = true;
            enable(this);
        }

        void stop() {
            if (running) {
                running = false;
                for (Sub s : subscriptions) {
                    s.close(); // Coinage closes every subscription before the disable hooks run
                }
                disable(this);
            }
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
        public Reports reports() {
            return (Reports) Proxy.newProxyInstance(Reports.class.getClassLoader(), new Class<?>[]{Reports.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("netWorth")) {
                            UUID player = (UUID) args[0];
                            long total = worth.getOrDefault(player, 0L);
                            return new NetWorth(player, (String) args[1], total, Map.of());
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override
        public Subscription subscribe(String owner, EconomyListener listener) {
            this.owner = owner;
            this.listener = listener;
            subscription = new Sub();
            subscriptions.add(subscription);
            Runnable hook = onSubscribe;
            if (hook != null) {
                hook.run();
            }
            return subscription;
        }

        @Override
        public Accounts accounts() {
            return null;
        }

        @Override
        public Ledger ledger() {
            return null;
        }

        @Override
        public Mail mail() {
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
        public List<Subscription> subscriptions() {
            return List.copyOf(subscriptions);
        }

        @Override
        public String version() {
            return "test";
        }
    }
}
