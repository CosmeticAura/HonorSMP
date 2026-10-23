package com.honorsmp;

import com.honorsmp.HonorData.PlayerRecord;
import com.honorsmp.logic.DonateMath;
import com.honorsmp.logic.DonationRules;
import com.honorsmp.logic.DonationRotation;
import com.honorsmp.logic.TreasuryMigration;
import java.time.LocalDate;
import java.util.ArrayList;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
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
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ItemScatterer;

/**
 * /donate: a 3 row chest. Slots 0..17 take only donatable plain items, the bottom row holds Close / info / Confirm.
 * While open the live inventory owns the items; they leave it exactly once (confirm, close,
 * disconnect, death or server stop), guarded by {@link Session#closed}.
 */
public final class Donations {
    private static final int FOOD_SLOTS = 18;
    private static final int CLOSE_SLOT = 18;
    private static final int INFO_SLOT = 22;
    private static final int CONFIRM_SLOT = 24;

    private static final class Session {
        final ServerPlayerEntity player;
        final SimpleInventory inv;
        ScreenHandler handler;
        boolean closed;

        Session(ServerPlayerEntity player, SimpleInventory inv) {
            this.player = player;
            this.inv = inv;
        }
    }

    private final Honor h;
    private final Map<UUID, Session> open = new HashMap<>();

    public Donations(Honor h) {
        this.h = h;
    }

    // ------------------------------------------------------------ crash safety

    private RegistryOps<JsonElement> ops() {
        return RegistryOps.of(JsonOps.INSTANCE, h.server.getRegistryManager());
    }

    /** Writes the food slots of every live session into data so a crash cannot eat them. */
    public void sync() {
        Map<String, List<JsonElement>> pd = new LinkedHashMap<>();
        for (Session s : open.values()) {
            if (s.closed) continue;
            List<JsonElement> l = pd.computeIfAbsent(s.player.getUuid().toString(), k -> new ArrayList<>());
            for (int i = 0; i < FOOD_SLOTS; i++) {
                ItemStack st = s.inv.getStack(i);
                if (st.isEmpty()) continue;
                try {
                    l.add(ItemStack.CODEC.encodeStart(ops(), st).getOrThrow());
                } catch (Exception ex) {
                    Honor.LOG.error("Could not save a donation stack: {}", st, ex);
                }
            }
            if (l.isEmpty()) pd.remove(s.player.getUuid().toString());
        }
        h.data.donatePending = pd;
    }

    /** Startup: items left in screens at a crash go to the owner's /donation box. */
    public void load() {
        if (h.data.donatePending != null) {
            for (var e : h.data.donatePending.entrySet()) {
                if (e.getValue() == null) continue;
                for (JsonElement je : e.getValue()) {
                    try {
                        ItemStack st = ItemStack.CODEC.parse(ops(), je).getOrThrow();
                        if (!st.isEmpty()) h.donationBox.addToBox(e.getKey(), st);
                    } catch (Exception ex) {
                        Honor.LOG.error("Skipping unreadable donation stack: {}", je, ex);
                    }
                }
            }
        }
        h.data.donatePending = new LinkedHashMap<>();
        migratePots();
    }

    private static void tell(ServerPlayerEntity p, String m, Formatting f) {
        p.sendMessage(Text.literal(m).formatted(f));
    }

    private static void giveBack(ServerPlayerEntity p, ItemStack st) {
        if (st.isEmpty()) return;
        if (p.isAlive() && !p.isRemoved()) p.getInventory().offerOrDrop(st);
        else ItemScatterer.spawn(p.getEntityWorld(), p.getX(), p.getY(), p.getZ(), st);
    }

    private static int nutrition(ItemStack st) {
        FoodComponent f = st.get(DataComponentTypes.FOOD);
        return f == null ? -1 : f.nutrition();
    }

    private String id(ItemStack st) {
        return Registries.ITEM.getId(st.getItem()).toString();
    }

    private static boolean plain(ItemStack st) {
        return st.getComponentChanges().isEmpty();
    }

    private int perItem(ItemStack st) {
        if (st.isEmpty()) return -1;
        return DonationRules.pointsPerItem(id(st), nutrition(st), plain(st), h.config);
    }

    private boolean isDonatable(ItemStack st) {
        return perItem(st) >= 0;
    }

