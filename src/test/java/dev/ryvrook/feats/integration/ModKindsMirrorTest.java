package dev.ryvrook.feats.integration;

import dev.ryvrook.trackerapi.api.Actor;
import dev.ryvrook.trackerapi.api.Header;
import dev.ryvrook.trackerapi.api.event.DungeonRun;
import dev.ryvrook.trackerapi.api.event.EntityDied;
import dev.ryvrook.trackerapi.api.event.ModAction;
import dev.ryvrook.trackerapi.api.event.ModMilestone;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bests TrackerAPI's bindings write straight into its counters (relic kills, the parry streak, the Endgame
 * wave and combo bests) are read back into Feats when an event of that mod arrives.
 */
class ModKindsMirrorTest {
    private static final UUID P = UUID.fromString("00000000-0000-0000-0000-000000000401");

    private final List<String> raised = new ArrayList<>();
    private final Map<String, Long> counters = new HashMap<>();
    private final Set<String> bound = new java.util.HashSet<>();

    private final ModKinds.Sink sink = new ModKinds.Sink() {
        @Override
        public void add(UUID player, String key, long amount) {
        }

        @Override
        public void raise(UUID player, String key, long value) {
            raised.add(key + "=" + value);
        }

        @Override
        public void discover(UUID player, String prefix, String id, String total) {
        }
    };

    private final ModKinds.Reads reads = new ModKinds.Reads() {
        @Override
        public long get(UUID player, String key) {
            return counters.getOrDefault(key, 0L);
        }

        @Override
        public Map<String, Long> all(UUID player) {
            return counters;
        }

        @Override
        public boolean bound(String mod) {
            return bound.contains(mod);
        }
    };

    private final ModKinds kinds = new ModKinds(sink, reads);

    private static Header h() {
        return new Header(1L, 1L, "default", P);
    }

    @Test
    void relicKillsAreMirroredOnAKillWhileTheRelicsBindingIsBound() {
        counters.put("relics:kills", 7L);
        counters.put("relics:kills:Wanmine_Weapon_Eclipsebound_Dagger", 3L);
        counters.put("relics:abilities", 9L);
        counters.put("kills", 50L);
        EntityDied kill = new EntityDied(h(), new Actor(Actor.Kind.CREATURE, UUID.randomUUID(), "Goblin", "Goblin_Scrapper"),
                new Actor(Actor.Kind.PLAYER, P, "Alder", null), null, null, false, false, 0, 0, 0, false, List.of(), Set.of(),
                false, null, 0);
        kinds.onDeath(kill);
        assertTrue(raised.isEmpty(), "nothing while the relics binding is not bound");
        bound.add("relics");
        kinds.onDeath(kill);
        assertEquals(List.of("relics:kills=7", "relics:kills:Wanmine_Weapon_Eclipsebound_Dagger=3").size(), raised.size());
        assertTrue(raised.contains("relics:kills=7"));
        assertTrue(raised.contains("relics:kills:Wanmine_Weapon_Eclipsebound_Dagger=3"));
        raised.clear();
        EntityDied byCreature = new EntityDied(h(), new Actor(Actor.Kind.PLAYER, P, "Alder", null),
                new Actor(Actor.Kind.CREATURE, UUID.randomUUID(), "Goblin", "Goblin_Scrapper"), null, null, false, false,
                0, 0, 0, false, List.of(), Set.of(), false, null, 0);
        kinds.onDeath(byCreature);
        assertTrue(raised.isEmpty(), "a creature kill mirrors nothing");
    }

    @Test
    void theParryStreakFollowsAParry() {
        counters.put("parries:streak_best", 4L);
        kinds.onAction(new ModAction(h(), "parries", "parry", false, "Goblin_Scrapper", 0));
        assertTrue(raised.contains("parries:streak_best=4"));
        raised.clear();
        kinds.onAction(new ModAction(h(), "zephyr", "dodge", false, null, 0));
        assertTrue(raised.isEmpty(), "a zephyr move reads no parry streak");
    }

    @Test
    void endgameBestsFollowAMilestone() {
        counters.put("endgame:wave_best", 12L);
        counters.put("endgame:combo_best", 30L);
        kinds.onMilestone(new ModMilestone(h(), "endgame", "wave", "frozen_trial/12", null));
        assertTrue(raised.contains("endgame:wave_best=12"));
        assertTrue(raised.contains("endgame:combo_best=30"));
        raised.clear();
        counters.put("endgame:wave_best", 0L);
        kinds.onMilestone(new ModMilestone(h(), "endgame", "achievement", "a1", null));
        assertTrue(raised.contains("endgame:combo_best=30"));
        assertTrue(raised.stream().noneMatch(r -> r.startsWith("endgame:wave_best")), "a zero best is not raised");
    }

    @Test
    void withoutReadsNothingIsMirrored() {
        ModKinds plain = new ModKinds(sink);
        counters.put("parries:streak_best", 4L);
        plain.onAction(new ModAction(h(), "parries", "parry", false, null, 0));
        assertTrue(raised.isEmpty());
        plain.onDungeonRun(new DungeonRun(h(), "endgame", "arena", DungeonRun.Phase.COMPLETED, null, 1, 1000));
    }
}
