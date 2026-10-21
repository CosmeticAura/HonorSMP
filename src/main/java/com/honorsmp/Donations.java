package com.honorsmp;

import com.honorsmp.HonorData.PlayerRecord;
import com.honorsmp.logic.DonateMath;
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
 * /donate: a 3 row chest. Slots 0..17 take only food, the bottom row holds Close / info / Confirm.
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

    /** Startup: food left in screens at a crash goes to the owner's bounty claim box. */
    public void load() {
        if (h.data.donatePending != null) {
            for (var e : h.data.donatePending.entrySet()) {
                if (e.getValue() == null) continue;
                for (JsonElement je : e.getValue()) {
                    try {
                        ItemStack st = ItemStack.CODEC.parse(ops(), je).getOrThrow();
                        if (!st.isEmpty()) h.bounties.addToClaimBox(e.getKey(), st);
                    } catch (Exception ex) {
                        Honor.LOG.error("Skipping unreadable donation stack: {}", je, ex);
                    }
                }
            }
        }
        h.data.donatePending = new LinkedHashMap<>();
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

    private boolean isFood(ItemStack st) {
        if (st.isEmpty()) return false;
        if (!(st.contains(DataComponentTypes.FOOD) || st.isOf(Items.CAKE))) return false;
        return DonateMath.donatable(id(st), nutrition(st), h.config);
    }

    private long hungerIn(SimpleInventory inv) {
        long total = 0;
        for (int i = 0; i < FOOD_SLOTS; i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty()) continue;
            int v = DonateMath.hungerValue(id(st), nutrition(st), st.getCount(), h.config);
            if (v > 0) total += v;
        }
        return total;
    }

    private static String today() {
        return DonateMath.todayKey(LocalDate.now());
    }

    // ------------------------------------------------------------ open

    /** Returns an error message, or null when the chest opened. */
    public String open(ServerPlayerEntity p) {
        if (h.isJudge(p)) return "Judges cannot donate: judges are outside the honor system and earn no honor. Nothing to do here.";
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
        s.handler = ChestUi.openCustom(p, Text.literal("Donate food to the treasury"),
                (syncId, playerInv, player) -> new DonateHandler(syncId, playerInv, inv, s));
        if (s.handler == null) {
            open.remove(p.getUuid());
            return "Could not open the donation chest. Press Esc to close any other screen and type /donate again. If it keeps failing, tell an op.";
        }
        if (DonateMath.remainingToday(r, today, h.config) <= 0) {
            tell(p, "You have already reached today's donation limit (" + h.config.donateDailyCap + " honor). Come back tomorrow.", Formatting.YELLOW);
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
        ItemStack close = named(new ItemStack(Items.BARRIER), "Close", Formatting.RED, List.of(line("Gives your food back.", Formatting.GRAY)));
        ItemStack confirm = named(new ItemStack(Items.LIME_WOOL), "Confirm donation", Formatting.GREEN,
                List.of(line("Food goes to the treasury.", Formatting.GRAY)));
        for (int i = FOOD_SLOTS; i < 27; i++) {
            ItemStack f = named(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), " ", Formatting.GRAY, null);
            inv.setStack(i, f);
        }
        inv.setStack(CLOSE_SLOT, close);
        inv.setStack(CONFIRM_SLOT, confirm);

        PlayerRecord r = h.record(s.player);
        String today = today();
        long hunger = hungerIn(inv);
        DonateMath.Quote q = DonateMath.quote(hunger, r, today, h.config);
        List<Text> lore = new ArrayList<>();
        lore.add(line("Worth now: " + q.honor() + " honor", Formatting.GOLD));
        lore.add(line("Hunger points in chest: " + hunger, Formatting.GRAY));
        lore.add(line("Carry-over points: " + r.donateCarry + " -> " + q.newCarry(), Formatting.GRAY));
        lore.add(line(h.config.honorPerHungerPoints + " hunger points = 1 honor", Formatting.DARK_GRAY));
        lore.add(line(q.remainingToday() <= 0 ? "Daily cap reached" : "You can still earn " + q.remainingToday() + " honor today",
                q.remainingToday() <= 0 ? Formatting.RED : Formatting.GREEN));
        inv.setStack(INFO_SLOT, named(new ItemStack(Items.PAPER), "Donation value", Formatting.YELLOW, lore));
    }

    private boolean chestFull(Session s) {
        PlayerRecord r = h.record(s.player);
        return DonateMath.chestFull(hungerIn(s.inv), r, today(), h.config);
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

        private void refuseFull() {
            tell(s.player, "You have reached today's donation limit. You can't add more food today; click Close to get it back.", Formatting.RED);
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
                            if (!isFood(cur)) {
                                syncState();
                                return;
                            }
                            if (chestFull(s)) {
                                refuseFull();
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
                        if (hot == null || (!hot.isEmpty() && !isFood(hot))) {
                            syncState();
                            return;
                        }
                        if (!hot.isEmpty() && chestFull(s)) {
                            refuseFull();
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
                if (!isFood(orig)) return ItemStack.EMPTY;
                if (chestFull(s)) {
                    refuseFull();
                    return ItemStack.EMPTY;
                }
                moved = insertItem(orig, 0, FOOD_SLOTS, false);
            }
            if (!moved) return ItemStack.EMPTY;
            if (orig.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
            return copy;
        }

        @Override
        public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
            if (slot.inventory == s.inv) return slot.getIndex() < FOOD_SLOTS && isFood(stack);
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
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < FOOD_SLOTS; i++) if (!s.inv.getStack(i).isEmpty()) items.add(s.inv.getStack(i));
        if (items.isEmpty()) {
            tell(p, "Put some food in the top two rows of the chest first, then click Confirm.", Formatting.YELLOW);
            return;
        }
        PlayerRecord r = h.record(p);
        String today = today();
        DonateMath.rollDay(r, today);
        DonateMath.Quote q = DonateMath.quote(hungerIn(s.inv), r, today, h.config);
        long raw = (hungerIn(s.inv) + r.donateCarry) / Math.max(1, h.config.honorPerHungerPoints);
        long remaining = q.remainingToday();
        if (raw > remaining) {
            if (remaining <= 0) tell(p, "You have reached today's donation limit. Take your food out (Close gives it back) and come back tomorrow.", Formatting.RED);
            else tell(p, "Too much food for today's limit: take some out. You can still earn " + remaining + " honor today.", Formatting.YELLOW);
            return;
        }
        s.closed = true;
        if (open.get(p.getUuid()) == s) open.remove(p.getUuid());
        for (int i = 0; i < FOOD_SLOTS; i++) {
            ItemStack st = s.inv.getStack(i);
            if (!st.isEmpty()) h.treasury.addItem(st);
            s.inv.setStack(i, ItemStack.EMPTY);
        }
        DonateMath.apply(q, r, today);
        if (q.honor() > 0) h.change(p.getUuid().toString(), p.getName().getString(), q.honor(), "Donated food", "donate", p, "", 0);
        h.markDirty();
        h.saveData();
        tell(p, "+" + q.honor() + " honor for your donation. " + q.newCarry() + " hunger points carried over.", Formatting.GREEN);
        p.closeHandledScreen();
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
