package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.BountyEscrow;
import com.honorsmp.logic.CivChestEscrow;
import com.honorsmp.logic.CivChestRows;
import com.honorsmp.logic.CivChestRows.Status;
import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.MenuClicks;
import com.honorsmp.logic.Wallet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 1.6.8: civ chest rows bought as a civ upgrade. */
class CivChestRowsTest {
    static class Bag implements Wallet {
        final Map<String, Integer> items = new HashMap<>();
        Bag with(String id, int n) { items.put(id, n); return this; }
        public int count(String id) { return items.getOrDefault(id, 0); }
        public void take(String id, int n) { items.merge(id, -n, Integer::sum); }
    }

    static Bag rich() {
        return new Bag().with("minecraft:iron_ingot", 500).with("minecraft:gold_ingot", 500).with("minecraft:redstone", 500)
                .with("minecraft:lapis_lazuli", 500).with("minecraft:diamond", 500).with("minecraft:netherite_ingot", 500)
                .with("minecraft:emerald", 500);
    }

    // ---------------------------------------------------------------- rows

    @Test
    void rowCounts() {
        assertEquals(1, CivChestRows.unlockedRows(0));
        assertEquals(9, CivChestRows.unlockedSlots(0));
        assertEquals(54, CivChestRows.unlockedSlots(5));
        assertEquals(2, CivChestRows.nextRow(0));
        assertEquals(6, CivChestRows.nextRow(4));
        assertEquals(0, CivChestRows.nextRow(5));
        // odd saved values are clamped
        assertEquals(1, CivChestRows.unlockedRows(-4));
        assertEquals(6, CivChestRows.unlockedRows(99));
    }

    @Test
    void infoLine() {
        assertEquals("Civ chest: 1 of 6 rows", CivChestRows.infoLine(0));
        assertEquals("Civ chest: 4 of 6 rows", CivChestRows.infoLine(3));
        assertEquals("Civ chest: 6 of 6 rows", CivChestRows.infoLine(5));
    }

    // ---------------------------------------------------------------- prices

    @Test
    void defaultPricesMatchTheSpecAndNeverEmeralds() {
        var d = CivChestRows.defaultCosts();
        assertEquals(5, d.size());
        assertEquals(Map.of("minecraft:iron_ingot", 32), CivChestRows.costOf(d, 2));
        assertEquals(Map.of("minecraft:gold_ingot", 32), CivChestRows.costOf(d, 3));
        assertEquals(Map.of("minecraft:redstone", 64, "minecraft:lapis_lazuli", 32), CivChestRows.costOf(d, 4));
        assertEquals(Map.of("minecraft:diamond", 16), CivChestRows.costOf(d, 5));
        assertEquals(Map.of("minecraft:netherite_ingot", 2), CivChestRows.costOf(d, 6));
        for (int r = 2; r <= 6; r++) assertFalse(CivChestRows.costOf(d, r).containsKey("minecraft:emerald"));
        assertEquals(d, new HonorConfig().civChestRowCosts);
    }

    @Test
    void configPricesAreRespected() {
        var cfg = List.of(List.of("minecraft:coal:5"), List.of("copper_ingot:7", "minecraft:flint:2"));
        assertEquals(Map.of("minecraft:coal", 5), CivChestRows.costOf(cfg, 2));
        assertEquals(Map.of("minecraft:copper_ingot", 7, "minecraft:flint", 2), CivChestRows.costOf(cfg, 3));
        // rows missing from the config use their default
        assertEquals(Map.of("minecraft:redstone", 64, "minecraft:lapis_lazuli", 32), CivChestRows.costOf(cfg, 4));
        assertEquals(Map.of("minecraft:netherite_ingot", 2), CivChestRows.costOf(null, 6));
        // a broken entry or an emerald entry sends the row back to its default
        var bad = List.of(List.of("minecraft:coal:x"), List.of("minecraft:emerald:9"), List.<String>of());
        assertEquals(Map.of("minecraft:iron_ingot", 32), CivChestRows.costOf(bad, 2));
        assertEquals(Map.of("minecraft:gold_ingot", 32), CivChestRows.costOf(bad, 3));
        assertEquals(Map.of("minecraft:redstone", 64, "minecraft:lapis_lazuli", 32), CivChestRows.costOf(bad, 4));
        assertTrue(CivChestRows.costOf(cfg, 1).isEmpty());
        assertTrue(CivChestRows.costOf(cfg, 7).isEmpty());
    }

