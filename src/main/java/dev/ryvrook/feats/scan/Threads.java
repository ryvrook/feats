package dev.ryvrook.feats.scan;

import com.hypixel.hytale.server.core.universe.world.World;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Hands one task to a world thread and waits a bounded time for it. Only ever called on the Feats IO thread. */
final class Threads {
    static final long TIMEOUT_SECONDS = 10;

    private Threads() {
    }

    static <T> T onWorld(World world, Supplier<T> task) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        world.execute(() -> {
            try {
                result.complete(task.get());
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        return result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
