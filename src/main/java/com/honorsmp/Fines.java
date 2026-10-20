package com.honorsmp;

import com.google.gson.JsonElement;
import com.honorsmp.HonorData.Fine;
import com.honorsmp.HonorData.PlayerRecord;
import com.honorsmp.logic.FineMatch;
import com.honorsmp.logic.FineTimer;
import com.honorsmp.logic.MenuClicks;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ItemScatterer;

/**
 * Fines. A judge sets one with a put-in chest (the items are only a template: they are copied and
 * the originals go straight back to the judge). The fined player pays with another chest. While a
 * chest is open its live inventory owns the items; they leave it exactly once, on close (normal
 * close, disconnect, death or server stop), guarded by {@link Session#closed}.
 */
public final class Fines {
    private static final int EDIT_ROWS = 3;
    private static final int PAY_DISPLAY = 27; // slots 0..26 show what is owed, 27..53 take the payment

    private static final class Session {
        final ServerPlayerEntity player;
        final boolean pay;
        final String target; // edit only
        final int caseId;    // edit: case, pay: fine id
        final SimpleInventory inv;
        ScreenHandler handler;
        boolean closed;

        Session(ServerPlayerEntity player, boolean pay, String target, int caseId, SimpleInventory inv) {
            this.player = player;
            this.pay = pay;
            this.target = target;
            this.caseId = caseId;
            this.inv = inv;
        }
    }

    private final Honor h;
    private final Map<UUID, Session> open = new HashMap<>();

    public Fines(Honor h) {
        this.h = h;
    }

    /** Hook called by the case menu's Fine button: fines the case's killer. */
    public static void openFineFor(Honor h, ServerPlayerEntity judge, HonorData.Case c) {
        if (c.killer == null || c.killer.isEmpty()) {
            judge.sendMessage(Text.literal("That case has no killer to fine.").formatted(Formatting.RED));
            return;
        }
        PlayerRecord t = h.data.players.get(c.killer);
        if (t == null) {
            judge.sendMessage(Text.literal("Unknown player.").formatted(Formatting.RED));
            return;
        }
        String e = h.fines.openEditor(judge, t, c.id);
        if (e != null) judge.sendMessage(Text.literal(e).formatted(Formatting.RED));
    }

    // ------------------------------------------------------------ helpers

    private RegistryOps<JsonElement> ops() {
        return RegistryOps.of(JsonOps.INSTANCE, h.server.getRegistryManager());
    }

    private List<ItemStack> decode(Fine f) {
        List<ItemStack> out = new ArrayList<>();
        for (JsonElement e : f.items) {
            try {
                ItemStack s = ItemStack.CODEC.parse(ops(), e).getOrThrow();
                if (!s.isEmpty()) out.add(s);
            } catch (Exception ex) {
                Honor.LOG.error("Skipping unreadable fine stack: {}", e, ex);
            }
        }
        return out;
    }

    private static void giveBack(ServerPlayerEntity p, ItemStack st) {
        if (p.isAlive() && !p.isRemoved()) p.getInventory().offerOrDrop(st);
        else ItemScatterer.spawn(p.getEntityWorld(), p.getX(), p.getY(), p.getZ(), st);
    }

    private static void addEnch(Map<String, Integer> out, String prefix, ItemEnchantmentsComponent c) {
        if (c == null) return;
        for (var entry : c.getEnchantments()) out.put(prefix + entry.getIdAsString(), c.getLevel(entry));
    }

    public static FineMatch.Desc desc(ItemStack s) {
        Map<String, Integer> en = new TreeMap<>();
        addEnch(en, "", s.get(DataComponentTypes.ENCHANTMENTS));
        addEnch(en, "stored:", s.get(DataComponentTypes.STORED_ENCHANTMENTS));
        Integer dmg = s.get(DataComponentTypes.DAMAGE);
        Text nm = s.get(DataComponentTypes.CUSTOM_NAME);
        NbtComponent cd = s.get(DataComponentTypes.CUSTOM_DATA);
        return new FineMatch.Desc(Registries.ITEM.getId(s.getItem()).toString(), s.getCount(), dmg == null ? 0 : dmg, en,
                nm == null ? "" : nm.getString(), cd == null ? "" : cd.copyNbt().toString());
    }

