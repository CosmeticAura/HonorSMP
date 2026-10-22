package com.honorsmp;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.CivTax;
import com.honorsmp.logic.CivUpgrades;
import com.honorsmp.logic.CivUpgrades.BuyCheck;
import com.honorsmp.logic.CivUpgrades.Upgrade;
import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.Wallet;
import java.util.LinkedHashMap;
import java.util.function.LongConsumer;

/**
 * Thin, Minecraft-free glue between HonorData.Civ and the pure upgrade/tax rules. Every method is null-safe for civs
 * loaded from a 1.5.0 data.json. Callers mark the data dirty after a change.
 * Permission (leader/officer) is NOT checked here; see {@link #canManage}.
 */
public final class CivUpgradeApi {
    private CivUpgradeApi() {}

    /** Result of {@link #buy}. */
    public record BuyResult(BuyCheck check, int newLevel, int emeraldsTaken, int diamondsTaken) {
        public boolean ok() { return check == BuyCheck.OK; }
    }

    /** Result of {@link #pay}. */
    public enum PayResult { PAID, NOTHING_OWED, NOT_YET_DUE, NOT_ENOUGH_EMERALDS }

    public static boolean canManage(Civ civ, String uuid) {
        return civ != null && uuid != null && (uuid.equals(civ.leader) || (civ.officers != null && civ.officers.contains(uuid)));
    }

    // ---------------------------------------------------------------- levels

    /** Owned level (0..2), ignoring whether the tax is late. */
    public static int ownedLevel(Civ civ, Upgrade u) {
        if (civ == null || civ.upgrades == null) return 0;
        Integer v = civ.upgrades.get(u.id);
        return v == null ? 0 : Math.max(0, Math.min(CivUpgrades.MAX_LEVEL, v));
    }

    public static int ownedLevel(Civ civ, String id) {
        Upgrade u = Upgrade.byId(id);
        return u == null ? 0 : ownedLevel(civ, u);
    }

    /** Level that should currently take effect: owned level, or 0 when switched off (tax late / feature disabled). */
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
        return civ != null && CivTax.upgradesActive(c, totalLevels(civ), civ.taxDueAt, now);
    }

    /** Removes every upgrade and the tax clock (disband, reset). No refund. */
    public static void clear(Civ civ) {
        if (civ == null) return;
        civ.upgrades = null;
        civ.taxDueAt = 0;
        civ.taxWarnedFor = 0;
    }

    // ---------------------------------------------------------------- buying

    public static BuyCheck checkBuy(Civ civ, Upgrade u, HonorConfig c, long now, int emeralds, int diamonds) {
        return CivUpgrades.canBuy(c, ownedLevel(civ, u), isLate(civ, now), emeralds, diamonds);
    }

    /**
     * Buys the next level: checks, takes the items from the wallet, sets the level, and starts the tax clock if this
     * is the civ's first upgrade level. Nothing changes unless the result is OK. Not allowed while the tax is late.
     */
    public static BuyResult buy(Civ civ, Upgrade u, HonorConfig c, long now, Wallet w) {
        int cur = ownedLevel(civ, u);
        BuyCheck chk = checkBuy(civ, u, c, now, w.emeralds(), w.diamonds());
        if (chk != BuyCheck.OK) return new BuyResult(chk, cur, 0, 0);
        int next = cur + 1;
        int em = CivUpgrades.emeraldCost(c, next);
        int di = CivUpgrades.diamondCost(c, next);
        boolean first = totalLevels(civ) == 0;
        w.take(em, di);
        if (civ.upgrades == null) civ.upgrades = new LinkedHashMap<>();
        civ.upgrades.put(u.id, next);
        if (first || civ.taxDueAt <= 0) {
            civ.taxDueAt = CivTax.firstDue(c, now);
            civ.taxWarnedFor = 0;
        }
        return new BuyResult(BuyCheck.OK, next, em, di);
    }

    // ---------------------------------------------------------------- tax

    public static long taxOwed(Civ civ, HonorConfig c) {
        return civ == null ? 0 : CivTax.owed(c, totalLevels(civ), civ.members == null ? 0 : civ.members.size());
    }

    /** Epoch ms the tax is due, 0 when nothing is owed. */
    public static long taxDueAt(Civ civ) { return hasUpgrades(civ) ? civ.taxDueAt : 0; }

    /** True when the civ has upgrades and the due date has passed (show [Unpaid]). */
    public static boolean isLate(Civ civ, long now) {
        return civ != null && CivTax.isLate(totalLevels(civ), civ.taxDueAt, now);
    }

    /** True when a 24h-before warning should be shown (not late, due within the window). */
    public static boolean warningDue(Civ civ, HonorConfig c, long now) {
        return civ != null && CivTax.warningDue(c, totalLevels(civ), civ.taxDueAt, now);
    }

    /**
     * For the periodic check: true once per due date when the 24h warning window is entered (and remembers it), so
     * leaders/officers online are told once. Login warnings should use {@link #warningDue} / {@link #isLate} instead.
     */
    public static boolean takeWarningOnce(Civ civ, HonorConfig c, long now) {
        if (!warningDue(civ, c, now) || civ.taxWarnedFor == civ.taxDueAt) return false;
        civ.taxWarnedFor = civ.taxDueAt;
        return true;
    }

    public static boolean canPay(Civ civ, HonorConfig c, long now) {
        return civ != null && CivTax.canPay(c, totalLevels(civ), civ.members == null ? 0 : civ.members.size(), civ.taxDueAt, now);
    }

    /**
     * Pays one period of tax from the wallet (emeralds only). On PAID the due date moves on (see CivTax) and
     * {@code treasuryDeposit} gets the emerald amount to add to the judges' treasury (Honor.treasury.addEmeralds).
     * Nothing changes for any other result.
     */
    public static PayResult pay(Civ civ, HonorConfig c, long now, Wallet w, LongConsumer treasuryDeposit) {
        long owed = taxOwed(civ, c);
        if (owed <= 0) return PayResult.NOTHING_OWED;
        if (!canPay(civ, c, now)) return PayResult.NOT_YET_DUE;
        if (w.emeralds() < owed) return PayResult.NOT_ENOUGH_EMERALDS;
        w.take((int) owed, 0);
        civ.taxDueAt = CivTax.dueAfterPay(c, civ.taxDueAt, now);
        civ.taxWarnedFor = 0;
        treasuryDeposit.accept(owed);
        return PayResult.PAID;
    }
}
