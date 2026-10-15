package dev.ryvrook.feats.rewards;

import java.math.BigDecimal;
import java.util.List;

/**
 * What one reward hands out. Every part is optional.
 *
 * @param commands    console commands, placeholders filled in
 * @param permissions permission nodes added to the player
 * @param group       permission group the player joins, empty for none
 * @param message     chat line sent to the player, empty for none
 * @param coins       money paid through Coinage in whole units of the currency, such as 25 or 2.50, null for none
 * @param currency    the Coinage currency id, empty for the server's primary currency
 */
public record Reward(List<String> commands, List<String> permissions, String group, String message,
                     BigDecimal coins, String currency) {
    public Reward {
        commands = List.copyOf(commands);
        permissions = List.copyOf(permissions);
        group = group == null ? "" : group.trim();
        message = message == null ? "" : message;
        coins = coins == null || coins.signum() <= 0 ? null : coins;
        currency = currency == null ? "" : currency.trim();
    }

    /** A reward without money. */
    public Reward(List<String> commands, List<String> permissions, String group, String message) {
        this(commands, permissions, group, message, null, "");
    }

    /** True when the reward would do nothing. */
    public boolean isEmpty() {
        return commands.isEmpty() && permissions.isEmpty() && group.isEmpty() && message.isEmpty() && coins == null;
    }
}
