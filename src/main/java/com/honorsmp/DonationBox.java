package com.honorsmp;

import com.google.gson.JsonElement;
import com.honorsmp.logic.BountyEscrow;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * /donation: each player's claim box of shared donations. Same escrow and dupe rules as the bounty
 * claim chest: while the screen is open the live inventory owns the stacks it shows (the rest wait in
 * the escrow), and they go back exactly once on close, disconnect, death or server stop.
 * Rows 0..4 (45 slots) hold items, the bottom row holds Close / info / Show more.
 */
public final class DonationBox {
    private static final int ITEM_SLOTS = 45;
    private static final int CLOSE_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int MORE_SLOT = 53;

    private static final class Session {
        final ServerPlayerEntity player;
        final SimpleInventory inv = new SimpleInventory(54);
        ScreenHandler handler;
        boolean closed;

        Session(ServerPlayerEntity player) {
            this.player = player;
        }
    }

    private final Honor h;
    private final BountyEscrow<ItemStack> escrow = new BountyEscrow<>(Bounties.OPS);
    private final Map<UUID, Session> open = new HashMap<>();

    public DonationBox(Honor h) {
        this.h = h;
    }

    // ------------------------------------------------------------ storage

    private RegistryOps<JsonElement> ops() {
        return RegistryOps.of(JsonOps.INSTANCE, h.server.getRegistryManager());
    }

    public void load() {
        if (h.data.donateBoxes == null) return;
        for (var e : h.data.donateBoxes.entrySet()) {
            if (e.getValue() == null) continue;
            for (JsonElement je : e.getValue()) {
                try {
                    ItemStack s = ItemStack.CODEC.parse(ops(), je).getOrThrow();
                    if (!s.isEmpty()) escrow.loadBox(e.getKey(), s);
                } catch (Exception ex) {
                    Honor.LOG.error("Skipping unreadable donation box stack: {}", je, ex);
                }
            }
        }
    }

    /** Writes escrow plus the live open screens into data. Called from saveData. */
    public void sync() {
        Map<String, List<ItemStack>> boxes = new LinkedHashMap<>();
        for (var e : escrow.boxes().entrySet()) boxes.put(e.getKey(), new ArrayList<>(e.getValue()));
        for (Session s : open.values()) {
            if (s.closed) continue;
            List<ItemStack> l = boxes.computeIfAbsent(s.player.getUuid().toString(), k -> new ArrayList<>());
            for (int i = 0; i < ITEM_SLOTS; i++) if (!s.inv.getStack(i).isEmpty()) l.add(s.inv.getStack(i));
        }
        Map<String, List<JsonElement>> out = new LinkedHashMap<>();
        for (var e : boxes.entrySet()) {
            List<JsonElement> l = new ArrayList<>();
            for (ItemStack st : e.getValue()) {
                try {
                    l.add(ItemStack.CODEC.encodeStart(ops(), st).getOrThrow());
                } catch (Exception ex) {
                    Honor.LOG.error("Could not save a donation box stack: {}", st, ex);
                }
            }
            if (!l.isEmpty()) out.put(e.getKey(), l);
        }
        h.data.donateBoxes = out;
    }

    /** Adds {@code count} of the item to a player's box (stacks are split at max stack size). */
    public void give(String uuid, ItemStack sample, int count) {
        if (count <= 0 || sample.isEmpty()) return;
        escrow.addToBox(uuid, sample.copyWithCount(count));
    }

    /** Recovery: items that were in a /donate screen at a crash go to the donor's box. */
    public void addToBox(String uuid, ItemStack st) {
        escrow.addToBox(uuid, st);
    }

    public long itemsFor(String uuid) {
        return escrow.boxItems(uuid);
    }

    // ------------------------------------------------------------ login

    /** Tells a player about a donation share now if online, otherwise on their next login. */
    public void notifyShare(String uuid, String msg) {
        ServerPlayerEntity online = null;
        try {
            online = h.server.getPlayerManager().getPlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException ignored) {
        }
        if (online != null) {
            online.sendMessage(Text.literal(msg).formatted(Formatting.GREEN));
            return;
        }
        com.honorsmp.logic.DonationRotation.addNotice(h.data.donateNotices.computeIfAbsent(uuid, k -> new ArrayList<>()), msg);
    }

