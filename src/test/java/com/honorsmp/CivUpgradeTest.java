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

/** Civ upgrades with the 1.6.5 daily upkeep paid in the upgrade's own item (no emeralds). */
class CivUpgradeTest {
    static final long H = 3_600_000L, D = 24 * H, T0 = 1_000_000_000L;
    static final String IRON = "minecraft:iron_ingot", GUN = "minecraft:gunpowder", RED = "minecraft:redstone",
            LAPIS = "minecraft:lapis_lazuli", GOLD = "minecraft:gold_ingot", DIA = "minecraft:diamond";
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
        assertEquals(GUN, CivUpgrades.itemOf(c, Upgrade.STRENGTH));
        assertEquals(RED, CivUpgrades.itemOf(c, Upgrade.SPEED));
        assertEquals(LAPIS, CivUpgrades.itemOf(c, Upgrade.HASTE));
        assertEquals(GOLD, CivUpgrades.itemOf(c, Upgrade.FORTITUDE));
        int[][] per = {{16, 32}, {16, 32}, {32, 64}, {16, 32}, {8, 16}};
        for (Upgrade u : Upgrade.values()) {
            assertEquals(per[u.ordinal()][0], CivUpgrades.upkeepPerDay(c, u, 1), u.id);
            assertEquals(per[u.ordinal()][1], CivUpgrades.upkeepPerDay(c, u, 2), u.id);
            assertEquals(0, CivUpgrades.upkeepPerDay(c, u, 0));
            assertNotEquals("minecraft:emerald", CivUpgrades.itemOf(c, u));
        }
        assertEquals(4, c.civBuyDays);
        assertEquals(8, c.civLevel2Diamonds);
        assertEquals(2, c.civUpkeepWarningHours);
        assertEquals(3, c.civUpkeepMaxAheadDays);
    }

    @Test
    void buyCostsAreFourDaysOfUpkeepAndLevelTwoAddsDiamonds() {
        assertEquals(m(IRON, 64), CivUpgrades.buyCost(c, Upgrade.RESISTANCE, 1));
        assertEquals(m(IRON, 128, DIA, 8), CivUpgrades.buyCost(c, Upgrade.RESISTANCE, 2));
        assertEquals(m(RED, 128), CivUpgrades.buyCost(c, Upgrade.SPEED, 1));
        assertEquals(m(RED, 256, DIA, 8), CivUpgrades.buyCost(c, Upgrade.SPEED, 2));
        assertEquals(m(GOLD, 32), CivUpgrades.buyCost(c, Upgrade.FORTITUDE, 1));
        assertEquals(m(LAPIS, 64), CivUpgrades.buyCost(c, Upgrade.HASTE, 1));
        assertEquals(m(GUN, 64), CivUpgrades.buyCost(c, Upgrade.STRENGTH, 1));
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
        W w = new W(LAPIS, 63);
        var r = CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, w);
        assertEquals(BuyCheck.NOT_ENOUGH_ITEMS, r.check());
        assertEquals(LAPIS, r.missing());
        assertEquals(63, w.count(LAPIS)); // nothing taken
        assertEquals(0, v.upkeepDueAt);
        w.have.put(LAPIS, 100);
        assertTrue(CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, w).ok());
        assertEquals(36, w.count(LAPIS));
        assertEquals(T0 + D, v.upkeepDueAt); // first upkeep one day after the first purchase
        // level 2: 4 days of level 2 upkeep (4 x 32 lapis) plus 8 diamonds
        assertEquals(BuyCheck.NOT_ENOUGH_ITEMS, CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0 + H, new W(LAPIS, 500, DIA, 7)).check());
        W w2 = new W(LAPIS, 128, DIA, 8);
        assertTrue(CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0 + H, w2).ok());
        assertEquals(0, w2.count(LAPIS));
        assertEquals(0, w2.count(DIA));
        assertEquals(2, CivUpgradeApi.ownedLevel(v, "haste"));
        assertEquals(T0 + D, v.upkeepDueAt); // a later purchase keeps the due date
        assertEquals(BuyCheck.MAX_LEVEL, CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, new W(LAPIS, 999, DIA, 99)).check());
    }

    @Test
    void emeraldsDoNothing() {
        Civ v = civ(1);
        assertEquals(BuyCheck.NOT_ENOUGH_ITEMS, CivUpgradeApi.buy(v, Upgrade.SPEED, c, T0, new W("minecraft:emerald", 9999)).check());
        assertEquals(0, CivUpgradeApi.totalLevels(v));
    }

    @Test
    void disabledSwitch() {
        c.civUpgradesEnabled = false;
        assertEquals(BuyCheck.DISABLED, CivUpgradeApi.buy(civ(1), Upgrade.SPEED, c, T0, new W(RED, 999)).check());
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
        assertEquals(m(IRON, 16, RED, 64, GOLD, 8), CivUpgradeApi.owedPerDay(v, c));
        // does not depend on the number of members
        v.members.remove("u4");
        assertEquals(m(IRON, 16, RED, 64, GOLD, 8), CivUpgradeApi.owedPerDay(v, c));
        // two upgrades paid in the same item add up
        c.civUpgradeItems.put("strength", "iron_ingot");
        v.upgrades.put("strength", 2);
        assertEquals(m(IRON, 48, RED, 64, GOLD, 8), CivUpgradeApi.owedPerDay(v, c));
    }

    @Test
    void dueAndWarningWindow() {
        Civ v = civ(2);
        assertFalse(CivUpgradeApi.warningDue(v, c, T0));
        CivUpgradeApi.buy(v, Upgrade.STRENGTH, c, T0, new W(GUN, 64));
        long due = T0 + D;
        assertFalse(CivUpgradeApi.warningDue(v, c, due - 2 * H - 1));
        assertTrue(CivUpgradeApi.warningDue(v, c, due - 2 * H));
        assertTrue(CivUpgradeApi.takeWarningOnce(v, c, due - H));
        assertFalse(CivUpgradeApi.takeWarningOnce(v, c, due - 30 * 60_000L)); // once only
        assertTrue(CivUpgradeApi.warningDue(v, c, due - 1)); // login warning still true
        assertFalse(CivUpgradeApi.isLate(v, due - 1));
        assertTrue(CivUpgradeApi.isLate(v, due));
        assertFalse(CivUpgradeApi.warningDue(v, c, due)); // late, not "warning"
    }

    @Test
    void lateSwitchesOffAndPayingRestores() {
        Civ v = civ(2);
        CivUpgradeApi.buy(v, Upgrade.RESISTANCE, c, T0, new W(IRON, 64));
        long due = T0 + D;
        assertTrue(CivUpgradeApi.upgradesActive(v, c, due - 1));
        assertEquals(1, CivUpgradeApi.activeLevel(v, Upgrade.RESISTANCE, c, due - 1));
        long late = due + 20 * D; // many days missed
        assertFalse(CivUpgradeApi.upgradesActive(v, c, late));
        assertEquals(0, CivUpgradeApi.activeLevel(v, Upgrade.RESISTANCE, c, late));
        assertEquals(1, CivUpgradeApi.ownedLevel(v, Upgrade.RESISTANCE)); // not lost
        assertEquals(BuyCheck.UPKEEP_UNPAID, CivUpgradeApi.buy(v, Upgrade.SPEED, c, late, new W(RED, 999)).check());
        assertEquals(m(IRON, 16), CivUpgradeApi.owedPerDay(v, c)); // one day only, debts do not stack
        // short by one: nothing changes
        var no = CivUpgradeApi.pay(v, c, late, m(IRON, 15));
        assertEquals(CivUpgradeApi.PayStatus.NOT_ENOUGH, no.status());
        assertTrue(no.taken().isEmpty());
        assertFalse(CivUpgradeApi.upgradesActive(v, c, late));
        assertEquals(due, v.upkeepDueAt);
        var ok = CivUpgradeApi.pay(v, c, late, m(IRON, 16));
        assertTrue(ok.paid());
        assertEquals(1, ok.days());
        assertEquals(m(IRON, 16), ok.taken());
        assertTrue(CivUpgradeApi.upgradesActive(v, c, late));
        assertEquals(late + D, v.upkeepDueAt); // missed days forgiven
        assertFalse(CivUpgradeApi.isLate(v, late + D - 1));
    }

    @Test
    void payAheadIsCappedAtThreeDays() {
        Civ v = civ(1);
        CivUpgradeApi.buy(v, Upgrade.FORTITUDE, c, T0, new W(GOLD, 32)); // 8 gold a day, due T0 + 1d
        long now = T0 + 2 * H;
        long due = v.upkeepDueAt;
        assertEquals(2, CivUpgradeApi.payableDays(v, c, now)); // due is 22h ahead: room for 2 whole days up to now+3d
        // offering ten days of gold pays only two, takes only two days' worth
        var r = CivUpgradeApi.pay(v, c, now, m(GOLD, 80));
        assertEquals(2, r.days());
        assertEquals(m(GOLD, 16), r.taken());
        assertEquals(due + 2 * D, v.upkeepDueAt);
        assertTrue(v.upkeepDueAt <= now + 3 * D);
        assertEquals(0, CivUpgradeApi.payableDays(v, c, now));
        var again = CivUpgradeApi.pay(v, c, now, m(GOLD, 80));
        assertEquals(CivUpgradeApi.PayStatus.PAID_AHEAD_MAX, again.status());
        assertEquals(due + 2 * D, v.upkeepDueAt); // unchanged
        // a day later one more day fits
        assertEquals(1, CivUpgradeApi.payableDays(v, c, now + D));
    }

    @Test
    void payingWhileLateAllowsThreeDaysFromNow() {
        Civ v = civ(1);
        CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, new W(LAPIS, 64));
        long late = T0 + 9 * D;
        assertEquals(3, CivUpgradeApi.payableDays(v, c, late));
        var r = CivUpgradeApi.pay(v, c, late, m(LAPIS, 1000));
        assertEquals(3, r.days());
        assertEquals(m(LAPIS, 48), r.taken());
        assertEquals(late + 3 * D, v.upkeepDueAt);
    }

    @Test
    void severalUpgradesNeedEveryItemForADay() {
        Civ v = civ(1);
        v.upgrades = new LinkedHashMap<>();
        v.upgrades.put("resistance", 1);
        v.upgrades.put("speed", 1);
        v.upkeepDueAt = T0 + D;
        // plenty of iron but no redstone: not even one day
        assertEquals(CivUpgradeApi.PayStatus.NOT_ENOUGH, CivUpgradeApi.pay(v, c, T0, m(IRON, 500)).status());
        // 2 days of iron, 1 day of redstone: one day
        var r = CivUpgradeApi.pay(v, c, T0, m(IRON, 32, RED, 32));
        assertEquals(1, r.days());
        assertEquals(m(IRON, 16, RED, 32), r.taken());
    }

    @Test
    void nothingOwedWithoutUpgrades() {
        Civ v = civ(4);
        assertEquals(CivUpgradeApi.PayStatus.NOTHING_OWED, CivUpgradeApi.pay(v, c, T0 + 100 * D, m(IRON, 99)).status());
        assertFalse(CivUpgradeApi.isLate(v, T0 + 100 * D));
        assertFalse(CivUpgradeApi.upgradesActive(v, c, T0));
        assertEquals(0, CivUpgradeApi.upkeepDueAt(v));
    }

    @Test
    void disbandClears() {
        Civ v = civ(2);
        CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0, new W(LAPIS, 64));
        CivUpgradeApi.clear(v);
        assertEquals(0, CivUpgradeApi.totalLevels(v));
        assertEquals(0, v.upkeepDueAt);
        assertTrue(CivUpgradeApi.owedPerDay(v, c).isEmpty());
        // rebuying starts a fresh clock
        CivUpgradeApi.buy(v, Upgrade.HASTE, c, T0 + 50 * D, new W(LAPIS, 64));
        assertEquals(T0 + 51 * D, v.upkeepDueAt);
    }

    @Test
    void pureHelpers() {
        assertEquals(2, CivUpkeep.coveredDays(m(IRON, 16, RED, 32), m(IRON, 40, RED, 64), 3));
        assertEquals(3, CivUpkeep.coveredDays(m(IRON, 16), m(IRON, 400), 3));
        assertEquals(0, CivUpkeep.coveredDays(Map.of(), m(IRON, 400), 3));
        assertEquals(m(IRON, 48), CivUpkeep.cost(m(IRON, 16), 3));
        assertEquals("32 redstone, 16 iron ingot", CivUpkeep.describe(m(RED, 32, IRON, 16)));
        assertEquals("lapis lazuli", CivUpkeep.pretty(LAPIS));
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
        assertEquals(T0 + D, rome.upkeepDueAt); // first upkeep 24 hours after load
        assertTrue(CivUpgradeApi.upgradesActive(rome, c, T0 + D - 1));
        assertFalse(CivUpgradeApi.upgradesActive(rome, c, T0 + D));
        assertFalse(CivUpgradeApi.migrate(rome, T0 + 5 * D)); // runs once: the date stays
        assertEquals(T0 + D, rome.upkeepDueAt);
        assertFalse(CivUpgradeApi.migrate(gaul, T0)); // no upgrades, no clock
        assertEquals(0, gaul.upkeepDueAt);
        String out = Honor.GSON.toJson(d);
        assertFalse(out.contains("taxDueAt"), out);
        assertFalse(out.contains("taxWarnedFor"), out);
        assertTrue(out.contains("upkeepDueAt"));
        assertEquals(T0 + D, HonorData.parse(out).civs.get("c1").upkeepDueAt);
    }

    @Test
    void oldDataJsonLoadsWithNoUpgrades() {
        String json = "{\"version\":1,\"civs\":{\"c1\":{\"id\":\"c1\",\"name\":\"Rome\",\"leader\":\"u1\",\"members\":[\"u1\",\"u2\"]}}}";
        Civ v = HonorData.parse(json).civs.get("c1");
        assertEquals(0, CivUpgradeApi.totalLevels(v));
        assertFalse(CivUpgradeApi.upgradesActive(v, c, T0));
        assertFalse(CivUpgradeApi.isLate(v, T0 + 999 * D));
        assertTrue(CivUpgradeApi.owedPerDay(v, c).isEmpty());
        assertTrue(CivUpgradeApi.buy(v, Upgrade.SPEED, c, T0, new W(RED, 128)).ok());
        String out = new com.google.gson.Gson().toJson(HonorData.parse(new com.google.gson.Gson().toJson(HonorData.parse(json))));
        assertNotNull(out);
    }

    @Test
    void oldConfigGetsUpkeepDefaultsAndDropsTaxKeys() {
        HonorConfig k = Honor.GSON.fromJson("{\"minScore\":-500,\"taxPerLevelPerMemberPerWeek\":9,\"upgradeEmeraldCost\":[1,2],\"taxIntervalDays\":7}", HonorConfig.class);
        k.migrate();
        assertEquals(IRON, CivUpgrades.itemOf(k, Upgrade.RESISTANCE));
        assertEquals(64, CivUpgrades.buyItemCost(k, Upgrade.RESISTANCE, 1));
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
        assertEquals(32, CivUpgrades.upkeepPerDay(k, Upgrade.SPEED, 1));
        assertEquals(RED, CivUpgrades.itemOf(k, Upgrade.SPEED));
    }
}