    private static String pretty(FineMatch.Desc d) {
        String id = d.id().contains(":") ? d.id().substring(d.id().indexOf(':') + 1) : d.id();
        StringBuilder sb = new StringBuilder(d.count() + "x " + id.replace('_', ' '));
        if (!d.enchants().isEmpty()) {
            List<String> l = new ArrayList<>();
            for (var e : d.enchants().entrySet()) {
                String n = e.getKey().replace("stored:", "");
                l.add(n.substring(n.indexOf(':') + 1).replace('_', ' ') + " " + e.getValue());
            }
            sb.append(" (").append(String.join(", ", l)).append(")");
        }
        if (!d.name().isEmpty()) sb.append(" named \"").append(d.name()).append("\"");
        if (d.damage() > 0) sb.append(" [damage ").append(d.damage()).append("]");
        return sb.toString();
    }

    private String contentsText(Fine f) {
        List<String> l = new ArrayList<>();
        for (ItemStack s : decode(f)) l.add(pretty(desc(s)));
        return l.isEmpty() ? "nothing" : String.join(", ", l);
    }

    private String hoursText() {
        double v = h.config.fineIntervalHours;
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private long intervalMs() {
        return FineTimer.intervalMs(h.config.fineIntervalHours);
    }

    public Fine find(int id) {
        for (Fine f : h.data.fines) if (f.id == id) return f;
        return null;
    }

    public List<Fine> openFor(String uuid) {
        List<Fine> l = new ArrayList<>();
        for (Fine f : h.data.fines) if ("OPEN".equals(f.status) && f.target.equals(uuid)) l.add(f);
        return l;
    }

    private static void tell(ServerPlayerEntity p, String m, Formatting f) {
        p.sendMessage(Text.literal(m).formatted(f));
    }

    // ------------------------------------------------------------ judge: set a fine

    /** Returns an error message, or null when the chest opened. */
    public String openEditor(ServerPlayerEntity judge, PlayerRecord target, int caseId) {
        if (!h.isJudge(judge)) return "Only judges can fine players.";
        if (open.containsKey(judge.getUuid())) return "Close your other fine screen first.";
        if (!FineTimer.canBeFined(h.isJudge(target.uuid))) return target.name + " is a judge. Judges cannot be fined.";
        if (target.uuid.equals(judge.getUuid().toString())) return "You cannot fine yourself.";
        SimpleInventory inv = new SimpleInventory(EDIT_ROWS * 9);
        Session s = new Session(judge, false, target.uuid, caseId, inv);
        open.put(judge.getUuid(), s);
        s.handler = ChestUi.open(judge, Text.literal("Fine for " + target.name + " (close to confirm)"), EDIT_ROWS, inv, who -> closeEdit(s));
        if (s.handler == null) {
            open.remove(judge.getUuid());
            return "Could not open the fine screen.";
        }
        return null;
    }

    private void closeEdit(Session s) {
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
            tell(s.player, "Fine cancelled (nothing was put in).", Formatting.YELLOW);
            return;
        }
        PlayerRecord t = h.data.players.get(s.target);
        String err = null;
        if (!h.isJudge(s.player)) err = "You are no longer a judge.";
        else if (t == null) err = "Unknown player.";
        else if (!FineTimer.canBeFined(h.isJudge(t.uuid))) err = t.name + " is a judge and cannot be fined.";
        if (err == null) {
            Fine f = new Fine();
            f.id = h.data.nextFineId++;
            f.target = t.uuid;
            f.targetName = t.name;
            f.judge = s.player.getUuid().toString();
            f.judgeName = s.player.getName().getString();
            f.caseId = s.caseId;
            f.createdAt = System.currentTimeMillis();
            for (ItemStack st : items) {
                try {
                    f.items.add(ItemStack.CODEC.encodeStart(ops(), st.copy()).getOrThrow());
                } catch (Exception ex) {
                    Honor.LOG.error("Could not save a fine stack: {}", st, ex);
                }
            }
            if (f.items.isEmpty()) err = "Could not read those items.";
            else {
                h.data.fines.add(f);
                tell(s.player, "Fine #" + f.id + " set on " + t.name + ": " + contentsText(f) + ". Your items were given back.", Formatting.GREEN);
                ServerPlayerEntity live = h.online(t.uuid);
                if (live != null) {
                    announce(live, f);
                    f.notified = true;
                }
            }
        }
        // the judge's items were only a template: they always go back
        for (ItemStack st : items) giveBack(s.player, st);
        if (err != null) tell(s.player, err, Formatting.RED);
        h.markDirty();
        h.saveData();
    }

