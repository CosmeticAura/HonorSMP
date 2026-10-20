package com.honorsmp;

import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.LootRoller;
import com.honorsmp.logic.LootTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/** Turns a rolled loot table into real items and hands them to the killer. */
final class BountyLoot {
    private BountyLoot() {
    }

    /** victimTierIndex 0..2 (Honorable, Noble, Paragon). */
    static void pay(Honor h, ServerPlayerEntity killer, int victimTierIndex) {
        LootTable[] tables = h.config.bountyLoot;
        LootTable table = tables != null && victimTierIndex >= 0 && victimTierIndex < tables.length ? tables[victimTierIndex] : null;
        if (table == null) table = HonorConfig.defaultBountyLoot()[victimTierIndex];
        List<LootRoller.Drop> drops = LootRoller.roll(table, new java.util.Random());
        DynamicRegistryManager reg = killer.getRegistryManager();
        Random mcRandom = killer.getRandom();
        List<String> names = new ArrayList<>();
        for (LootRoller.Drop d : drops) {
            try {
                ItemStack stack = build(d, reg, mcRandom);
                if (stack == null || stack.isEmpty()) continue;
                String label = stack.getCount() + "x " + describe(stack);
                int left = stack.getCount();
                int max = stack.getMaxCount();
                while (left > 0) {
                    ItemStack part = stack.copyWithCount(Math.min(left, max));
                    left -= part.getCount();
                    killer.getInventory().offerOrDrop(part);
                }
                names.add(label);
            } catch (Exception e) {
                Honor.LOG.warn("Skipped bad bounty loot entry {}: {}", d.entry().type, e.toString());
            }
        }
        if (names.isEmpty()) return;
        killer.sendMessage(Text.literal("Bounty loot: " + String.join(", ", names)).formatted(Formatting.GREEN));
    }

    private static ItemStack build(LootRoller.Drop d, DynamicRegistryManager reg, Random rng) {
        switch (d.entry().type) {
            case "item": {
                ItemStack s = stackOf(d.itemId());
                if (s == null) return null;
                return s.copyWithCount(Math.min(d.count(), Math.max(1, s.getMaxCount()) * 4));
            }
            case "enchanted_gear": {
                ItemStack s = stackOf(d.itemId());
                if (s == null) return null;
                Optional<? extends net.minecraft.registry.entry.RegistryEntryList<Enchantment>> pool =
                        reg.getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(EnchantmentTags.IN_ENCHANTING_TABLE);
                return EnchantmentHelper.enchant(rng, s.copyWithCount(1), d.level(), reg, pool);
            }
            case "book": {
                List<RegistryEntry<Enchantment>> all = new ArrayList<>();
                reg.getOrThrow(RegistryKeys.ENCHANTMENT).streamEntries().forEach(e -> {
                    if (!e.isIn(EnchantmentTags.CURSE)) all.add(e);
                });
                if (all.isEmpty()) return null;
                RegistryEntry<Enchantment> ench = all.get(rng.nextInt(all.size()));
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                book.addEnchantment(ench, LootRoller.bookLevel(d, ench.value().getMaxLevel()));
                return book;
            }
            default:
                return null;
        }
    }

    private static ItemStack stackOf(String id) {
        Identifier ident = Identifier.tryParse(id);
        if (ident == null || !Registries.ITEM.containsId(ident)) return null;
        return new ItemStack(Registries.ITEM.get(ident));
    }

    private static String describe(ItemStack s) {
        String base = s.getItemName().getString();
        if (s.isOf(Items.ENCHANTED_BOOK) || s.hasEnchantments()) {
            var ench = EnchantmentHelper.getEnchantments(s);
            List<String> parts = new ArrayList<>();
            for (var e : ench.getEnchantments()) parts.add(Enchantment.getName(e, ench.getLevel(e)).getString());
            if (!parts.isEmpty()) return base + " (" + String.join(", ", parts) + ")";
        }
        return base;
    }
}
