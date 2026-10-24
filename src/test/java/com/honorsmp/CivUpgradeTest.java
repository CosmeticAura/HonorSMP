package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.CivUpgrades;
import com.honorsmp.logic.CivUpgrades.BuyCheck;
import com.honorsmp.logic.CivUpgrades.Upgrade;
import com.honorsmp.logic.CivUpkeep;
import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.Wallet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Civ upgrades with the 1.6.5 daily upkeep paid in the upgrade's own item (items changed in 1.7.1). */
class CivUpgradeTest {
    static final long H = 3_600_000L, D = 24 * H, T0 = 1_000_000_000L;
    static final String IRON = "minecraft:iron_ingot", GOLD_I = "minecraft:gold_ingot", DIAM = "minecraft:diamond",
            EMER = "minecraft:emerald", SCRAP = "minecraft:netherite_scrap", DIA = "minecraft:diamond";
    final HonorConfig c = new HonorConfig();

    static class W implements Wallet {
        final Map<String, Integer> have = new LinkedHashMap<>();
        W(Object... kv) { for (int i = 0; i < kv.length; i += 2) have.put((String) kv[i], (Integer) kv[i + 1]); }
        public int count(String id) { return have.getOrDefault(id, 0); }
        public void take(String id, int n) { have.merge(id, -n, Integer::sum); }
    }

