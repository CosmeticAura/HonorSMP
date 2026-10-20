package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.JudgeRules;
import com.honorsmp.logic.TreasuryMath;
import java.util.List;
import org.junit.jupiter.api.Test;

class JudgeTreasuryTest {
    @Test
    void judgesAreExemptFromAutoChangesOnly() {
        assertFalse(JudgeRules.autoChangeAllowed(true, "auto"));
        assertTrue(JudgeRules.autoChangeAllowed(true, "admin"));
        assertTrue(JudgeRules.autoChangeAllowed(false, "auto"));
    }

    @Test
    void emeraldsSplitIntoStacks() {
        assertEquals(List.of(), TreasuryMath.splitStacks(0, 64));
        assertEquals(List.of(64, 64, 22), TreasuryMath.splitStacks(150, 64));
        assertEquals(List.of(64), TreasuryMath.splitStacks(64, 64));
    }

    @Test
    void oldTreasuryFieldStillReadsAndNewListDefaultsEmpty() {
        HonorData d = HonorData.parse("{\"treasuryEmeralds\":150}");
        assertEquals(150, d.treasuryEmeralds);
        assertTrue(d.treasury.isEmpty());
    }
}
