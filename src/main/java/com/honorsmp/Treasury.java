package com.honorsmp;

import com.google.gson.JsonElement;
import com.honorsmp.logic.TreasuryMath;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.registry.RegistryOps;

/**
 * Judges' item pool. While the UI is closed the stacks live in {@code items}. While it is open the
 * live inventory is the only owner of what it shows; {@code items} then holds only overflow beyond
 * 54 slots, and emeralds paid meanwhile wait in {@code pendingEmeralds}.
 */
public final class Treasury {
    private final Honor h;
    private final List<ItemStack> items = new ArrayList<>();
    private SimpleInventory open;
    private ServerPlayerEntity viewer;
    private ScreenHandler handler;
    private long pendingEmeralds;

    public Treasury(Honor h) {
        this.h = h;
    }

    private RegistryOps<JsonElement> ops() {
        return RegistryOps.of(JsonOps.INSTANCE, h.server.getRegistryManager());
    }

    /** Reads data.treasury and migrates the old treasuryEmeralds count. */
    public void load() {
        items.clear();
        if (h.data.treasury == null) h.data.treasury = new ArrayList<>();
        for (JsonElement e : h.data.treasury) {
            try {
                ItemStack s = ItemStack.CODEC.parse(ops(), e).getOrThrow();
                if (!s.isEmpty()) items.add(s);
            } catch (Exception ex) {
                Honor.LOG.error("Skipping unreadable treasury stack: {}", e, ex);
            }
        }
        if (h.data.treasuryEmeralds > 0) {
            addEmeralds(h.data.treasuryEmeralds);
            h.data.treasuryEmeralds = 0;
        }
    }

    /** The stacks held while the UI is closed (startup migration only; the caller must not keep or copy them). */
    List<ItemStack> closedItems() {
        return items;
    }

    /** Writes the current contents (live inventory if open) into data.treasury. */
    public void sync() {
        List<ItemStack> all = new ArrayList<>();
        if (open != null) {
            for (int i = 0; i < open.size(); i++) if (!open.getStack(i).isEmpty()) all.add(open.getStack(i));
        }
        all.addAll(items);
        List<JsonElement> out = new ArrayList<>();
        for (ItemStack s : all) {
            try {
                out.add(ItemStack.CODEC.encodeStart(ops(), s).getOrThrow());
            } catch (Exception ex) {
                Honor.LOG.error("Could not save a treasury stack: {}", s, ex);
            }
        }
        if (pendingEmeralds > 0) {
            for (int n : TreasuryMath.splitStacks(pendingEmeralds, 64)) {
                out.add(ItemStack.CODEC.encodeStart(ops(), new ItemStack(Items.EMERALD, n)).getOrThrow());
            }
        }
        h.data.treasury = out;
    }

    /** Adds paid items (fines). Works while the UI is open: they wait in the overflow list and merge on close. */
    public void addItem(ItemStack s) {
        if (!s.isEmpty()) items.add(s.copy());
    }

    /** Adds emeralds, merging into existing emerald stacks. */
    public void addEmeralds(long n) {
        if (n <= 0) return;
        if (open != null) {
            pendingEmeralds += n;
            return;
        }
        long left = n;
        for (ItemStack s : items) {
            if (left <= 0) break;
            if (s.isOf(Items.EMERALD) && s.getCount() < s.getMaxCount()) {
                int add = (int) Math.min(left, s.getMaxCount() - s.getCount());
                s.increment(add);
                left -= add;
            }
        }
        for (int c : TreasuryMath.splitStacks(left, 64)) items.add(new ItemStack(Items.EMERALD, c));
    }

    /** Returns an error message, or null when opened. */
    public String openFor(ServerPlayerEntity p) {
        if (viewer != null && (viewer.isRemoved() || viewer.currentScreenHandler != handler)) {
            // stale: the handler is gone without onClosed having run
            close(null);
        }
        if (open != null) return "Another judge has the treasury open. Wait for them to close it.";
        SimpleInventory inv = new SimpleInventory(54);
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack s : items) {
            ItemStack left = inv.addStack(s.copy());
            if (!left.isEmpty()) rest.add(left);
        }
        items.clear();
        items.addAll(rest);
        open = inv;
        viewer = p;
        handler = ChestUi.open(p, Text.literal("Treasury"), 6, inv, who -> close(who));
        if (handler == null) {
            close(null);
            return "Could not open the treasury. Press Esc to close any other screen and run /honor treasury again.";
        }
        return null;
    }

    /** Snapshots the open inventory into items and clears the open state. Safe to call twice. */
    private void close(PlayerEntity who) {
        if (open == null) return;
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < open.size(); i++) {
            ItemStack s = open.getStack(i);
            if (!s.isEmpty()) all.add(s);
            open.setStack(i, ItemStack.EMPTY); // removed from the inventory so it cannot be counted twice
        }
        all.addAll(items);
        items.clear();
        items.addAll(all);
        open = null;
        viewer = null;
        handler = null;
        long pend = pendingEmeralds;
        pendingEmeralds = 0;
        addEmeralds(pend);
        h.markDirty();
        h.saveData();
    }

    /** Closes the UI for this player if they have it open (disconnect, death). */
    public void closeIfViewer(ServerPlayerEntity p) {
        if (viewer == p) {
            p.closeHandledScreen();
            close(null);
        }
    }

    /** Full reset: forget all contents (including the open UI) WITHOUT writing anything back, then close the screen. */
    public void wipe() {
        ServerPlayerEntity v = viewer;
        if (open != null) for (int i = 0; i < open.size(); i++) open.setStack(i, ItemStack.EMPTY);
        open = null;
        viewer = null;
        handler = null;
        items.clear();
        pendingEmeralds = 0;
        h.data.treasury = new ArrayList<>();
        if (v != null) v.closeHandledScreen();
    }

    /** Server stopping: close any open UI so contents are flushed before the final save. */
    public void closeAll() {
        if (viewer != null) viewer.closeHandledScreen();
        close(null);
    }
}
