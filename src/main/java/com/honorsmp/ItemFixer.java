package com.honorsmp;

import com.honorsmp.logic.EnchantCap;
import com.honorsmp.logic.NetheriteRules;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Single entry point for the Part E nerfs: enchant caps and the netherite sword/axe/armor ban. */
public final class ItemFixer {
    private ItemFixer() {}

    private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

    /**
     * Applies both rules. Returns the same instance (caps are applied in place) or, for a netherite
     * conversion, a new diamond stack the caller must put back. {@code owner} gets the netherite ingot
     * refund; with null no ingot is given.
     */
    public static ItemStack fix(ItemStack stack, ServerPlayerEntity owner) {
        if (stack == null || stack.isEmpty()) return stack;
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        String dia = NetheriteRules.diamondFor(id);
        if (dia != null) {
            Item d = Registries.ITEM.get(Identifier.of(dia));
            ItemStack n = new ItemStack(d, stack.getCount());
            n.applyChanges(stack.getComponentChanges());
            if (n.contains(DataComponentTypes.DAMAGE)) {
                n.setDamage(NetheriteRules.clampDamage(stack.getDamage(), n.getMaxDamage()));
            }
            fixCaps(n);
            if (owner != null) {
                for (int i = 0; i < stack.getCount(); i++) owner.giveItemStack(new ItemStack(Items.NETHERITE_INGOT));
            }
            return n;
        }
        fixCaps(stack);
        return stack;
    }

    /** Lowers only the capped enchants on the item and its stored book enchants, in place. */
    public static boolean fixCaps(ItemStack stack) {
        if (stack == null || stack.isEmpty() || Honor.I == null) return false;
        Map<String, Integer> caps = Honor.I.config.enchantCaps;
        if (caps == null || caps.isEmpty()) return false;
        boolean a = capComponent(stack, DataComponentTypes.ENCHANTMENTS, caps);
        boolean b = capComponent(stack, DataComponentTypes.STORED_ENCHANTMENTS, caps);
        return a || b;
    }

    private static boolean capComponent(ItemStack stack, ComponentType<ItemEnchantmentsComponent> type, Map<String, Integer> caps) {
        ItemEnchantmentsComponent comp = stack.get(type);
        if (comp == null || comp.isEmpty()) return false;
        Map<String, Integer> levels = new LinkedHashMap<>();
        Map<String, RegistryEntry<Enchantment>> entries = new LinkedHashMap<>();
        for (var e : comp.getEnchantmentEntries()) {
            String key = e.getKey().getKey().map(k -> k.getValue().toString()).orElse(null);
            if (key == null) continue;
            levels.put(key, e.getIntValue());
            entries.put(key, e.getKey());
        }
        EnchantCap.Result r = EnchantCap.apply(levels, caps);
        if (!r.changed()) return false;
        ItemEnchantmentsComponent.Builder b = new ItemEnchantmentsComponent.Builder(comp);
        for (var e : r.enchants().entrySet()) {
            if (e.getValue() != levels.get(e.getKey()).intValue()) b.set(entries.get(e.getKey()), e.getValue());
        }
        stack.set(type, b.build());
        return true;
    }

    /** Fixes a whole inventory; converted stacks are written back. */
    public static void sweep(Inventory inv, ServerPlayerEntity owner) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            ItemStack f = fix(s, owner);
            if (f != s) inv.setStack(i, f);
        }
    }

    /** Player inventory, armor, offhand and the cursor stack. */
    public static void sweepPlayer(ServerPlayerEntity p) {
        sweep(p.getInventory(), p);
        for (EquipmentSlot es : WORN) {
            ItemStack s = p.getEquippedStack(es);
            if (s.isEmpty()) continue;
            ItemStack f = fix(s, p);
            if (f != s) p.equipStack(es, f);
        }
        sweepHandler(p.currentScreenHandler, p);
    }

    /** Every slot of an open screen (chests, ender chest, trade/anvil outputs) plus the cursor. */
    public static void sweepHandler(ScreenHandler h, ServerPlayerEntity owner) {
        if (h == null) return;
        for (Slot slot : h.slots) {
            ItemStack s = slot.getStack();
            if (s.isEmpty()) continue;
            ItemStack f = fix(s, owner);
            if (f != s) slot.setStack(f);
        }
        ItemStack cur = h.getCursorStack();
        if (!cur.isEmpty()) {
            ItemStack f = fix(cur, owner);
            if (f != cur) h.setCursorStack(f);
        }
    }
}
