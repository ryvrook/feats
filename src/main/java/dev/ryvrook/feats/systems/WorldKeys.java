package dev.ryvrook.feats.systems;

import java.util.Locale;

/** Turns engine ids into the per id counter keys the world hooks write. The TrackerAPI bridge uses it too. */
public final class WorldKeys {
    private WorldKeys() {
    }

    /**
     * The id prefix of a trigger volume, such as {@code Dungeon_Checkpoint} for {@code Dungeon_Checkpoint_3}.
     * The id is instance data, so anything after a dash, a hash, a colon or a space is dropped, and trailing
     * underscore segments that are a number or a hex tag are dropped too. Null for an empty id.
     */
    public static String volumePrefix(String id) {
        if (id == null) {
            return null;
        }
        String s = id.strip();
        int cut = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '-' || c == '#' || c == ':' || Character.isWhitespace(c)) {
                cut = i;
                break;
            }
        }
        s = s.substring(0, cut);
        while (true) {
            int us = s.lastIndexOf('_');
            if (us <= 0) {
                break;
            }
            String tail = s.substring(us + 1);
            if (tail.isEmpty() || isNumber(tail) || isHexTag(tail)) {
                s = s.substring(0, us);
            } else {
                break;
            }
        }
        return s.isEmpty() ? null : s;
    }

    private static boolean isNumber(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Four or more hex characters with at least one digit, which is what generated tags look like. */
    private static boolean isHexTag(String s) {
        if (s.length() < 4) {
            return false;
        }
        boolean digit = false;
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (c >= '0' && c <= '9') {
                digit = true;
            } else if (c < 'a' || c > 'f') {
                return false;
            }
        }
        return digit;
    }

    /** The instance key from a discovery title key, trimmed, null when blank. */
    public static String instanceKey(String titleKey) {
        if (titleKey == null) {
            return null;
        }
        String s = titleKey.strip();
        return s.isEmpty() ? null : s;
    }

    /** A moon phase as its counter id. */
    public static String moonKey(int phase) {
        return Integer.toString(phase);
    }

    /** A world name as the key the per world maps use. */
    static String worldKey(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
