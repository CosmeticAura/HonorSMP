package com.honorsmp;

import com.google.gson.JsonElement;
import com.honorsmp.HonorData.Civ;
import com.honorsmp.HonorData.PlayerRecord;
import com.honorsmp.logic.BountyEscrow;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.Formatting;

/**
 * Item bounties. The escrow logic is in {@link BountyEscrow}. While a screen is open its live
 * Inventory is the only owner of the items it shows; they leave it exactly once, on close
 * (normal close, disconnect, death or server stop), guarded by {@link Session#closed}.
 */
public final class Bounties {
    private static final int SET_ROWS = 3;
    private static final int CLAIM_ROWS = 6;

    private static final BountyEscrow.Ops<ItemStack> OPS = new BountyEscrow.Ops<>() {
        public boolean same(ItemStack a, ItemStack b) { return ItemStack.areItemsAndComponentsEqual(a, b); }
        public int count(ItemStack t) { return t.getCount(); }
        public ItemStack withCount(ItemStack t, int n) { return t.copyWithCount(n); }
        public int maxStack(ItemStack t) { return t.getMaxCount(); }
    };

    private static final class Session {
        final ServerPlayerEntity player;
        final boolean claim;
        final String target; // set only
        final SimpleInventory inv;
        BountyEscrow<ItemStack>.SetSession set;
        ScreenHandler handler;
        boolean closed;

        Session(ServerPlayerEntity player, boolean claim, String target, SimpleInventory inv) {
            this.player = player;
            this.claim = claim;
            this.target = target;
            this.inv = inv;
        }
    }

    private final Honor h;
    private final BountyEscrow<ItemStack> escrow = new BountyEscrow<>(OPS);
    private final Map<UUID, Session> open = new HashMap<>();

    public Bounties(Honor h) {
        this.h = h;
    }

    private RegistryOps<JsonElement> ops() {
        return RegistryOps.of(JsonOps.INSTANCE, h.server.getRegistryManager());
    }

    // ------------------------------------------------------------ storage

    public void load() {
        for (var e : h.data.bountyItems.entrySet()) for (ItemStack s : decode(e.getValue())) escrow.loadBounty(e.getKey(), s);
        for (var e : h.data.bountyBoxes.entrySet()) for (ItemStack s : decode(e.getValue())) escrow.loadBox(e.getKey(), s);
        // screens that were open at a crash: give the contents back through the owner's claim box
        for (var e : h.data.bountyPending.entrySet()) for (ItemStack s : decode(e.getValue())) escrow.addToBox(e.getKey(), s);
        h.data.bountyPending = new LinkedHashMap<>();
        if (h.data.bounties != null && !h.data.bounties.isEmpty()) {
            long n = escrow.migrateEmeralds(h.data.bounties, c -> new ItemStack(Items.EMERALD, c));
            Honor.LOG.info("Migrated {} old emerald bounty emeralds into item bounties", n);
        }
        h.data.bounties = new LinkedHashMap<>();
    }

    private List<ItemStack> decode(List<JsonElement> l) {
        List<ItemStack> out = new ArrayList<>();
        if (l == null) return out;
        for (JsonElement e : l) {
            try {
                ItemStack s = ItemStack.CODEC.parse(ops(), e).getOrThrow();
                if (!s.isEmpty()) out.add(s);
            } catch (Exception ex) {
                Honor.LOG.error("Skipping unreadable bounty stack: {}", e, ex);
            }
        }
        return out;
    }

    private List<JsonElement> encode(List<ItemStack> l) {
        List<JsonElement> out = new ArrayList<>();
        for (ItemStack s : l) {
            if (s.isEmpty()) continue;
            try {
                out.add(ItemStack.CODEC.encodeStart(ops(), s).getOrThrow());
            } catch (Exception ex) {
                Honor.LOG.error("Could not save a bounty stack: {}", s, ex);
            }
        }
        return out;
    }

