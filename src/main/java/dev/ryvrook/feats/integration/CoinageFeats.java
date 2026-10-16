package dev.ryvrook.feats.integration;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.coinage.api.CoinageApi;
import dev.ryvrook.coinage.api.Currency;
import dev.ryvrook.coinage.api.EconomyListener;
import dev.ryvrook.coinage.api.NetWorth;
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
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.stats.PlayerProgress;
import dev.ryvrook.feats.stats.StatKeys;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.Hooks;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Counts the {@code coinage:} counters of the Coinage tab, docs/EXPANSION-PLAN.md section 8.2, from the typed
 * events Coinage publishes. Loaded by name from {@link SiblingLinks} once the plugin manager listed Coinage, so
 * no Coinage class is touched on a server without it. It follows Coinage through its enable and disable hooks
 * the way {@link CoinageRewards} does, subscribes an {@link EconomyListener} under owner {@code Feats} on enable
 * and closes the subscription on disable, so a Coinage that starts after Feats or stops for a while is picked
 * up again.
 *
 * <p>Amounts arrive in minor units and are written in whole coins. The minor units under a coin are carried in
 * memory per player, counter and currency, so small amounts add up, and any payment above zero counts toward
 * First Coin. Online players are credited through
 * the tracker's increment and raiseTo, offline players through addOffline, which merges the adds into their
 * file. Balance and net worth are read, not counted. {@code afterTransaction} raises the balance of a player
 * account that moved in the primary currency, and a 5 minute timer on the engine scheduler asks
 * {@code reports().netWorth} for every online player. Listener threads are Coinage's and the tracker is safe
 * from any thread. Every call into Coinage is guarded, and the first failure is logged once.
 */
public final class CoinageFeats implements AutoCloseable {
    static final String OWNER = "Feats";
    /** How often net worth is read for online players. */
    static final long NET_WORTH_SECONDS = 300;
    /** The War Chest threshold in whole coins. */
    static final long WAR_CHEST_COINS = 50_000;

    static final String RECEIVED = StatKeys.COINAGE_PREFIX + "received";
    static final String PAID = StatKeys.COINAGE_PREFIX + "paid";
    static final String PAYMENTS_SENT = StatKeys.COINAGE_PREFIX + "payments_sent";
    static final String BALANCE_MAX = StatKeys.COINAGE_PREFIX + "balance_max";
    static final String NET_WORTH_MAX = StatKeys.COINAGE_PREFIX + "net_worth_max";
    static final String SHOP_SOLD = StatKeys.COINAGE_PREFIX + "shop_sold";
    static final String SHOP_BOUGHT = StatKeys.COINAGE_PREFIX + "shop_bought";
    static final String LISTINGS_SOLD = StatKeys.COINAGE_PREFIX + "listings_sold";
    static final String AUCTIONS_WON = StatKeys.COINAGE_PREFIX + "auctions_won";
    static final String TRADES = StatKeys.COINAGE_PREFIX + "trades";
    static final String SAVINGS_DEPOSITS = StatKeys.COINAGE_PREFIX + "savings_deposits";
    static final String INTEREST = StatKeys.COINAGE_PREFIX + "interest";
    static final String LOANS = StatKeys.COINAGE_PREFIX + "loans";
    static final String LOANS_REPAID = StatKeys.COINAGE_PREFIX + "loans_repaid";
    static final String LOANS_DEFAULTED = StatKeys.COINAGE_PREFIX + "loans_defaulted";
    static final String BETS = StatKeys.COINAGE_PREFIX + "bets";
    static final String BETS_WON = StatKeys.COINAGE_PREFIX + "bets_won";
    static final String STAKED = StatKeys.COINAGE_PREFIX + "staked";
    static final String LOST = StatKeys.COINAGE_PREFIX + "lost";
    static final String LOTTERY_WINS = StatKeys.COINAGE_PREFIX + "lottery_wins";
    static final String DAILY_BEST_STREAK = StatKeys.COINAGE_PREFIX + "daily_best_streak";
    static final String NOTES_ISSUED = StatKeys.COINAGE_PREFIX + "notes_issued";
    static final String ALLIED_TRADES = StatKeys.COINAGE_PREFIX + "allied_trades";
    static final String WAR_CHEST = StatKeys.COINAGE_PREFIX + "war_chest";

    /** The listing kind Coinage gives an auction in {@link ListingSold#kind()}, next to sale and order. */
    static final String AUCTION = "auction";

    /** The Dominion facts the cross mod feats need. Null when Dominion is not installed. */
    public interface Facts {
        boolean allied(UUID a, UUID b);

