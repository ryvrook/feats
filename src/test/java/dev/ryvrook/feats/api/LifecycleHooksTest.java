package dev.ryvrook.feats.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleHooksTest {
    private final List<String> seen = new ArrayList<>();
    private final Consumer<FeatsApi> enable = api -> seen.add("enable");
    private final Runnable disable = () -> seen.add("disable");

    @AfterEach
    void removeHooks() {
        FeatsApi.removeHooks(enable, disable);
    }

    @Test
    void hooksRunOnEveryStartAndStop() throws Exception {
        FeatsApi.onEnable(enable);
        FeatsApi.onDisable(disable);
        assertTrue(seen.isEmpty(), "Feats has not started, so nothing runs yet");

        FeatsApi first = Stand.in();
        FeatsApi.enable(first);
        assertTrue(FeatsApi.isEnabled());
        FeatsApi.disable(first);
        assertFalse(FeatsApi.isEnabled());
        FeatsApi second = Stand.in();
        FeatsApi.enable(second);
        FeatsApi.disable(second);

        assertEquals(List.of("enable", "disable", "enable", "disable"), seen);
    }

    @Test
    void aHookAddedAfterStartRunsAtOnce() throws Exception {
        FeatsApi api = Stand.in();
        FeatsApi.enable(api);
        try {
            FeatsApi.onEnable(enable);
            assertEquals(List.of("enable"), seen);
        } finally {
            FeatsApi.disable(api);
        }
    }

    @Test
    void anOldInstanceStoppingLeavesTheNewOneAlone() throws Exception {
        FeatsApi.onDisable(disable);
        FeatsApi old = Stand.in();
        FeatsApi now = Stand.in();
        FeatsApi.enable(now);
        try {
            FeatsApi.disable(old);
            assertTrue(FeatsApi.isEnabled());
            assertTrue(seen.isEmpty());
        } finally {
            FeatsApi.disable(now);
        }
    }

    @Test
    void aFailingHookDoesNotStopTheOthers() throws Exception {
        Consumer<FeatsApi> broken = api -> {
            throw new IllegalStateException("broken on purpose");
        };
        FeatsApi.onEnable(broken);
        FeatsApi.onEnable(enable);
        FeatsApi api = Stand.in();
        try {
            FeatsApi.enable(api);
            assertEquals(List.of("enable"), seen);
        } finally {
            FeatsApi.disable(api);
            FeatsApi.removeHooks(broken, null);
        }
    }

    @Test
    void theApiVersionCountsTheHooks() {
        assertEquals(2, FeatsApi.API_VERSION);
    }

    /** FeatsApi objects without a plugin behind them. The hooks never read the plugin. */
    private static final class Stand {
        static FeatsApi in() throws Exception {
            var unsafe = Class.forName("sun.misc.Unsafe");
            var field = unsafe.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Object u = field.get(null);
            return (FeatsApi) unsafe.getMethod("allocateInstance", Class.class).invoke(u, FeatsApi.class);
        }
    }
}
