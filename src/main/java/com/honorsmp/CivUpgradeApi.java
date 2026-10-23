package com.honorsmp;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.CivUpgrades;
import com.honorsmp.logic.CivUpgrades.BuyCheck;
import com.honorsmp.logic.CivUpgrades.Upgrade;
import com.honorsmp.logic.CivUpkeep;
import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.Wallet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin, Minecraft-free glue between HonorData.Civ and the pure upgrade/upkeep rules. Every method is null-safe for civs
 * loaded from a 1.5.0 data.json. Callers mark the data dirty after a change.
 * Permission (leader/officer) is NOT checked here; see {@link #canManage}.
 */
public final class CivUpgradeApi {
    private CivUpgradeApi() {}

    /** Result of {@link #buy}. {@code missing} is the item id that was short (NOT_ENOUGH_ITEMS only). */
    public record BuyResult(BuyCheck check, int newLevel, Map<String, Integer> taken, String missing) {
        public boolean ok() { return check == BuyCheck.OK; }
    }

    public enum PayStatus { PAID, NOTHING_OWED, PAID_AHEAD_MAX, NOT_ENOUGH }

    /** Result of {@link #pay}: the days paid and the items that must be taken from what was offered. */
    public record PayResult(PayStatus status, int days, Map<String, Integer> taken) {
        public boolean paid() { return status == PayStatus.PAID; }
    }

    public static boolean canManage(Civ civ, String uuid) {
        return civ != null && uuid != null && (uuid.equals(civ.leader) || (civ.officers != null && civ.officers.contains(uuid)));
    }

    // ---------------------------------------------------------------- levels

    /** Owned level (0..2), ignoring whether the upkeep is late. */
    public static int ownedLevel(Civ civ, Upgrade u) {
        if (civ == null || civ.upgrades == null) return 0;
        Integer v = civ.upgrades.get(u.id);
        return v == null ? 0 : Math.max(0, Math.min(CivUpgrades.MAX_LEVEL, v));
    }

    public static int ownedLevel(Civ civ, String id) {
        Upgrade u = Upgrade.byId(id);
        return u == null ? 0 : ownedLevel(civ, u);
    }

    /** Level that should currently take effect: owned level, or 0 when switched off (upkeep late / feature disabled). */
    public static int activeLevel(Civ civ, Upgrade u, HonorConfig c, long now) {
        return upgradesActive(civ, c, now) ? ownedLevel(civ, u) : 0;
    }

    public static int totalLevels(Civ civ) {
        int n = 0;
        for (Upgrade u : Upgrade.values()) n += ownedLevel(civ, u);
        return n;
    }

    public static boolean hasUpgrades(Civ civ) { return totalLevels(civ) > 0; }

    public static boolean upgradesActive(Civ civ, HonorConfig c, long now) {
        return civ != null && CivUpkeep.upgradesActive(c, totalLevels(civ), civ.upkeepDueAt, now);
    }

    /** Removes every upgrade and the upkeep clock (disband, reset). No refund. */
    public static void clear(Civ civ) {
        if (civ == null) return;
        civ.upgrades = null;
        civ.upkeepDueAt = 0;
        civ.upkeepWarnedFor = 0;
    }

    /**
     * 1.6.5, on load: a civ that owns upgrades but has no upkeep date (it came from the weekly-tax versions, whose
     * taxDueAt field is dropped) gets its first upkeep due 24 hours from now. Returns true when it changed.
     */
    public static boolean migrate(Civ civ, long now) {
        if (civ == null) return false;
        long due = CivUpkeep.migrateDue(totalLevels(civ), civ.upkeepDueAt, now);
        if (due == civ.upkeepDueAt) return false;
        civ.upkeepDueAt = due;
        civ.upkeepWarnedFor = 0;
        return true;
    }

    // ---------------------------------------------------------------- buying

    public static BuyCheck checkBuy(Civ civ, Upgrade u, HonorConfig c, long now, Wallet w) {
        return CivUpgrades.canBuy(c, u, ownedLevel(civ, u), isLate(civ, now), have(CivUpgrades.buyCost(c, u, ownedLevel(civ, u) + 1), w));
    }

    private static Map<String, Integer> have(Map<String, Integer> need, Wallet w) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String id : need.keySet()) m.put(id, w.count(id));
        return m;
    }

    /**
     * Buys the next level: checks, takes the items from the wallet, sets the level, and starts the upkeep clock if this
     * is the civ's first upgrade level. Nothing changes unless the result is OK. Not allowed while the upkeep is late.
     */
    public static BuyResult buy(Civ civ, Upgrade u, HonorConfig c, long now, Wallet w) {
        int cur = ownedLevel(civ, u);
        int next = cur + 1;
        Map<String, Integer> cost = CivUpgrades.buyCost(c, u, next);
        BuyCheck chk = CivUpgrades.canBuy(c, u, cur, isLate(civ, now), have(cost, w));
        if (chk != BuyCheck.OK) {
            String miss = chk == BuyCheck.NOT_ENOUGH_ITEMS ? CivUpgrades.missing(cost, have(cost, w)) : null;
            return new BuyResult(chk, cur, Map.of(), miss);
        }
        boolean first = totalLevels(civ) == 0;
        for (var e : cost.entrySet()) w.take(e.getKey(), e.getValue());
        if (civ.upgrades == null) civ.upgrades = new LinkedHashMap<>();
        civ.upgrades.put(u.id, next);
        if (first || civ.upkeepDueAt <= 0) {
            civ.upkeepDueAt = CivUpkeep.firstDue(now);
            civ.upkeepWarnedFor = 0;
        }
        return new BuyResult(BuyCheck.OK, next, cost, null);
    }

    // ---------------------------------------------------------------- upkeep

    /** Item id to amount the civ owes per day, summed over its owned upgrade levels. Empty when it owns none. */
    public static Map<String, Integer> owedPerDay(Civ civ, HonorConfig c) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Upgrade u : Upgrade.values()) {
            int n = CivUpgrades.upkeepPerDay(c, u, ownedLevel(civ, u));
            if (n > 0) m.merge(CivUpgrades.itemOf(c, u), n, Integer::sum);
        }
        return m;
    }

    /** What one upgrade owned at its level costs per day, as {item, amount}; null when not owned. */
    public static Map.Entry<String, Integer> upgradeUpkeep(Civ civ, Upgrade u, HonorConfig c) {
        int lvl = ownedLevel(civ, u);
        if (lvl <= 0) return null;
        return Map.entry(CivUpgrades.itemOf(c, u), CivUpgrades.upkeepPerDay(c, u, lvl));
    }

    /** Epoch ms the upkeep is due, 0 when nothing is owed. */
    public static long upkeepDueAt(Civ civ) { return hasUpgrades(civ) ? civ.upkeepDueAt : 0; }

    /** True when the civ has upgrades and the due date has passed (show [Unpaid]). */
    public static boolean isLate(Civ civ, long now) {
        return civ != null && CivUpkeep.isLate(totalLevels(civ), civ.upkeepDueAt, now);
    }

    /** True when a "due soon" warning should be shown (not late, due within the warning window). */
    public static boolean warningDue(Civ civ, HonorConfig c, long now) {
        return civ != null && CivUpkeep.warningDue(c, totalLevels(civ), civ.upkeepDueAt, now);
    }

    /** For the periodic check: true once per due date when the warning window is entered (and remembers it). */
    public static boolean takeWarningOnce(Civ civ, HonorConfig c, long now) {
        if (!warningDue(civ, c, now) || civ.upkeepWarnedFor == civ.upkeepDueAt) return false;
        civ.upkeepWarnedFor = civ.upkeepDueAt;
        return true;
    }

    /** Whole days that can still be paid now (0 = already paid the maximum ahead, or no upgrades). */
    public static int payableDays(Civ civ, HonorConfig c, long now) {
        return civ == null ? 0 : CivUpkeep.payableDays(c, totalLevels(civ), civ.upkeepDueAt, now);
    }

    /**
     * Pays from {@code offered} (item id to amount, plain items): as many whole days as the items cover, at most the
     * pay-ahead cap. On PAID the due date moves on and {@code taken} lists exactly what to remove from the offered
     * items (the rest goes back); it is empty for every other status, and then nothing changes.
     */
    public static PayResult pay(Civ civ, HonorConfig c, long now, Map<String, Integer> offered) {
        Map<String, Integer> perDay = owedPerDay(civ, c);
        if (perDay.isEmpty() || !hasUpgrades(civ)) return new PayResult(PayStatus.NOTHING_OWED, 0, Map.of());
        int room = payableDays(civ, c, now);
        if (room <= 0) return new PayResult(PayStatus.PAID_AHEAD_MAX, 0, Map.of());
        int days = CivUpkeep.coveredDays(perDay, offered, room);
        if (days <= 0) return new PayResult(PayStatus.NOT_ENOUGH, 0, Map.of());
        civ.upkeepDueAt = CivUpkeep.dueAfterPay(civ.upkeepDueAt, now, days);
        civ.upkeepWarnedFor = 0;
        return new PayResult(PayStatus.PAID, days, CivUpkeep.cost(perDay, days));
    }
}