    public void onLogin(ServerPlayerEntity p) {
        List<String> waiting = h.data.donateNotices.remove(p.getUuid().toString());
        if (waiting != null) {
            for (String m : waiting) p.sendMessage(Text.literal(m).formatted(Formatting.GREEN));
            h.markDirty();
            if (!waiting.isEmpty()) return;
        }
        long n = itemsFor(p.getUuid().toString());
        if (n > 0) p.sendMessage(Text.literal("You have " + n + " donated items to claim. Use /donation.").formatted(Formatting.GREEN));
    }

    // ------------------------------------------------------------ open

    /** Returns an error message, or null when the chest opened. */
    public String open(ServerPlayerEntity p) {
        Session old = open.get(p.getUuid());
        if (old != null && !old.closed) {
            if (p.isRemoved() || p.currentScreenHandler != old.handler) close(old); // stale
            else return "Your donation box is already open. Press Esc to close it, then type /donation again.";
        }
        String id = p.getUuid().toString();
        if (escrow.boxItems(id) <= 0 || escrow.isBoxOpen(id)) {
            return "Nothing to claim right now. When someone uses /donate, your share shows up here.";
        }
        Session s = new Session(p);
        fill(s);
        open.put(p.getUuid(), s);
        s.handler = ChestUi.openCustom(p, Text.literal("Donations to claim"),
                (syncId, playerInv, who) -> new TakeOnlyHandler(syncId, playerInv, s.inv, s));
        if (s.handler == null) {
            close(s);
            return "Could not open the donation box. Press Esc to close any other screen and type /donation again.";
        }
        return null;
    }

    /** Moves the first 45 stacks from the escrow into the screen (the escrow must not be open yet). */
    private void fill(Session s) {
        List<ItemStack> stacks = escrow.openBox(s.player.getUuid().toString(), ITEM_SLOTS);
        if (stacks == null) stacks = new ArrayList<>();
        for (int i = 0; i < ITEM_SLOTS; i++) s.inv.setStack(i, i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
        buttons(s);
    }

    private static Text line(String t, Formatting f) {
        return Text.literal(t).setStyle(Style.EMPTY.withItalic(false).withColor(f));
    }

    private static ItemStack named(ItemStack st, String name, Formatting color, List<Text> lore) {
        st.set(DataComponentTypes.CUSTOM_NAME, line(name, color));
        if (lore != null && !lore.isEmpty()) st.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return st;
    }

    private void buttons(Session s) {
        for (int i = ITEM_SLOTS; i < 54; i++) s.inv.setStack(i, named(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), " ", Formatting.GRAY, null));
        s.inv.setStack(CLOSE_SLOT, named(new ItemStack(Items.BARRIER), "Close", Formatting.RED, List.of(line("Anything left stays in your box.", Formatting.GRAY))));
        String id = s.player.getUuid().toString();
        List<ItemStack> rest = escrow.boxes().get(id);
        long moreStacks = rest == null ? 0 : rest.size();
        long moreItems = escrow.boxItems(id);
        List<Text> lore = new ArrayList<>();
        lore.add(line("Take items out to claim them. You can't put items in.", Formatting.GRAY));
        lore.add(line(moreItems > 0 ? moreItems + " more items waiting on the next page" : "Nothing more waiting", Formatting.YELLOW));
        s.inv.setStack(INFO_SLOT, named(new ItemStack(Items.PAPER), "Your share of donations", Formatting.GOLD, lore));
        if (moreStacks > 0) {
            s.inv.setStack(MORE_SLOT, named(new ItemStack(Items.ARROW), "Show next items", Formatting.GREEN,
                    List.of(line("Shows the next " + Math.min(moreStacks, ITEM_SLOTS) + " stacks. Items left on this page stay in your box.", Formatting.GRAY))));
        }
    }

    /** Puts what is left on this page back and loads the next 45 stacks. */
    private void showMore(Session s) {
        String id = s.player.getUuid().toString();
        List<ItemStack> left = takeItems(s);
        escrow.closeBox(id, left);
        // what was left on the page is appended behind the waiting stacks, so the next page differs
        fill(s);
        h.markDirty();
    }

