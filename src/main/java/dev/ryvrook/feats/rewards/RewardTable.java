package dev.ryvrook.feats.rewards;

import dev.ryvrook.feats.model.Tier;
import org.bson.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** The parsed contents of {@code Rewards.json}. Immutable, swapped whole on reload. */
public record RewardTable(Map<String, Reward> feats, Map<Tier, Reward> tiers, List<Threshold> points,
                          List<Threshold> count) {

    /** A reward given once when a total reaches {@code at}. */
    public record Threshold(long at, Reward reward) {}

    public static final RewardTable EMPTY = new RewardTable(Map.of(), Map.of(), List.of(), List.of());

    public RewardTable {
        feats = Map.copyOf(feats);
        tiers = tiers.isEmpty() ? Map.of() : new EnumMap<>(tiers);
        points = List.copyOf(points);
        count = List.copyOf(count);
    }

    public boolean isEmpty() {
        return feats.isEmpty() && tiers.isEmpty() && points.isEmpty() && count.isEmpty();
    }

    /** Number of rewards defined, for the load log line. */
    public int size() {
        return feats.size() + tiers.size() + points.size() + count.size();
    }

    /**
     * Parses the file. Unknown feat ids and tier names, and entries without a positive At, are reported
     * through {@code warn} and skipped. Throws on JSON that cannot be parsed at all.
     */
    public static RewardTable parse(String json, Predicate<String> knownFeat, Consumer<String> warn) {
        Document root = Document.parse(json);
        Map<String, Reward> feats = new LinkedHashMap<>();
        if (root.get("Feats") instanceof Document section) {
            for (Map.Entry<String, Object> e : section.entrySet()) {
                if (!(e.getValue() instanceof Document entry)) {
                    warn.accept("Feats entry " + e.getKey() + " is not an object");
                    continue;
                }
                if (!knownFeat.test(e.getKey())) {
                    warn.accept("Feats entry " + e.getKey() + " names no feat");
                    continue;
                }
                addIfUseful(feats, e.getKey(), reward(entry));
            }
        }
        Map<Tier, Reward> tiers = new EnumMap<>(Tier.class);
        if (root.get("Tiers") instanceof Document section) {
            for (Map.Entry<String, Object> e : section.entrySet()) {
                Tier tier;
                try {
                    tier = Tier.valueOf(e.getKey().trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    warn.accept("Tiers entry " + e.getKey() + " is not BRONZE, SILVER, GOLD or PLATINUM");
                    continue;
                }
                if (e.getValue() instanceof Document entry) {
                    Reward reward = reward(entry);
                    if (!reward.isEmpty()) {
                        tiers.put(tier, reward);
                    }
                }
            }
        }
        return new RewardTable(feats, tiers, thresholds(root, "Points", warn), thresholds(root, "Count", warn));
    }

    private static void addIfUseful(Map<String, Reward> into, String key, Reward reward) {
        if (!reward.isEmpty()) {
            into.put(key, reward);
        }
    }

    private static List<Threshold> thresholds(Document root, String name, Consumer<String> warn) {
        List<Threshold> out = new ArrayList<>();
        if (root.get(name) instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Document entry)) {
                    continue;
                }
                long at = entry.get("At") instanceof Number n ? n.longValue() : 0;
                if (at <= 0) {
                    warn.accept(name + " entry without a positive At was skipped");
                    continue;
                }
                Reward reward = reward(entry);
                if (!reward.isEmpty()) {
                    out.add(new Threshold(at, reward));
                }
            }
        }
        out.sort(Comparator.comparingLong(Threshold::at));
        return out;
    }

    private static Reward reward(Document d) {
        return new Reward(strings(d.get("Commands")), strings(d.get("Permissions")),
                d.get("Group") instanceof String g ? g : "",
                d.get("Message") instanceof String m ? m : "",
                amount(d.get("Coins")),
                d.get("Currency") instanceof String c ? c : "");
    }

    /** Coins as a number or a string such as "2.50". Anything else, zero or negative is no money. */
    static BigDecimal amount(Object value) {
        try {
            BigDecimal amount = switch (value) {
                case null -> null;
                case Integer i -> BigDecimal.valueOf(i);
                case Long l -> BigDecimal.valueOf(l);
                case Number n -> new BigDecimal(n.toString());
                case String s when !s.isBlank() -> new BigDecimal(s.trim().replace(",", ""));
                default -> null;
            };
            return amount == null || amount.signum() <= 0 ? null : amount;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> strings(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s && !s.isBlank()) {
                    out.add(s.trim());
                }
            }
        } else if (value instanceof String s && !s.isBlank()) {
            out.add(s.trim());
        }
        return out;
    }

    /** The file written on first start. Valid as it is and gives nothing until filled in. */
    public static final String EXAMPLE = """
            {
              "_readme": "Rewards are handed out only while RewardsEnabled is true in Feats.json. Feats holds rewards for single feats, keyed by feat id. Tiers holds rewards for every unlock of a tier, keyed by BRONZE, SILVER, GOLD or PLATINUM. Points holds rewards for reaching a points total and Count holds rewards for reaching a number of unlocked feats, each entry with an At value. Every reward has Commands, which run as the console, Permissions, which are added to the player, a Group the player is added to and a Message sent to the player in chat. With Coinage installed a reward can also pay Coins, an amount in whole units such as 25 or 2.50, in the Currency named or the primary one when Currency is empty. Leave a field empty to skip it. Commands and messages can use {player} for the player name, {uuid}, {feat} for the feat id, {name} for the feat name, {tier} and {points} for the player's total after the unlock. Each reward is given once per player and remembered in their player file. That record survives /feats reset, so a player cannot reset to earn the same rewards again. Unlocks found when a player joins count as real unlocks and get their rewards too. Admin grants give rewards only with --rewards. The _example section shows the shape and is never read. Run /feats admin reload after editing.",
              "Feats": {},
              "Tiers": {},
              "Points": [],
              "Count": [],
              "_example": {
                "Feats": {
                  "digger": {"Commands": ["give {player} Tool_Pickaxe_Iron"], "Permissions": [], "Group": "", "Message": "Your {name} reward is in your inventory"}
                },
                "Tiers": {
                  "GOLD": {"Coins": 50, "Currency": "", "Message": "50 coins for a Gold feat"},
                  "PLATINUM": {"Commands": [], "Permissions": [], "Group": "", "Message": "Platinum feat earned, you now have {points} points"}
                },
                "Points": [
                  {"At": 1000, "Commands": [], "Permissions": ["example.kit.veteran"], "Group": "", "Message": "You reached {points} points"}
                ],
                "Count": [
                  {"At": 50, "Commands": [], "Permissions": [], "Group": "Veteran", "Message": "Fifty feats unlocked"}
                ]
              }
            }
            """;
}
