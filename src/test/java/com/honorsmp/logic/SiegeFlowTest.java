package com.honorsmp.logic;

import static com.honorsmp.logic.SiegeFlow.Action.*;
import static com.honorsmp.logic.SiegeFlow.Phase;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SiegeFlowTest {
    static final long S = 1000L;

    // warn 60 s, 3 waves, 20 s delay, abandon 120 s, max 15 min; started at t=0
    SiegeFlow flow() {
        return new SiegeFlow(0, 60, 3, 20, 120, 15);
    }

    @Test
    void warningWaitsForCountdownAndMembers() {
        SiegeFlow f = flow();
        assertEquals(NONE, f.tick(10 * S, true, true, 0));
        assertEquals(Phase.WARNING, f.phase());
        assertEquals(0, f.wave());
        // countdown over but nobody near: keep waiting
        assertEquals(NONE, f.tick(61 * S, false, false, 0));
        assertEquals(Phase.WARNING, f.phase());
        assertEquals(NONE, f.tick(500 * S, false, false, 0));
        assertEquals(Phase.WARNING, f.phase());
        assertEquals(START_WAVE, f.tick(501 * S, true, true, 0));
        assertEquals(1, f.wave());
        assertEquals(Phase.WAVE, f.phase());
    }

    @Test
    void noProgressWhileMobsLeft() {
        SiegeFlow f = flow();
        assertEquals(START_WAVE, f.tick(60 * S, true, true, 0));
        for (int t = 61; t < 200; t++) {
            assertEquals(NONE, f.tick(t * S, true, true, 3));
        }
        assertEquals(Phase.WAVE, f.phase());
        assertEquals(1, f.wave());
    }

    @Test
    void delayAfterLastMobThenNextWave() {
        SiegeFlow f = flow();
        f.tick(60 * S, true, true, 0);
        assertEquals(WAVE_CLEARED, f.tick(100 * S, true, true, 0));
        assertEquals(Phase.WAVE_DELAY, f.phase());
        assertEquals(NONE, f.tick(119 * S, true, true, 0));
        assertEquals(START_WAVE, f.tick(120 * S, true, true, 0));
        assertEquals(2, f.wave());
        assertEquals(Phase.WAVE, f.phase());
    }

    @Test
    void winAfterThreeWaves() {
        SiegeFlow f = flow();
        long t = 60 * S;
        assertEquals(START_WAVE, f.tick(t, true, true, 0));
        for (int w = 1; w < 3; w++) {
            t += 10 * S;
            assertEquals(WAVE_CLEARED, f.tick(t, true, true, 0));
            t += 20 * S;
            assertEquals(START_WAVE, f.tick(t, true, true, 0));
            assertEquals(w + 1, f.wave());
        }
        t += 10 * S;
        assertEquals(WON, f.tick(t, true, true, 0));
        assertEquals(Phase.WON, f.phase());
        assertTrue(f.finished());
        assertEquals(NONE, f.tick(t + S, true, true, 0));
    }

    @Test
    void abandonLoss() {
        SiegeFlow f = flow();
        f.tick(60 * S, true, true, 0); // lastNear = 60
        assertEquals(NONE, f.tick(100 * S, true, true, 5)); // lastNear = 100
        assertEquals(NONE, f.tick(219 * S, false, false, 5));
        assertEquals(LOST, f.tick(220 * S, false, false, 5)); // 120 s without a member near
        assertEquals(Phase.LOST, f.phase());
    }

    @Test
    void abandonLossAlsoDuringDelay() {
        SiegeFlow f = flow();
        f.tick(60 * S, true, true, 0);
        assertEquals(WAVE_CLEARED, f.tick(70 * S, true, true, 0)); // lastNear = 70
        assertEquals(LOST, f.tick(190 * S, false, false, 0));
    }

    @Test
    void memberReturningResetsAbandonTimer() {
        SiegeFlow f = flow();
        f.tick(60 * S, true, true, 0);
        assertEquals(NONE, f.tick(170 * S, true, true, 5));
        assertEquals(NONE, f.tick(280 * S, true, false, 5));
        assertEquals(LOST, f.tick(290 * S, true, false, 5));
    }

    @Test
    void maxMinutesLoss() {
        SiegeFlow f = flow();
        f.tick(60 * S, true, true, 0);
        long end = 60 * S + 15 * 60 * S;
        assertEquals(NONE, f.tick(end - S, true, true, 4));
        assertEquals(LOST, f.tick(end, true, true, 4));
    }

    @Test
    void loseTimersDoNotRunDuringWarning() {
        SiegeFlow f = flow();
        assertEquals(NONE, f.tick(30 * 60 * S, false, false, 0));
        assertEquals(Phase.WARNING, f.phase());
        assertEquals(START_WAVE, f.tick(30 * 60 * S + S, true, true, 0));
        assertEquals(NONE, f.tick(30 * 60 * S + 2 * S, true, true, 3));
    }

    @Test
    void stop() {
        SiegeFlow f = flow();
        f.tick(60 * S, true, true, 0);
        f.stop();
        assertEquals(Phase.LOST, f.phase());
        assertTrue(f.finished());
        assertEquals(NONE, f.tick(70 * S, true, true, 0));
        SiegeFlow g = flow();
        g.stop();
        assertEquals(Phase.LOST, g.phase());
    }

    @Test
    void stopAfterWinKeepsWon() {
        SiegeFlow f = new SiegeFlow(0, 0, 1, 0, 120, 15);
        f.tick(0, true, true, 0);
        assertEquals(WON, f.tick(S, true, true, 0));
        f.stop();
        assertEquals(Phase.WON, f.phase());
    }
}
