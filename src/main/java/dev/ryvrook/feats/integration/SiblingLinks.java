package dev.ryvrook.feats.integration;

import dev.ryvrook.feats.track.Hooks;

/**
 * Starts and stops the bindings to Coinage, Chronicle Stats and Atlas that write sibling counters. Each binding
 * is loaded by name only when its sibling is in {@code ctx.siblings()}, so no class of a missing sibling is
 * touched. Filled in by the 1.7.2 sibling work, see docs/EXPANSION-PLAN.md section 8.
 */
public final class SiblingLinks implements AutoCloseable {
    private SiblingLinks() {
    }

    /** Called from the plugin's start. Never throws. */
    public static SiblingLinks start(Hooks.Context ctx) {
        return new SiblingLinks();
    }

    @Override
    public void close() {
    }
}
