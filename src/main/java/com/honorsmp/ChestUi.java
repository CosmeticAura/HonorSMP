package com.honorsmp;

import java.util.function.Consumer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Server-side chest UI backed by a real Inventory. Vanilla handles every click, shift-click,
 * number key and drag. onClose runs from the handler's onClosed (which vanilla calls on close,
 * disconnect and server stop), so the inventory is the single source of truth.
 */
public final class ChestUi {
    private ChestUi() {}

    /** Opens the UI. Returns the handler, or null if it could not be opened. */
    public static ScreenHandler open(ServerPlayerEntity p, Text title, int rows, Inventory inv, Consumer<PlayerEntity> onClose) {
        ScreenHandlerType<GenericContainerScreenHandler> type = switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
        int r = Math.max(1, Math.min(6, rows));
        var opened = p.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, player) ->
                new GenericContainerScreenHandler(type, syncId, playerInv, inv, r) {
                    @Override
                    public void onClosed(PlayerEntity who) {
                        super.onClosed(who);
                        onClose.accept(who);
                    }
                }, title));
        return opened.isPresent() ? p.currentScreenHandler : null;
    }

    /**
     * 1.6.8: the civ chest. Always 6 rows; only the first {@code unlockedSlots} slots hold items. The rest hold gray
     * panes that no click type can touch: nothing is picked up, put in, shift clicked, number keyed, dropped, cloned,
     * dragged or double-click collected there.
     */
    public static ScreenHandler openRows(ServerPlayerEntity p, Text title, Inventory inv, int unlockedSlots, Consumer<PlayerEntity> onClose) {
        var opened = p.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, player) ->
                new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, 6) {
                    private boolean locked(int slot) {
                        return slot >= unlockedSlots && slot < com.honorsmp.logic.CivChestRows.TOTAL_SLOTS;
                    }

                    @Override
                    public void onSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType type, PlayerEntity who) {
                        if (locked(slotIndex)) {
                            syncState(); // the client may have predicted a move; show the real state
                            return;
                        }
                        super.onSlotClick(slotIndex, button, type, who);
                    }

                    @Override
                    public boolean canInsertIntoSlot(net.minecraft.item.ItemStack stack, net.minecraft.screen.slot.Slot slot) {
                        return !locked(slot.id) && super.canInsertIntoSlot(stack, slot);
                    }

                    @Override
                    public net.minecraft.item.ItemStack quickMove(PlayerEntity who, int index) {
                        if (index < 0 || index >= slots.size() || locked(index)) return net.minecraft.item.ItemStack.EMPTY;
                        net.minecraft.screen.slot.Slot slot = slots.get(index);
                        if (!slot.hasStack()) return net.minecraft.item.ItemStack.EMPTY;
                        net.minecraft.item.ItemStack stack = slot.getStack();
                        net.minecraft.item.ItemStack before = stack.copy();
                        boolean moved = index < com.honorsmp.logic.CivChestRows.TOTAL_SLOTS
                                ? insertItem(stack, com.honorsmp.logic.CivChestRows.TOTAL_SLOTS, slots.size(), true)
                                : insertItem(stack, 0, unlockedSlots, false);
                        if (!moved) return net.minecraft.item.ItemStack.EMPTY;
                        if (stack.isEmpty()) slot.setStack(net.minecraft.item.ItemStack.EMPTY);
                        else slot.markDirty();
                        return before;
                    }

                    @Override
                    public void onClosed(PlayerEntity who) {
                        super.onClosed(who);
                        onClose.accept(who);
                    }
                }, title));
        return opened.isPresent() ? p.currentScreenHandler : null;
    }

    /** Opens a UI built by a custom handler factory (e.g. a take-only claim box). Returns the handler or null. */
    public static ScreenHandler openCustom(ServerPlayerEntity p, Text title, net.minecraft.screen.ScreenHandlerFactory f) {
        var opened = p.openHandledScreen(new SimpleNamedScreenHandlerFactory(f, title));
        return opened.isPresent() ? p.currentScreenHandler : null;
    }
}
