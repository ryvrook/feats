package dev.ryvrook.feats.rewards;

import dev.ryvrook.feats.model.Tier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardTableTest {
    @Test
    void coinsArePerFeatPerTierAndPerThreshold() {
        String json = """
                {"Feats": {"digger": {"Coins": 25}},
                 "Tiers": {"GOLD": {"Coins": "2.50", "Currency": "gems"}},
                 "Points": [{"At": 100, "Coins": 1.5}],
                 "Count": []}
                """;
        List<String> warnings = new ArrayList<>();
        RewardTable table = RewardTable.parse(json, id -> true, warnings::add);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals(0, new BigDecimal("25").compareTo(table.feats().get("digger").coins()));
        assertEquals("", table.feats().get("digger").currency());
        Reward gold = table.tiers().get(Tier.GOLD);
        assertEquals(0, new BigDecimal("2.50").compareTo(gold.coins()));
        assertEquals("gems", gold.currency());
        assertEquals(0, new BigDecimal("1.5").compareTo(table.points().get(0).reward().coins()));
    }

    @Test
    void zeroNegativeAndJunkCoinsAreNoMoney() {
        assertNull(RewardTable.amount(0));
        assertNull(RewardTable.amount(-5));
        assertNull(RewardTable.amount("lots"));
        assertNull(RewardTable.amount(""));
        assertNull(RewardTable.amount(List.of()));
        assertEquals(0, new BigDecimal("1000").compareTo(RewardTable.amount("1,000")));
        RewardTable table = RewardTable.parse("{\"Feats\": {\"digger\": {\"Coins\": 0}}}", id -> true, w -> {});
        assertTrue(table.isEmpty(), "a reward whose only part is zero coins does nothing");
    }

    @Test
    void theTemplateStillParsesToNothing() {
        RewardTable table = RewardTable.parse(RewardTable.EXAMPLE, id -> true, w -> {});
        assertTrue(table.isEmpty());
        assertFalse(new Reward(List.of(), List.of(), "", "", new BigDecimal("3"), "").isEmpty());
    }
}
