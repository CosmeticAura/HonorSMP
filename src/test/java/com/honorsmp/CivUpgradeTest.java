package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.CivTax;
import com.honorsmp.logic.CivUpgrades;
import com.honorsmp.logic.CivUpgrades.BuyCheck;
import com.honorsmp.logic.CivUpgrades.Upgrade;
import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.Wallet;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CivUpgradeTest {
    static final long H = 3_600_000L, D = 24 * H, T0 = 1_000_000_000L;
    final HonorConfig c = new HonorConfig();

    static class W implements Wallet {
        int em, di;
        W(int em, int di) { this.em = em; this.di = di; }
        public int emeralds() { return em; }
        public int diamonds() { return di; }
        public void take(int e, int d) { em -= e; di -= d; }
    }

    Civ civ(int members) {
        Civ v = new Civ();
        v.id = "c1"; v.name = "Rome"; v.leader = "u0";
        for (int i = 0; i < members; i++) v.members.add("u" + i);
        return v;
    }

    @Test
    void costs() {
        assertEquals(32, CivUpgrades.emeraldCost(c, 1));
        assertEquals(0, CivUpgrades.diamondCost(c, 1));
        assertEquals(64, CivUpgrades.emeraldCost(c, 2));
        assertEquals(8, CivUpgrades.diamondCost(c, 2));
        assertEquals(10, CivUpgrades.resistancePercent(c, 2));
        assertEquals(0.5, CivUpgrades.strengthBonus(c, 1));
        assertEquals(4, CivUpgrades.fortitudeHp(c, 2));
        assertEquals(0, CivUpgrades.speedPercent(c, 0));
    }

    @Test
    void buyFlow() {
        Civ v = civ(3);
        W w = new W(31, 0);
        assertEquals(BuyCheck.NOT_ENOUGH_EMERALDS, CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, w).check());
        assertEquals(31, w.em);
        assertEquals(0, v.taxDueAt);
        w.em = 100;
        var r = CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, w);
        assertTrue(r.ok());
        assertEquals(68, w.em);
        assertEquals(T0 + 7 * D, v.taxDueAt);
        // level 2 needs diamonds, does not move the due date
        assertEquals(BuyCheck.NOT_ENOUGH_DIAMONDS, CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0 + D, new W(100, 7)).check());
        w.em = 64; w.di = 8;
        assertTrue(CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0 + D, w).ok());
        assertEquals(0, w.em);
        assertEquals(0, w.di);
        assertEquals(2, CivUpgradeApi.ownedLevel(v, "haste"));
        assertEquals(T0 + 7 * D, v.taxDueAt);
        assertEquals(BuyCheck.MAX_LEVEL, CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, new W(999, 99)).check());
    }

    @Test
    void disabledSwitch() {
        c.civUpgradesEnabled = false;
        assertEquals(BuyCheck.DISABLED, CivUpgradeApi.buy(civ(1), Upgrade.SPEED, c, T0, new W(99, 99)).check());
    }

    @Test
    void taxMath() {
        Civ v = civ(5);
        assertEquals(0, CivUpgradeApi.taxOwed(v, c));
        CivUpgradeApi.buy(v, Upgrade.SPEED, c, T0, new W(500, 50));
        assertEquals(1 * 5 * 4, CivUpgradeApi.taxOwed(v, c));
        CivUpgradeApi.buy(v, Upgrade.SPEED, c, T0, new W(500, 50));
        CivUpgradeApi.buy(v, Upgrade.FORTITUDE, c, T0, new W(500, 50));
        assertEquals(3 * 5 * 4, CivUpgradeApi.taxOwed(v, c));
        v.members.remove("u4");
        assertEquals(3 * 4 * 4, CivUpgradeApi.taxOwed(v, c));
        c.taxPerLevelPerMemberPerWeek = 1;
        assertEquals(12, CivUpgradeApi.taxOwed(v, c));
    }

    @Test
    void dueAndWarningWindow() {
        Civ v = civ(2);
        assertFalse(CivUpgradeApi.warningDue(v, c, T0));
        CivUpgradeApi.buy(v, Upgrade.STRENGTH, c, T0, new W(64, 0));
        long due = T0 + 7 * D;
        assertFalse(CivUpgradeApi.warningDue(v, c, due - 24 * H - 1));
        assertTrue(CivUpgradeApi.warningDue(v, c, due - 24 * H));
        assertTrue(CivUpgradeApi.takeWarningOnce(v, c, due - 23 * H));
        assertFalse(CivUpgradeApi.takeWarningOnce(v, c, due - 22 * H)); // once only
        assertTrue(CivUpgradeApi.warningDue(v, c, due - 1)); // login warning still true
        assertFalse(CivUpgradeApi.isLate(v, due - 1));
        assertTrue(CivUpgradeApi.isLate(v, due));
        assertFalse(CivUpgradeApi.warningDue(v, c, due)); // late, not "warning"
    }

    @Test
    void lateSwitchesOffAndPayingRestores() {
        Civ v = civ(2);
        CivUpgradeApi.buy(v, Upgrade.RESISTANCE, c, T0, new W(64, 0));
        long due = T0 + 7 * D;
        assertTrue(CivUpgradeApi.upgradesActive(v, c, due - 1));
        assertEquals(1, CivUpgradeApi.activeLevel(v, Upgrade.RESISTANCE, c, due - 1));
        long late = due + 20 * D; // many weeks missed
        assertFalse(CivUpgradeApi.upgradesActive(v, c, late));
        assertEquals(0, CivUpgradeApi.activeLevel(v, Upgrade.RESISTANCE, c, late));
        assertEquals(1, CivUpgradeApi.ownedLevel(v, Upgrade.RESISTANCE)); // not lost
        assertEquals(BuyCheck.TAX_UNPAID, CivUpgradeApi.buy(v, Upgrade.SPEED, c, late, new W(99, 9)).check());
        assertEquals(8, CivUpgradeApi.taxOwed(v, c)); // one period only
        W w = new W(7, 0);
        AtomicLong tr = new AtomicLong();
        assertEquals(CivUpgradeApi.PayResult.NOT_ENOUGH_EMERALDS, CivUpgradeApi.pay(v, c, late, w, tr::addAndGet));
        assertFalse(CivUpgradeApi.upgradesActive(v, c, late));
        w.em = 10;
        assertEquals(CivUpgradeApi.PayResult.PAID, CivUpgradeApi.pay(v, c, late, w, tr::addAndGet));
        assertEquals(2, w.em);
        assertEquals(8, tr.get());
        assertTrue(CivUpgradeApi.upgradesActive(v, c, late));
        assertEquals(late + 7 * D, v.taxDueAt);
    }

    @Test
    void payingEarlyRules() {
        Civ v = civ(1);
        CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, new W(32, 0));
        long due = v.taxDueAt;
        AtomicLong tr = new AtomicLong();
        assertEquals(CivUpgradeApi.PayResult.NOT_YET_DUE, CivUpgradeApi.pay(v, c, T0 + D, new W(99, 0), tr::addAndGet));
        assertEquals(0, tr.get());
        assertEquals(CivUpgradeApi.PayResult.PAID, CivUpgradeApi.pay(v, c, due - 10 * H, new W(99, 0), tr::addAndGet));
        assertEquals(due + 7 * D, v.taxDueAt);
        assertEquals(4, tr.get());
    }

    @Test
    void nothingOwedWithoutUpgrades() {
        Civ v = civ(4);
        assertEquals(CivUpgradeApi.PayResult.NOTHING_OWED, CivUpgradeApi.pay(v, c, T0 + 100 * D, new W(99, 0), x -> fail()));
        assertFalse(CivUpgradeApi.isLate(v, T0 + 100 * D));
        assertFalse(CivUpgradeApi.upgradesActive(v, c, T0));
        assertEquals(0, CivUpgradeApi.taxDueAt(v));
    }

    @Test
    void disbandClears() {
        Civ v = civ(2);
        CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, new W(64, 0));
        CivUpgradeApi.clear(v);
        assertEquals(0, CivUpgradeApi.totalLevels(v));
        assertEquals(0, v.taxDueAt);
        assertEquals(0, CivUpgradeApi.taxOwed(v, c));
        // rebuying starts a fresh clock
        CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0 + 50 * D, new W(64, 0));
        assertEquals(T0 + 57 * D, v.taxDueAt);
    }

    @Test
    void oldDataJsonLoadsWithNoUpgrades() {
        String json = "{\"version\":1,\"civs\":{\"c1\":{\"id\":\"c1\",\"name\":\"Rome\",\"leader\":\"u1\",\"members\":[\"u1\",\"u2\"]}}}";
        Civ v = HonorData.parse(json).civs.get("c1");
        assertEquals(0, CivUpgradeApi.totalLevels(v));
        assertFalse(CivUpgradeApi.upgradesActive(v, c, T0));
        assertFalse(CivUpgradeApi.isLate(v, T0 + 999 * D));
        assertEquals(0, CivUpgradeApi.taxOwed(v, c));
        assertTrue(CivUpgradeApi.buy(v, Upgrade.SPEED, c, T0, new W(32, 0)).ok());
        // round trip
        String out = new com.google.gson.Gson().toJson(HonorData.parse(new com.google.gson.Gson().toJson(HonorData.parse(json))));
        assertNotNull(out);
    }

    @Test
    void oldConfigWithoutUpgradeKeysKeepsDefaults() {
        HonorConfig k = new com.google.gson.Gson().fromJson("{\"minScore\":-500}", HonorConfig.class);
        assertEquals(32, CivUpgrades.emeraldCost(k, 1));
        assertEquals(4, k.taxPerLevelPerMemberPerWeek);
        assertEquals(7, k.taxIntervalDays);
        assertEquals(24, k.taxWarningHours);
        assertTrue(k.civUpgradesEnabled);
        assertEquals(7 * D, CivTax.intervalMs(k));
    }
}
