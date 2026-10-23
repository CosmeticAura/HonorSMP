package com.honorsmp.logic;

/** Pure rules for what can be donated and what each item is worth in points. */
public final class DonationRules {
    private DonationRules() {}

    private static final String CAKE = "minecraft:cake";

    /**
     * Points one item of this kind is worth, or -1 when it is refused.
     *
     * @param nutrition hunger value from the item's food component, or -1 when it has none
     * @param plain     true when the stack has no changed components (no name, enchants, etc.)
     */
    public static int pointsPerItem(String itemId, int nutrition, boolean plain, HonorConfig cfg) {
        if (!plain || itemId == null) return -1;
        if (cfg.donateZeroFoods != null && cfg.donateZeroFoods.contains(itemId)) return -1;
        if (cfg.donateExtraItems != null && cfg.donateExtraItems.containsKey(itemId)) {
            Integer p = cfg.donateExtraItems.get(itemId);
            return p == null || p < 0 ? -1 : p;
        }
        if (CAKE.equals(itemId)) return Math.max(0, cfg.donateCakeHunger);
        if (nutrition >= 0) return nutrition;
        return -1;
    }

    /**
     * Cap type of a donatable item: its bare id ("golden_apple") when that id has an entry in
     * donateItemDailyCaps, otherwise "food" for anything with nutrition (and cake), otherwise the bare id.
     */
    public static String category(String itemId, int nutrition, HonorConfig cfg) {
        if (cfg.donateCapTypes != null) {
            String t = cfg.donateCapTypes.get(itemId);
            if (t != null && !t.isEmpty()) return t;
        }
        String bare = itemId.startsWith("minecraft:") ? itemId.substring("minecraft:".length()) : itemId;
        if (cfg.donateItemDailyCaps != null && cfg.donateItemDailyCaps.containsKey(bare)) return bare;
        if (nutrition >= 0 || CAKE.equals(itemId)) return DonateMath.FOOD;
        return bare;
    }

    public static boolean donatable(String itemId, int nutrition, boolean plain, HonorConfig cfg) {
        return pointsPerItem(itemId, nutrition, plain, cfg) >= 0;
    }

    public static long points(String itemId, int nutrition, boolean plain, int count, HonorConfig cfg) {
        int p = pointsPerItem(itemId, nutrition, plain, cfg);
        return p < 0 ? 0 : (long) p * count;
    }

    /**
     * 1.6.2 treasury clean-up: does a treasury stack of this kind move to the /donation boxes? True for anything a
     * player could donate (food, golden apples, totems, pearls, ores, ingots, blocks) and for any food at all, even
     * the ones worth 0 points. Emeralds and emerald blocks never move (they are not donatable since 1.6.5). Stacks with a
     * changed name, enchantments or other components stay too, because /donate refuses them.
     */
    public static boolean treasuryMoves(String itemId, int nutrition, boolean plain, HonorConfig cfg) {
        if (!plain || itemId == null) return false;
        if ("minecraft:emerald".equals(itemId) || "minecraft:emerald_block".equals(itemId)) return false;
        if (cfg.donateExtraItems != null && cfg.donateExtraItems.containsKey(itemId)) {
            Integer p = cfg.donateExtraItems.get(itemId);
            if (p != null && p >= 0) return true;
        }
        return CAKE.equals(itemId) || nutrition >= 0;
    }
}
