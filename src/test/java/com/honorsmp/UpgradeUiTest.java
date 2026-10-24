package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.CivUpgrades.Upgrade;
import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.UpgradeUi;
import com.honorsmp.logic.UpgradeUi.Action;
import com.honorsmp.logic.UpgradeUi.Status;
import com.honorsmp.logic.Wallet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 1.7.0: the state of each icon in the single /civ upgrades menu, the permission text and the confirm click. */
class UpgradeUiTest {
    static final long H = 3_600_000L, D = 24 * H, T0 = 1_000_000_000L;
    final HonorConfig c = new HonorConfig();

    static class W implements Wallet {
        final Map<String, Integer> have = new LinkedHashMap<>();
        W(Object... kv) { for (int i = 0; i < kv.length; i += 2) have.put((String) kv[i], (Integer) kv[i + 1]); }
        public int count(String id) { return have.getOrDefault(id, 0); }
        public void take(String id, int n) { have.merge(id, -n, Integer::sum); }
    }

    Civ civWithResistance() {
        Civ v = new Civ();
        v.id = "c1"; v.name = "Rome"; v.leader = "u0";
        for (int i = 0; i < 3; i++) v.members.add("u" + i);
        assertTrue(CivUpgradeApi.buy(v, Upgrade.RESISTANCE, c, T0, new W("minecraft:iron_ingot", 999)).ok());
        return v;
    }

    // ------------------------------------------------------------ status of each icon

    @Test
    void statusRules() {
        assertEquals(Status.NOT_BOUGHT, UpgradeUi.status(0, false, false));
        assertEquals(Status.ON, UpgradeUi.status(1, false, false));
        assertEquals(Status.PAUSED, UpgradeUi.status(2, true, false));
        assertEquals(Status.UNPAID, UpgradeUi.status(1, false, true));
        assertEquals("Not bought", Status.NOT_BOUGHT.label);
        assertEquals("On", Status.ON.label);
        assertEquals("Paused", Status.PAUSED.label);
        assertEquals("Unpaid (off)", Status.UNPAID.label);
    }

    @Test
    void notBoughtIcon() {
        Civ v = civWithResistance();
        UpgradeView s = UpgradeView.of(v, Upgrade.SPEED, c, T0 + H, true);
        assertEquals(Status.NOT_BOUGHT, s.status());
        assertEquals(0, s.level());
        assertEquals("-", s.paidUntil());
        assertEquals(List.of(Action.BUY), s.actions());
        assertEquals("Click to buy level 1", s.clickHint());
    }

    @Test
    void onIconShowsTimeLeftAndCost() {
        Civ v = civWithResistance();
        UpgradeView s = UpgradeView.of(v, Upgrade.RESISTANCE, c, T0 + 20 * H, true);
        assertEquals(Status.ON, s.status());
        assertEquals(1, s.level());
        assertEquals("4h 0m left", s.paidUntil());
        assertEquals("1d 4h left", UpgradeUi.paidUntil(Status.ON, T0 + D + 4 * H, T0));
        assertTrue(s.dailyCost().contains("32 iron ingot"), s.dailyCost());
        assertEquals(List.of(Action.BUY, Action.PAY, Action.PAUSE), s.actions());
    }

    @Test
    void pausedIconOffersResumeNotBuy() {
        Civ v = civWithResistance();
        assertTrue(CivUpgradeApi.pause(v, Upgrade.RESISTANCE, T0 + H));
        UpgradeView s = UpgradeView.of(v, Upgrade.RESISTANCE, c, T0 + 2 * H, true);
        assertEquals(Status.PAUSED, s.status());
        assertEquals("paused, no cost", s.paidUntil());
        assertEquals(List.of(Action.RESUME), s.actions());
        assertTrue(s.dailyCost().startsWith("none while paused"), s.dailyCost());
    }

    @Test
    void unpaidIconOffersPayNotBuy() {
        Civ v = civWithResistance();
        UpgradeView s = UpgradeView.of(v, Upgrade.RESISTANCE, c, T0 + D + 2 * H, true);
        assertEquals(Status.UNPAID, s.status());
        assertTrue(s.paidUntil().startsWith("ran out 2h"), s.paidUntil());
        assertEquals(List.of(Action.PAY, Action.PAUSE), s.actions());
    }

    @Test
    void maxLevelHasNoBuyAndDisabledHasNoButtons() {
        assertEquals(List.of(Action.PAY, Action.PAUSE), UpgradeUi.actions(true, 2, 2, false, false, 3));
        assertEquals(List.of(Action.PAUSE), UpgradeUi.actions(true, 2, 2, false, false, 0)); // paid the maximum ahead
        assertEquals(List.of(), UpgradeUi.actions(false, 1, 2, false, false, 3));
    }

    // ------------------------------------------------------------ permission

    @Test
    void membersCanLookButNotClick() {
        assertEquals("Only the leader or officers can do that", UpgradeUi.ONLY_MANAGERS);
        assertEquals("Only the leader or officers can do that", UpgradeView.permission(false));
        assertNull(UpgradeView.permission(true));
        Civ v = civWithResistance();
        assertTrue(CivUpgradeApi.canManage(v, "u0")); // leader
        assertFalse(CivUpgradeApi.canManage(v, "u1")); // plain member
        UpgradeView look = UpgradeView.of(v, Upgrade.RESISTANCE, c, T0 + H, false);
        assertFalse(look.manage());
        assertTrue(look.clickHint().contains("Only the leader or officers"));
        assertEquals(Status.ON, look.status()); // the icon is the same for a member
    }

    // ------------------------------------------------------------ confirm click

    @Test
    void confirmNeedsASecondClickOnTheSameButtonWithinFiveSeconds() {
        var cf = new UpgradeUi.Confirm();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertFalse(cf.click(a, "buy:speed", 1000));
        assertTrue(cf.armed(a, "buy:speed", 2000));
        assertFalse(cf.armed(b, "buy:speed", 2000)); // another player is not armed
        assertTrue(cf.click(a, "buy:speed", 1000 + UpgradeUi.CONFIRM_MS)); // exactly 5 s still counts
        assertFalse(cf.armed(a, "buy:speed", 1000 + UpgradeUi.CONFIRM_MS)); // used up
        assertFalse(cf.click(a, "buy:speed", 20_000)); // needs arming again
    }

    @Test
    void confirmExpiresAndIsPerAction() {
        var cf = new UpgradeUi.Confirm();
        UUID a = UUID.randomUUID();
        assertFalse(cf.click(a, "pause:haste", 0));
        assertFalse(cf.click(a, "pause:haste", UpgradeUi.CONFIRM_MS + 1)); // too late: re-arms only
        assertFalse(cf.click(a, "buy:haste", UpgradeUi.CONFIRM_MS + 2)); // other action: re-arms only
        assertFalse(cf.armed(a, "pause:haste", UpgradeUi.CONFIRM_MS + 3));
        assertTrue(cf.click(a, "buy:haste", UpgradeUi.CONFIRM_MS + 4));
        assertFalse(cf.click(a, "rows", 0));
        cf.clear(a);
        assertFalse(cf.armed(a, "rows", 1));
    }

    @Test
    void rulesAreUntouched() {
        // 1.7.0 changes only the UI: buying level 1 still costs 4 days of upkeep and the first due date is a day later
        Civ v = civWithResistance();
        assertEquals(T0 + D, CivUpgradeApi.dueAt(v, Upgrade.RESISTANCE));
        assertEquals(1, CivUpgradeApi.ownedLevel(v, Upgrade.RESISTANCE));
    }
}