    static Map<String, Integer> m(Object... kv) {
        Map<String, Integer> x = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) x.put((String) kv[i], (Integer) kv[i + 1]);
        return x;
    }

    Civ civ(int members) {
        Civ v = new Civ();
        v.id = "c1"; v.name = "Rome"; v.leader = "u0";
        for (int i = 0; i < members; i++) v.members.add("u" + i);
        return v;
    }

    // ---------------------------------------------------------------- items and costs

    @Test
    void defaultItemsAndUpkeep() {
        assertEquals(IRON, CivUpgrades.itemOf(c, Upgrade.RESISTANCE));
        assertEquals(GOLD_I, CivUpgrades.itemOf(c, Upgrade.STRENGTH));
        assertEquals(DIAM, CivUpgrades.itemOf(c, Upgrade.SPEED));
        assertEquals(EMER, CivUpgrades.itemOf(c, Upgrade.HASTE));
        assertEquals(SCRAP, CivUpgrades.itemOf(c, Upgrade.FORTITUDE));
        int[][] per = {{32, 64}, {16, 32}, {4, 8}, {32, 64}, {2, 4}};
        for (Upgrade u : Upgrade.values()) {
            assertEquals(per[u.ordinal()][0], CivUpgrades.upkeepPerDay(c, u, 1), u.id);
            assertEquals(per[u.ordinal()][1], CivUpgrades.upkeepPerDay(c, u, 2), u.id);
            assertEquals(0, CivUpgrades.upkeepPerDay(c, u, 0));
        }
        assertEquals(4, c.civBuyDays);
        assertEquals(8, c.civLevel2Diamonds);
        assertEquals(2, c.civUpkeepWarningHours);
        assertEquals(3, c.civUpkeepMaxAheadDays);
    }

    @Test
    void buyCostsAreFourDaysOfUpkeepAndLevelTwoAddsDiamonds() {
        assertEquals(m(IRON, 128), CivUpgrades.buyCost(c, Upgrade.RESISTANCE, 1));
        assertEquals(m(IRON, 256, DIA, 8), CivUpgrades.buyCost(c, Upgrade.RESISTANCE, 2));
        assertEquals(m(DIAM, 16), CivUpgrades.buyCost(c, Upgrade.SPEED, 1));
        assertEquals(m(DIAM, 40), CivUpgrades.buyCost(c, Upgrade.SPEED, 2)); // speed is paid in diamonds too: 4 x 8 + 8 add up
        assertEquals(m(SCRAP, 8), CivUpgrades.buyCost(c, Upgrade.FORTITUDE, 1));
        assertEquals(m(EMER, 128), CivUpgrades.buyCost(c, Upgrade.HASTE, 1));
        assertEquals(m(GOLD_I, 64), CivUpgrades.buyCost(c, Upgrade.STRENGTH, 1));
        // tunable
        c.civBuyDays = 2;
        c.civLevel2Diamonds = 3;
        c.civUpkeepPerDay.put("haste", new int[] {10, 20});
        c.civUpgradeItems.put("haste", "amethyst_shard");
        assertEquals(m("minecraft:amethyst_shard", 20), CivUpgrades.buyCost(c, Upgrade.HASTE, 1));
        assertEquals(m("minecraft:amethyst_shard", 40, DIA, 3), CivUpgrades.buyCost(c, Upgrade.HASTE, 2));
        // effect values are unchanged
        assertEquals(10, CivUpgrades.resistancePercent(c, 2));
        assertEquals(0.5, CivUpgrades.strengthBonus(c, 1));
        assertEquals(4, CivUpgrades.fortitudeHp(c, 2));
        assertEquals(0, CivUpgrades.speedPercent(c, 0));
    }

    @Test
    void buyFlow() {
        Civ v = civ(3);
        W w = new W(EMER, 127);
        var r = CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, w);
        assertEquals(BuyCheck.NOT_ENOUGH_ITEMS, r.check());
        assertEquals(EMER, r.missing());
        assertEquals(127, w.count(EMER)); // nothing taken
        assertEquals(0, CivUpgradeApi.dueAt(v, Upgrade.HASTE));
        w.have.put(EMER, 200);
        assertTrue(CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, w).ok());
        assertEquals(72, w.count(EMER));
        assertEquals(T0 + D, CivUpgradeApi.dueAt(v, Upgrade.HASTE)); // first upkeep one day after the first purchase
        // level 2: 4 days of level 2 upkeep (4 x 64 emeralds) plus 8 diamonds
        assertEquals(BuyCheck.NOT_ENOUGH_ITEMS, CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0 + H, new W(EMER, 500, DIA, 7)).check());
        W w2 = new W(EMER, 256, DIA, 8);
        assertTrue(CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0 + H, w2).ok());
        assertEquals(0, w2.count(EMER));
        assertEquals(0, w2.count(DIA));
        assertEquals(2, CivUpgradeApi.ownedLevel(v, "haste"));
        assertEquals(T0 + D, CivUpgradeApi.dueAt(v, Upgrade.HASTE)); // a later purchase keeps the due date
        assertEquals(BuyCheck.MAX_LEVEL, CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, new W(EMER, 999, DIA, 99)).check());
    }

    @Test
    void wrongItemDoesNothing() { // emeralds are Haste's item now, not Speed's
        Civ v = civ(1);
        assertEquals(BuyCheck.NOT_ENOUGH_ITEMS, CivUpgradeApi.buy(v, Upgrade.SPEED, c, T0, new W("minecraft:emerald", 9999)).check());
        assertEquals(0, CivUpgradeApi.totalLevels(v));
    }

    @Test
    void disabledSwitch() {
        c.civUpgradesEnabled = false;
        assertEquals(BuyCheck.DISABLED, CivUpgradeApi.buy(civ(1), Upgrade.SPEED, c, T0, new W(DIAM, 999)).check());
    }

    // ---------------------------------------------------------------- upkeep math

    @Test
    void owedPerDaySumsEveryOwnedLevel() {
        Civ v = civ(5);
        assertTrue(CivUpgradeApi.owedPerDay(v, c).isEmpty());
        v.upgrades = new LinkedHashMap<>();
        v.upgrades.put("resistance", 1);
        v.upgrades.put("speed", 2);
        v.upgrades.put("fortitude", 1);
        assertEquals(m(IRON, 32, DIAM, 8, SCRAP, 2), CivUpgradeApi.owedPerDay(v, c));
        // does not depend on the number of members
        v.members.remove("u4");
        assertEquals(m(IRON, 32, DIAM, 8, SCRAP, 2), CivUpgradeApi.owedPerDay(v, c));
        // two upgrades paid in the same item add up
        c.civUpgradeItems.put("strength", "iron_ingot");
        v.upgrades.put("strength", 2);
        assertEquals(m(IRON, 64, DIAM, 8, SCRAP, 2), CivUpgradeApi.owedPerDay(v, c));
    }

    @Test
    void dueAndWarningWindow() {
        Civ v = civ(2);
        Upgrade S = Upgrade.STRENGTH;
        assertFalse(CivUpgradeApi.warningDue(v, S, c, T0));
        CivUpgradeApi.buy(v, S, c, T0, new W(GOLD_I, 64));
        long due = T0 + D;
        assertFalse(CivUpgradeApi.warningDue(v, S, c, due - 2 * H - 1));
        assertTrue(CivUpgradeApi.warningDue(v, S, c, due - 2 * H));
        assertTrue(CivUpgradeApi.takeWarningOnce(v, S, c, due - H));
        assertFalse(CivUpgradeApi.takeWarningOnce(v, S, c, due - 30 * 60_000L)); // once only
        assertTrue(CivUpgradeApi.warningDue(v, S, c, due - 1)); // login warning still true
        assertFalse(CivUpgradeApi.isLate(v, S, due - 1));
        assertTrue(CivUpgradeApi.isLate(v, S, due));
        assertTrue(CivUpgradeApi.isLate(v, due));
        assertFalse(CivUpgradeApi.warningDue(v, S, c, due)); // late, not "warning"
    }

    @Test
    void lateSwitchesOffAndPayingRestores() {
        Civ v = civ(2);
        Upgrade R = Upgrade.RESISTANCE;
        CivUpgradeApi.buy(v, R, c, T0, new W(IRON, 128));
        long due = T0 + D;
        assertTrue(CivUpgradeApi.upgradesActive(v, c, due - 1));
        assertEquals(1, CivUpgradeApi.activeLevel(v, R, c, due - 1));
        long late = due + 20 * D; // many days missed
        assertFalse(CivUpgradeApi.upgradesActive(v, c, late));
        assertEquals(0, CivUpgradeApi.activeLevel(v, R, c, late));
        assertEquals(1, CivUpgradeApi.ownedLevel(v, R)); // not lost
        assertEquals(BuyCheck.UPKEEP_UNPAID, CivUpgradeApi.buy(v, R, c, late, new W(IRON, 999, DIA, 99)).check());
        assertEquals(m(IRON, 32), CivUpgradeApi.owedPerDay(v, c)); // one day only, debts do not stack
        var no = CivUpgradeApi.pay(v, R, c, late, m(IRON, 31));
        assertEquals(CivUpgradeApi.PayStatus.NOT_ENOUGH, no.status());
        assertTrue(no.taken().isEmpty());
        assertFalse(CivUpgradeApi.upgradesActive(v, c, late));
        assertEquals(due, CivUpgradeApi.dueAt(v, R));
        var ok = CivUpgradeApi.pay(v, R, c, late, m(IRON, 32));
        assertTrue(ok.paid());
        assertEquals(1, ok.days());
        assertEquals(m(IRON, 32), ok.taken());
        assertTrue(CivUpgradeApi.upgradesActive(v, c, late));
        assertEquals(late + D, CivUpgradeApi.dueAt(v, R)); // missed days forgiven
        assertFalse(CivUpgradeApi.isLate(v, late + D - 1));
    }

    @Test
    void payAheadIsCappedAtThreeDays() {
        Civ v = civ(1);
        Upgrade F = Upgrade.FORTITUDE;
        CivUpgradeApi.buy(v, F, c, T0, new W(SCRAP, 8)); // 2 netherite scrap a day, due T0 + 1d
        long now = T0 + 2 * H;
        long due = CivUpgradeApi.dueAt(v, F);
        assertEquals(2, CivUpgradeApi.payableDays(v, F, c, now));
        var r = CivUpgradeApi.pay(v, F, c, now, m(SCRAP, 80));
        assertEquals(2, r.days());
        assertEquals(m(SCRAP, 4), r.taken());
        assertEquals(due + 2 * D, CivUpgradeApi.dueAt(v, F));
        assertTrue(CivUpgradeApi.dueAt(v, F) <= now + 3 * D);
        assertEquals(0, CivUpgradeApi.payableDays(v, F, c, now));
        var again = CivUpgradeApi.pay(v, F, c, now, m(SCRAP, 80));
        assertEquals(CivUpgradeApi.PayStatus.PAID_AHEAD_MAX, again.status());
        assertEquals(due + 2 * D, CivUpgradeApi.dueAt(v, F)); // unchanged
        assertEquals(1, CivUpgradeApi.payableDays(v, F, c, now + D));
    }

    @Test
    void payingWhileLateAllowsThreeDaysFromNow() {
        Civ v = civ(1);
        CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, new W(EMER, 128));
        long late = T0 + 9 * D;
        assertEquals(3, CivUpgradeApi.payableDays(v, Upgrade.HASTE, c, late));
        var r = CivUpgradeApi.pay(v, Upgrade.HASTE, c, late, m(EMER, 1000));
        assertEquals(3, r.days());
        assertEquals(m(EMER, 96), r.taken());
        assertEquals(late + 3 * D, CivUpgradeApi.dueAt(v, Upgrade.HASTE));
    }

    // ---------------------------------------------------------------- 1.6.9: one clock per upgrade

    Civ two() {
        Civ v = civ(2);
        CivUpgradeApi.buy(v, Upgrade.RESISTANCE, c, T0, new W(IRON, 128));
        CivUpgradeApi.buy(v, Upgrade.SPEED, c, T0 + 12 * H, new W(DIAM, 128));
        return v;
    }

    @Test
    void eachUpgradeHasItsOwnClock() {
        Civ v = two();
        assertEquals(T0 + D, CivUpgradeApi.dueAt(v, Upgrade.RESISTANCE));
        assertEquals(T0 + 12 * H + D, CivUpgradeApi.dueAt(v, Upgrade.SPEED));
        assertEquals(0, CivUpgradeApi.dueAt(v, Upgrade.HASTE));
    }

    @Test
    void onlyTheLateUpgradeSwitchesOff() {
        Civ v = two();
        long now = T0 + D + H; // resistance late, speed still paid
        assertTrue(CivUpgradeApi.isLate(v, Upgrade.RESISTANCE, now));
        assertFalse(CivUpgradeApi.isLate(v, Upgrade.SPEED, now));
        assertEquals(0, CivUpgradeApi.activeLevel(v, Upgrade.RESISTANCE, c, now));
        assertEquals(1, CivUpgradeApi.activeLevel(v, Upgrade.SPEED, c, now));
        assertTrue(CivUpgradeApi.isLate(v, now));
        assertEquals(java.util.List.of(Upgrade.RESISTANCE), CivUpgradeApi.lateUpgrades(v, now));
        // buying another level of the paid one still works; the late one is blocked
        assertEquals(BuyCheck.UPKEEP_UNPAID, CivUpgradeApi.checkBuy(v, Upgrade.RESISTANCE, c, now, new W(IRON, 999, DIA, 99)));
        assertEquals(BuyCheck.OK, CivUpgradeApi.checkBuy(v, Upgrade.SPEED, c, now, new W(DIAM, 999, DIA, 99)));
        // paying speed does not touch resistance
        long resDue = CivUpgradeApi.dueAt(v, Upgrade.RESISTANCE);
        assertTrue(CivUpgradeApi.pay(v, Upgrade.SPEED, c, now, m(DIAM, 4)).paid());
        assertEquals(resDue, CivUpgradeApi.dueAt(v, Upgrade.RESISTANCE));
        assertTrue(CivUpgradeApi.isLate(v, Upgrade.RESISTANCE, now));
    }

    @Test
    void payAheadCapIsPerUpgrade() {
        Civ v = two();
        long now = T0 + 13 * H;
        var a = CivUpgradeApi.pay(v, Upgrade.RESISTANCE, c, now, m(IRON, 999));
        assertTrue(a.paid());
        assertEquals(0, CivUpgradeApi.payableDays(v, Upgrade.RESISTANCE, c, now));
        assertTrue(CivUpgradeApi.dueAt(v, Upgrade.RESISTANCE) <= now + 3 * D);
        // speed still has its own room
        assertTrue(CivUpgradeApi.payableDays(v, Upgrade.SPEED, c, now) > 0);
        assertEquals(CivUpgradeApi.PayStatus.PAID_AHEAD_MAX, CivUpgradeApi.pay(v, Upgrade.RESISTANCE, c, now, m(IRON, 999)).status());
        assertTrue(CivUpgradeApi.pay(v, Upgrade.SPEED, c, now, m(DIAM, 999)).paid());
        // paying one upgrade takes only that upgrade's item
        assertTrue(CivUpgradeApi.pay(v, Upgrade.HASTE, c, now, m(EMER, 999)).status() == CivUpgradeApi.PayStatus.NOTHING_OWED);
    }

    @Test
    void pausedUpgradeCostsNothingAndNeverWarnsOrGoesLate() {
        Civ v = two();
        long now = T0 + 2 * H;
        assertTrue(CivUpgradeApi.pause(v, Upgrade.RESISTANCE, now));
        assertFalse(CivUpgradeApi.pause(v, Upgrade.RESISTANCE, now)); // already paused
        assertFalse(CivUpgradeApi.pause(v, Upgrade.HASTE, now)); // not owned
        assertTrue(CivUpgradeApi.isPaused(v, Upgrade.RESISTANCE));
        assertEquals(1, CivUpgradeApi.ownedLevel(v, Upgrade.RESISTANCE)); // level kept
        assertEquals(0, CivUpgradeApi.activeLevel(v, Upgrade.RESISTANCE, c, now)); // off
        assertEquals(m(DIAM, 4), CivUpgradeApi.owedPerDay(v, c)); // no iron owed
        assertTrue(CivUpgradeApi.owedPerDay(v, Upgrade.RESISTANCE, c).isEmpty());
        long way = T0 + 40 * D; // far past any due date
        assertFalse(CivUpgradeApi.isLate(v, Upgrade.RESISTANCE, way));
        assertFalse(CivUpgradeApi.warningDue(v, Upgrade.RESISTANCE, c, T0 + D - H));
        assertFalse(CivUpgradeApi.takeWarningOnce(v, Upgrade.RESISTANCE, c, T0 + D - H));
        assertFalse(CivUpgradeApi.lateUpgrades(v, T0 + D + H).contains(Upgrade.RESISTANCE)); // no [Unpaid] for a paused one
        assertFalse(CivUpgradeApi.isLate(v, T0 + D + H)); // speed is still paid, resistance paused
        assertEquals(BuyCheck.PAUSED, CivUpgradeApi.checkBuy(v, Upgrade.RESISTANCE, c, now, new W(IRON, 999, DIA, 99)));
        // survives a save and load
        Civ back = HonorData.parse(Honor.GSON.toJson(snapshot(v))).civs.get("c1");
        assertTrue(CivUpgradeApi.isPaused(back, Upgrade.RESISTANCE));
    }

    @Test
    void payingAPausedUpgradeResumesWithItsRemainingTime() {
        Civ v = two();
        long pauseAt = T0 + 2 * H; // 22 hours of paid time left
        CivUpgradeApi.pause(v, Upgrade.RESISTANCE, pauseAt);
        long later = T0 + 30 * D;
        var r = CivUpgradeApi.pay(v, Upgrade.RESISTANCE, c, later, m(IRON, 32));
        assertTrue(r.paid());
        assertTrue(r.resumed());
        assertFalse(CivUpgradeApi.isPaused(v, Upgrade.RESISTANCE));
        assertEquals(later + 22 * H + D, CivUpgradeApi.dueAt(v, Upgrade.RESISTANCE));
        assertEquals(1, CivUpgradeApi.activeLevel(v, Upgrade.RESISTANCE, c, later));
        assertFalse(CivUpgradeApi.isLate(v, Upgrade.RESISTANCE, later + 5 * H));
    }

    static HonorData snapshot(Civ v) {
        HonorData d = new HonorData();
        d.civs.put(v.id, v);
        return d;
    }

    @Test
    void nothingOwedWithoutUpgrades() {
        Civ v = civ(4);
        assertEquals(CivUpgradeApi.PayStatus.NOTHING_OWED, CivUpgradeApi.pay(v, Upgrade.SPEED, c, T0 + 100 * D, m(IRON, 99)).status());
        assertFalse(CivUpgradeApi.isLate(v, T0 + 100 * D));
        assertFalse(CivUpgradeApi.upgradesActive(v, c, T0));
        assertEquals(0, CivUpgradeApi.dueAt(v, Upgrade.SPEED));
    }

    @Test
    void disbandClears() {
        Civ v = civ(2);
        CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, new W(EMER, 128));
        CivUpgradeApi.clear(v);
        assertEquals(0, CivUpgradeApi.totalLevels(v));
        assertEquals(0, CivUpgradeApi.dueAt(v, Upgrade.HASTE));
        assertNull(v.upgradeDue);
        assertTrue(CivUpgradeApi.owedPerDay(v, c).isEmpty());
        // rebuying starts a fresh clock
        CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0 + 50 * D, new W(EMER, 128));
        assertEquals(T0 + 51 * D, CivUpgradeApi.dueAt(v, Upgrade.HASTE));
    }

    @Test
    void pureHelpers() {
        assertEquals(2, CivUpkeep.coveredDays(m(IRON, 16, DIAM, 32), m(IRON, 40, DIAM, 64), 3));
        assertEquals(3, CivUpkeep.coveredDays(m(IRON, 16), m(IRON, 400), 3));
        assertEquals(0, CivUpkeep.coveredDays(Map.of(), m(IRON, 400), 3));
        assertEquals(m(IRON, 48), CivUpkeep.cost(m(IRON, 16), 3));
        assertEquals("32 diamond, 16 iron ingot", CivUpkeep.describe(m(DIAM, 32, IRON, 16)));
        assertEquals("emerald", CivUpkeep.pretty(EMER));
        assertEquals(T0 + D, CivUpkeep.migrateDue(2, 0, T0));
        assertEquals(555, CivUpkeep.migrateDue(2, 555, T0));
        assertEquals(0, CivUpkeep.migrateDue(0, 0, T0));
    }

    // ---------------------------------------------------------------- migration and old files

    @Test
    void oldWeeklyTaxDataIsDroppedAndUpgradesKeepAFreshDay() {
        String json = "{\"version\":3,\"civs\":{\"c1\":{\"id\":\"c1\",\"name\":\"Rome\",\"leader\":\"u1\",\"members\":[\"u1\",\"u2\"],"
                + "\"upgrades\":{\"haste\":2,\"speed\":1},\"taxDueAt\":123456,\"taxWarnedFor\":123456},"
                + "\"c2\":{\"id\":\"c2\",\"name\":\"Gaul\",\"leader\":\"u3\",\"members\":[\"u3\"],\"taxDueAt\":5}}}";
        HonorData d = HonorData.parse(json);
        Civ rome = d.civs.get("c1"), gaul = d.civs.get("c2");
        assertEquals(0, rome.upkeepDueAt);
        assertEquals(2, CivUpgradeApi.ownedLevel(rome, "haste")); // keeps what it owns
        assertTrue(CivUpgradeApi.migrate(rome, T0));
        assertEquals(T0 + D, CivUpgradeApi.dueAt(rome, Upgrade.HASTE)); // first upkeep 24 hours after load, per upgrade
        assertEquals(T0 + D, CivUpgradeApi.dueAt(rome, Upgrade.SPEED));
        assertTrue(CivUpgradeApi.upgradesActive(rome, c, T0 + D - 1));
        assertFalse(CivUpgradeApi.upgradesActive(rome, c, T0 + D));
        assertFalse(CivUpgradeApi.migrate(rome, T0 + 5 * D)); // runs once: the dates stay
        assertEquals(T0 + D, CivUpgradeApi.dueAt(rome, Upgrade.HASTE));
        assertFalse(CivUpgradeApi.migrate(gaul, T0)); // no upgrades, no clock
        assertNull(gaul.upgradeDue);
        String out = Honor.GSON.toJson(d);
        assertFalse(out.contains("taxDueAt"), out);
        assertFalse(out.contains("taxWarnedFor"), out);
        assertTrue(out.contains("upgradeDue"));
        assertEquals(T0 + D, CivUpgradeApi.dueAt(HonorData.parse(out).civs.get("c1"), Upgrade.HASTE));
    }

    @Test
    void migrationGivesEveryOwnedUpgradeTheSharedPaidTime() {
        // 1.6.8 file: one shared date; both upgrades must keep exactly that much paid time
        long shared = T0 + 2 * D + 5 * H;
        String json = "{\"version\":3,\"civs\":{\"c1\":{\"id\":\"c1\",\"name\":\"Rome\",\"leader\":\"u1\",\"members\":[\"u1\"],"
                + "\"upgrades\":{\"resistance\":1,\"fortitude\":2},\"upkeepDueAt\":" + shared + ",\"upkeepWarnedFor\":" + shared + "}}}";
        Civ rome = HonorData.parse(json).civs.get("c1");
        assertTrue(CivUpgradeApi.migrate(rome, T0));
        assertEquals(shared, CivUpgradeApi.dueAt(rome, Upgrade.RESISTANCE));
        assertEquals(shared, CivUpgradeApi.dueAt(rome, Upgrade.FORTITUDE));
        assertEquals(0, CivUpgradeApi.dueAt(rome, Upgrade.SPEED));
        assertFalse(CivUpgradeApi.isLate(rome, shared - 1));
        assertTrue(CivUpgradeApi.isLate(rome, shared));
        // an already-late 1.6.8 civ stays late for both
        Civ late = HonorData.parse(json.replace(String.valueOf(shared), String.valueOf(T0 - D))).civs.get("c1");
        CivUpgradeApi.migrate(late, T0);
        assertEquals(2, CivUpgradeApi.lateUpgrades(late, T0).size());
        assertFalse(CivUpgradeApi.migrate(rome, T0)); // idempotent
    }

    @Test
    void oldDataJsonLoadsWithNoUpgrades() {
        String json = "{\"version\":1,\"civs\":{\"c1\":{\"id\":\"c1\",\"name\":\"Rome\",\"leader\":\"u1\",\"members\":[\"u1\",\"u2\"]}}}";
        Civ v = HonorData.parse(json).civs.get("c1");
        assertEquals(0, CivUpgradeApi.totalLevels(v));
        assertFalse(CivUpgradeApi.upgradesActive(v, c, T0));
        assertFalse(CivUpgradeApi.isLate(v, T0 + 999 * D));
        assertTrue(CivUpgradeApi.owedPerDay(v, c).isEmpty());
        assertTrue(CivUpgradeApi.buy(v, Upgrade.SPEED, c, T0, new W(DIAM, 128)).ok());
        String out = new com.google.gson.Gson().toJson(HonorData.parse(new com.google.gson.Gson().toJson(HonorData.parse(json))));
        assertNotNull(out);
    }

    @Test
    void oldConfigGetsUpkeepDefaultsAndDropsTaxKeys() {
        HonorConfig k = Honor.GSON.fromJson("{\"minScore\":-500,\"taxPerLevelPerMemberPerWeek\":9,\"upgradeEmeraldCost\":[1,2],\"taxIntervalDays\":7}", HonorConfig.class);
        k.migrate();
        assertEquals(IRON, CivUpgrades.itemOf(k, Upgrade.RESISTANCE));
        assertEquals(128, CivUpgrades.buyItemCost(k, Upgrade.RESISTANCE, 1));
        assertTrue(k.civUpgradesEnabled);
        String out = Honor.GSON.toJson(k);
        for (String old : new String[] {"taxPerLevelPerMemberPerWeek", "taxIntervalDays", "taxWarningHours", "upgradeEmeraldCost", "upgradeDiamondCost"})
            assertFalse(out.contains(old), old);
        for (String key : new String[] {"civUpgradeItems", "civUpkeepPerDay", "civBuyDays", "civLevel2Diamonds"}) assertTrue(out.contains(key), key);
        // an edited config round-trips
        k.civUpkeepPerDay.put("speed", new int[] {1, 2});
        HonorConfig back = Honor.GSON.fromJson(Honor.GSON.toJson(k), HonorConfig.class);
        assertEquals(2, CivUpgrades.upkeepPerDay(back, Upgrade.SPEED, 2));
    }

    @Test
    void partialUpkeepMapsFallBackToDefaults() {
        HonorConfig k = new HonorConfig();
        k.civUpkeepPerDay = new LinkedHashMap<>();
        k.civUpgradeItems = new LinkedHashMap<>();
        assertEquals(4, CivUpgrades.upkeepPerDay(k, Upgrade.SPEED, 1));
        assertEquals(DIAM, CivUpgrades.itemOf(k, Upgrade.SPEED));
    }

    // ---------------------------------------------------------------- 1.7.1: new defaults and their migration

    /** A 1.7.0 config file as it sits on a live server: the old default items and amounts, no civUpgradeItemsVersion. */
    static final String OLD_170_CONFIG = "{\"civUpgradeItems\":{\"resistance\":\"minecraft:iron_ingot\",\"strength\":\"minecraft:gunpowder\","
            + "\"speed\":\"minecraft:redstone\",\"haste\":\"minecraft:lapis_lazuli\",\"fortitude\":\"minecraft:gold_ingot\"},"
            + "\"civUpkeepPerDay\":{\"resistance\":[16,32],\"strength\":[16,32],\"speed\":[32,64],\"haste\":[16,32],\"fortitude\":[8,16]}}";

    @Test
    void oldDefaultsMoveToTheNewOnes() {
        HonorConfig k = Honor.GSON.fromJson(OLD_170_CONFIG, HonorConfig.class);
        assertEquals(0, k.civUpgradeItemsVersion);
        assertTrue(k.migrate());
        assertEquals(1, k.civUpgradeItemsVersion);
        assertEquals(IRON, CivUpgrades.itemOf(k, Upgrade.RESISTANCE));
        assertEquals(GOLD_I, CivUpgrades.itemOf(k, Upgrade.STRENGTH));
        assertEquals(DIAM, CivUpgrades.itemOf(k, Upgrade.SPEED));
        assertEquals(EMER, CivUpgrades.itemOf(k, Upgrade.HASTE));
        assertEquals(SCRAP, CivUpgrades.itemOf(k, Upgrade.FORTITUDE));
        int[][] per = {{32, 64}, {16, 32}, {4, 8}, {32, 64}, {2, 4}};
        for (Upgrade u : Upgrade.values()) {
            assertEquals(per[u.ordinal()][0], CivUpgrades.upkeepPerDay(k, u, 1), u.id);
            assertEquals(per[u.ordinal()][1], CivUpgrades.upkeepPerDay(k, u, 2), u.id);
        }
        // runs once
        assertFalse(k.migrate());
    }

    @Test
    void handEditedValuesSurviveTheMigration() {
        String json = "{\"civUpgradeItems\":{\"resistance\":\"minecraft:copper_ingot\",\"strength\":\"minecraft:gunpowder\","
                + "\"speed\":\"minecraft:redstone\",\"haste\":\"minecraft:lapis_lazuli\",\"fortitude\":\"minecraft:gold_ingot\"},"
                + "\"civUpkeepPerDay\":{\"resistance\":[16,32],\"strength\":[20,40],\"speed\":[32,64],\"haste\":[16,32],\"fortitude\":[8,16]}}";
        HonorConfig k = Honor.GSON.fromJson(json, HonorConfig.class);
        k.migrate();
        assertEquals("minecraft:copper_ingot", CivUpgrades.itemOf(k, Upgrade.RESISTANCE)); // item edited by hand: kept
        assertEquals(32, CivUpgrades.upkeepPerDay(k, Upgrade.RESISTANCE, 1));              // amount untouched default: moves
        assertEquals(GOLD_I, CivUpgrades.itemOf(k, Upgrade.STRENGTH));                      // item default: moves
        assertEquals(20, CivUpgrades.upkeepPerDay(k, Upgrade.STRENGTH, 1));                 // amount edited: kept
        assertEquals(40, CivUpgrades.upkeepPerDay(k, Upgrade.STRENGTH, 2));
        assertEquals(DIAM, CivUpgrades.itemOf(k, Upgrade.SPEED));
        // a migrated, then re-edited config is not touched again
        k.civUpgradeItems.put("speed", "minecraft:redstone");
        k.civUpkeepPerDay.put("speed", new int[] {32, 64});
        assertFalse(k.migrate());
        assertEquals("minecraft:redstone", CivUpgrades.itemOf(k, Upgrade.SPEED));
        assertEquals(32, CivUpgrades.upkeepPerDay(k, Upgrade.SPEED, 1));
    }

    @Test
    void daysAlreadyPaidStayPaidAfterTheChange() {
        Civ v = civ(2);
        CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, new W(EMER, 128));
        long due = CivUpgradeApi.dueAt(v, Upgrade.HASTE);
        HonorConfig k = Honor.GSON.fromJson(OLD_170_CONFIG, HonorConfig.class);
        k.migrate();
        assertEquals(due, CivUpgradeApi.dueAt(v, Upgrade.HASTE)); // the migration touches only config, not the paid time
        assertEquals(m(EMER, 32), CivUpgradeApi.owedPerDay(v, k)); // the next payment uses the new item and amount
    }
}