    /** Donation points per cap type (food combined, golden_apple, ...) for the stacks in the food slots. */
    private Map<String, Long> pointsByType(SimpleInventory inv) {
        Map<String, Long> total = new LinkedHashMap<>();
        for (int i = 0; i < FOOD_SLOTS; i++) {
            ItemStack st = inv.getStack(i);
            int p = perItem(st);
            if (p > 0) total.merge(DonationRules.category(id(st), nutrition(st), h.config), (long) p * st.getCount(), Long::sum);
        }
        return total;
    }

    private static long sum(Map<String, Long> m) {
        long t = 0;
        for (long v : m.values()) t += v;
        return t;
    }

    /** Everyone who has ever joined (player records) except the donor. */
    private List<String> recipients(ServerPlayerEntity donor) {
        String me = donor.getUuid().toString();
        List<String> out = new ArrayList<>();
        for (String u : h.data.players.keySet()) if (!u.equals(me)) out.add(u);
        return out;
    }

    private static String today() {
        return DonateMath.todayKey(LocalDate.now());
    }

    // ------------------------------------------------------------ open

    /** Returns an error message, or null when the chest opened. */
    public String open(ServerPlayerEntity p) {
        Session old = open.get(p.getUuid());
        if (old != null) {
            if (!old.closed && p.currentScreenHandler == old.handler) return "Your donation chest is already open. Press Esc to close it, then type /donate again.";
            open.remove(p.getUuid()); // stale
        }
        PlayerRecord r = h.record(p);
        String today = today();
        DonateMath.rollDay(r, today);
        SimpleInventory inv = new SimpleInventory(27);
        Session s = new Session(p, inv);
        open.put(p.getUuid(), s);
        h.markDirty();
        refresh(s);
        s.handler = ChestUi.openCustom(p, Text.literal("Donate items to other players"),
                (syncId, playerInv, player) -> new DonateHandler(syncId, playerInv, inv, s));
        if (s.handler == null) {
            open.remove(p.getUuid());
            return "Could not open the donation chest. Press Esc to close any other screen and type /donate again. If it keeps failing, tell an op.";
        }
        if (recipients(p).isEmpty()) {
            tell(p, "Nobody else has joined the server yet, so there is nobody to share with. Items you put in will not be taken.", Formatting.YELLOW);
        }
        return null;
    }

    // ------------------------------------------------------------ display

