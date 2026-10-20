package com.honorsmp.logic;

import java.util.LinkedHashMap;
import java.util.Map;

/** Netherite swords, axes and armor are not allowed; they become the diamond version. */
public final class NetheriteRules {
    private NetheriteRules() {}

    private static final Map<String, String> TO_DIAMOND = Map.of(
        "minecraft:netherite_sword", "minecraft:diamond_sword",
        "minecraft:netherite_axe", "minecraft:diamond_axe",
        "minecraft:netherite_helmet", "minecraft:diamond_helmet",
        "minecraft:netherite_chestplate", "minecraft:diamond_chestplate",
        "minecraft:netherite_leggings", "minecraft:diamond_leggings",
        "minecraft:netherite_boots", "minecraft:diamond_boots");

    private static final Map<String, Integer> DIAMOND_MAX = Map.of(
        "minecraft:diamond_sword", 1561, "minecraft:diamond_axe", 1561,
        "minecraft:diamond_helmet", 363, "minecraft:diamond_chestplate", 528,
        "minecraft:diamond_leggings", 495, "minecraft:diamond_boots", 429);

    /** Diamond item id for a restricted netherite item id, or null when the item is allowed. */
    public static String diamondFor(String id) {
        return TO_DIAMOND.get(id);
    }

    public static boolean isRestricted(String id) {
        return TO_DIAMOND.containsKey(id);
    }

    public static int diamondMaxDamage(String diamondId) {
        return DIAMOND_MAX.getOrDefault(diamondId, 1561);
    }

    /** Same absolute damage, clamped so the diamond item never breaks from the conversion. */
    public static int clampDamage(int damage, int diamondMax) {
        return Math.max(0, Math.min(damage, diamondMax - 1));
    }

    /** Data-only view of a stack, used to test the conversion without Minecraft classes. */
    public record ItemData(String id, Map<String, Integer> enchants, int damage, String name) {}

    /** Converts a restricted netherite item; any other item is returned as is. */
    public static ItemData convert(ItemData in, int diamondMax) {
        String dia = diamondFor(in.id());
        if (dia == null) return in;
        return new ItemData(dia, new LinkedHashMap<>(in.enchants()), clampDamage(in.damage(), diamondMax), in.name());
    }
}
