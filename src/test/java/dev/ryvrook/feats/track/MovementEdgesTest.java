package dev.ryvrook.feats.track;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementEdgesTest {
    private static final float TICK = 0.05f;

    /** One tick with every flag off except the ones named. */
    private static void idle(MovementEdges.State s, double y) {
        MovementEdges.step(s, false, 0, false, false, false, false, false, false, false, false, y, TICK);
    }

    private static void jump(MovementEdges.State s, int extra) {
        MovementEdges.step(s, true, extra, false, false, false, false, false, false, false, false, 64, TICK);
    }

    @Test
    void edgesCountOncePerPress() {
        MovementEdges.State s = new MovementEdges.State();
        idle(s, 64);
        for (int i = 0; i < 10; i++) {
            jump(s, 0); // held for ten ticks
        }
        idle(s, 64);
        jump(s, 0);
        idle(s, 64);
        MovementEdges.step(s, false, 0, true, false, false, false, false, false, false, false, 64, TICK);
        MovementEdges.step(s, false, 0, true, false, false, false, false, false, false, false, 64, TICK);
        MovementEdges.step(s, false, 0, false, true, false, false, false, false, false, false, 64, TICK);
        idle(s, 64);
        MovementEdges.Drained d = s.drain();
        assertEquals(2, d.jumps());
        assertEquals(1, d.rolls());
        assertEquals(1, d.mantles());
        assertEquals(0, d.doubleJumps());
        assertEquals(0, d.glides());
        assertTrue(s.drain().isEmpty(), "a drain clears the counts");
    }

    @Test
    void doubleJumpsFollowTheExtraJumpsUsed() {
        MovementEdges.State s = new MovementEdges.State();
        idle(s, 64);
        jump(s, 0);
        jump(s, 1); // the second press in the air
        jump(s, 1);
        idle(s, 64); // landed, the counter resets to zero without counting
        jump(s, 0);
        jump(s, 2); // two extra jumps at once
        MovementEdges.Drained d = s.drain();
        assertEquals(2, d.jumps());
        assertEquals(3, d.doubleJumps());
    }

    @Test
    void secondsAccumulateAndFractionsCarry() {
        MovementEdges.State s = new MovementEdges.State();
        for (int i = 0; i < 30; i++) { // 1.5 s sprinting and crouching, 0.5 s of that climbing
            MovementEdges.step(s, false, 0, false, false, i < 10, true, true, false, false, false, 64, TICK);
        }
        assertTrue(s.due());
        MovementEdges.Drained d = s.drain();
        assertEquals(1, d.sprintSeconds());
        assertEquals(1, d.crouchSeconds());
        assertEquals(0, d.climbSeconds());
        assertFalse(s.due());
        for (int i = 0; i < 10; i++) { // another 0.5 s, so the carried half completes a second
            MovementEdges.step(s, false, 0, false, false, true, true, true, false, false, false, 64, TICK);
        }
        d = s.drain();
        assertEquals(1, d.sprintSeconds());
        assertEquals(1, d.crouchSeconds());
        assertEquals(1, d.climbSeconds());
    }

    @Test
    void glidesCountOnceAndTheLongestIsKept() {
        MovementEdges.State s = new MovementEdges.State();
        idle(s, 100);
        for (int i = 0; i < 60; i++) { // a 3 s glide
            MovementEdges.step(s, false, 0, false, false, false, false, false, true, false, false, 100 - i, TICK);
        }
        MovementEdges.Drained d = s.drain();
        assertEquals(1, d.glides());
        assertEquals(3, d.longestGlideSeconds(), "a glide still going reports its length so far");
        for (int i = 0; i < 20; i++) { // 1 s more, then it ends
            MovementEdges.step(s, false, 0, false, false, false, false, false, true, false, false, 40, TICK);
        }
        idle(s, 40);
        for (int i = 0; i < 10; i++) { // a short second glide
            MovementEdges.step(s, false, 0, false, false, false, false, false, true, false, false, 40, TICK);
        }
        idle(s, 40);
        d = s.drain();
        assertEquals(1, d.glides());
        assertEquals(4, d.longestGlideSeconds(), "the ended glide's full length wins over the short one");
        idle(s, 40);
        assertEquals(0, s.drain().longestGlideSeconds());
    }

    @Test
    void waterLandingsNeedThirtyMetresAndFluid() {
        MovementEdges.State s = new MovementEdges.State();
        idle(s, 100);
        for (int i = 1; i <= 20; i++) { // falls from 100 to 60, lands in water
            MovementEdges.step(s, false, 0, false, false, false, false, false, false, true, false, 100 - i * 2, TICK);
        }
        MovementEdges.step(s, false, 0, false, false, false, false, false, false, false, true, 60, TICK);
        assertEquals(1, s.drain().waterLandings());

        idle(s, 100);
        for (int i = 1; i <= 10; i++) { // 20 m only
            MovementEdges.step(s, false, 0, false, false, false, false, false, false, true, false, 100 - i * 2, TICK);
        }
        MovementEdges.step(s, false, 0, false, false, false, false, false, false, false, true, 80, TICK);
        assertEquals(0, s.drain().waterLandings());

        idle(s, 100);
        for (int i = 1; i <= 20; i++) { // 40 m onto the ground
            MovementEdges.step(s, false, 0, false, false, false, false, false, false, true, false, 100 - i * 2, TICK);
        }
        MovementEdges.step(s, false, 0, false, false, false, false, false, false, false, false, 60, TICK);
        assertEquals(0, s.drain().waterLandings());
    }

    @Test
    void drainReportsEmptyWhenNothingMoved() {
        MovementEdges.State s = new MovementEdges.State();
        for (int i = 0; i < 25; i++) {
            idle(s, 64);
        }
        assertTrue(s.due());
        assertTrue(s.drain().isEmpty());
    }
}
