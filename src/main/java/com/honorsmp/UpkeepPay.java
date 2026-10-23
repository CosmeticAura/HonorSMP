package com.honorsmp;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.CivUpgrades.Upgrade;
import com.honorsmp.logic.CivUpkeep;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ItemScatterer;

/**
 * /civ upkeep pay (1.6.5; 1.6.9: one upgrade at a time): a put-in chest like the fine payment. Matching plain items are taken for as many whole days
 * as they cover (up to the pay-ahead cap) and go to the judges' treasury; everything else comes back. While the chest
 * is open its inventory owns the items; they leave it exactly once, on close (normal close, disconnect, death or
 * server stop), guarded by {@link Session#closed}.
 */
public final class UpkeepPay {
    private static final int ROWS = 6;

    private static final class Session {
        final ServerPlayerEntity player;
        final String civId;
        final Upgrade upgrade;
        final SimpleInventory inv;
        ScreenHandler handler;
        boolean closed;

        Session(ServerPlayerEntity player, String civId, Upgrade upgrade, SimpleInventory inv) {
            this.player = player;
            this.civId = civId;
            this.upgrade = upgrade;
            this.inv = inv;
        }
    }

    private final Honor h;
    private final Map<UUID, Session> open = new HashMap<>();

    public UpkeepPay(Honor h) {
        this.h = h;
    }

    private static void tell(ServerPlayerEntity p, String m, Formatting f) {
        p.sendMessage(Text.literal(m).formatted(f));
    }

    private static void giveBack(ServerPlayerEntity p, ItemStack st) {
        if (p.isAlive() && !p.isRemoved()) p.getInventory().offerOrDrop(st);
        else ItemScatterer.spawn(p.getEntityWorld(), p.getX(), p.getY(), p.getZ(), st);
    }

    /** Returns an error message, or null when the chest opened. */
    public String openFor(ServerPlayerEntity p, Civ civ, Upgrade u) {
        if (open.containsKey(p.getUuid())) return "You already have an upkeep screen open. Press Esc to close it, then run the command again.";
        if (!CivUpgradeApi.canManage(civ, p.getUuid().toString())) return "Only the leader or officers can pay the upkeep. Ask one of them.";
        long now = System.currentTimeMillis();
        if (CivUpgradeApi.ownedLevel(civ, u) <= 0) return "Your civ does not own " + u.display + ", so it owes no upkeep for it. See /civ upgrades.";
        Map<String, Integer> perDay = CivUpgradeApi.upgradeUpkeepPerDay(civ, u, h.config);
        int room = CivUpgradeApi.payableDays(civ, u, h.config, now);
        if (room <= 0) return "Your civ has already paid " + u.display + " " + h.config.civUpkeepMaxAheadDays + " days ahead (until "
                + CivUpgradeMenu.date(CivUpgradeApi.dueAt(civ, u)) + "). Pay again later.";
        SimpleInventory inv = new SimpleInventory(ROWS * 9);
        Session s = new Session(p, civ.id, u, inv);
        open.put(p.getUuid(), s);
        s.handler = ChestUi.open(p, Text.literal(u.display + " upkeep: " + CivUpkeep.describe(perDay) + " a day (close to pay)"), ROWS, inv, who -> close(s));
        if (s.handler == null) {
            open.remove(p.getUuid());
            return "Could not open the upkeep screen. Press Esc to close any other screen and run /civ upkeep pay again.";
        }
        tell(p, "Put the " + u.display + " upkeep items in the chest and close it. Each day needs " + CivUpkeep.describe(perDay) + "; you can pay up to "
                + room + (room == 1 ? " day" : " days") + " now. Extra items are returned.", Formatting.YELLOW);
        return null;
    }

