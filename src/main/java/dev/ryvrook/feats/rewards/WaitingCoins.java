package dev.ryvrook.feats.rewards;

import org.bson.Document;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coins rewards that came due while Coinage was stopped or in the middle of a reload. The reward is already
 * recorded as given, so the money waits here and is paid once Coinage is back. A Feats shutdown writes what
 * still waits to a file in the data folder and the next start reads it back, which covers the reload Coinage
 * does of Feats a few seconds after its own. Safe from any thread.
 */
final class WaitingCoins {
    /** Payments kept at most. Past this a new one is dropped and logged. */
    static final int LIMIT = 10_000;

    record Payment(UUID player, String currency, BigDecimal amount, String reason) {}

    private final Queue<Payment> waiting = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();

    /** Keeps one payment for later. False when {@link #LIMIT} are already waiting. */
    boolean add(Payment payment) {
        if (size.incrementAndGet() > LIMIT) {
            size.decrementAndGet();
            return false;
        }
        waiting.add(payment);
        return true;
    }

    /** Takes every waiting payment out, each exactly once. */
    List<Payment> takeAll() {
        List<Payment> out = new ArrayList<>();
        Payment next;
        while ((next = waiting.poll()) != null) {
            size.decrementAndGet();
            out.add(next);
        }
        return out;
    }

    boolean isEmpty() {
        return waiting.isEmpty();
    }

    int size() {
        return Math.max(0, size.get());
    }

    /**
     * Writes the waiting payments to {@code file} and takes them out, or deletes the file when none wait.
     * Returns how many were written.
     */
    int save(Path file) throws IOException {
        List<Payment> all = takeAll();
        if (all.isEmpty()) {
            Files.deleteIfExists(file);
            return 0;
        }
        List<Document> rows = new ArrayList<>(all.size());
        for (Payment p : all) {
            rows.add(new Document("Player", p.player().toString())
                    .append("Currency", p.currency())
                    .append("Coins", p.amount().toPlainString())
                    .append("Reason", p.reason()));
        }
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, new Document("Payments", rows).toJson(), StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        return all.size();
    }

    /**
     * Reads payments a shutdown left in {@code file}, adds them and deletes the file. Rows that do not parse are
     * skipped. Returns how many were added.
     */
    int load(Path file) throws IOException {
        if (!Files.exists(file)) {
            return 0;
        }
        Document doc = Document.parse(Files.readString(file, StandardCharsets.UTF_8));
        int added = 0;
        if (doc.get("Payments") instanceof List<?> rows) {
            for (Object row : rows) {
                Payment p = row instanceof Document d ? parse(d) : null;
                if (p != null && add(p)) {
                    added++;
                }
            }
        }
        Files.delete(file);
        return added;
    }

    private static Payment parse(Document d) {
        try {
            UUID player = UUID.fromString(String.valueOf(d.get("Player")));
            BigDecimal amount = RewardTable.amount(d.get("Coins"));
            if (amount == null) {
                return null;
            }
            String currency = d.get("Currency") instanceof String c ? c : "";
            String reason = d.get("Reason") instanceof String r ? r : "feats";
            return new Payment(player, currency, amount, reason);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