    @Test
    void buyingUsesTheConfiguredPrice() {
        var cfg = List.of(List.of("minecraft:coal:5"));
        var bag = new Bag().with("minecraft:coal", 12).with("minecraft:iron_ingot", 100);
        var r = CivChestRows.buy(cfg, 0, 2, bag);
        assertEquals(Status.OK, r.status());
        assertEquals(7, bag.count("minecraft:coal"));
        assertEquals(100, bag.count("minecraft:iron_ingot")); // the default price was not charged
    }

    // ---------------------------------------------------------------- buying

    @Test
    void rowsAreBoughtInOrderOnly() {
        var d = CivChestRows.defaultCosts();
        var bag = rich();
        var before = new HashMap<>(bag.items);
        assertEquals(Status.OUT_OF_ORDER, CivChestRows.buy(d, 0, 3, bag).status());
        assertEquals(Status.OUT_OF_ORDER, CivChestRows.buy(d, 0, 6, bag).status());
        assertEquals(Status.OUT_OF_ORDER, CivChestRows.buy(d, 2, 2, bag).status()); // already bought
        assertEquals(Status.OUT_OF_ORDER, CivChestRows.buy(d, 0, 1, bag).status()); // row 1 is never for sale
        assertEquals(before, bag.items, "refused buys take nothing");
        int bought = 0;
        for (int row = 2; row <= 6; row++) {
            var r = CivChestRows.buy(d, bought, row, bag);
            assertEquals(Status.OK, r.status(), "row " + row);
            assertEquals(bought + 1, r.bought());
            bought = r.bought();
        }
        assertEquals(5, bought);
        assertEquals(Status.ALL_BOUGHT, CivChestRows.buy(d, 5, 6, bag).status());
    }

    @Test
    void costIsTakenExactlyAndEmeraldsAreUntouched() {
        var bag = rich();
        var r = CivChestRows.buy(CivChestRows.defaultCosts(), 2, 4, bag);
        assertEquals(Status.OK, r.status());
        assertEquals(Map.of("minecraft:redstone", 64, "minecraft:lapis_lazuli", 32), r.taken());
        assertEquals(436, bag.count("minecraft:redstone"));
        assertEquals(468, bag.count("minecraft:lapis_lazuli"));
        assertEquals(500, bag.count("minecraft:iron_ingot"));
        assertEquals(500, bag.count("minecraft:emerald"));
    }

    @Test
    void notEnoughIsAllOrNothingAndNamesTheMissingItem() {
        var bag = new Bag().with("minecraft:redstone", 100).with("minecraft:lapis_lazuli", 31);
        var r = CivChestRows.buy(CivChestRows.defaultCosts(), 2, 4, bag);
        assertEquals(Status.NOT_ENOUGH_ITEMS, r.status());
        assertEquals("minecraft:lapis_lazuli", r.missing());
        assertEquals(2, r.bought());
        assertTrue(r.taken().isEmpty());
        assertEquals(100, bag.count("minecraft:redstone"));
        assertEquals(31, bag.count("minecraft:lapis_lazuli"));
    }

    // ---------------------------------------------------------------- locked slots

    @Test
    void lockedSlotsRefuseEveryClickType() {
        int unlocked = CivChestRows.unlockedSlots(1); // 18 slots open
        for (MenuClicks.Kind k : MenuClicks.Kind.values()) {
            for (int slot = unlocked; slot < 54; slot++) {
                assertFalse(CivChestRows.vanillaMayProcess(k, slot, unlocked, 54), k + " on locked slot " + slot);
            }
            for (int slot = 0; slot < unlocked; slot++) {
                assertTrue(CivChestRows.vanillaMayProcess(k, slot, unlocked, 54), k + " on open slot " + slot);
            }
            // outside the window and the player's own slots stay usable
            assertTrue(CivChestRows.vanillaMayProcess(k, -999, unlocked, 54));
            assertTrue(CivChestRows.vanillaMayProcess(k, 54, unlocked, 54));
            assertTrue(CivChestRows.vanillaMayProcess(k, 80, unlocked, 54));
        }
    }

    @Test
    void nothingCanBeInsertedIntoLockedSlots() {
        assertTrue(CivChestRows.mayInsert(0, 9, 54));
        assertTrue(CivChestRows.mayInsert(8, 9, 54));
        assertFalse(CivChestRows.mayInsert(9, 9, 54));
        assertFalse(CivChestRows.mayInsert(53, 9, 54));
        assertFalse(CivChestRows.mayInsert(-1, 9, 54));
        assertTrue(CivChestRows.mayInsert(53, 54, 54)); // everything bought
    }

    @Test
    void buyingARowOpensExactlyNineMoreSlots() {
        for (int b = 0; b < 5; b++) {
            int before = CivChestRows.unlockedSlots(b);
            assertFalse(CivChestRows.isSlotUnlocked(before, b));
            assertTrue(CivChestRows.isSlotUnlocked(before, b + 1));
            assertEquals(before + 9, CivChestRows.unlockedSlots(b + 1));
        }
    }