        boolean atWar(UUID player);
    }

    private final FeatsTracker tracker;
    private final HytaleLogger logger;
    private final ScheduledExecutorService scheduler;
    private final Facts facts;
    private final Listener listener = new Listener();
    private final Consumer<CoinageApi> enable = this::bind;
    private final Runnable disable = this::unbind;
    private final AtomicBoolean warned = new AtomicBoolean();

    private volatile CoinageApi api;
    private volatile Subscription subscription;
    private volatile ScheduledFuture<?> netWorthTimer;
    /** The primary currency id read on bind, the fallback of {@link #primaryId()}. */
    private volatile String primary = "";
    /** Minor units in one whole coin of each currency read on bind, the fallback of {@link #unit}. */
    private volatile Map<String, Long> units = Map.of();
    private volatile long primaryUnit = 1;
    private volatile boolean closed;
    /** Held across bind, unbind and close, so an enable racing the shutdown cannot subscribe after it. */
    private final Object bindLock = new Object();
    /**
     * Minor units under one whole coin carried per player, counter and currency, so small amounts such as
     * 1 percent interest on 30 coins add up instead of flooring to nothing each time. Memory only, dropped
     * when Coinage unbinds. A negative value is a coin counted early, see {@link #carried}.
     */
    private final Map<String, Long> remainders = new ConcurrentHashMap<>();
    /** Past this many carried remainders the table is cleared, so it cannot grow without end. */
    static final int REMAINDERS_LIMIT = 50_000;

    CoinageFeats(FeatsTracker tracker, HytaleLogger logger, ScheduledExecutorService scheduler, Facts facts) {
        this.tracker = tracker;
        this.logger = logger;
        this.scheduler = scheduler;
        this.facts = facts;
    }

    /** Called by name from {@link SiblingLinks}. The Dominion facts are added only when Dominion is installed too. */
    public static CoinageFeats install(Hooks.Context ctx, ScheduledExecutorService scheduler) {
        Facts facts = ctx.siblings() != null && ctx.siblings().contains(Sibling.DOMINION) ? new CrossModFeats(ctx.logger()) : null;
        return install(ctx.tracker(), ctx.logger(), scheduler, facts);
    }

    static CoinageFeats install(FeatsTracker tracker, HytaleLogger logger, ScheduledExecutorService scheduler, Facts facts) {
        CoinageFeats feats = new CoinageFeats(tracker, logger, scheduler, facts);
        CoinageApi.onDisable(feats.disable);
        CoinageApi.onEnable(feats.enable);
        return feats;
    }

    /** True while Coinage is up and the listener is subscribed. */
    public boolean bound() {
        Subscription s = subscription;
        return api != null && s != null;
    }

    /** The listener Coinage calls, for the tests. */
    EconomyListener listener() {
        return listener;
    }

    /**
     * The Coinage enable hook. Runs under the bind lock, so it cannot interleave with an unbind or the close.
     * The close sets {@code closed} before it waits for the lock, so a bind that was already past the first
     * check sees it after subscribing and lets go of what it just made.
     */
    private void bind(CoinageApi live) {
        Subscription late = null;
        ScheduledFuture<?> lateTimer = null;
        synchronized (bindLock) {
            if (closed) {
                return;
            }
            try {
                Map<String, Long> fresh = new HashMap<>();
                for (Currency c : live.currencies().all()) {
                    fresh.put(c.id(), c.unit());
                }
                Currency p = live.currencies().primary();
                primary = p.id();
                primaryUnit = p.unit();
                units = Map.copyOf(fresh);
                Subscription old = subscription;
                if (old != null) {
                    old.close();
                }
                api = live;
                subscription = live.subscribe(OWNER, listener);
                if (scheduler != null) {
                    ScheduledFuture<?> timer = netWorthTimer;
                    if (timer != null) {
                        timer.cancel(false);
                    }
                    netWorthTimer = scheduler.scheduleWithFixedDelay(this::sampleNetWorth, NET_WORTH_SECONDS, NET_WORTH_SECONDS, TimeUnit.SECONDS);
                }
            } catch (RuntimeException | LinkageError e) {
                warn(e);
            }
            if (closed) {
                // the shutdown began while this bind subscribed
                api = null;
                late = subscription;
                subscription = null;
                lateTimer = netWorthTimer;
                netWorthTimer = null;
            }
        }
        release(late, lateTimer);
    }

    /** The Coinage disable hook. Drops the live API, the timer and the subscription. */
    private void unbind() {
        Subscription s;
        ScheduledFuture<?> timer;
        synchronized (bindLock) {
            api = null;
            timer = netWorthTimer;
            netWorthTimer = null;
            s = subscription;
            subscription = null;
            remainders.clear();
        }
        release(s, timer);
    }

