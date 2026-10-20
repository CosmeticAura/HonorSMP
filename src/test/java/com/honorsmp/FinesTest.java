package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.FineMatch;
import com.honorsmp.logic.FineMatch.Desc;
import com.honorsmp.logic.FineTimer;
import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.JudgeRules;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FinesTest {
    private static final long H = FineTimer.HOUR_MS;
    private static final long IV = FineTimer.intervalMs(2.5);

    private static Desc d(String id, int n) {
        return new Desc(id, n, 0, Map.of(), "", "");
    }

    @Test
    void noPenaltyBeforeFirstInterval() {
        assertEquals(0, FineTimer.penaltiesDue(0, 0, 2 * H, IV, false));
        assertEquals(1, FineTimer.penaltiesDue(0, 0, 150 * 60_000L, IV, false));
    }

    @Test
    void multipleIntervalsMissedOffline() {
        long now = 8 * H; // 3 whole intervals of 2.5 h
        assertEquals(3, FineTimer.penaltiesDue(0, 0, now, IV, false));
        long last = FineTimer.advance(0, 0, 3, IV);
        assertEquals(7 * H + 30 * 60_000L, last);
        assertEquals(0, FineTimer.penaltiesDue(0, last, now, IV, false));
        assertEquals(last + IV, FineTimer.nextPenaltyAt(0, last, IV));
        assertEquals(1, FineTimer.penaltiesDue(0, last, last + IV, IV, false));
        assertEquals(30, FineTimer.totalLoss(3, 10));
    }

    @Test
    void judgesAreExemptFromFinePenalties() {
        assertEquals(0, FineTimer.penaltiesDue(0, 0, 100 * H, IV, true));
        assertFalse(FineTimer.canBeFined(true));
        assertTrue(FineTimer.canBeFined(false));
        // the penalty uses a non-auto source, so a judge is only protected by the explicit exemption above
        assertTrue(JudgeRules.autoChangeAllowed(false, "fine"));
    }

    @Test
    void configDefaults() {
        HonorConfig c = new HonorConfig();
        assertEquals(2.5, c.fineIntervalHours);
        assertEquals(10, c.fineHonorPenalty);
    }

    @Test
    void oldDataHasNoFines() {
        HonorData old = HonorData.parse("{\"version\":1}");
        assertNotNull(old.fines);
        assertTrue(old.fines.isEmpty());
        assertEquals(1, old.nextFineId);
    }

    @Test
    void exactPaymentMatchesAndSurplusIsNotUsed() {
        var r = FineMatch.match(List.of(d("minecraft:diamond", 10)), List.of(d("minecraft:diamond", 64), d("minecraft:dirt", 5)));
        assertTrue(r.complete());
        assertEquals(List.of(10, 0), r.used());
    }

    @Test
    void countsAddUpAcrossStacks() {
        var r = FineMatch.match(List.of(d("minecraft:emerald", 30)), List.of(d("minecraft:emerald", 20), d("minecraft:emerald", 20)));
        assertTrue(r.complete());
        assertEquals(List.of(20, 10), r.used());
    }

    @Test
    void shortPaymentIsAllOrNothingAndReportsMissing() {
        var r = FineMatch.match(List.of(d("minecraft:diamond", 5), d("minecraft:emerald", 10)),
                List.of(d("minecraft:diamond", 5), d("minecraft:emerald", 4)));
        assertFalse(r.complete());
        assertEquals(List.of(0, 0), r.used());
        assertEquals(1, r.missing().size());
        assertEquals("minecraft:emerald", r.missing().get(0).id());
        assertEquals(6, r.missing().get(0).count());
    }

    @Test
    void enchantsMustMatch() {
        Desc owed = new Desc("minecraft:diamond_sword", 1, 0, Map.of("minecraft:sharpness", 4), "", "");
        Desc same = new Desc("minecraft:diamond_sword", 1, 0, Map.of("minecraft:sharpness", 4), "", "");
        Desc lower = new Desc("minecraft:diamond_sword", 1, 0, Map.of("minecraft:sharpness", 3), "", "");
        Desc plain = d("minecraft:diamond_sword", 1);
        assertTrue(FineMatch.match(List.of(owed), List.of(same)).complete());
        assertFalse(FineMatch.match(List.of(owed), List.of(lower)).complete());
        assertFalse(FineMatch.match(List.of(owed), List.of(plain)).complete());
        assertFalse(FineMatch.match(List.of(plain), List.of(owed)).complete());
    }

    @Test
    void damageAndNameAndDataAreCompared() {
        Desc fresh = d("minecraft:iron_axe", 1);
        assertTrue(FineMatch.match(List.of(fresh), List.of(new Desc("minecraft:iron_axe", 1, 0, Map.of(), "", ""))).complete());
        assertFalse(FineMatch.match(List.of(fresh), List.of(new Desc("minecraft:iron_axe", 1, 30, Map.of(), "", ""))).complete());
        assertFalse(FineMatch.match(List.of(fresh), List.of(new Desc("minecraft:iron_axe", 1, 0, Map.of(), "Bob's", ""))).complete());
        assertFalse(FineMatch.match(List.of(fresh), List.of(new Desc("minecraft:iron_axe", 1, 0, Map.of(), "", "{x:1}"))).complete());
    }
}
