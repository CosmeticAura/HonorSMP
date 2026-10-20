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
}