    // ---------------------------------------------------------------- migration

    record Item(String id, int count) {}

    static final BountyEscrow.Ops<Item> OPS = new BountyEscrow.Ops<>() {
        public boolean same(Item a, Item b) { return a.id().equals(b.id()); }
        public int count(Item t) { return t.count(); }
        public Item withCount(Item t, int n) { return new Item(t.id(), n); }
        public int maxStack(Item t) { return 64; }
    };

    static long sum(List<Item> l) { return l.stream().mapToLong(Item::count).sum(); }

    static List<Item> stacks(int n) {
        List<Item> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new Item("item" + i, 1 + i));
        return l;
    }

    @Test
    void fitKeepsTheFirstStacksAndReturnsTheRest() {
        var all = stacks(30);
        var fit = CivChestRows.fit(all, 9);
        assertEquals(all.subList(0, 9), fit.keep());
        assertEquals(all.subList(9, 30), fit.overflow());
        var again = new ArrayList<>(fit.keep());
        again.addAll(fit.overflow());
        assertEquals(all, again, "nothing lost, nothing duplicated, same order");
    }

    @Test
    void fitWithRoomMovesNothing() {
        var all = stacks(10);
        var fit = CivChestRows.fit(all, 18);
        assertEquals(all, fit.keep());
        assertTrue(fit.overflow().isEmpty());
        assertTrue(CivChestRows.fit(null, 9).keep().isEmpty());
    }

    @Test
    void trimMovesOnlyWhatNoLongerFits() {
        var chest = new CivChestEscrow<>(OPS, 54);
        var all = stacks(30);
        for (Item t : all) chest.load("civ", t);
        long total = sum(all);
        var over = chest.trim("civ", 18);
        assertEquals(12, over.size());
        assertEquals(18, chest.stored("civ").size());
        assertEquals(total, sum(chest.stored("civ")) + sum(over), "every item is in exactly one place");
        // trimming again to the same size moves nothing
        assertTrue(chest.trim("civ", 18).isEmpty());
        // a bigger chest takes everything that is stored
        assertTrue(chest.trim("civ", 54).isEmpty());
        assertEquals(18, chest.stored("civ").size());
    }

    @Test
    void trimLeavesAnOpenChestAlone() {
        var chest = new CivChestEscrow<>(OPS, 54);
        for (Item t : stacks(30)) chest.load("civ", t);
        assertEquals(CivChestEscrow.Status.OK, chest.open("civ", "L", true, 54).status());
        assertTrue(chest.trim("civ", 9).isEmpty());
        assertTrue(chest.trim("none", 9).isEmpty());
    }

    @Test
    void openOnlyHandsOutTheOpenSlots() {
        var chest = new CivChestEscrow<>(OPS, 54);
        var all = stacks(30);
        for (Item t : all) chest.load("civ", t);
        var res = chest.open("civ", "L", true, 18);
        assertEquals(CivChestEscrow.Status.OK, res.status());
        assertTrue(res.items().size() <= 18);
        // closing with what the screen held keeps every stack
        assertTrue(chest.close("civ", "L", res.items()));
        assertEquals(sum(all), sum(chest.stored("civ")), "the stacks behind the open slots stay stored, nothing lost");
    }

    // ---------------------------------------------------------------- data

    @Test
    void oldDataStartsWithOneRow() {
        var civ = new HonorData.Civ();
        assertEquals(0, civ.chestRows);
        assertEquals(1, CivChestRows.unlockedRows(civ.chestRows));
    }

    @Test
    void disbandingTakesTheBoughtRowsWithIt() {
        var civ = new HonorData.Civ();
        civ.chestRows = 4;
        CivUpgradeApi.clear(civ);
        assertEquals(0, civ.chestRows);
    }

    @Test
    void apiBuysInOrderAndStoresTheCount() {
        var civ = new HonorData.Civ();
        var cfg = new HonorConfig();
        var bag = rich();
        assertEquals(0, CivUpgradeApi.chestRows(civ));
        var r = CivUpgradeApi.buyChestRow(civ, cfg, bag);
        assertTrue(r.ok());
        assertEquals(1, civ.chestRows);
        assertEquals(468, bag.count("minecraft:iron_ingot"));
        for (int i = 0; i < 4; i++) assertTrue(CivUpgradeApi.buyChestRow(civ, cfg, bag).ok());
        assertEquals(5, civ.chestRows);
        assertEquals(Status.ALL_BOUGHT, CivUpgradeApi.buyChestRow(civ, cfg, bag).status());
        assertEquals(5, civ.chestRows);
    }
}
