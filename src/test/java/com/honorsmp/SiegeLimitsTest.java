package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.honorsmp.logic.SiegeLimits;

class SiegeLimitsTest {
    @Test
    void notEndedWhileMembersNear() {
        assertFalse(SiegeLimits.endsAsLoss(60_000, 0, 60_000, 120, 15));
    }

    @Test
    void abandonBoundary() {
        long start = 0, last = 100_000;
        assertFalse(SiegeLimits.endsAsLoss(last + 119_999, start, last, 120, 15));
        assertTrue(SiegeLimits.endsAsLoss(last + 120_000, start, last, 120, 15));
        assertTrue(SiegeLimits.abandoned(last + 200_000, last, 120));
    }

    @Test
    void maxDurationBoundary() {
        long start = 1000;
        long end = start + 15 * 60_000L;
        assertFalse(SiegeLimits.endsAsLoss(end - 1, start, end - 1, 120, 15));
        assertTrue(SiegeLimits.endsAsLoss(end, start, end, 120, 15), "members present but time is up");
        assertTrue(SiegeLimits.timedOut(end, start, 15));
        assertFalse(SiegeLimits.timedOut(end - 1, start, 15));
    }
}
