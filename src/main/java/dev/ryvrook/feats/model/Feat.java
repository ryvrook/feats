package dev.ryvrook.feats.model;

import dev.ryvrook.feats.stats.PlayerProgress;

import java.util.List;

/**
 * Definition of one achievement.
 *
 * @param id          stable identifier persisted in player files
 * @param name        display name
 * @param description what the player has to do
 * @param category    browser tab
 * @param tier        rarity / point value
 * @param icon        vanilla item id used for the toast and browser icon
 * @param requirements every requirement must be met to unlock, empty for world and first feats
 * @param hidden      hidden feats show as "???" until unlocked
 * @param scope       personal, world milestone or server first
 * @param trigger     what raises a world or first feat, null for personal feats
 */
public record Feat(
        String id,
        String name,
        String description,
        Category category,
        Tier tier,
        String icon,
        List<Requirement> requirements,
        boolean hidden,
        Scope scope,
        Trigger trigger
) {
    public Feat {
        requirements = List.copyOf(requirements);
        if (scope == null) {
            scope = Scope.PERSONAL;
        }
        if (scope == Scope.PERSONAL) {
            if (requirements.isEmpty()) {
                throw new IllegalArgumentException("feat " + id + " has no requirements");
            }
            if (trigger != null) {
                throw new IllegalArgumentException("personal feat " + id + " cannot have a trigger");
            }
        } else {
            if (!requirements.isEmpty()) {
                throw new IllegalArgumentException(scope + " feat " + id + " cannot have requirements");
            }
            if (trigger == null) {
                throw new IllegalArgumentException(scope + " feat " + id + " needs a trigger");
            }
            if (scope == Scope.FIRST && trigger.kind() != Trigger.Kind.AFTER) {
                throw new IllegalArgumentException("first feat " + id + " must follow a personal feat");
            }
        }
    }

    /** A personal feat, as every feat was before world and first feats. */
    public Feat(String id, String name, String description, Category category, Tier tier, String icon,
                List<Requirement> requirements, boolean hidden) {
        this(id, name, description, category, tier, icon, requirements, hidden, Scope.PERSONAL, null);
    }

    /** True for feats each player earns from their own counters. */
    public boolean isPersonal() {
        return scope == Scope.PERSONAL;
    }

    /** Sum of every requirement target, the denominator of the progress bar. */
    public long totalTarget() {
        long total = 0;
        for (Requirement r : requirements) {
            total += r.target();
        }
        return total;
    }

    /** Sum of clamped current values, the numerator of the progress bar. */
    public long currentProgress(PlayerProgress progress) {
        long total = 0;
        for (Requirement r : requirements) {
            total += Math.min(progress.get(r.key()), r.target());
        }
        return total;
    }

    public double fraction(PlayerProgress progress) {
        long target = totalTarget();
        return target == 0 ? (isPersonal() ? 1.0 : 0.0) : (double) currentProgress(progress) / (double) target;
    }

    /** World and first feats are never satisfied by counters, only their trigger unlocks them. */
    public boolean isSatisfied(PlayerProgress progress) {
        if (!isPersonal()) {
            return false;
        }
        // Indexed rather than for each, this runs for every watched counter bump and should not allocate.
        for (int i = 0, n = requirements.size(); i < n; i++) {
            Requirement r = requirements.get(i);
            if (progress.get(r.key()) < r.target()) {
                return false;
            }
        }
        return true;
    }

    /** True when a single counter drives this feat, so "x / y" labels make sense. */
    public boolean isSingleCounter() {
        return requirements.size() == 1;
    }
}