    private void announce(ServerPlayerEntity p, Fine f) {
        tell(p, "You were fined by " + f.judgeName + " (fine #" + f.id + "): " + contentsText(f) + ".", Formatting.RED);
        tell(p, "Pay with /fine pay. Every " + hoursText() + " hours it stays unpaid costs you " + h.config.fineHonorPenalty + " honor.", Formatting.YELLOW);
    }

    // ------------------------------------------------------------ player: pay

    /** fineId 0 = the oldest open fine. Returns an error message, or null when the chest opened. */
    public String openPay(ServerPlayerEntity p, int fineId) {
        if (open.containsKey(p.getUuid())) return "Close your other fine screen first.";
        List<Fine> mine = openFor(p.getUuid().toString());
        Fine f = null;
        if (fineId == 0) {
            if (!mine.isEmpty()) f = mine.get(0);
        } else {
            for (Fine x : mine) if (x.id == fineId) f = x;
        }
        if (f == null) return fineId == 0 ? "You have no open fines." : "You have no open fine #" + fineId + ".";
        List<ItemStack> owed = decode(f);
        SimpleInventory inv = new SimpleInventory(54);
        for (int i = 0; i < owed.size() && i < PAY_DISPLAY; i++) inv.setStack(i, owed.get(i));
        Session s = new Session(p, true, null, f.id, inv);
        open.put(p.getUuid(), s);
        s.handler = ChestUi.openCustom(p, Text.literal("Fine #" + f.id + ": owed on top, put items on the bottom half"),
                (syncId, playerInv, player) -> new PayHandler(syncId, playerInv, inv));
        if (s.handler == null) {
            open.remove(p.getUuid());
            return "Could not open the payment screen.";
        }
        ((PayHandler) s.handler).session = s;
        return null;
    }

    private final class PayHandler extends GenericContainerScreenHandler {
        Session session;

        PayHandler(int syncId, PlayerInventory playerInv, SimpleInventory inv) {
            super(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, 6);
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType type, PlayerEntity player) {
            // the top half only displays what is owed: nothing can be put in or taken out
            if (slotIndex >= 0 && slotIndex < PAY_DISPLAY) {
                syncState();
                return;
            }
            super.onSlotClick(slotIndex, button, type, player);
        }

        @Override
        public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
            if (slot.inventory == getInventory() && slot.getIndex() < PAY_DISPLAY) return false;
            return super.canInsertIntoSlot(stack, slot);
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int index) {
            Slot slot = slots.get(index);
            if (!slot.hasStack() || (index < PAY_DISPLAY)) return ItemStack.EMPTY;
            ItemStack orig = slot.getStack();
            ItemStack copy = orig.copy();
            boolean moved = index < 54 ? insertItem(orig, 54, slots.size(), true) : insertItem(orig, PAY_DISPLAY, 54, false);
            if (!moved) return ItemStack.EMPTY;
            if (orig.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
            return copy;
        }

        @Override
        public void onClosed(PlayerEntity who) {
            super.onClosed(who);
            if (session != null) closePay(session);
        }
    }

