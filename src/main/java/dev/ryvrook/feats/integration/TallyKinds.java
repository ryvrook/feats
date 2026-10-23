package dev.ryvrook.feats.integration;

import dev.ryvrook.feats.track.FeatsTracker;
import dev.ryvrook.trackerapi.api.Subscription;
import dev.ryvrook.trackerapi.api.TrackerApi;
import dev.ryvrook.trackerapi.api.event.ModTally;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors the {@code ModTally} record of TrackerAPI 1.3.0 into the Feats counters, under the keys TrackerAPI's own
 * counter sink writes. {@code amount} is added to {@code <mod>:<stat>} and, with an id, to {@code <mod>:<stat>:<id>},
 * and {@code <mod>:<stat>_best} is raised to {@code best} when it is above zero. Either may be zero. The Angler's
 * Almanac, Arcanum and Ymmersive Melodies bindings publish it for their per id sums and bests, {@code fish:caught:<loot>},
 * {@code arcanum:energy_best}, {@code melodies:band_best}, the feats under {@code model/mods} read them.
 *
 * <p>{@code ModTally} is the one TrackerAPI 1.3.0 type in Feats and lives in this class only, so {@link TrackerBridge}
 * and {@link ModKinds} still load against TrackerAPI 1.2.x and 1.1.x. The bridge touches this class only once
 * {@code api.version()} is at least {@link TrackerBridge#TALLY_KINDS_MINIMUM}. The writes go through the
 * {@link ModKinds.Sink} of the mod mirror. Runs on the TrackerAPI dispatch thread, the tracker is safe from any thread.
 */
final class TallyKinds {
    /** The record kinds subscribed, for the listener count the bridge logs. */
    static final int KINDS = 1;
    /** The suffix of the best a tally raises, {@code <mod>:<stat>_best}. */
    static final String BEST = "_best";

    private final ModKinds.Sink sink;

    TallyKinds(ModKinds.Sink sink) {
        this.sink = sink;
    }

    /** Subscribes the mirror and gives the subscription to close on unbind. */
    static List<Subscription> install(TrackerApi api, String owner, FeatsTracker tracker) {
        return new TallyKinds(ModKinds.sink(tracker)).bind(api, owner);
    }

    List<Subscription> bind(TrackerApi api, String owner) {
        List<Subscription> out = new ArrayList<>(KINDS);
        out.add(api.subscribe(owner, ModTally.class, this::onTally));
        return out;
    }

    void onTally(ModTally e) {
        UUID p = e.player();
        String mod = e.mod();
        String stat = e.stat();
        if (p == null || blank(mod) || blank(stat)) {
            return;
        }
        String key = mod + ':' + stat;
        if (e.amount() > 0) {
            sink.add(p, key, e.amount());
            if (!blank(e.id())) {
                sink.add(p, key + ':' + e.id(), e.amount());
            }
        }
        if (e.best() > 0) {
            sink.raise(p, key + BEST, e.best());
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isEmpty();
    }
}