    /** Closes a subscription and cancels a timer outside the lock, so no Coinage call runs while it is held. */
    private static void release(Subscription s, ScheduledFuture<?> timer) {
        if (timer != null) {
            timer.cancel(false);
        }
        if (s != null) {
            try {
                s.close(); // Coinage closes every subscription when it stops, so this is usually a no op
            } catch (RuntimeException | LinkageError ignored) {
                // Coinage is already gone
            }
        }
    }

    /** Removes the hooks, for the Feats shutdown. */
    @Override
    public void close() {
        closed = true; // before the lock, so a bind holding it sees the close once it subscribed
        unbind();
        try {
            CoinageApi.removeHooks(enable, disable);
        } catch (RuntimeException | LinkageError ignored) {
            // Coinage is already gone
        }
    }

    // ---- the timer ----

    /** Reads the net worth of every online player and raises the max, and the War Chest flag with Dominion. */
    void sampleNetWorth() {
        CoinageApi a = api;
        if (a == null || closed) {
            return;
        }
        try {
            String currency = primaryId();
            for (PlayerProgress p : tracker.loadedPlayers()) {
                UUID uuid = p.uuid();
                NetWorth worth = a.reports().netWorth(uuid, currency);
                if (worth == null) {
                    continue;
                }
                long coins = whole(worth.total(), currency);
                if (coins > 0) {
                    tracker.raiseTo(uuid, NET_WORTH_MAX, coins);
                }
                if (facts != null && coins >= WAR_CHEST_COINS && facts.atWar(uuid)) {
                    tracker.raiseTo(uuid, WAR_CHEST, 1);
                }
            }
        } catch (RuntimeException | LinkageError e) {
            warn(e);
        }
    }

    // ---- the writes ----

    /**
     * Whole coins of an amount added to a counter, with the minor units under a coin carried to the next amount
     * of the same player, counter and currency. With {@code firstCoin} an amount that leaves the carry under one
     * coin still counts one coin, and the carry goes negative by the rest of that coin, so later amounts pay it
     * back and the counter stays within one coin of the true total. Received uses it, so any payment above zero
     * gives First Coin.
     */
    long carried(UUID uuid, String key, long minor, String currency, boolean firstCoin) {
        if (uuid == null || minor <= 0) {
            return 0;
        }
        long u = unit(currency);
        if (u <= 1) {
            return minor;
        }
        if (remainders.size() >= REMAINDERS_LIMIT) {
            remainders.clear();
        }
        String id = uuid + "|" + key + "|" + (currency == null ? "" : currency);
        long[] coins = {0};
        remainders.compute(id, (k, old) -> {
            long total = (old == null ? 0 : old) + minor;
            long whole = total > 0 ? total / u : 0; // a negative carry is a coin already counted
            if (whole == 0 && firstCoin && total > 0) {
                whole = 1;
            }
            coins[0] = whole;
            long rest = total - whole * u;
            return rest == 0 ? null : rest;
        });
        return coins[0];
    }

    /** Whole coins of an amount in minor units, floored. An unknown currency uses the primary's unit. */
    long whole(long minor, String currency) {
        if (minor <= 0) {
            return 0;
        }
        long u = unit(currency);
        return u <= 1 ? minor : Math.floorDiv(minor, u);
    }

    /**
     * Minor units in one whole coin. Asked of Coinage each time, since a {@code /coinage reload} can change the
     * decimals and an enable hook does not see that. The table read on bind answers while Coinage is stopping.
     */
    private long unit(String currency) {
        CoinageApi a = api;
        if (a != null) {
            try {
                Optional<Currency> c = a.currencies().resolve(currency);
                if (c.isPresent()) {
                    return c.get().unit();
                }
            } catch (RuntimeException | LinkageError e) {
                warn(e);
            }
        }
        Long cached = currency == null ? null : units.get(currency);
        return cached == null ? primaryUnit : cached;
    }

    /** The id of the primary currency, asked of Coinage each time for the same reason as {@link #unit}. */
    private String primaryId() {
        CoinageApi a = api;
        if (a != null) {
            try {
                return a.currencies().primary().id();
            } catch (RuntimeException | LinkageError e) {
                warn(e);
            }
        }
        return primary;
    }

    /** True for the primary currency, which a blank id also names. */
    private boolean isPrimary(String currency) {
        return currency == null || currency.isBlank() || primaryId().equals(currency);
    }