    private void closePay(Session s) {
        if (s.closed) return;
        s.closed = true;
        if (open.get(s.player.getUuid()) == s) open.remove(s.player.getUuid());
        List<ItemStack> offered = new ArrayList<>();
        for (int i = 0; i < s.inv.size(); i++) {
            ItemStack st = s.inv.getStack(i);
            if (i >= PAY_DISPLAY && !st.isEmpty()) offered.add(st);
            s.inv.setStack(i, ItemStack.EMPTY); // display copies just vanish
        }
        Fine f = find(s.caseId);
        if (offered.isEmpty()) {
            if (f != null && "OPEN".equals(f.status)) tell(s.player, "Nothing paid. Fine #" + f.id + " is still open.", Formatting.YELLOW);
            return;
        }
        if (f == null || !"OPEN".equals(f.status)) {
            for (ItemStack st : offered) giveBack(s.player, st);
            tell(s.player, "That fine is no longer open. Your items were returned.", Formatting.YELLOW);
            return;
        }
        List<FineMatch.Desc> offeredDesc = new ArrayList<>();
        for (ItemStack st : offered) offeredDesc.add(desc(st));
        List<FineMatch.Desc> owedDesc = new ArrayList<>();
        for (ItemStack st : decode(f)) owedDesc.add(desc(st));
        FineMatch.Result r = FineMatch.match(owedDesc, offeredDesc);
        if (!r.complete()) {
            for (ItemStack st : offered) giveBack(s.player, st);
            List<String> miss = new ArrayList<>();
            for (FineMatch.Desc d : r.missing()) miss.add(pretty(d));
            tell(s.player, "Fine #" + f.id + " not paid, you must pay it all at once. Still missing: " + String.join(", ", miss)
                    + ". Your items were returned.", Formatting.RED);
            return;
        }
        for (int i = 0; i < offered.size(); i++) {
            int used = r.used().get(i);
            ItemStack st = offered.get(i);
            if (used > 0) h.treasury.addItem(st.copyWithCount(used));
            if (st.getCount() - used > 0) giveBack(s.player, st.copyWithCount(st.getCount() - used));
        }
        f.status = "PAID";
        f.closedAt = System.currentTimeMillis();
        f.closedBy = s.player.getName().getString();
        f.notified = true;
        tell(s.player, "Fine #" + f.id + " paid. Thank you.", Formatting.GREEN);
        for (ServerPlayerEntity j : h.server.getPlayerManager().getPlayerList()) {
            if (h.isJudge(j)) tell(j, s.player.getName().getString() + " paid fine #" + f.id + ". The items are in the treasury.", Formatting.GOLD);
        }
        h.markDirty();
        h.saveData();
    }

    // ------------------------------------------------------------ cancel

    /** Returns an error message or null. */
    public String cancel(int id, String byName) {
        Fine f = find(id);
        if (f == null) return "No fine #" + id + ".";
        if (!"OPEN".equals(f.status)) return "Fine #" + id + " is already " + f.status.toLowerCase() + ".";
        f.status = "CANCELLED";
        f.closedAt = System.currentTimeMillis();
        f.closedBy = byName;
        ServerPlayerEntity live = h.online(f.target);
        if (live != null) {
            tell(live, "Fine #" + f.id + " was cancelled by " + byName + ". You owe nothing.", Formatting.GREEN);
            f.notified = true;
        } else {
            f.notified = false; // told on next login
        }
        // a pay screen open on it hands everything back when it closes (checked there)
        Session ps = live == null ? null : open.get(live.getUuid());
        if (ps != null && ps.pay && ps.caseId == f.id) tell(live, "Close the payment screen: your items will be returned.", Formatting.YELLOW);
        h.markDirty();
        h.saveData();
        return null;
    }

    // ------------------------------------------------------------ views

    public List<String> describeOpen(String uuid, boolean all) {
        long now = System.currentTimeMillis();
        List<String> out = new ArrayList<>();
        for (Fine f : h.data.fines) {
            if (!"OPEN".equals(f.status) || (!all && !f.target.equals(uuid))) continue;
            long next = FineTimer.nextPenaltyAt(f.createdAt, f.lastPenaltyAt, intervalMs());
            out.add("#" + f.id + (all ? " " + f.targetName : "") + ": " + contentsText(f) + " | " + MenuClicks.age(now - f.createdAt)
                    + " | next -" + h.config.fineHonorPenalty + " honor in " + Taxes.dueText(next - now));
        }
        return out;
    }