    private void close(Session s) {
        if (s.closed) return;
        s.closed = true;
        if (open.get(s.player.getUuid()) == s) open.remove(s.player.getUuid());
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < s.inv.size(); i++) {
            ItemStack st = s.inv.getStack(i);
            if (!st.isEmpty()) items.add(st);
            s.inv.setStack(i, ItemStack.EMPTY);
        }
        if (items.isEmpty()) {
            tell(s.player, "Nothing paid.", Formatting.YELLOW);
            return;
        }
        Civ civ = h.data.civs.get(s.civId);
        String err = null;
        if (civ == null) err = "Your civ no longer exists. Your items were returned.";
        else if (!CivUpgradeApi.canManage(civ, s.player.getUuid().toString())) err = "You are no longer the leader or an officer. Your items were returned.";
        Map<String, Integer> taken = Map.of();
        if (err == null) {
            Map<String, Integer> offered = new LinkedHashMap<>();
            for (ItemStack st : items) if (st.getComponentChanges().isEmpty()) offered.merge(CivUpgradeMenu.idOf(st), st.getCount(), Integer::sum);
            long now = System.currentTimeMillis();
            var res = CivUpgradeApi.pay(civ, s.upgrade, h.config, now, offered);
            switch (res.status()) {
                case PAID -> {
                    taken = res.taken();
                    h.markDirty();
                    CivPerks.syncCiv(h, civ); // upgrades back on straight away
                    String who = s.player.getName().getString();
                    String msg = who + " paid " + res.days() + (res.days() == 1 ? " day" : " days") + " of " + s.upgrade.display + " upkeep ("
                            + CivUpkeep.describe(taken) + "). Paid until " + CivUpgradeMenu.date(CivUpgradeApi.dueAt(civ, s.upgrade)) + "."
                            + (res.resumed() ? " " + s.upgrade.display + " is switched back on." : "");
                    for (String id : civ.members) {
                        ServerPlayerEntity m = h.online(id);
                        if (m != null) m.sendMessage(Text.literal("[" + civ.name + "] " + msg).formatted(Formatting.GOLD));
                    }
                }
                case NOTHING_OWED -> err = "Your civ owes no upkeep for " + s.upgrade.display + ". Your items were returned.";
                case PAID_AHEAD_MAX -> err = "Your civ has already paid " + s.upgrade.display + " " + h.config.civUpkeepMaxAheadDays + " days ahead. Your items were returned.";
                case NOT_ENOUGH -> err = "Not enough for one day (" + CivUpkeep.describe(CivUpgradeApi.upgradeUpkeepPerDay(civ, s.upgrade, h.config))
                        + "; only plain, unnamed, unenchanted items count). Your items were returned.";
            }
        }
        // what was taken goes to the judges' treasury, the rest back to the payer
        Map<String, Integer> left = new LinkedHashMap<>(taken);
        for (ItemStack st : items) {
            int use = 0;
            if (st.getComponentChanges().isEmpty()) {
                use = Math.min(st.getCount(), left.getOrDefault(CivUpgradeMenu.idOf(st), 0));
                if (use > 0) left.merge(CivUpgradeMenu.idOf(st), -use, Integer::sum);
            }
            if (use > 0) h.treasury.addItem(st.copyWithCount(use));
            if (st.getCount() - use > 0) giveBack(s.player, st.copyWithCount(st.getCount() - use));
        }
        if (err != null) tell(s.player, err, Formatting.RED);
        h.markDirty();
        h.saveData();
    }

    /** Disconnect or death: close this player's upkeep screen so its contents are settled now. */
    public void closeFor(ServerPlayerEntity p) {
        Session s = open.get(p.getUuid());
        if (s == null) return;
        p.closeHandledScreen();
        close(s);
    }

    /** Full reset: drop open sessions WITHOUT paying or returning anything, then close the screens. */
    public void wipe() {
        List<ServerPlayerEntity> viewers = new ArrayList<>();
        for (Session s : open.values()) {
            s.closed = true;
            for (int i = 0; i < s.inv.size(); i++) s.inv.setStack(i, ItemStack.EMPTY);
            viewers.add(s.player);
        }
        open.clear();
        for (ServerPlayerEntity p : viewers) p.closeHandledScreen();
    }

    /** Server stopping: settle every open screen before the final save. */
    public void closeAll() {
        for (Session s : new ArrayList<>(open.values())) {
            s.player.closeHandledScreen();
            close(s);
        }
    }
}
