package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RankPerksTest {
    private final HonorConfig c = new HonorConfig();

    @Test
    void perkTablesMatchSpec() {
        assertArrayEquals(new int[] {20, 35, 50}, c.potionBonusPercent);
        assertArrayEquals(new int[] {10, 20, 30}, c.tradeDiscountPercent);
        assertArrayEquals(new int[] {15, 25, 35}, c.hungerSlowPercent);
        assertEquals(2, RankPerks.healthBonus(Tier.HONORABLE, c));
        assertEquals(4, RankPerks.healthBonus(Tier.NOBLE, c));
        assertEquals(6, RankPerks.healthBonus(Tier.PARAGON, c));
        assertEquals(6, RankPerks.healthBonus(Tier.JUDGE, c));
        assertEquals(0, RankPerks.healthBonus(Tier.NEUTRAL, c));
        assertEquals(0, RankPerks.healthBonus(Tier.WARLORD, c));
    }

    @Test
    void extrasByTier() {
        assertFalse(RankPerks.keepsXp(Tier.HONORABLE, c));
        assertTrue(RankPerks.keepsXp(Tier.NOBLE, c));
        assertTrue(RankPerks.keepsXp(Tier.PARAGON, c));
        assertFalse(RankPerks.keepsHotbar(Tier.NOBLE, c));
        assertTrue(RankPerks.keepsHotbar(Tier.PARAGON, c));
        assertTrue(RankPerks.goldName(Tier.PARAGON, c));
        assertTrue(RankPerks.goldName(Tier.JUDGE, c));
        assertFalse(RankPerks.goldName(Tier.NOBLE, c));
        assertFalse(RankPerks.keepsXp(Tier.MARAUDER, c));
        assertFalse(RankPerks.keepsHotbar(Tier.NEUTRAL, c));
        c.keepXpMinRank = 0;
        assertFalse(RankPerks.keepsXp(Tier.PARAGON, c));
    }

    @Test
    void healthNeverAboveMax() {
        assertEquals(20f, RankPerks.clampHealth(26f, 20f));
        assertEquals(12f, RankPerks.clampHealth(12f, 20f));
    }

    @Test
    void keepOnDeathNoDupes() {
        List<String> inv = new ArrayList<>();
        for (int i = 0; i < 41; i++) inv.add(i % 2 == 0 ? "item" + i : "");
        var r = KeepOnDeath.select(inv, String::isEmpty, "", true);
        Set<Integer> keptSlots = r.kept().keySet();
        for (int i = 0; i < 41; i++) {
            boolean shouldKeep = (i < 9 || i == 40) && i % 2 == 0;
            assertEquals(shouldKeep, keptSlots.contains(i), "slot " + i);
            if (keptSlots.contains(i)) assertEquals("", r.remaining().get(i)); // not also dropped
            else assertEquals(inv.get(i), r.remaining().get(i));
        }
        assertTrue(keptSlots.contains(40));
        // every item ends up in exactly one of kept / remaining
        Set<String> all = new HashSet<>();
        for (String s : inv) if (!s.isEmpty()) all.add(s);
        Set<String> seen = new HashSet<>(r.kept().values());
        for (String s : r.remaining()) if (!s.isEmpty()) assertTrue(seen.add(s), "dup " + s);
        assertEquals(all, seen);
        assertEquals(41, inv.size());
        assertEquals("item0", inv.get(0)); // input untouched
    }

    @Test
    void keepNothingWhenNotParagon() {
        List<String> inv = List.of("a", "b", "c");
        var r = KeepOnDeath.select(inv, String::isEmpty, "", false);
        assertTrue(r.kept().isEmpty());
        assertEquals(inv, r.remaining());
    }

    @Test
    void migrationReplacesOldDefaultsOnly() {
        HonorConfig old = new HonorConfig();
        old.potionBonusPercent = new int[] {15, 30, 50};
        old.tradeDiscountPercent = new int[] {5, 10, 15};
        old.hungerSlowPercent = new int[] {10, 15, 20};
        old.perksVersion = 0;
        assertTrue(old.migrate());
        assertArrayEquals(new int[] {20, 35, 50}, old.potionBonusPercent);
        assertArrayEquals(new int[] {10, 20, 30}, old.tradeDiscountPercent);
        assertArrayEquals(new int[] {15, 25, 35}, old.hungerSlowPercent);
        assertFalse(old.migrate()); // second run changes nothing

        HonorConfig custom = new HonorConfig();
        custom.potionBonusPercent = new int[] {15, 30, 60}; // admin changed
        custom.tradeDiscountPercent = new int[] {5, 10, 15};
        custom.hungerSlowPercent = new int[] {1, 2, 3};
        custom.perksVersion = 0;
        custom.migrate();
        assertArrayEquals(new int[] {15, 30, 60}, custom.potionBonusPercent);
        assertArrayEquals(new int[] {10, 20, 30}, custom.tradeDiscountPercent);
        assertArrayEquals(new int[] {1, 2, 3}, custom.hungerSlowPercent);
    }

    @Test
    void oldConfigFileGetsNewKeysAndMigrates() {
        String json = "{\"potionBonusPercent\":[15,30,50],\"tradeDiscountPercent\":[5,10,15],\"hungerSlowPercent\":[10,15,20],\"minBounty\":7}";
        HonorConfig cfg = Honor.GSON.fromJson(json, HonorConfig.class);
        assertArrayEquals(new int[] {2, 4, 6}, cfg.healthBonusHp); // missing key keeps default
        assertEquals(3, cfg.keepHotbarMinRank);
        cfg.migrate();
        assertArrayEquals(new int[] {20, 35, 50}, cfg.potionBonusPercent);
        assertEquals(7, cfg.minBounty);
        // re-saved file does not migrate again
        HonorConfig again = Honor.GSON.fromJson(Honor.GSON.toJson(cfg), HonorConfig.class);
        assertFalse(again.migrate());
    }
}