    /** Adds to a counter for an online or offline player. */
    private void add(UUID uuid, String key, long amount) {
        if (uuid != null && amount > 0) {
            tracker.addOffline(uuid, key, amount);
        }
    }

    /** Raises a record for an online player. Offline players have no live copy to raise. */
    private void raise(UUID uuid, String key, long value) {
        if (uuid != null && value > 0) {
            tracker.raiseTo(uuid, key, value);
        }
    }

    private void warn(Throwable e) {
        if (warned.compareAndSet(false, true)) {
            logger.at(Level.WARNING).withCause(e).log("Feats could not read a Coinage event, so some Coinage feats may miss it");
        }
    }

    private void guard(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException | LinkageError e) {
            warn(e);
        }
    }

    /** The subscribed listener. Each method maps one record to its counters, see the class comment. */
    private final class Listener implements EconomyListener {
        @Override
        public void afterTransaction(Transaction tx) {
            guard(() -> {
                if (tx == null || !isPrimary(tx.currency())) {
                    return;
                }
                // A player account has the player's uuid as its id, so only an online player's account can match
                // a loaded player. Shared accounts have ids Coinage made up and never match.
                UUID to = tx.to();
                if (to != null && tracker.loadedProgress(to) != null) {
                    raise(to, BALANCE_MAX, whole(tx.toAfter(), tx.currency()));
                }
                UUID from = tx.from();
                if (from != null && tx.type() != Transaction.Type.SET && tracker.loadedProgress(from) != null) {
                    raise(from, BALANCE_MAX, whole(tx.fromAfter(), tx.currency()));
                }
            });
        }

        @Override
        public void payment(Payment e) {
            guard(() -> {
                add(e.to(), RECEIVED, carried(e.to(), RECEIVED, e.net(), e.currency(), true));
                add(e.from(), PAID, carried(e.from(), PAID, e.amount(), e.currency(), false));
                add(e.from(), PAYMENTS_SENT, 1);
            });
        }

        @Override
        public void shopSale(ShopSale e) {
            guard(() -> add(e.player(), e.bought() ? SHOP_BOUGHT : SHOP_SOLD, e.quantity()));
        }

        @Override
        public void listingSold(ListingSold e) {
            guard(() -> {
                add(e.seller(), LISTINGS_SOLD, 1);
                if (AUCTION.equals(e.kind())) {
                    add(e.buyer(), AUCTIONS_WON, 1);
                }
            });
        }

        @Override
        public void tradeSettled(TradeSettled e) {
            guard(() -> {
                add(e.a(), TRADES, 1);
                add(e.b(), TRADES, 1);
                if (facts != null && facts.allied(e.a(), e.b())) {
                    add(e.a(), ALLIED_TRADES, 1);
                    add(e.b(), ALLIED_TRADES, 1);
                }
            });
        }

        @Override
        public void savingsMoved(SavingsMoved e) {
            guard(() -> {
                if (e.deposited()) {
                    add(e.player(), SAVINGS_DEPOSITS, 1);
                }
            });
        }

        @Override
        public void interestPaid(InterestPaid e) {
            guard(() -> add(e.player(), INTEREST, carried(e.player(), INTEREST, e.amount(), e.currency(), false)));
        }

        @Override
        public void loanTaken(LoanTaken e) {
            guard(() -> add(e.borrower(), LOANS, 1));
        }

        @Override
        public void loanPaid(LoanPaid e) {
            guard(() -> {
                if (e.remaining() <= 0) {
                    add(e.borrower(), LOANS_REPAID, 1);
                }
            });
        }

        @Override
        public void loanDefaulted(LoanDefaulted e) {
            guard(() -> add(e.borrower(), LOANS_DEFAULTED, 1));
        }

        @Override
        public void betSettled(BetSettled e) {
            guard(() -> {
                add(e.player(), BETS, 1);
                if (e.won()) {
                    add(e.player(), BETS_WON, 1);
                }
                add(e.player(), STAKED, carried(e.player(), STAKED, e.stake(), e.currency(), false));
                long lost = e.stake() - e.payout();
                if (lost > 0) {
                    add(e.player(), LOST, carried(e.player(), LOST, lost, e.currency(), false));
                }
            });
        }

        @Override
        public void lotteryDrawn(LotteryDrawn e) {
            guard(() -> add(e.winner(), LOTTERY_WINS, 1));
        }

        @Override
        public void dailyClaimed(DailyClaimed e) {
            guard(() -> raise(e.player(), DAILY_BEST_STREAK, e.streak()));
        }

        @Override
        public void noteIssued(NoteIssued e) {
            guard(() -> add(e.player(), NOTES_ISSUED, 1));
        }
    }
}
