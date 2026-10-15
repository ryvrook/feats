package dev.ryvrook.feats.rewards;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.ProgressStore;
import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.feats.track.UnlockNotifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Coins rewards that come due while Coinage is reloading wait for it, across a Feats restart too. */
class WaitingCoinsTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** A Coinage link that can be switched off, recording what it paid. */
    private static final class Payer implements CoinPayer {
        volatile boolean up;
        final List<String> paid = new ArrayList<>();

        @Override
        public String pay(UUID player, String currency, BigDecimal amount, String reason) {
            if (!up) {
                return NOT_RUNNING;
            }
            paid.add(player + " " + amount.toPlainString() + " " + reason);
            return null;
        }

        @Override
        public boolean ready() {
            return up;
        }
    }

    private static RewardService service(Path dir) {
        ProgressStore store = new ProgressStore(dir, LOGGER);
        FeatsTracker tracker = new FeatsTracker(new FeatRegistry(), store, new UnlockNotifier(() -> null, LOGGER),
                LOGGER, Runnable::run);
        return new RewardService(() -> null, tracker, dir, LOGGER);
    }

    @Test
    void aPaymentWhileCoinageIsAwayWaitsAndIsPaidOnceAfterARestart() throws Exception {
        Path dir = Files.createTempDirectory("feats-coins");
        Payer payer = new Payer();
        RewardService first = service(dir);
        first.setCoinPayer(payer);
        UUID player = UUID.randomUUID();
        first.payCoins(new WaitingCoins.Payment(player, "", new BigDecimal("25"), "feats:digger"));
        assertTrue(payer.paid.isEmpty());
        assertEquals(1, first.coinsWaiting());

        first.stop(); // Coinage still away, so the payment goes to the file
        assertTrue(Files.exists(dir.resolve(RewardService.WAITING_FILE)));
        assertEquals(0, first.coinsWaiting());

        RewardService second = service(dir);
        second.setCoinPayer(payer);
        second.loadWaiting();
        assertFalse(Files.exists(dir.resolve(RewardService.WAITING_FILE)), "read once, never twice");
        assertEquals(1, second.coinsWaiting());
        payer.up = true;
        second.stop(); // with Coinage up the stop pays what waits instead of writing it
        assertEquals(List.of(player + " 25 feats:digger"), payer.paid);
        assertFalse(Files.exists(dir.resolve(RewardService.WAITING_FILE)));

        RewardService third = service(dir);
        third.setCoinPayer(payer);
        third.loadWaiting();
        assertEquals(0, third.coinsWaiting());
        assertEquals(1, payer.paid.size());
    }

    @Test
    void aRefusedPaymentDoesNotWait() throws Exception {
        Path dir = Files.createTempDirectory("feats-coins");
        RewardService rewards = service(dir);
        rewards.setCoinPayer((player, currency, amount, reason) -> "Coinage has no currency " + currency);
        rewards.payCoins(new WaitingCoins.Payment(UUID.randomUUID(), "gems", BigDecimal.TEN, "feats:x"));
        assertEquals(0, rewards.coinsWaiting());
    }

    @Test
    void theWaitingListIsBounded() {
        WaitingCoins waiting = new WaitingCoins();
        WaitingCoins.Payment p = new WaitingCoins.Payment(UUID.randomUUID(), "", BigDecimal.ONE, "feats:x");
        for (int i = 0; i < WaitingCoins.LIMIT; i++) {
            assertTrue(waiting.add(p));
        }
        assertFalse(waiting.add(p));
        assertEquals(WaitingCoins.LIMIT, waiting.size());
        assertEquals(WaitingCoins.LIMIT, waiting.takeAll().size());
        assertTrue(waiting.isEmpty());
    }
}