    private static ItemStack named(ItemStack st, String name, Formatting color, List<Text> lore) {
        st.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).setStyle(Style.EMPTY.withItalic(false).withColor(color)));
        if (lore != null && !lore.isEmpty()) st.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return st;
    }

    private static Text line(String s, Formatting f) {
        return Text.literal(s).setStyle(Style.EMPTY.withItalic(false).withColor(f));
    }

    private void refresh(Session s) {
        SimpleInventory inv = s.inv;
        ItemStack close = named(new ItemStack(Items.BARRIER), "Close", Formatting.RED, List.of(line("Gives your items back.", Formatting.GRAY)));
        ItemStack confirm = named(new ItemStack(Items.LIME_WOOL), "Confirm donation", Formatting.GREEN,
                List.of(line("Items are shared equally with every other player.", Formatting.GRAY)));
        for (int i = FOOD_SLOTS; i < 27; i++) {
            ItemStack f = named(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), " ", Formatting.GRAY, null);
            inv.setStack(i, f);
        }
        inv.setStack(CLOSE_SLOT, close);
        inv.setStack(CONFIRM_SLOT, confirm);

        PlayerRecord r = h.record(s.player);
        String today = today();
        DonateMath.rollDay(r, today);
        Map<String, Long> pts = pointsByType(inv);
        long points = sum(pts);
        boolean judge = h.isJudge(s.player);
        DonateMath.Quote q = DonateMath.quote(pts, r, today, h.config, judge);
        int others = recipients(s.player).size();
        List<Text> lore = new ArrayList<>();
        if (judge) {
            lore.add(line("Judges can donate but earn no honor", Formatting.GOLD));
        } else {
            lore.add(line("Chest now: +" + q.honor() + " honor", Formatting.GOLD));
            int overallCap = h.config.donateDailyCap;
            int overallLeft = q.remainingToday();
            lore.add(line("Today: " + (overallCap - overallLeft) + "/" + overallCap + " honor"
                    + (overallLeft <= 0 ? " (cap reached: items are still shared, no honor)" : ", " + overallLeft + " left"),
                    overallLeft <= 0 ? Formatting.RED : Formatting.GREEN));
            // one line per type: configured caps first, then any other type that is in the chest
            java.util.Set<String> types = new java.util.LinkedHashSet<>();
            if (h.config.donateItemDailyCaps != null) types.addAll(h.config.donateItemDailyCaps.keySet());
            types.addAll(pts.keySet());
            for (String t : DonateMath.order(types, h.config)) {
                int cap = DonateMath.typeCap(h.config, t);
                int earned = DonateMath.earnedToday(r, today, t);
                int now = q.byType().getOrDefault(t, 0);
                int canEarn = DonateMath.earnableToday(r, today, h.config, t);
                String text = DonateMath.label(t) + ": " + (cap < 0 ? earned + " today, no cap" : earned + "/" + cap + " today, can still earn " + canEarn)
                        + (now > 0 ? " (chest +" + now + ")" : "");
                lore.add(line(text, canEarn <= 0 ? Formatting.RED : (now > 0 ? Formatting.YELLOW : Formatting.GRAY)));
            }
            lore.add(line("Also: coal, copper, iron, gold, redstone, lapis, quartz, amethyst (common)", Formatting.GRAY));
            lore.add(line("and diamond, netherite (rare): ingots, raw, blocks, silk-touched ores", Formatting.GRAY));
            lore.add(line(h.config.honorPerHungerPoints + " points = 1 honor. Points in chest: " + points, Formatting.DARK_GRAY));
        }
        if (!judge) for (String l : Donor.summary(h, r)) lore.add(line(l, Formatting.LIGHT_PURPLE));
        lore.add(line(others == 0 ? "Nobody else has joined yet: nothing is taken" : "Shared equally with " + others + " other players", Formatting.AQUA));
        lore.add(line("Only plain items count: no custom names or enchants", Formatting.DARK_GRAY));
        inv.setStack(INFO_SLOT, named(new ItemStack(Items.PAPER), "Donation value", Formatting.YELLOW, lore));
    }

    // ------------------------------------------------------------ handler

    private final class DonateHandler extends GenericContainerScreenHandler {
        private final Session s;

        DonateHandler(int syncId, PlayerInventory playerInv, SimpleInventory inv, Session s) {
            super(ScreenHandlerType.GENERIC_9X3, syncId, playerInv, inv, 3);
            this.s = s;
        }

        private boolean inChest(int idx) {
            return idx >= 0 && idx < 27;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType type, PlayerEntity player) {
            boolean valid = slotIndex == -999 || slotIndex == -1 || (slotIndex >= 0 && slotIndex < slots.size());
            if (!valid || s.closed) {
                syncState();
                return;
            }
            // button row
            if (inChest(slotIndex) && slotIndex >= FOOD_SLOTS) {
                if ((type == SlotActionType.PICKUP || type == SlotActionType.QUICK_MOVE) && (button == 0 || button == 1)) {
                    if (slotIndex == CLOSE_SLOT) {
                        syncState();
                        s.player.closeHandledScreen();
                        return;
                    }
                    if (slotIndex == CONFIRM_SLOT) {
                        confirm(s);
                    }
                }
                syncState();
                return;
            }
            boolean food = inChest(slotIndex);
            switch (type) {
                case QUICK_CRAFT, CLONE, PICKUP_ALL -> {
                    syncState();
                    return;
                }
                case PICKUP -> {
                    if (food) {
                        ItemStack cur = getCursorStack();
                        if (!cur.isEmpty()) {
                            if (!isDonatable(cur)) {
                                syncState();
                                return;
                            }
                        }
                    }
                }
                case SWAP -> {
                    if (food) {
                        ItemStack hot = button == 40 ? player.getInventory().getStack(40)
                                : (button >= 0 && button < 9 ? player.getInventory().getStack(button) : null);
                        if (hot == null || (!hot.isEmpty() && !isDonatable(hot))) {
                            syncState();
                            return;
                        }
                    }
                }
                case QUICK_MOVE, THROW -> { }
                default -> {
                    syncState();
                    return;
                }
            }
            super.onSlotClick(slotIndex, button, type, player);
            h.markDirty();
            refresh(s);
            sendContentUpdates();
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int index) {
            if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
            if (index >= FOOD_SLOTS && index < 27) return ItemStack.EMPTY;
            Slot slot = slots.get(index);
            if (!slot.hasStack()) return ItemStack.EMPTY;
            ItemStack orig = slot.getStack();
            ItemStack copy = orig.copy();
            boolean moved;
            if (index < FOOD_SLOTS) {
                moved = insertItem(orig, 27, slots.size(), true);
            } else {
                if (!isDonatable(orig)) return ItemStack.EMPTY;
                moved = insertItem(orig, 0, FOOD_SLOTS, false);
            }
            if (!moved) return ItemStack.EMPTY;
            if (orig.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
            return copy;
        }

        @Override
        public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
            if (slot.inventory == s.inv) return slot.getIndex() < FOOD_SLOTS && isDonatable(stack);
            return super.canInsertIntoSlot(stack, slot);
        }

        @Override
        public void onClosed(PlayerEntity who) {
            super.onClosed(who); // returns the cursor stack
            settle(s);
        }
    }

    // ------------------------------------------------------------ confirm / settle

    private void confirm(Session s) {
        if (s.closed) return;
        ServerPlayerEntity p = s.player;
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, ItemStack> sample = new LinkedHashMap<>();
        for (int i = 0; i < FOOD_SLOTS; i++) {
            ItemStack st = s.inv.getStack(i);
            if (st.isEmpty()) continue;
            counts.merge(id(st), (long) st.getCount(), Long::sum);
            sample.putIfAbsent(id(st), st.copyWithCount(1));
        }
        if (counts.isEmpty()) {
            tell(p, "Put some food or other donatable items in the top two rows of the chest first, then click Confirm.", Formatting.YELLOW);
            return;
        }
        List<String> rec = recipients(p);
        if (rec.isEmpty()) {
            tell(p, "Nobody else has joined the server yet, so there is nobody to share with. Nothing was taken; Close gives your items back.", Formatting.YELLOW);
            return;
        }
        PlayerRecord r = h.record(p);
        String today = today();
        DonateMath.rollDay(r, today);
        DonateMath.Quote q = DonateMath.quote(pointsByType(s.inv), r, today, h.config, h.isJudge(p));
        List<String> names = new ArrayList<>();
        for (var e : counts.entrySet()) names.add(e.getValue() + "x " + sample.get(e.getKey()).getItemName().getString());

        // from here on the items leave the screen exactly once
        s.closed = true;
        if (open.get(p.getUuid()) == s) open.remove(p.getUuid());
        for (int i = 0; i < FOOD_SLOTS; i++) s.inv.setStack(i, ItemStack.EMPTY);
        shareOut(counts, sample, rec, p.getName().getString());
        DonateMath.apply(q, r, today);
        if (q.honor() > 0) {
            h.change(p.getUuid().toString(), p.getName().getString(), q.honor(), "Donated items", "donate", p, "", 0);
            Donor.recordAward(h, p, q.honor()); // lifetime total + weekly window (judges earn none: q.honor() is 0 for them)
        }
        h.markDirty();
        h.saveData();
        if (h.isJudge(p)) tell(p, "Thanks! Your donation was shared with " + rec.size() + " players. Judges earn no honor.", Formatting.GREEN);
        else tell(p, "+" + q.honor() + " honor for your donation, shared with " + rec.size() + " players."
                + (q.capReached() ? " Some items were past a daily cap and earned no honor." : ""), Formatting.GREEN);
        h.server.getPlayerManager().broadcast(Text.literal(p.getName().getString() + " donated " + String.join(", ", names)
                + ". Use /donation to claim your share.").formatted(Formatting.GOLD), false);
        p.closeHandledScreen();
    }

    /** Hands counts (item id to amount) to the recipients by rotation, files them in their claim boxes and tells them. */
    void shareOut(Map<String, Long> counts, Map<String, ItemStack> sample, List<String> rec, String donor) {
        Map<String, Map<String, Long>> shares = DonationRotation.share(h.data.donateRotation, counts, rec);
        if (shares == null) return;
        for (var r : shares.entrySet()) {
            Map<String, Long> named = new LinkedHashMap<>();
            for (var it : r.getValue().entrySet()) {
                long n = it.getValue();
                while (n > 0) {
                    int chunk = (int) Math.min(Integer.MAX_VALUE, n);
                    h.donationBox.give(r.getKey(), sample.get(it.getKey()), chunk);
                    n -= chunk;
                }
                named.merge(sample.get(it.getKey()).getItemName().getString(), it.getValue(), Long::sum);
            }
            h.donationBox.notifyShare(r.getKey(), DonationRotation.notice(DonationRotation.describe(named), donor));
        }
    }

    /**
     * Startup: hands out the old leftover pots (1.4.x) by rotation among everyone who has joined. Entries whose
     * item cannot be resolved stay in donatePots and are retried next start. Running it again with an empty
     * pot does nothing.
     */
    private void migratePots() {
        Map<String, Long> pots = h.data.donatePots;
        if (pots == null || pots.isEmpty()) return;
        List<String> rec = new ArrayList<>(h.data.players.keySet());
        if (rec.isEmpty()) return; // nobody to give to yet: keep the pots
        Map<String, Long> ready = new LinkedHashMap<>();
        Map<String, ItemStack> sample = new LinkedHashMap<>();
        Map<String, Long> keep = new LinkedHashMap<>();
        for (var e : pots.entrySet()) {
            long n = e.getValue() == null ? 0 : e.getValue();
            if (n <= 0) continue;
            try {
                net.minecraft.util.Identifier ident = net.minecraft.util.Identifier.tryParse(e.getKey());
                if (ident == null || !Registries.ITEM.containsId(ident)) throw new IllegalStateException("unknown item");
                ItemStack st = new ItemStack(Registries.ITEM.get(ident));
                if (st.isEmpty()) throw new IllegalStateException("empty item");
                ready.put(e.getKey(), n);
                sample.put(e.getKey(), st);
            } catch (Exception ex) {
                Honor.LOG.error("Could not read old donation pot entry {} ({}); keeping it for the next start", e.getKey(), n, ex);
                keep.put(e.getKey(), n);
            }
        }
        if (!ready.isEmpty()) shareOut(ready, sample, rec, "an earlier donation");
        h.data.donatePots = keep;
        h.markDirty();
        Honor.LOG.info("Handed out the old donation pots ({} item types, {} kept)", ready.size(), keep.size());
    }

    /**
     * 1.6.2, once: donatable stacks still in the judges' treasury (food donated before 1.4.0 and the like) are shared
     * out like a new donation. Emeralds, emerald blocks and stacks with changed components stay. Runs after the
     * treasury and the claim boxes are loaded and before the first save, so the removal and the new box contents
     * are written together. With nobody to share with it waits for the next start.
     */
    public void migrateTreasury() {
        List<String> rec = new ArrayList<>(h.data.players.keySet());
        List<ItemStack> list = h.treasury.closedItems();
        List<ItemStack> backup = new ArrayList<>(list);
        boolean flag = h.data.treasuryDonationsMoved;
        List<ItemStack> moved = TreasuryMigration.runOnce(h.data, list, rec,
                st -> DonationRules.treasuryMoves(id(st), nutrition(st), plain(st), h.config));
        if (h.data.treasuryDonationsMoved != flag) h.markDirty();
        if (moved.isEmpty()) return;
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, ItemStack> sample = new LinkedHashMap<>();
        for (ItemStack st : moved) {
            counts.merge(id(st), (long) st.getCount(), Long::sum);
            sample.putIfAbsent(id(st), st.copyWithCount(1));
        }
        try {
            shareOut(counts, sample, rec, "an earlier donation");
        } catch (RuntimeException ex) {
            // put everything back so nothing is lost; the next start tries again
            Honor.LOG.error("Could not move donations out of the treasury; leaving them there", ex);
            list.clear();
            list.addAll(backup);
            h.data.treasuryDonationsMoved = false;
            return;
        }
        h.markDirty();
        Honor.LOG.info("Moved {} donated treasury stacks ({} item types) into the /donation boxes", moved.size(), counts.size());
    }

    private void settle(Session s) {
        if (s.closed) return;
        s.closed = true;
        if (open.get(s.player.getUuid()) == s) open.remove(s.player.getUuid());
        for (int i = 0; i < FOOD_SLOTS; i++) {
            ItemStack st = s.inv.getStack(i);
            s.inv.setStack(i, ItemStack.EMPTY);
            giveBack(s.player, st);
        }
    }

    // ------------------------------------------------------------ lifecycle

    public void closeFor(ServerPlayerEntity p) {
        Session s = open.get(p.getUuid());
        if (s == null) return;
        p.closeHandledScreen();
        settle(s);
    }

    /** Full reset: drop open sessions WITHOUT returning anything, then close the screens. */
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
            settle(s);
        }
    }
}
