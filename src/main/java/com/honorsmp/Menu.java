package com.honorsmp;

import com.honorsmp.logic.MenuClicks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Generic read-only chest menu: icon items with a name and lore, one click handler per slot. EVERY
 * click type is swallowed (vanilla's onSlotClick is never called), so nothing can be moved in, out or
 * around: pickup, shift-click, number keys, offhand swap, drag, double-click collect, throw, clone.
 * The icons live in a private inventory that is emptied on close, so nothing can leak on close,
 * disconnect, death or server stop. Use {@link #closeFor} / {@link #closeAll} from those hooks.
 *
 * <pre>
 * Menu.create(Text.literal("Title"), 3)
 *     .set(13, Menu.icon(Items.EMERALD, Text.literal("Hi"), List.of(Text.literal("lore"))), (who, right, shift) -> ...)
 *     .open(player);
 * </pre>
 */
public final class Menu {
    @FunctionalInterface
    public interface Click {
        void on(ServerPlayerEntity who, boolean rightClick, boolean shift);
    }

    private static final Map<UUID, MenuHandler> OPEN = new HashMap<>();

    private final Text title;
    private final int rows;
    private final ItemStack[] icons;
    private final Click[] clicks;

    private Menu(Text title, int rows) {
        this.title = title;
        this.rows = rows;
        this.icons = new ItemStack[rows * 9];
        this.clicks = new Click[rows * 9];
    }

    /** rows: 1 to 6 (3 = 27 slots, 6 = 54 slots). */
    public static Menu create(Text title, int rows) {
        return new Menu(title, Math.max(1, Math.min(6, rows)));
    }

    public int size() {
        return rows * 9;
    }

    /** Puts a non-clickable icon in the slot. */
    public Menu icon(int slot, ItemStack icon) {
        return set(slot, icon, null);
    }

    /** Puts an icon in the slot; the handler runs when the player left/right/shift-clicks it. */
    public Menu set(int slot, ItemStack icon, Click onClick) {
        if (slot < 0 || slot >= size()) throw new IndexOutOfBoundsException("slot " + slot);
        icons[slot] = icon;
        clicks[slot] = onClick;
        return this;
    }

    /** Returns false if the menu could not be opened. Replaces any screen the player has open. */
    public boolean open(ServerPlayerEntity p) {
        SimpleInventory inv = new SimpleInventory(size());
        for (int i = 0; i < icons.length; i++) if (icons[i] != null) inv.setStack(i, icons[i].copy());
        ScreenHandlerType<GenericContainerScreenHandler> type = switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
        MenuHandler[] made = new MenuHandler[1];
        var opened = p.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory((syncId, playerInv, who) -> {
            made[0] = new MenuHandler(type, syncId, playerInv, inv, rows, clicks.clone());
            return made[0];
        }, title));
        if (opened.isEmpty() || made[0] == null) return false;
        OPEN.put(p.getUuid(), made[0]);
        return true;
    }

    /** Closes this player's menu if one is open (disconnect, death). */
    public static void closeFor(ServerPlayerEntity p) {
        MenuHandler h = OPEN.remove(p.getUuid());
        if (h == null) return;
        if (p.currentScreenHandler == h) p.closeHandledScreen();
        h.release();
    }

    /** Server stopping. */
    public static void closeAll() {
        for (var e : new ArrayList<>(OPEN.values())) {
            if (e.viewer != null && e.viewer.currentScreenHandler == e) e.viewer.closeHandledScreen();
            e.release();
        }
        OPEN.clear();
    }

    // ------------------------------------------------------------ icon helpers

    /** Item with a name (no italics) and lore lines. */
    public static ItemStack icon(Item item, Text name, List<Text> lore) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponentTypes.CUSTOM_NAME, name.copy().styled(st -> st.withItalic(false)));
        if (lore != null && !lore.isEmpty()) {
            List<Text> l = new ArrayList<>();
            for (Text t : lore) l.add(t.copy().styled(st -> st.withItalic(false)));
            s.set(DataComponentTypes.LORE, new LoreComponent(l));
        }
        return s;
    }

    public static ItemStack icon(Item item, String name, Formatting color, String... lore) {
        List<Text> l = new ArrayList<>();
        for (String x : lore) l.add(Text.literal(x).formatted(Formatting.GRAY));
        return icon(item, Text.literal(name).formatted(color), l);
    }

    // ------------------------------------------------------------ handler

    private static final class MenuHandler extends GenericContainerScreenHandler {
        private final SimpleInventory inv;
        private final Click[] clicks;
        private final int menuSlots;
        ServerPlayerEntity viewer;
        private boolean released;

        MenuHandler(ScreenHandlerType<GenericContainerScreenHandler> type, int syncId, PlayerInventory playerInv,
                    SimpleInventory inv, int rows, Click[] clicks) {
            super(type, syncId, playerInv, inv, rows);
            this.inv = inv;
            this.clicks = clicks;
            this.menuSlots = rows * 9;
            if (playerInv.player instanceof ServerPlayerEntity sp) viewer = sp;
        }

        private static MenuClicks.Kind kind(SlotActionType t) {
            return switch (t) {
                case PICKUP -> MenuClicks.Kind.PICKUP;
                case QUICK_MOVE -> MenuClicks.Kind.QUICK_MOVE;
                case SWAP -> MenuClicks.Kind.SWAP;
                case CLONE -> MenuClicks.Kind.CLONE;
                case THROW -> MenuClicks.Kind.THROW;
                case QUICK_CRAFT -> MenuClicks.Kind.QUICK_CRAFT;
                case PICKUP_ALL -> MenuClicks.Kind.PICKUP_ALL;
            };
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType type, PlayerEntity player) {
            // vanilla never sees the click, whatever it is
            if (!getCursorStack().isEmpty()) setCursorStack(ItemStack.EMPTY);
            syncState(); // the client predicted something: snap it back
            if (!(player instanceof ServerPlayerEntity sp)) return;
            if (!MenuClicks.activates(kind(type), slotIndex, menuSlots, button)) return;
            Click c = clicks[slotIndex];
            if (c == null) return;
            try {
                c.on(sp, button == 1, type == SlotActionType.QUICK_MOVE);
            } catch (Exception e) {
                Honor.LOG.error("Menu click handler failed", e);
            }
        }

        @Override
        public net.minecraft.item.ItemStack quickMove(PlayerEntity player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canInsertIntoSlot(ItemStack stack, net.minecraft.screen.slot.Slot slot) {
            return false;
        }

        void release() {
            if (released) return;
            released = true;
            for (int i = 0; i < inv.size(); i++) inv.setStack(i, ItemStack.EMPTY);
        }

        @Override
        public void onClosed(PlayerEntity who) {
            super.onClosed(who);
            if (OPEN.get(who.getUuid()) == this) OPEN.remove(who.getUuid());
            release();
        }
    }
}