    /** Judge menu: every open fine; click one to cancel it. */
    public void openCancelMenu(ServerPlayerEntity p) {
        if (!h.isJudge(p)) {
            tell(p, "Only judges can do that.", Formatting.RED);
            return;
        }
        List<Fine> list = new ArrayList<>();
        for (Fine f : h.data.fines) if ("OPEN".equals(f.status)) list.add(f);
        Menu m = Menu.create(Text.literal("Open fines (" + list.size() + ")"), 6);
        long now = System.currentTimeMillis();
        for (int i = 0; i < Math.min(53, list.size()); i++) {
            Fine f = list.get(i);
            Item icon = Items.GOLD_INGOT;
            List<ItemStack> st = decode(f);
            if (!st.isEmpty()) icon = st.get(0).getItem();
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Owes: " + contentsText(f)).formatted(Formatting.GRAY));
            lore.add(Text.literal("Set by " + f.judgeName + ", " + MenuClicks.age(now - f.createdAt)).formatted(Formatting.GRAY));
            if (f.caseId > 0) lore.add(Text.literal("Case #" + f.caseId).formatted(Formatting.GRAY));
            lore.add(Text.literal("Click to CANCEL this fine").formatted(Formatting.RED));
            int id = f.id;
            m.set(i, Menu.icon(icon, Text.literal("Fine #" + f.id + " on " + f.targetName).formatted(Formatting.GOLD), lore), (who, r, sh) -> {
                String e = cancel(id, who.getName().getString());
                tell(who, e == null ? "Fine #" + id + " cancelled." : e, e == null ? Formatting.GREEN : Formatting.RED);
                openCancelMenu(who);
            });
        }
        if (list.isEmpty()) m.icon(22, Menu.icon(Items.BARRIER, "No open fines", Formatting.GRAY));
        if (!m.open(p)) tell(p, "Could not open the fines menu.", Formatting.RED);
    }

    // ------------------------------------------------------------ timer, login, shutdown

    /** Charges honor for every whole interval each open fine has run, online or not. Judges are exempt. */
    public void tick() {
        long now = System.currentTimeMillis();
        long iv = intervalMs();
        boolean changed = false;
        for (Fine f : h.data.fines) {
            if (!"OPEN".equals(f.status)) continue;
            boolean judge = h.isJudge(f.target);
            long due = FineTimer.penaltiesDue(f.createdAt, f.lastPenaltyAt, now, iv, judge);
            long judgeSkipped = judge ? (now - FineTimer.baseline(f.createdAt, f.lastPenaltyAt)) / iv : 0;
            long adv = judge ? judgeSkipped : due;
            if (adv <= 0) continue;
            f.lastPenaltyAt = FineTimer.advance(f.createdAt, f.lastPenaltyAt, adv, iv);
            changed = true;
            if (due <= 0) continue;
            int loss = FineTimer.totalLoss(due, h.config.fineHonorPenalty);
            if (loss <= 0) continue;
            f.penalties += (int) due;
            h.change(f.target, f.targetName, -loss, "Unpaid fine #" + f.id + (due > 1 ? " (" + due + " periods)" : ""), "fine",
                    null, f.judgeName, f.caseId);
            ServerPlayerEntity live = h.online(f.target);
            if (live != null) {
                tell(live, "Fine #" + f.id + " is still unpaid: -" + loss + " honor. Pay it with /fine pay.", Formatting.RED);
            }
        }
        if (changed) h.markDirty();
    }

    public void onLogin(ServerPlayerEntity p) {
        String id = p.getUuid().toString();
        tick(); // catch up on missed intervals
        for (Fine f : h.data.fines) {
            if (f.notified || !f.target.equals(id)) continue;
            if ("OPEN".equals(f.status)) announce(p, f);
            else if ("CANCELLED".equals(f.status)) tell(p, "Fine #" + f.id + " was cancelled by " + f.closedBy + ". You owe nothing.", Formatting.GREEN);
            f.notified = true;
            h.markDirty();
        }
    }

    /** Disconnect or death: close this player's fine screen so its contents are settled now. */
    public void closeFor(ServerPlayerEntity p) {
        Session s = open.get(p.getUuid());
        if (s == null) return;
        p.closeHandledScreen();
        if (s.pay) closePay(s);
        else closeEdit(s);
    }

    /** Server stopping: settle every open screen before the final save. */
    public void closeAll() {
        for (Session s : new ArrayList<>(open.values())) {
            s.player.closeHandledScreen();
            if (s.pay) closePay(s);
            else closeEdit(s);
        }
    }
}
