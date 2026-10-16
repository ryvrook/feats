package dev.ryvrook.feats.placeholders;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.ryvrook.feats.model.FeatRegistry;
import dev.ryvrook.feats.model.Pack;
import dev.ryvrook.feats.model.Sibling;
import dev.ryvrook.feats.stats.WorldStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The pure parts of the 1.7.2 placeholders. The PlaceholderAPI hook itself is not on the test classpath. */
class FeatsExpansionTest {
    private final FeatRegistry registry = new FeatRegistry(EnumSet.of(Sibling.COINAGE, Sibling.CHRONICLE),
            EnumSet.of(Pack.SOCIAL, Pack.CALENDAR));

    @Test
    void packIsCaseBlindAndFalseForOffOrUnknown() {
        assertEquals("true", FeatsExpansion.packOn(registry, "social"));
        assertEquals("true", FeatsExpansion.packOn(registry, "Calendar"));
        assertEquals("true", FeatsExpansion.packOn(registry, "SOCIAL"));
        assertEquals("false", FeatsExpansion.packOn(registry, "grind"));
        assertEquals("false", FeatsExpansion.packOn(registry, "nothing"));
        assertEquals("false", FeatsExpansion.packOn(registry, ""));
        assertEquals("false", FeatsExpansion.packOn(new FeatRegistry(), "social"));
    }

    @Test
    void siblingsAreCommaSeparatedPluginNamesInEnumOrder() {
        assertEquals("Coinage, ChronicleStats", FeatsExpansion.siblingsOf(registry));
        assertEquals("", FeatsExpansion.siblingsOf(new FeatRegistry()));
    }

    @Test
    void serverTotalsReadByKeyAndIgnoreCase() throws Exception {
        WorldStore world = new WorldStore(Files.createTempDirectory("feats-placeholders"), HytaleLogger.forEnclosingClass());
        world.addTotal("blocks_broken", 42L);
        assertEquals(42L, FeatsExpansion.serverTotal(world, "blocks_broken"));
        assertEquals(42L, FeatsExpansion.serverTotal(world, "Blocks_Broken"));
        assertEquals(0L, FeatsExpansion.serverTotal(world, "unknown"));
        assertEquals(0L, FeatsExpansion.serverTotal(null, "blocks_broken"));
    }
}
