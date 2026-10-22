package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.CivEffects;
import com.honorsmp.logic.CivUpgrades.Upgrade;
import com.honorsmp.logic.HonorConfig;
import org.junit.jupiter.api.Test;

class CivEffectsTest {
    private final HonorConfig c = new HonorConfig();

    @Test
    void activeValuesFollowLevels() {
        var v = CivEffects.values(c, true, 1, 2, 1, 2, 2);
        assertEquals(5, v.resistancePercent());
        assertEquals(1.0, v.strengthBonus());
        assertEquals(5, v.speedPercent());
        assertEquals(2, v.hasteLevel());
        assertEquals(4, v.fortitudeHp());
    }

    @Test
    void inactiveOrUnownedIsNothing() {
        assertEquals(CivEffects.Values.NONE, CivEffects.values(c, false, 2, 2, 2, 2, 2));
        assertEquals(CivEffects.Values.NONE, CivEffects.values(c, true, 0, 0, 0, 0, 0));
    }

    @Test
    void damageMultiplier() {
        assertEquals(1f, CivEffects.damageMultiplier(0));
        assertEquals(0.95f, CivEffects.damageMultiplier(5), 1e-6);
        assertEquals(0.9f, CivEffects.damageMultiplier(10), 1e-6);
        assertEquals(0f, CivEffects.damageMultiplier(150));
        assertEquals(1f, CivEffects.damageMultiplier(-5));
    }

    @Test
    void slotMapping() {
        for (Upgrade u : Upgrade.values()) assertEquals(u, CivEffects.upgradeAt(CivEffects.slotOf(u)));
        assertNull(CivEffects.upgradeAt(CivEffects.FIRST_SLOT - 1));
        assertNull(CivEffects.upgradeAt(CivEffects.FIRST_SLOT + Upgrade.values().length));
        assertNull(CivEffects.upgradeAt(-3));
    }

    @Test
    void durationAndDescriptions() {
        assertEquals("2d 5h", CivEffects.duration(53L * 3_600_000L));
        assertEquals("3h 10m", CivEffects.duration(-(190L * 60_000L)));
        assertEquals("1m", CivEffects.duration(10));
        assertEquals("none", CivEffects.describe(c, Upgrade.SPEED, 0));
        assertEquals("+0.5 attack damage", CivEffects.describe(c, Upgrade.STRENGTH, 1));
        assertEquals("+1 attack damage", CivEffects.describe(c, Upgrade.STRENGTH, 2));
        assertEquals("Haste II", CivEffects.describe(c, Upgrade.HASTE, 2));
        assertEquals("10% less damage taken", CivEffects.describe(c, Upgrade.RESISTANCE, 2));
    }
}
