package dev.ryvrook.feats.export;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.Tier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Posts unlocks to a Discord webhook as embeds. Off unless {@code DiscordWebhookUrl} is set.
 *
 * <p>{@link #post} only queues and never blocks. Requests are started one at a time from the Feats IO
 * thread without waiting on the answer, with a 10 second timeout and at most one every 2 seconds. The queue holds 50 posts, and anything beyond
 * that is dropped with one log line per minute. A 429 answer pauses posting for the Retry-After time
 * and the same post is tried again. The webhook URL is never logged.
 */
public final class DiscordWebhook {
    static final int QUEUE_LIMIT = 50;
    static final long GAP_MILLIS = 2_000L;
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long LOG_EVERY_MILLIS = 60_000L;
    private static final long DEFAULT_RETRY_MILLIS = 5_000L;
    private static final long MAX_RETRY_MILLIS = 10L * 60_000L;
    private static final Pattern RETRY_IN_BODY = Pattern.compile("\"retry_after\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

    private final ScheduledExecutorService io;
    private final HytaleLogger logger;
    private final LinkedBlockingDeque<Post> queue = new LinkedBlockingDeque<>(QUEUE_LIMIT);
    private final AtomicBoolean pumpQueued = new AtomicBoolean();
    /** Earliest time the next request may go out. Written when an answer arrives, read before each pump. */
    private volatile long nextAt;
    private HttpClient client;

    private long dropped;
    private long lastDropLogAt;
    private long lastProblemLogAt;

    private record Post(String url, String body) {
    }

    public DiscordWebhook(ScheduledExecutorService io, HytaleLogger logger) {
        this.io = io;
        this.logger = logger;
    }

    /** Queues one post. Safe from any thread and never blocks. */
    public void post(String url, String body) {
        if (url == null || url.isBlank() || body == null) {
            return;
        }
        if (!queue.offerLast(new Post(url, body))) {
            noteDropped();
        }
        schedulePump();
    }

    /** Posts waiting to be sent. */
    public int pending() {
        return queue.size();
    }

    private void schedulePump() {
        if (!pumpQueued.compareAndSet(false, true)) {
            return;
        }
        long delay = Math.max(0, nextAt - System.currentTimeMillis());
        try {
            io.schedule(this::pump, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            pumpQueued.set(false); // shutting down, the rest is dropped
        }
    }

    /**
     * Runs on the IO thread. Starts one post without waiting for the answer, so a slow or unreachable
     * webhook never holds up the file saves that share this thread. The next post is lined up once the
     * answer arrives.
     */
    private void pump() {
        boolean sent = false;
        try {
            Post post = queue.pollFirst();
            if (post != null) {
                sent = send(post);
            }
        } catch (Throwable t) {
            problem("Feats Discord post failed with " + t.getClass().getSimpleName());
        } finally {
            if (!sent) {
                next();
            }
        }
    }

    /** Lets the next pump be scheduled, and schedules it when posts are waiting. */
    private void next() {
        pumpQueued.set(false);
        if (!queue.isEmpty()) {
            schedulePump();
        }
    }

    /** Returns true when a request is in flight, whose completion then calls {@link #next}. */
    private boolean send(Post post) {
        URI uri;
        try {
            uri = URI.create(post.url());
        } catch (IllegalArgumentException e) {
            uri = null;
        }
        String scheme = uri == null ? null : uri.getScheme();
        if (uri == null || uri.getHost() == null || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
            problem("Feats Discord webhook URL is not a valid http or https address, check DiscordWebhookUrl");
            return false;
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Feats")
                .POST(HttpRequest.BodyPublishers.ofString(post.body(), StandardCharsets.UTF_8))
                .build();
        client().sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, error) -> {
                    try {
                        answered(post, response, error);
                    } catch (Throwable t) {
                        problem("Feats Discord post failed with " + t.getClass().getSimpleName());
                    } finally {
                        next();
                    }
                });
        return true;
    }

    /** Handles the answer to one post, on the HTTP client's thread. */
    private void answered(Post post, HttpResponse<String> response, Throwable error) {
        long now = System.currentTimeMillis();
        if (error != null || response == null) {
            // Only the exception type, because some messages carry the address.
            Throwable cause = error != null && error.getCause() != null ? error.getCause() : error;
            nextAt = now + GAP_MILLIS;
            problem("Feats Discord post failed with " + (cause == null ? "no answer" : cause.getClass().getSimpleName()));
            return;
        }
        int status = response.statusCode();
        if (status == 429) {
            long wait = retryAfterMillis(response);
            nextAt = now + Math.max(GAP_MILLIS, wait);
            if (!queue.offerFirst(post)) {
                noteDropped();
            }
            problem("Feats Discord webhook is rate limited, waiting " + Math.max(1, (wait + 999) / 1000) + " seconds");
            return;
        }
        nextAt = now + GAP_MILLIS;
        if (status / 100 != 2) {
            problem("Feats Discord webhook answered with status " + status);
        }
    }

    private HttpClient client() {
        if (client == null) {
            client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
        }
        return client;
    }

    /** Seconds from the Retry-After header, or from the JSON body Discord sends, as milliseconds. */
    static long retryAfterMillis(HttpResponse<String> response) {
        Optional<String> header = response.headers().firstValue("Retry-After");
        if (header.isPresent()) {
            long ms = secondsToMillis(header.get());
            if (ms >= 0) {
                return ms;
            }
        }
        String body = response.body();
        if (body != null) {
            Matcher m = RETRY_IN_BODY.matcher(body);
            if (m.find()) {
                long ms = secondsToMillis(m.group(1));
                if (ms >= 0) {
                    return ms;
                }
            }
        }
        return DEFAULT_RETRY_MILLIS;
    }

    private static long secondsToMillis(String value) {
        try {
            double seconds = Double.parseDouble(value.trim());
            if (seconds < 0 || Double.isNaN(seconds)) {
                return -1;
            }
            return Math.min(MAX_RETRY_MILLIS, (long) Math.ceil(seconds * 1000.0));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private synchronized void noteDropped() {
        dropped++;
        long now = System.currentTimeMillis();
        if (now - lastDropLogAt >= LOG_EVERY_MILLIS) {
            lastDropLogAt = now;
            logger.at(Level.WARNING).log("Feats Discord queue is full and posts are being dropped, %d so far", dropped);
        }
    }

    private synchronized void problem(String line) {
        long now = System.currentTimeMillis();
        if (now - lastProblemLogAt >= LOG_EVERY_MILLIS) {
            lastProblemLogAt = now;
            logger.at(Level.WARNING).log("%s", line);
        }
    }

    /** True when the tier is at or above the minimum. An unknown minimum falls back to Gold. */
    public static boolean meetsTier(Tier tier, String minimum) {
        Tier min;
        try {
            min = Tier.valueOf(minimum == null ? "GOLD" : minimum.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            min = Tier.GOLD;
        }
        return tier.ordinal() >= min.ordinal();
    }

    /**
     * The webhook body for one unlock. Title with player and feat, the feat description, the tier
     * colour and a footer with the points and the rarity when known. Mentions are switched off so a
     * player name can never ping anyone.
     */
    public static String embed(String playerName, Feat feat, double rarity, int rareBelowPercent) {
        String footer = feat.tier().displayName() + ", " + feat.tier().points() + " points";
        String tag = Rarity.tag(rarity, rareBelowPercent);
        if (!tag.isEmpty()) {
            footer += ", " + tag;
        }
        return "{\"allowed_mentions\": {\"parse\": []}, \"embeds\": [{"
                + "\"title\": " + LeaderboardExporter.quote(playerName + " unlocked " + feat.name())
                + ", \"description\": " + LeaderboardExporter.quote(feat.description())
                + ", \"color\": " + colour(feat.tier())
                + ", \"footer\": {\"text\": " + LeaderboardExporter.quote(footer) + "}"
                + "}]}";
    }

    static int colour(Tier tier) {
        try {
            return Integer.parseInt(tier.color().replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return 0xE8A93B;
        }
    }
}
