package dev.ryvrook.feats.track;

import com.hypixel.hytale.math.util.ChunkUtil;

/**
 * The floor and ceiling of a world column, from the engine's chunk constants. Worlds are
 * {@link ChunkUtil#HEIGHT} blocks tall starting at {@link ChunkUtil#MIN_Y}, so in 0.7.1 the lowest block
 * layer is y 0 and the highest is y 319. A player counts as at the floor or the ceiling within
 * {@link #MARGIN} blocks of it, since feet rest on top of the bottom block and a jump at the top lands
 * a little lower.
 *
 * <p>The height constants are marked deprecated in 0.7.1, ahead of worlds of other heights, but the
 * engine offers nothing else to read yet. They are compile time constants, so a server with a different
 * height needs a rebuild of Feats.
 */
@SuppressWarnings("deprecation")
public final class WorldBounds {
    private WorldBounds() {}

    /** Blocks of slack at either end. */
    public static final double MARGIN = 3.0;
    /** Feet at or below this y count as the bottom of the world. */
    public static final double FLOOR = ChunkUtil.MIN_Y + MARGIN;
    /** Feet at or above this y count as the top of the world. */
    public static final double CEILING = ChunkUtil.MIN_Y + ChunkUtil.HEIGHT - 1 - MARGIN;

    /** True within {@link #MARGIN} blocks above the lowest layer. Falling past it into the void does not count. */
    public static boolean atFloor(double y) {
        return y >= ChunkUtil.MIN_Y && y <= FLOOR;
    }

    public static boolean atCeiling(double y) {
        return y >= CEILING;
    }
}
