package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.*;
import com.honorsmp.logic.DonorPerks.DonorTier;
import org.junit.jupiter.api.Test;

class JudgePerksTest {
    private final HonorConfig c = new HonorConfig();

    @Test
    void judgeIsParagonWhateverTheScore() {
        for (Tier t : Tier.values()) {
            assertEquals(Tier.PARAGON, RankPerks.effectiveTier(true, t));
        }
        assertEquals(Tier.PARAGON, RankPerks.effectiveTier(false, Tier.JUDGE));
    }

    @Test
    void nonJudgeUnchanged() {
        for (Tier t : Tier.values()) {
            if (t != Tier.JUDGE) assertEquals(t, RankPerks.effectiveTier(false, t));
        }
    }

    @Test
    void judgeGetsEveryParagonPerk() {
        Tier j = RankPerks.effectiveTier(true, Tier.WARLORD);
        assertEquals(RankPerks.healthBonus(Tier.PARAGON, c), RankPerks.healthBonus(j, c));
        assertEquals(6, RankPerks.healthBonus(j, c));
        assertEquals(c.tradeDiscountPercent[2], RankPerks.tradeDiscountPercent(j, c));
        assertEquals(c.potionBonusPercent[2], RankPerks.potionBonusPercent(j, c));
        assertEquals(c.hungerSlowPercent[2], RankPerks.hungerSlowPercent(j, c));
        assertTrue(RankPerks.keepsXp(j, c));
        assertTrue(RankPerks.keepsHotbar(j, c));
        assertTrue(RankPerks.goldName(j, c));
        assertTrue(RankPerks.hasCompass(j));
        assertEquals(30, RankPerks.tradeDiscountPercent(j, c));
        assertEquals(50, RankPerks.potionBonusPercent(j, c));
        assertEquals(35, RankPerks.hungerSlowPercent(j, c));
    }

    @Test
    void judgeNeverDishonoredOrDonor() {
        for (Tier raw : new Tier[] {Tier.OUTLAW, Tier.MARAUDER, Tier.WARLORD}) {
            Tier j = RankPerks.effectiveTier(true, raw);
            assertFalse(j.isDishonored());
            assertTrue(j.isHonorable());
        }
        c.donorSupporterMin = 0;
        assertEquals(DonorTier.NONE, DonorPerks.effectiveTier(1_000_000, true, c));
        int rank = RankPerks.hungerSlowPercent(RankPerks.effectiveTier(true, Tier.JUDGE), c);
        assertEquals(rank, DonorPerks.totalHungerPercent(rank, DonorTier.NONE, c));
    }

    @Test
    void nonJudgeDishonoredAndNeutralGetNoRankPerks() {
        assertEquals(0, RankPerks.healthBonus(Tier.WARLORD, c));
        assertEquals(0, RankPerks.tradeDiscountPercent(Tier.NEUTRAL, c));
        assertEquals(0, RankPerks.potionBonusPercent(Tier.OUTLAW, c));
        assertEquals(0, RankPerks.hungerSlowPercent(Tier.MARAUDER, c));
        assertFalse(RankPerks.hasCompass(Tier.HONORABLE));
        assertTrue(RankPerks.hasCompass(Tier.NOBLE));
        assertFalse(RankPerks.goldName(Tier.NOBLE, c));
    }
}