    private static List<ItemStack> contents(Inventory inv) {
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < inv.size(); i++) if (!inv.getStack(i).isEmpty()) l.add(inv.getStack(i));
        return l;
    }

    /** Writes escrow + live open screens into data. Called from saveData. */
    public void sync() {
        Map<String, List<ItemStack>> boxes = new LinkedHashMap<>();
        for (var e : escrow.boxes().entrySet()) boxes.put(e.getKey(), new ArrayList<>(e.getValue()));
        Map<String, List<ItemStack>> pending = new LinkedHashMap<>();
        for (Session s : open.values()) {
            if (s.closed) continue;
            String id = s.player.getUuid().toString();
            if (s.claim) boxes.computeIfAbsent(id, k -> new ArrayList<>()).addAll(contents(s.inv));
            else pending.computeIfAbsent(id, k -> new ArrayList<>()).addAll(contents(s.inv));
        }
        Map<String, List<JsonElement>> b = new LinkedHashMap<>();
        for (var e : escrow.bounties().entrySet()) b.put(e.getKey(), encode(e.getValue()));
        h.data.bountyItems = b;
        Map<String, List<JsonElement>> bx = new LinkedHashMap<>();
        for (var e : boxes.entrySet()) bx.put(e.getKey(), encode(e.getValue()));
        h.data.bountyBoxes = bx;
        Map<String, List<JsonElement>> pd = new LinkedHashMap<>();
        for (var e : pending.entrySet()) pd.put(e.getKey(), encode(e.getValue()));
        h.data.bountyPending = pd;
    }

    // ------------------------------------------------------------ queries

    public boolean hasBounty(String target) {
        return escrow.hasBounty(target);
    }

    public long itemsOn(String target) {
        return escrow.itemsOn(target);
    }

    public Map<String, List<ItemStack>> view() {
        return escrow.bounties();
    }

    // ------------------------------------------------------------ set

    /** Returns an error message, or null when the UI opened. */
    public String openSet(ServerPlayerEntity p, PlayerRecord target) {
        String err = busy(p);
        if (err != null) return err;
        String me = p.getUuid().toString();
        if (target.uuid.equals(me)) return "You cannot put a bounty on yourself.";
        if (!h.tier(target).isDishonored()) return target.name + " is not dishonored. Bounties can only go on dishonored players.";
        Civ mine = h.civOf(me);
        if (mine != null && mine.members.contains(target.uuid)) return "You cannot put a bounty on a member of your own civilization.";

        SimpleInventory inv = new SimpleInventory(SET_ROWS * 9);
        Session s = new Session(p, false, target.uuid, inv);
        s.set = escrow.startSet(target.uuid);
        open.put(p.getUuid(), s);
        s.handler = ChestUi.open(p, Text.literal("Bounty on " + target.name), SET_ROWS, inv, who -> closeSet(s));
        if (s.handler == null) {
            open.remove(p.getUuid());
            return "Could not open the bounty screen.";
        }
        return null;
    }

    private void closeSet(Session s) {
        if (s.closed) return;
        s.closed = true;
        if (open.get(s.player.getUuid()) == s) open.remove(s.player.getUuid());
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < s.inv.size(); i++) {
            ItemStack st = s.inv.getStack(i);
            if (!st.isEmpty()) items.add(st);
            s.inv.setStack(i, ItemStack.EMPTY); // out of the inventory so it can never be counted twice
        }
        PlayerRecord t = h.data.players.get(s.target);
        boolean valid = t != null && h.tier(t).isDishonored();
        List<ItemStack> back = s.set.commit(items, valid);
        if (!back.isEmpty()) {
            for (ItemStack st : back) giveBack(s.player, st);
            s.player.sendMessage(Text.literal("That player can no longer take a bounty. Your items were returned.").formatted(Formatting.YELLOW));
        } else if (!items.isEmpty()) {
            long n = items.stream().mapToLong(ItemStack::getCount).sum();
            h.server.getPlayerManager().broadcast(Text.literal(s.player.getName().getString() + " put a bounty of " + n + " items on "
                    + t.name + "'s head. Total: " + escrow.itemsOn(s.target) + " items.").formatted(Formatting.GOLD), false);
        }
        h.markDirty();
        h.saveData();
    }

    private static void giveBack(ServerPlayerEntity p, ItemStack st) {
        if (p.isAlive() && !p.isRemoved()) {
            p.getInventory().offerOrDrop(st);
        } else {
            ItemScatterer.spawn(p.getEntityWorld(), p.getX(), p.getY(), p.getZ(), st);
        }
    }

    // ------------------------------------------------------------ kill payout

    /** Moves every bounty item on the victim into the killer's box, clears them, saves at once. */
    public void payout(ServerPlayerEntity killer, PlayerRecord kr, PlayerRecord vr) {
        long n = escrow.payout(vr.uuid, kr.uuid);
        if (n <= 0) return;
        h.markDirty();
        h.saveData();
        h.server.getPlayerManager().broadcast(Text.literal(kr.name + " collected the bounty on " + vr.name + ".").formatted(Formatting.GOLD), false);
        killer.sendMessage(Text.literal("You have a bounty to claim (" + n + " items). Use /bounty claim.").formatted(Formatting.GREEN));
    }

    public void onLogin(ServerPlayerEntity p) {
        long n = escrow.boxItems(p.getUuid().toString());
        if (n > 0) p.sendMessage(Text.literal("You have a bounty to claim (" + n + " items). Use /bounty claim.").formatted(Formatting.GREEN));
    }

    // ------------------------------------------------------------ claim

    public String openClaim(ServerPlayerEntity p) {
        String err = busy(p);
        if (err != null) return err;
        String id = p.getUuid().toString();
        if (escrow.boxItems(id) <= 0 || escrow.isBoxOpen(id)) return "You have no bounty to claim.";
        List<ItemStack> stacks = escrow.openBox(id, CLAIM_ROWS * 9);
        if (stacks == null) return "Your claim box is already open.";
        SimpleInventory inv = new SimpleInventory(CLAIM_ROWS * 9);
        for (int i = 0; i < stacks.size(); i++) inv.setStack(i, stacks.get(i));
        Session s = new Session(p, true, null, inv);
        open.put(p.getUuid(), s);
        s.handler = ChestUi.openCustom(p, Text.literal("Bounty claim"), (syncId, playerInv, who) -> new TakeOnlyHandler(syncId, playerInv, inv, s));
        if (s.handler == null) {
            closeClaim(s);
            return "Could not open the claim box.";
        }
        return null;
    }

    private void closeClaim(Session s) {
        if (s.closed) return;
        s.closed = true;
        if (open.get(s.player.getUuid()) == s) open.remove(s.player.getUuid());
        List<ItemStack> left = new ArrayList<>();
        for (int i = 0; i < s.inv.size(); i++) {
            ItemStack st = s.inv.getStack(i);
            if (!st.isEmpty()) left.add(st);
            s.inv.setStack(i, ItemStack.EMPTY);
        }
        escrow.closeBox(s.player.getUuid().toString(), left);
        h.markDirty();
        h.saveData();
    }

    /** 9x6 chest where nothing can be put in: every insertion route is refused in onSlotClick. */
    private final class TakeOnlyHandler extends GenericContainerScreenHandler {
        private final Session s;
        private final int boxSlots;

        TakeOnlyHandler(int syncId, PlayerInventory playerInv, Inventory inv, Session s) {
            super(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, CLAIM_ROWS);
            this.s = s;
            this.boxSlots = CLAIM_ROWS * 9;
        }

        private void refuse(PlayerEntity player) {
            // the client predicted the move; resend everything so it snaps back
            syncState();
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType type, PlayerEntity player) {
            boolean inBox = slotIndex >= 0 && slotIndex < boxSlots;
            boolean valid = slotIndex == -999 || slotIndex == -1 || (slotIndex >= 0 && slotIndex < slots.size());
            if (!valid) {
                refuse(player);
                return;
            }
            boolean ok;
            switch (type) {
                // dropping a held stack outside (-999) is fine; on a box slot only with an empty cursor (take)
                case PICKUP -> ok = !inBox || getCursorStack().isEmpty();
                // moving out of the box is a take; moving from the player inventory into the box is refused
                case QUICK_MOVE -> ok = inBox;
                // hotbar/offhand swap on a box slot would put the hotbar stack in unless that hotbar slot is empty
                case SWAP -> ok = !inBox || player.getInventory().getStack(button).isEmpty();
                // creative middle-click duplicates the stack
                case CLONE -> ok = false;
                // Q on a box slot drops from the box: a take
                case THROW -> ok = true;
                // drags can paint across box slots: refused entirely (never starts a vanilla drag)
                case QUICK_CRAFT -> ok = false;
                // double click gathers matching stacks onto the cursor; it only removes from slots
                case PICKUP_ALL -> ok = true;
                default -> ok = false;
            }
            if (!ok) {
                refuse(player);
                return;
            }
            super.onSlotClick(slotIndex, button, type, player);
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            if (slot < 0 || slot >= boxSlots) return ItemStack.EMPTY; // never push into the box
            return super.quickMove(player, slot);
        }

        @Override
        public boolean canInsertIntoSlot(ItemStack stack, net.minecraft.screen.slot.Slot slot) {
            return false;
        }

        @Override
        public void onClosed(PlayerEntity who) {
            super.onClosed(who); // returns the cursor stack to the player
            closeClaim(s);
        }
    }

    // ------------------------------------------------------------ closing

    private String busy(ServerPlayerEntity p) {
        Session s = open.get(p.getUuid());
        if (s != null && !s.closed) {
            if (p.isRemoved() || p.currentScreenHandler != s.handler) {
                // stale: handler gone without onClosed having run
                if (s.claim) closeClaim(s);
                else closeSet(s);
            } else {
                return "You already have a bounty screen open.";
            }
        }
        return null;
    }

    /** Disconnect or death: close this player's bounty UI so the contents are committed now. */
    public void closeFor(ServerPlayerEntity p) {
        Session s = open.get(p.getUuid());
        if (s == null) return;
        p.closeHandledScreen();
        if (s.claim) closeClaim(s);
        else closeSet(s);
    }

    /** Server stopping: close every open bounty UI before the final save. */
    public void closeAll() {
        for (Session s : new ArrayList<>(open.values())) {
            s.player.closeHandledScreen();
            if (s.claim) closeClaim(s);
            else closeSet(s);
        }
    }
}
