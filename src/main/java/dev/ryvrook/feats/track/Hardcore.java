package dev.ryvrook.feats.track;

import com.hypixel.hytale.logger.HytaleLogger;

/** Whether the universe runs hardcore mode. Filled in by the 1.7.2 world tracking work, see WorldHooks. */
public final class Hardcore {
    private Hardcore() {
    }

    /** True when the server runs with hardcore lives on. Read once at setup for the Hardcore pack on auto. */
    public static boolean detect(HytaleLogger logger) {
        return false;
    }
}
