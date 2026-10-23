package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.TradeCooldownRules;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 1.6.4: trades never lock, except blocklisted / donatable / enchanted-gear sell items. */
class TradeCooldownTest {
    HonorConfig cfg = new HonorConfig();

    /** The plain vanilla villager TradeOffer rule, used to prove "locks after max uses" with the real decision. */
    static int usesBeforeLock(boolean locks, int maxUses, int attempts) {
        int uses = 0;
        for (int i = 0; i < attempts; i++) {
            if (uses >= maxUses) break; // isDisabled()
            if (locks) uses++; // use() ran; otherwise the mixin cancelled it
        }
        return uses;
    }

    boolean locks(String id) {
        return TradeCooldownRules.locks(id, List.of(), -1, false, cfg);
    }

    @Test
    void blockedItemLocksAfterMaxUses() {
        assertTrue(locks("minecraft:diamond"));
        assertEquals(12, usesBeforeLock(locks("minecraft:diamond"), 12, 100));
        assertTrue(locks("minecraft:emerald"));
        assertTrue(locks("minecraft:name_tag"));
        assertTrue(locks("minecraft:saddle"));
        assertTrue(locks("minecraft:diamond_sword"));
        assertTrue(locks("minecraft:netherite_chestplate"));
        assertTrue(locks("minecraft:iron_ingot")); // donation list
        assertTrue(locks("minecraft:ancient_debris"));
    }

    @Test
    void unblockedItemNeverLocks() {
        assertFalse(locks("minecraft:paper"));
        assertFalse(locks("minecraft:iron_sword"));
        assertFalse(locks("minecraft:glass"));
        assertEquals(0, usesBeforeLock(locks("minecraft:paper"), 12, 100));
    }

    @Test
    void foodAndDonationItemsAreBlockedFromTheDonationRules() {
        assertTrue(TradeCooldownRules.locks("minecraft:bread", List.of(), 5, false, cfg)); // any food
        assertTrue(TradeCooldownRules.locks("minecraft:cake", List.of(), -1, false, cfg));
        assertTrue(locks("minecraft:golden_carrot") || TradeCooldownRules.locks("minecraft:golden_carrot", List.of(), 6, false, cfg));
        // a new donation item added by the admin is covered with no list change
        assertFalse(locks("minecraft:glow_berries_block"));
        cfg.donateExtraItems = new java.util.LinkedHashMap<>(cfg.donateExtraItems);
        cfg.donateExtraItems.put("minecraft:glow_berries_block", 3);
        assertTrue(locks("minecraft:glow_berries_block"));
        // refused foods are not donatable, so not locked
        assertFalse(TradeCooldownRules.locks("minecraft:rotten_flesh", List.of(), 4, false, cfg));
    }

    @Test
    void tagIdsWork() {
        cfg.tradeCooldownItems = List.of("#minecraft:swords");
        assertTrue(TradeCooldownRules.locks("minecraft:stone_sword", List.of("minecraft:swords"), -1, false, cfg));
        assertTrue(TradeCooldownRules.locks("minecraft:stone_sword", List.of("minecraft:swords", "minecraft:other"), -1, false, cfg));
        assertFalse(TradeCooldownRules.locks("minecraft:stone_sword", List.of("minecraft:axes"), -1, false, cfg));
        assertFalse(TradeCooldownRules.locks("minecraft:stone_sword", List.of(), -1, false, cfg));
        cfg.tradeCooldownItems = List.of("#swords"); // bare tag name means minecraft:
        assertTrue(TradeCooldownRules.locks("minecraft:stone_sword", List.of("minecraft:swords"), -1, false, cfg));
        // always-free tags win too
        cfg.tradeCooldownAlwaysFree = List.of("#minecraft:swords");
        assertFalse(TradeCooldownRules.locks("minecraft:stone_sword", List.of("minecraft:swords"), -1, false, cfg));
    }

    @Test
    void enchantedOutputIsBlocked() {
        assertFalse(TradeCooldownRules.locks("minecraft:iron_sword", List.of(), -1, false, cfg));
        assertTrue(TradeCooldownRules.locks("minecraft:iron_sword", List.of(), -1, true, cfg));
        assertTrue(TradeCooldownRules.locks("minecraft:leather_helmet", List.of(), -1, true, cfg));
    }

    @Test
    void alwaysFreeWinsOverEverything() {
        assertEquals(List.of("minecraft:enchanted_book", "minecraft:experience_bottle", "minecraft:ender_pearl"), cfg.tradeCooldownAlwaysFree);
        // ender pearl is a /donate item and on the blocklist, books are enchanted: still never limited
        assertTrue(cfg.tradeCooldownItems.contains("minecraft:ender_pearl"));
        assertFalse(locks("minecraft:ender_pearl"));
        assertFalse(TradeCooldownRules.locks("minecraft:enchanted_book", List.of(), -1, true, cfg));
        assertFalse(locks("minecraft:experience_bottle"));
        assertEquals(0, usesBeforeLock(locks("minecraft:ender_pearl"), 16, 100));
        // also when an admin puts a free item on the blocklist
        cfg.tradeCooldownItems = List.of("minecraft:experience_bottle", "minecraft:enchanted_book");
        assertFalse(locks("minecraft:experience_bottle"));
        assertFalse(TradeCooldownRules.locks("minecraft:enchanted_book", List.of(), -1, true, cfg));
    }

    @Test
    void switchedOffMeansVanillaForEverything() {
        cfg.noTradeCooldown = false;
        assertTrue(locks("minecraft:paper"));
        assertTrue(locks("minecraft:ender_pearl"));
    }

    @Test
    void idsWorkWithOrWithoutNamespaceAndCase() {
        cfg.tradeCooldownItems = List.of("PAPER");
        assertTrue(locks("minecraft:paper"));
        assertEquals("minecraft:paper", TradeCooldownRules.norm("paper"));
        assertEquals("#minecraft:swords", TradeCooldownRules.norm("#swords"));
        assertEquals("", TradeCooldownRules.norm("  "));
        assertEquals("", TradeCooldownRules.norm(null));
        cfg.tradeCooldownItems = java.util.Arrays.asList(null, "", "minecraft:paper");
        assertTrue(locks("minecraft:paper"));
    }

    @Test
    void defaultListHasTheRequiredItems() {
        List<String> l = cfg.tradeCooldownItems;
        for (String id : new String[] {"emerald", "diamond", "golden_apple", "enchanted_golden_apple", "golden_carrot", "totem_of_undying",
                "name_tag", "saddle", "netherite_scrap", "ancient_debris", "iron_block", "diamond_boots", "netherite_hoe", "diamond_spear"})
            assertTrue(l.contains("minecraft:" + id), id);
        // every default donation material is covered
        for (String id : HonorConfig.defaultMaterialPoints().keySet()) assertTrue(l.contains(id), id);
        assertFalse(l.contains("minecraft:enchanted_book"));
        assertEquals(l.size(), new java.util.HashSet<>(l).size()); // no duplicates
    }

    @Test
    void migrateFillsMissingLists() {
        cfg.tradeCooldownItems = null;
        cfg.tradeCooldownAlwaysFree = null;
        assertTrue(cfg.migrate());
        assertFalse(cfg.tradeCooldownItems.isEmpty());
        assertEquals(3, cfg.tradeCooldownAlwaysFree.size());
        assertTrue(cfg.noTradeCooldown);
    }
}
