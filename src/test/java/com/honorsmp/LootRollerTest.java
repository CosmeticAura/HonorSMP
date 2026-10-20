package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.honorsmp.logic.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LootRollerTest {
    private static LootTable table(int rolls, LootEntry... e) {
        return new LootTable(rolls, new ArrayList<>(Arrays.asList(e)));
    }

    @Test
    void rollCountAndRangesRespected() {
        LootTable t = table(5, LootEntry.item(1, 3, 6, "minecraft:emerald"));
        for (int seed = 0; seed < 50; seed++) {
            List<LootRoller.Drop> d = LootRoller.roll(t, new Random(seed));
            assertEquals(5, d.size());
            for (var x : d) assertTrue(x.count() >= 3 && x.count() <= 6);
        }
    }

    @Test
    void weightsRespected() {
        LootTable t = table(1, LootEntry.item(9, 1, 1, "a:heavy"), LootEntry.item(1, 1, 1, "a:light"));
        t.rolls = 1;
        Random r = new Random(42);
        int heavy = 0;
        for (int i = 0; i < 5000; i++) if (LootRoller.roll(t, r).get(0).itemId().equals("a:heavy")) heavy++;
        assertTrue(heavy > 4300 && heavy < 4700, "heavy=" + heavy);
    }

    @Test
    void deterministicForSeed() {
        LootTable t = HonorConfig.defaultBountyLoot()[2];
        assertEquals(LootRoller.roll(t, new Random(7)), LootRoller.roll(t, new Random(7)));
    }

    @Test
    void emptyAndNullSafe() {
        assertTrue(LootRoller.roll(null, new Random(1)).isEmpty());
        assertTrue(LootRoller.roll(table(3), new Random(1)).isEmpty());
        assertTrue(LootRoller.roll(table(0, LootEntry.item(1, 1, 1, "a:b")), new Random(1)).isEmpty());
    }

    @Test
    void badEntriesSkipped() {
        LootEntry zero = LootEntry.item(0, 1, 1, "a:zero");
        LootEntry neg = LootEntry.item(-5, 1, 1, "a:neg");
        LootEntry noItems = LootEntry.item(5, 1, 1);
        LootEntry unknown = LootEntry.item(5, 1, 1, "a:x");
        unknown.type = "banana";
        LootEntry nullType = LootEntry.item(5, 1, 1, "a:x");
        nullType.type = null;
        LootEntry good = LootEntry.item(1, 2, 2, "a:good");
        LootTable t = table(10, zero, neg, noItems, unknown, nullType, null, good);
        List<LootRoller.Drop> d = LootRoller.roll(t, new Random(3));
        assertEquals(10, d.size());
        for (var x : d) assertEquals("a:good", x.itemId());
        assertTrue(LootRoller.roll(table(3, zero, null, noItems), new Random(1)).isEmpty());
    }

    @Test
    void swappedAndNonPositiveRangesClamped() {
        LootTable t = table(30, LootEntry.item(1, 5, 2, "a:b"), LootEntry.item(1, -4, 0, "a:c"));
        for (var x : LootRoller.roll(t, new Random(5))) {
            assertTrue(x.count() >= 1);
            assertTrue(x.count() <= 5);
        }
    }

    @Test
    void bookLevels() {
        LootTable t = table(40, LootEntry.book(1, 2, 3, false));
        for (var x : LootRoller.roll(t, new Random(9))) {
            assertNull(x.itemId());
            assertTrue(x.level() >= 2 && x.level() <= 3);
            assertEquals(2, LootRoller.bookLevel(x, 2));
            assertTrue(LootRoller.bookLevel(x, 1) == 1);
        }
        var m = LootRoller.roll(table(1, LootEntry.book(1, 1, 1, true)), new Random(1)).get(0);
        assertEquals(5, LootRoller.bookLevel(m, 5));
    }

    @Test
    void gearPicksFromListedItems() {
        LootTable t = table(30, LootEntry.gear(1, 15, 30, "a:sword", "a:axe"));
        Set<String> seen = new HashSet<>();
        for (var x : LootRoller.roll(t, new Random(2))) {
            seen.add(x.itemId());
            assertTrue(x.level() >= 15 && x.level() <= 30);
        }
        assertEquals(Set.of("a:sword", "a:axe"), seen);
    }

    @Test
    void oldConfigGetsDefaultsAndRoundTrips() {
        HonorConfig old = new Gson().fromJson("{\"serverBountyEmeralds\":[1,2,3],\"minBounty\":4}", HonorConfig.class);
        assertEquals(3, old.bountyLoot.length);
        assertEquals(2, old.bountyLoot[0].rolls);
        assertEquals(3, old.bountyLoot[1].rolls);
        assertEquals(4, old.bountyLoot[2].rolls);
        HonorConfig back = new Gson().fromJson(new Gson().toJson(new HonorConfig()), HonorConfig.class);
        assertEquals(6, back.bountyLoot[2].entries.size());
        var a = LootRoller.roll(old.bountyLoot[1], new Random(1));
        var b = LootRoller.roll(back.bountyLoot[1], new Random(1));
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).itemId(), b.get(i).itemId());
            assertEquals(a.get(i).count(), b.get(i).count());
            assertEquals(a.get(i).level(), b.get(i).level());
        }
    }

    @Test
    void defaultsAllValid() {
        for (LootTable t : HonorConfig.defaultBountyLoot()) for (LootEntry e : t.entries) assertTrue(LootRoller.valid(e));
    }
}
