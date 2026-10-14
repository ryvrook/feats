package dev.ryvrook.feats.track;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.stats.ProgressStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/** Builds trackers over the real registry and a throwaway data folder, with no server running. */
final class TestTrackers {
    private TestTrackers() {}

    static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static FeatRegistry registry;

    /** One registry for every test, building it checks every feat definition. */
    static synchronized FeatRegistry registry() {
        if (registry == null) {
            registry = new FeatRegistry();
        }
        return registry;
    }

    static FeatsTracker tracker() {
        try {
            ProgressStore store = new ProgressStore(Files.createTempDirectory("feats-test"), LOGGER);
            return new FeatsTracker(registry(), store, new UnlockNotifier(() -> null, LOGGER), LOGGER, Runnable::run);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
