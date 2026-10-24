package com.honorsmp;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.CivEffects;
import com.honorsmp.logic.CivUpgrades;
import com.honorsmp.logic.CivUpgrades.Upgrade;
import com.honorsmp.logic.CivUpkeep;
import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.UpgradeUi;
import com.honorsmp.logic.UpgradeUi.Action;
import com.honorsmp.logic.UpgradeUi.Status;
import java.util.List;

/**
 * 1.7.0: what one upgrade icon in /civ upgrades says, as plain data (no Minecraft types, so it is unit tested).
 * {@link CivUpgradeMenu} only turns this into items. Reads the existing rules; changes nothing.
 */
public record UpgradeView(Upgrade upgrade, int level, Status status, String does, String paidUntil, String dailyCost,
                          List<Action> actions, String clickHint, int payRoom, boolean manage) {

    public static UpgradeView of(Civ civ, Upgrade u, HonorConfig c, long now, boolean manage) {
        int lvl = CivUpgradeApi.ownedLevel(civ, u);
        boolean paused = CivUpgradeApi.isPaused(civ, u);
        boolean late = CivUpgradeApi.isLate(civ, u, now);
        Status st = UpgradeUi.status(lvl, paused, late);
        String does = lvl > 0 ? CivEffects.describe(c, u, lvl) : "Level 1: " + CivEffects.describe(c, u, 1);
        String until = UpgradeUi.paidUntil(st, CivUpgradeApi.dueAt(civ, u), now);
        String cost;
        if (lvl <= 0) cost = CivUpgrades.upkeepPerDay(c, u, 1) + " " + CivUpkeep.pretty(CivUpgrades.itemOf(c, u)) + " a day once bought";
        else if (paused) cost = "none while paused (" + CivUpkeep.describe(CivUpgradeApi.upgradeUpkeepPerDay(civ, u, c)) + " when on)";
        else cost = CivUpkeep.describe(CivUpgradeApi.upgradeUpkeepPerDay(civ, u, c)) + " a day";
        int room = lvl > 0 ? CivUpgradeApi.payableDays(civ, u, c, now) : 0;
        boolean max = lvl >= CivUpgrades.MAX_LEVEL;
        List<Action> actions = UpgradeUi.actions(c.civUpgradesEnabled, lvl, CivUpgrades.MAX_LEVEL, paused, late, room);
        return new UpgradeView(u, lvl, st, does, until, cost, actions, UpgradeUi.clickHint(st, manage, max), room, manage);
    }

    /** True when {@code who} may press the action buttons (members may only look). */
    public static String permission(boolean manage) {
        return manage ? null : UpgradeUi.ONLY_MANAGERS;
    }
}
