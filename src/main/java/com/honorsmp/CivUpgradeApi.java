package com.honorsmp;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.CivUpgrades;
import com.honorsmp.logic.CivUpgrades.BuyCheck;
import com.honorsmp.logic.CivUpgrades.Upgrade;
import com.honorsmp.logic.CivUpkeep;
import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.Wallet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    /** Result of {@link #pay}: the days paid and the items that must be taken from what was offered. {@code resumed}: a paused upgrade was switched back on. */
    public record PayResult(PayStatus status, int days, Map<String, Integer> taken, boolean resumed) {
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

    /** Level that should currently take effect: owned level, or 0 when this upgrade is off (its upkeep late, paused, or the feature disabled). */
    public static int activeLevel(Civ civ, Upgrade u, HonorConfig c, long now) {
        return upgradeActive(civ, u, c, now) ? ownedLevel(civ, u) : 0;
    }

    public static int totalLevels(Civ civ) {
        int n = 0;
        for (Upgrade u : Upgrade.values()) n += ownedLevel(civ, u);
        return n;
    }

    public static boolean hasUpgrades(Civ civ) { return totalLevels(civ) > 0; }

    /** 1.6.9: this one upgrade works: owned, not paused, its own upkeep not late. */
    public static boolean upgradeActive(Civ civ, Upgrade u, HonorConfig c, long now) {
        int lvl = ownedLevel(civ, u);
        return civ != null && !isPaused(civ, u) && CivUpkeep.upgradesActive(c, lvl, dueAt(civ, u), now);
    }

    /** True when at least one upgrade works. */
    public static boolean upgradesActive(Civ civ, HonorConfig c, long now) {
        for (Upgrade u : Upgrade.values()) if (upgradeActive(civ, u, c, now)) return true;
        return false;
    }

    /** Removes every upgrade and every upkeep clock (disband, reset). No refund. */
    public static void clear(Civ civ) {
        if (civ == null) return;
        civ.upgrades = null;
        civ.upkeepDueAt = 0;
        civ.upkeepWarnedFor = 0;
        civ.upgradeDue = null;
        civ.upgradeWarned = null;
        civ.upgradePaused = null;
        civ.chestRows = 0; // 1.6.8: bought chest rows go with the upgrades
    }

    /**
     * On load. 1.6.9: every owned, unpaused upgrade without its own clock starts with the civ's old shared upkeep date,
     * so nobody loses paid days. A civ that owns upgrades but has no date at all (weekly-tax versions, 1.6.5 rule) gets
     * 24 hours from now. The old shared fields are left as they are. Returns true when anything changed.
     */
    public static boolean migrate(Civ civ, long now) {
        if (civ == null) return false;
        boolean changed = false;
        for (Upgrade u : Upgrade.values()) {
            int lvl = ownedLevel(civ, u);
            if (lvl <= 0 || isPaused(civ, u)) continue;
            if (civ.upgradeDue != null && civ.upgradeDue.containsKey(u.id)) continue;
            if (civ.upgradeDue == null) civ.upgradeDue = new LinkedHashMap<>();
            civ.upgradeDue.put(u.id, CivUpkeep.migrateDue(lvl, civ.upkeepDueAt, now));
            changed = true;
        }
        return changed;
    }

    // ---------------------------------------------------------------- per-upgrade clocks (1.6.9)

    /** Epoch ms this upgrade's upkeep is due; 0 when it is not owned, paused, or has no clock yet. */
    public static long dueAt(Civ civ, Upgrade u) {
        if (civ == null || civ.upgradeDue == null || ownedLevel(civ, u) <= 0 || isPaused(civ, u)) return 0;
        Long d = civ.upgradeDue.get(u.id);
        return d == null ? 0 : d;
    }

    public static long upkeepDueAt(Civ civ, Upgrade u) { return dueAt(civ, u); }

    public static boolean isPaused(Civ civ, Upgrade u) {
        return civ != null && civ.upgradePaused != null && civ.upgradePaused.containsKey(u.id) && ownedLevel(civ, u) > 0;
    }

    /** Paid time (ms) a paused upgrade still has when resumed by paying; 0 when it is not paused. */
    public static long pausedLeft(Civ civ, Upgrade u) {
        if (!isPaused(civ, u)) return 0;
        Long l = civ.upgradePaused.get(u.id);
        return l == null ? 0 : Math.max(0, l);
    }

    /** Due time as if the upgrade were running now: a paused upgrade resumes with the time it had left. */
    private static long effectiveDue(Civ civ, Upgrade u, long now) {
        return isPaused(civ, u) ? now + pausedLeft(civ, u) : dueAt(civ, u);
    }

    /** True when this upgrade is owned, not paused, and its own due date has passed. */
    public static boolean isLate(Civ civ, Upgrade u, long now) {
        return civ != null && !isPaused(civ, u) && CivUpkeep.isLate(ownedLevel(civ, u), dueAt(civ, u), now);
    }

    /** True when at least one non-paused upgrade is late (show [Unpaid]). */
    public static boolean isLate(Civ civ, long now) {
        for (Upgrade u : Upgrade.values()) if (isLate(civ, u, now)) return true;
        return false;
    }

    /** The non-paused upgrades that are late, in menu order. */
    public static List<Upgrade> lateUpgrades(Civ civ, long now) {
        List<Upgrade> out = new ArrayList<>();
        for (Upgrade u : Upgrade.values()) if (isLate(civ, u, now)) out.add(u);
        return out;
    }

    /** The paused upgrades, in menu order. */
    public static List<Upgrade> pausedUpgrades(Civ civ) {
        List<Upgrade> out = new ArrayList<>();
        for (Upgrade u : Upgrade.values()) if (isPaused(civ, u)) out.add(u);
        return out;
    }

    /**
     * Pauses an owned, running upgrade: it turns off, stops costing anything and stops warning; the level is kept and
     * the paid time it had left is remembered. Returns false when it is not owned or already paused.
     */
    public static boolean pause(Civ civ, Upgrade u, long now) {
        if (civ == null || ownedLevel(civ, u) <= 0 || isPaused(civ, u)) return false;
        long left = Math.max(0, dueAt(civ, u) - now);
        if (civ.upgradePaused == null) civ.upgradePaused = new LinkedHashMap<>();
        civ.upgradePaused.put(u.id, left);
        if (civ.upgradeDue != null) civ.upgradeDue.remove(u.id);
        if (civ.upgradeWarned != null) civ.upgradeWarned.remove(u.id);
        return true;
    }

    // ---------------------------------------------------------------- buying

    public static BuyCheck checkBuy(Civ civ, Upgrade u, HonorConfig c, long now, Wallet w) {
        return CivUpgrades.canBuy(c, u, ownedLevel(civ, u), isLate(civ, u, now), isPaused(civ, u), have(CivUpgrades.buyCost(c, u, ownedLevel(civ, u) + 1), w));
    }

    private static Map<String, Integer> have(Map<String, Integer> need, Wallet w) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String id : need.keySet()) m.put(id, w.count(id));
        return m;
    }

    /**
     * Buys the next level: checks, takes the items from the wallet, sets the level, and starts that upgrade's upkeep
     * clock if it had none (first level: due in 24 hours; a later level keeps the due date). Nothing changes unless the
     * result is OK. Not allowed while THIS upgrade's upkeep is late or it is paused; other upgrades do not matter.
     */
    public static BuyResult buy(Civ civ, Upgrade u, HonorConfig c, long now, Wallet w) {
        int cur = ownedLevel(civ, u);
        int next = cur + 1;
        Map<String, Integer> cost = CivUpgrades.buyCost(c, u, next);
        BuyCheck chk = CivUpgrades.canBuy(c, u, cur, isLate(civ, u, now), isPaused(civ, u), have(cost, w));
        if (chk != BuyCheck.OK) {
            String miss = chk == BuyCheck.NOT_ENOUGH_ITEMS ? CivUpgrades.missing(cost, have(cost, w)) : null;
            return new BuyResult(chk, cur, Map.of(), miss);
        }
        for (var e : cost.entrySet()) w.take(e.getKey(), e.getValue());
        if (civ.upgrades == null) civ.upgrades = new LinkedHashMap<>();
        civ.upgrades.put(u.id, next);
        if (cur == 0 || dueAt(civ, u) <= 0) {
            if (civ.upgradeDue == null) civ.upgradeDue = new LinkedHashMap<>();
            civ.upgradeDue.put(u.id, CivUpkeep.firstDue(now));
            if (civ.upgradeWarned != null) civ.upgradeWarned.remove(u.id);
        }
        return new BuyResult(BuyCheck.OK, next, cost, null);
    }

    // ---------------------------------------------------------------- chest rows (1.6.8)

    /** Rows bought so far, 0..5. */
    public static int chestRows(Civ civ) {
        return civ == null ? 0 : com.honorsmp.logic.CivChestRows.clampBought(civ.chestRows);
    }

    /**
     * Buys the next civ chest row: in order only, price from {@code civChestRowCosts}, all or nothing. On OK the civ's
     * row count is raised and {@code taken} lists what was taken from the wallet. No upkeep starts for rows.
     */
    public static com.honorsmp.logic.CivChestRows.BuyResult buyChestRow(Civ civ, HonorConfig c, Wallet w) {
        int have = chestRows(civ);
        var res = com.honorsmp.logic.CivChestRows.buy(c.civChestRowCosts, have, com.honorsmp.logic.CivChestRows.nextRow(have), w);
        if (res.ok()) civ.chestRows = res.bought();
        return res;
    }

    // ---------------------------------------------------------------- upkeep

    /** Item id to amount the civ owes per day, summed over its owned, non-paused upgrades. Empty when none. */
    public static Map<String, Integer> owedPerDay(Civ civ, HonorConfig c) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Upgrade u : Upgrade.values()) {
            for (var e : owedPerDay(civ, u, c).entrySet()) m.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        return m;
    }

    /** What one upgrade owes per day: its item and amount; empty when it is not owned or paused (paused costs nothing). */
    public static Map<String, Integer> owedPerDay(Civ civ, Upgrade u, HonorConfig c) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (isPaused(civ, u)) return m;
        int n = CivUpgrades.upkeepPerDay(c, u, ownedLevel(civ, u));
        if (n > 0) m.put(CivUpgrades.itemOf(c, u), n);
        return m;
    }

    /** What one upgrade costs per day at its owned level, even when paused (this is what paying resumes it for). */
    public static Map<String, Integer> upgradeUpkeepPerDay(Civ civ, Upgrade u, HonorConfig c) {
        Map<String, Integer> m = new LinkedHashMap<>();
        int n = CivUpgrades.upkeepPerDay(c, u, ownedLevel(civ, u));
        if (ownedLevel(civ, u) > 0 && n > 0) m.put(CivUpgrades.itemOf(c, u), n);
        return m;
    }

    /** What one upgrade owned at its level costs per day, as {item, amount}; null when not owned (a paused one still shows its price). */
    public static Map.Entry<String, Integer> upgradeUpkeep(Civ civ, Upgrade u, HonorConfig c) {
        int lvl = ownedLevel(civ, u);
        if (lvl <= 0) return null;
        return Map.entry(CivUpgrades.itemOf(c, u), CivUpgrades.upkeepPerDay(c, u, lvl));
    }

    /** True when a "due soon" warning should be shown for this upgrade (not paused, not late, due within the warning window). */
    public static boolean warningDue(Civ civ, Upgrade u, HonorConfig c, long now) {
        return civ != null && !isPaused(civ, u) && CivUpkeep.warningDue(c, ownedLevel(civ, u), dueAt(civ, u), now);
    }

    /** For the periodic check: true once per due date when the warning window is entered for this upgrade (and remembers it). */
    public static boolean takeWarningOnce(Civ civ, Upgrade u, HonorConfig c, long now) {
        if (!warningDue(civ, u, c, now)) return false;
        long due = dueAt(civ, u);
        if (civ.upgradeWarned != null && Long.valueOf(due).equals(civ.upgradeWarned.get(u.id))) return false;
        if (civ.upgradeWarned == null) civ.upgradeWarned = new LinkedHashMap<>();
        civ.upgradeWarned.put(u.id, due);
        return true;
    }

    /** Whole days that can still be paid now for this upgrade (0 = already paid the maximum ahead, or not owned). */
    public static int payableDays(Civ civ, Upgrade u, HonorConfig c, long now) {
        if (civ == null) return 0;
        long due = effectiveDue(civ, u, now);
        return CivUpkeep.payableDays(c, ownedLevel(civ, u), due, now);
    }

    /**
     * Pays one upgrade from {@code offered} (item id to amount, plain items): as many whole days of THAT upgrade as the
     * items cover, at most the pay-ahead cap for it. On PAID that upgrade's due date moves on (a paused upgrade is
     * switched back on first) and {@code taken} lists exactly what to remove from the offered items (the rest goes
     * back); it is empty for every other status, and then nothing changes.
     */
    public static PayResult pay(Civ civ, Upgrade u, HonorConfig c, long now, Map<String, Integer> offered) {
        if (civ == null || ownedLevel(civ, u) <= 0) return new PayResult(PayStatus.NOTHING_OWED, 0, Map.of(), false);
        boolean paused = isPaused(civ, u);
        Map<String, Integer> perDay = upgradeUpkeepPerDay(civ, u, c);
        long due = effectiveDue(civ, u, now);
        int room = CivUpkeep.payableDays(c, ownedLevel(civ, u), due, now);
        if (room <= 0) return new PayResult(PayStatus.PAID_AHEAD_MAX, 0, Map.of(), false);
        int days = CivUpkeep.coveredDays(perDay, offered, room);
        if (days <= 0) return new PayResult(PayStatus.NOT_ENOUGH, 0, Map.of(), false);
        if (civ.upgradeDue == null) civ.upgradeDue = new LinkedHashMap<>();
        civ.upgradeDue.put(u.id, CivUpkeep.dueAfterPay(due, now, days));
        if (civ.upgradeWarned != null) civ.upgradeWarned.remove(u.id);
        if (paused) civ.upgradePaused.remove(u.id);
        return new PayResult(PayStatus.PAID, days, CivUpkeep.cost(perDay, days), paused);
    }
}
