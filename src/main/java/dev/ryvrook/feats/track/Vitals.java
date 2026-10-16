package dev.ryvrook.feats.track;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Edge detection over the once a second health, oxygen and mana reads. A close call is health under
 * 10 percent followed by health over 25 percent without reaching zero in between. Oxygen low is a
 * sample under 10 percent followed by one where it rose. Mana emptied is mana reaching zero from above
 * zero. A missing stat is passed as a negative value and leaves its edge alone.
 */
public final class Vitals {
    /** What one sample found. */
    public record Events(boolean closeCall, boolean oxygenLow, boolean manaEmptied) {
        static final Events NONE = new Events(false, false, false);
    }

    private static final class State {
        boolean healthLow;
        boolean oxygenLow;
        float lastOxygen = -1f;
        float lastMana = -1f;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    /** Feeds one sample. Each value is the current amount and its maximum, negative when unknown. */
    public Events sample(UUID player, float health, float healthMax, float oxygen, float oxygenMax, float mana, float manaMax) {
        if (player == null) {
            return Events.NONE;
        }
        State s = states.computeIfAbsent(player, p -> new State());
        boolean closeCall = false;
        if (health >= 0f && healthMax > 0f) {
            float pct = health / healthMax;
            if (health <= 0f) {
                s.healthLow = false; // dead, the next life starts clean
            } else if (pct < 0.10f) {
                s.healthLow = true;
            } else if (s.healthLow && pct > 0.25f) {
                s.healthLow = false;
                closeCall = true;
            }
        }
        boolean oxygenLow = false;
        if (oxygen >= 0f && oxygenMax > 0f) {
            float pct = oxygen / oxygenMax;
            if (s.oxygenLow && s.lastOxygen >= 0f && pct > s.lastOxygen) {
                s.oxygenLow = false;
                oxygenLow = true;
            } else if (pct < 0.10f) {
                s.oxygenLow = true;
            }
            s.lastOxygen = pct;
        }
        boolean manaEmptied = false;
        if (mana >= 0f && manaMax > 0f) {
            if (s.lastMana > 0f && mana <= 0f) {
                manaEmptied = true;
            }
            s.lastMana = mana;
        }
        return closeCall || oxygenLow || manaEmptied ? new Events(closeCall, oxygenLow, manaEmptied) : Events.NONE;
    }

    /** The player left. */
    public void forget(UUID player) {
        if (player != null) {
            states.remove(player);
        }
    }
}
