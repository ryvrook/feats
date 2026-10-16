package dev.ryvrook.feats.track;

/**
 * Turns the per tick movement flags the client reports into counts. A jump, roll, mantle or glide counts
 * once when its flag goes from false to true. A double jump counts when the extra jumps used goes up.
 * Climbing, sprinting and crouching add up the tick length while the flag is on. A glide also keeps its
 * own running length so the longest glide can be recorded when it ends. A fall remembers the height it
 * started at, and ending it in fluid from {@link #WATER_LANDING_METRES} or more up is a water landing.
 *
 * <p>Everything lives in a {@link State} of primitive fields, one per player, and {@link #step} touches
 * nothing else, so a tick costs a handful of comparisons and no allocation. The counts wait in the state
 * until {@link State#drain} hands them out, which the system does once a second.
 */
public final class MovementEdges {
    private MovementEdges() {}

    /** The least height of a fall that counts as a water landing, in metres. */
    public static final double WATER_LANDING_METRES = 30.0;
    /** Float slack when taking whole seconds, a thousandth of a second. */
    private static final float SLACK = 0.001f;

    /** One player's previous flags and the counts waiting to be flushed. */
    public static final class State {
        // Flags at the last tick
        boolean jumping;
        boolean rolling;
        boolean mantling;
        boolean climbing;
        boolean sprinting;
        boolean crouching;
        boolean gliding;
        boolean falling;
        int extraJumps;
        double fallStartY;

        // Counts since the last drain
        int jumps;
        int doubleJumps;
        int rolls;
        int mantles;
        int glides;
        int waterLandings;
        float climbSeconds;
        float sprintSeconds;
        float crouchSeconds;
        /** The glide in progress, in seconds. */
        float glideSeconds;
        /** The longest glide that ended since the last drain, in seconds. */
        float endedGlideSeconds;
        /** Seconds since the last drain. */
        float sinceDrain;

        /** Whether the player was falling at the last tick. */
        public boolean falling() {
            return falling;
        }

        public int jumps() {
            return jumps;
        }

        public int doubleJumps() {
            return doubleJumps;
        }

        public int rolls() {
            return rolls;
        }

        public int mantles() {
            return mantles;
        }

        public int glides() {
            return glides;
        }

        public int waterLandings() {
            return waterLandings;
        }

        public float glideSeconds() {
            return glideSeconds;
        }

        /** True once a second's worth of ticks has gone by since the last drain. */
        public boolean due() {
            return sinceDrain >= 1f;
        }

        /**
         * Takes the counts gathered since the last drain and clears them. Whole seconds of climbing,
         * sprinting and crouching are taken and the fractions carry on. The longest glide is the longer of
         * the glide that ended and the one still going, so a long glide shows progress while it lasts.
         */
        public Drained drain() {
            // Tick lengths are floats, so twenty ticks of 0.05 land a hair under one. The slack rounds that up.
            int climb = (int) (climbSeconds + SLACK);
            climbSeconds = Math.max(0f, climbSeconds - climb);
            int sprint = (int) (sprintSeconds + SLACK);
            sprintSeconds = Math.max(0f, sprintSeconds - sprint);
            int crouch = (int) (crouchSeconds + SLACK);
            crouchSeconds = Math.max(0f, crouchSeconds - crouch);
            int longestGlide = (int) (Math.max(endedGlideSeconds, glideSeconds) + SLACK);
            Drained out = new Drained(jumps, doubleJumps, rolls, mantles, glides, waterLandings, climb, sprint, crouch,
                    longestGlide);
            jumps = 0;
            doubleJumps = 0;
            rolls = 0;
            mantles = 0;
            glides = 0;
            waterLandings = 0;
            endedGlideSeconds = 0f;
            sinceDrain = 0f;
            return out;
        }
    }

    /** What one drain handed out. {@code longestGlideSeconds} is a record to raise to, the rest are adds. */
    public record Drained(int jumps, int doubleJumps, int rolls, int mantles, int glides, int waterLandings,
                          int climbSeconds, int sprintSeconds, int crouchSeconds, int longestGlideSeconds) {
        public boolean isEmpty() {
            return jumps == 0 && doubleJumps == 0 && rolls == 0 && mantles == 0 && glides == 0 && waterLandings == 0
                    && climbSeconds == 0 && sprintSeconds == 0 && crouchSeconds == 0 && longestGlideSeconds == 0;
        }
    }

    /**
     * Feeds one tick of flags. {@code y} is the player's height this tick and {@code dt} the tick length in
     * seconds.
     */
    public static void step(State s, boolean jumping, int extraJumps, boolean rolling, boolean mantling,
                            boolean climbing, boolean sprinting, boolean crouching, boolean gliding, boolean falling,
                            boolean inFluid, double y, float dt) {
        if (jumping && !s.jumping) {
            s.jumps++;
        }
        if (extraJumps > s.extraJumps) {
            s.doubleJumps += extraJumps - s.extraJumps;
        }
        if (rolling && !s.rolling) {
            s.rolls++;
        }
        if (mantling && !s.mantling) {
            s.mantles++;
        }
        if (gliding) {
            if (!s.gliding) {
                s.glides++;
                s.glideSeconds = 0f;
            }
            s.glideSeconds += dt;
        } else if (s.gliding) {
            if (s.glideSeconds > s.endedGlideSeconds) {
                s.endedGlideSeconds = s.glideSeconds;
            }
            s.glideSeconds = 0f;
        }
        if (climbing) {
            s.climbSeconds += dt;
        }
        if (sprinting) {
            s.sprintSeconds += dt;
        }
        if (crouching) {
            s.crouchSeconds += dt;
        }
        if (falling && !s.falling) {
            s.fallStartY = y;
        } else if (!falling && s.falling && inFluid && s.fallStartY - y >= WATER_LANDING_METRES) {
            s.waterLandings++;
        }
        s.jumping = jumping;
        s.extraJumps = extraJumps;
        s.rolling = rolling;
        s.mantling = mantling;
        s.climbing = climbing;
        s.sprinting = sprinting;
        s.crouching = crouching;
        s.gliding = gliding;
        s.falling = falling;
        s.sinceDrain += dt;
    }
}