    private List<ItemStack> takeItems(Session s) {
        List<ItemStack> left = new ArrayList<>();
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack st = s.inv.getStack(i);
            if (!st.isEmpty()) left.add(st);
            s.inv.setStack(i, ItemStack.EMPTY);
        }
        return left;
    }

    private void close(Session s) {
        if (s.closed) return;
        s.closed = true;
        if (open.get(s.player.getUuid()) == s) open.remove(s.player.getUuid());
        List<ItemStack> left = takeItems(s);
        escrow.closeBox(s.player.getUuid().toString(), left);
        h.markDirty();
        h.saveData();
    }

    // ------------------------------------------------------------ handler

    private final class TakeOnlyHandler extends GenericContainerScreenHandler {
        private final Session s;

        TakeOnlyHandler(int syncId, PlayerInventory playerInv, Inventory inv, Session s) {
            super(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, 6);
            this.s = s;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType type, PlayerEntity player) {
            boolean valid = slotIndex == -999 || slotIndex == -1 || (slotIndex >= 0 && slotIndex < slots.size());
            if (!valid || s.closed) {
                syncState();
                return;
            }
            boolean inItems = slotIndex >= 0 && slotIndex < ITEM_SLOTS;
            boolean inButtons = slotIndex >= ITEM_SLOTS && slotIndex < 54;
            if (inButtons) {
                // buttons: nothing is ever picked up from this row, whatever the click type
                if ((type == SlotActionType.PICKUP || type == SlotActionType.QUICK_MOVE) && (button == 0 || button == 1)) {
                    if (slotIndex == CLOSE_SLOT) {
                        syncState();
                        s.player.closeHandledScreen();
                        return;
                    }
                    if (slotIndex == MORE_SLOT && s.inv.getStack(MORE_SLOT).isOf(Items.ARROW)) {
                        showMore(s);
                    }
                }
                syncState();
                sendContentUpdates();
                return;
            }
            boolean ok;
            switch (type) {
                case PICKUP -> ok = !inItems || getCursorStack().isEmpty();
                case QUICK_MOVE -> ok = inItems;
                case SWAP -> ok = (button == 40 || (button >= 0 && button < 9))
                        && (!inItems || player.getInventory().getStack(button).isEmpty());
                case THROW -> ok = true;
                default -> ok = false; // QUICK_CRAFT (drag), CLONE, PICKUP_ALL
            }
            if (!ok) {
                syncState();
                return;
            }
            super.onSlotClick(slotIndex, button, type, player);
            buttons(s);
            sendContentUpdates();
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            if (slot < 0 || slot >= ITEM_SLOTS) return ItemStack.EMPTY; // never push into the box, never take buttons
            return super.quickMove(player, slot);
        }

        @Override
        public boolean canInsertIntoSlot(ItemStack stack, net.minecraft.screen.slot.Slot slot) {
            return false;
        }

        @Override
        public void onClosed(PlayerEntity who) {
            super.onClosed(who); // returns the cursor stack
            close(s);
        }
    }

    // ------------------------------------------------------------ lifecycle

    public void closeFor(ServerPlayerEntity p) {
        Session s = open.get(p.getUuid());
        if (s == null) return;
        p.closeHandledScreen();
        close(s);
    }

    /** Full reset: drop everything WITHOUT returning, then close screens. */
    public void wipe() {
        List<ServerPlayerEntity> viewers = new ArrayList<>();
        for (Session s : open.values()) {
            s.closed = true;
            for (int i = 0; i < s.inv.size(); i++) s.inv.setStack(i, ItemStack.EMPTY);
            viewers.add(s.player);
        }
        open.clear();
        escrow.clear();
        h.data.donateBoxes = new LinkedHashMap<>();
        h.data.donatePots = new LinkedHashMap<>();
        h.data.donateRotation = new LinkedHashMap<>();
        h.data.donateNotices = new LinkedHashMap<>();
        for (ServerPlayerEntity p : viewers) p.closeHandledScreen();
    }

    public void closeAll() {
        for (Session s : new ArrayList<>(open.values())) {
            s.player.closeHandledScreen();
            close(s);
        }
    }
}
