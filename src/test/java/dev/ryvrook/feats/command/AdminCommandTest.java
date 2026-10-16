package dev.ryvrook.feats.command;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.Feat;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Pack;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.model.Trigger;
import dev.ryvrook.feats.stats.WorldStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The text the admin sub commands build, apart from the engine command plumbing. */
class AdminCommandTest {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Test
    void anUnknownIdIsStillUnknown() {
        assertEquals("There is no feat with the id nope.", AdminCommand.unknownFeat(new FeatRegistry(), "nope"));
    }

    /** Needs at least one pack feat in the built in list, which the 1.7.2 feat wave adds. */
    @Test
    void grantOfAFeatInAnOffPackSaysThePackIsOff() {
        FeatRegistry everything = new FeatRegistry(EnumSet.allOf(Sibling.class), EnumSet.allOf(Pack.class));
        Optional<Feat> packed = everything.all().stream().filter(f -> f.pack() != null).findFirst();
        assumeTrue(packed.isPresent(), "no pack feats in the registry yet");
        Feat feat = packed.get();
        String message = AdminCommand.unknownFeat(new FeatRegistry(), feat.id());
        assertTrue(message.contains("pack " + feat.pack() + " is off"), message);
        assertTrue(message.contains(feat.id()), message);
    }

    /** Needs at least one sibling feat in the built in list, which the 1.7.2 feat wave adds. */
    @Test
    void grantOfAFeatNeedingAMissingSiblingSaysWhichOne() {
        FeatRegistry everything = new FeatRegistry(EnumSet.allOf(Sibling.class), EnumSet.allOf(Pack.class));
        Optional<Feat> needs = everything.all().stream().filter(f -> !f.requires().isEmpty()).findFirst();
        assumeTrue(needs.isPresent(), "no sibling feats in the registry yet");
        Feat feat = needs.get();
        String message = AdminCommand.unknownFeat(new FeatRegistry(), feat.id());
        assertTrue(message.contains("needs"), message);
        for (Sibling s : feat.requires()) {
            assertTrue(message.contains(s.name()), message);
        }
    }

    @Test
    void reloadListsTheActivePacksInEnumOrder() {
        assertEquals("Packs on: SOCIAL, CALENDAR", AdminCommand.packsLine(new FeatRegistry(EnumSet.noneOf(Sibling.class),
                EnumSet.of(Pack.CALENDAR, Pack.SOCIAL))));
        assertEquals("Packs on: none", AdminCommand.packsLine(new FeatRegistry()));
    }

    @Test
    void totalsListEveryTotalAndEachTotalMilestone() throws Exception {
        FeatRegistry registry = new FeatRegistry();
        WorldStore world = new WorldStore(Files.createTempDirectory("feats-totals"), LOGGER);
        world.addTotal("blocks_broken", 1_500L);
        world.addTotal("zzz_last", 1L);
        List<String> lines = AdminCommand.Totals.totalLines(world, registry);
        assertEquals("Server totals", lines.get(0));
        assertEquals(" blocks_broken  1,500", lines.get(1));
        assertEquals(" zzz_last  1", lines.get(2));
        assertEquals("Total milestones", lines.get(3));
        List<Feat> triggered = registry.totalTriggered();
        assertEquals(4 + Math.max(1, triggered.size()), lines.size());
        if (triggered.isEmpty()) {
            assertEquals(" None on this server.", lines.get(4));
            return;
        }
        for (int i = 0; i < triggered.size(); i++) {
            Feat f = triggered.get(i);
            Trigger t = f.trigger();
            String line = lines.get(4 + i);
            assertTrue(line.startsWith(" " + f.name() + "  " + t.key() + " "), line);
            boolean reached = world.total(t.key()) >= t.target();
            assertTrue(line.endsWith(reached ? "reached" : "not yet"), line);
        }
    }

    @Test
    void aRecordedMilestoneCountsAsReachedEvenWhenTheTotalIsLower() throws Exception {
        FeatRegistry registry = new FeatRegistry();
        List<Feat> triggered = registry.totalTriggered();
        if (triggered.isEmpty()) {
            return; // nothing to check on a registry without total milestones
        }
        Feat f = triggered.getFirst();
        WorldStore world = new WorldStore(Files.createTempDirectory("feats-totals-reached"), LOGGER);
        world.putMilestoneIfAbsent(f.id(), new WorldStore.Entry(1L, null, null));
        List<String> lines = AdminCommand.Totals.totalLines(world, registry);
        String row = lines.stream().filter(l -> l.startsWith(" " + f.name() + "  ")).findFirst().orElseThrow();
        assertTrue(row.endsWith("reached"), row);
        assertFalse(row.contains("not yet"), row);
    }
}
