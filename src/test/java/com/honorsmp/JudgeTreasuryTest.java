package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.JudgeRules;
import com.honorsmp.logic.TreasuryMath;
import java.util.List;
import org.junit.jupiter.api.Test;

class JudgeTreasuryTest {
    @Test
    void judgeCannotRuleOwnCase() {
        assertFalse(JudgeRules.canRule("j", "j", "v"));
        assertFalse(JudgeRules.canRule("j", "k", "j"));
        assertTrue(JudgeRules.canRule("j", "k", "v"));
        assertTrue(JudgeRules.canRule(null, "k", "v"));
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
