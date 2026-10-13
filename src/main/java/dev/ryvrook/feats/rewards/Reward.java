package dev.ryvrook.feats.rewards;

import java.util.List;

/**
 * What one reward hands out. Every part is optional.
 *
 * @param commands    console commands, placeholders filled in
 * @param permissions permission nodes added to the player
 * @param group       permission group the player joins, empty for none
 * @param message     chat line sent to the player, empty for none
 */
public record Reward(List<String> commands, List<String> permissions, String group, String message) {
    public Reward {
        commands = List.copyOf(commands);
        permissions = List.copyOf(permissions);
        group = group == null ? "" : group.trim();
        message = message == null ? "" : message;
    }

    /** True when the reward would do nothing. */
    public boolean isEmpty() {
        return commands.isEmpty() && permissions.isEmpty() && group.isEmpty() && message.isEmpty();
    }
}
