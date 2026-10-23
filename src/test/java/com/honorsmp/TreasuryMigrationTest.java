package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.DonationRotation;
import com.honorsmp.logic.DonationRules;
import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.TreasuryMigration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 1.6.2: donations never reach the treasury, and old donatable treasury stacks are moved out exactly once. */
class TreasuryMigrationTest {
    HonorConfig cfg = new HonorConfig();

    boolean moves(String id, int nutrition) {
        return DonationRules.treasuryMoves("minecraft:" + id, nutrition, true, cfg);
    }

    @Test
    void donatableTypesMoveAndEmeraldsStay() {
        assertTrue(moves("bread", 5));
        assertTrue(moves("cooked_beef", 8));
        assertTrue(moves("rotten_flesh", 4)); // food worth 0 points is still food
        assertTrue(moves("cake", -1));
        assertTrue(moves("golden_apple", 4));
        assertTrue(moves("enchanted_golden_apple", 4));
        assertTrue(moves("totem_of_undying", -1));
        assertTrue(moves("ender_pearl", -1));
        for (String id : List.of("iron_ingot", "iron_block", "raw_gold", "coal_ore", "diamond", "diamond_block",
                "netherite_ingot", "lapis_lazuli", "ancient_debris", "amethyst_shard")) {
            assertTrue(moves(id, -1), id);
        }
        assertFalse(moves("emerald", -1));
        assertFalse(moves("emerald_block", -1));
        assertFalse(moves("diamond_sword", -1));
        assertFalse(moves("cobblestone", -1));
        assertFalse(DonationRules.treasuryMoves("minecraft:bread", 5, false, cfg)); // renamed or enchanted stays
        assertFalse(DonationRules.treasuryMoves(null, 5, true, cfg));
    }

    @Test
    void movesOnlyDonatableStacksInOrderAndKeepsTheRest() {
        HonorData d = new HonorData();
        List<String> t = new ArrayList<>(List.of("bread", "emerald", "golden_apple", "diamond_sword", "emerald_block", "bread", "iron_ingot"));
        List<String> moved = TreasuryMigration.runOnce(d, t, List.of("u1", "u2"),
                s -> List.of("bread", "golden_apple", "iron_ingot").contains(s));
        assertEquals(List.of("bread", "golden_apple", "bread", "iron_ingot"), moved);
        assertEquals(List.of("emerald", "diamond_sword", "emerald_block"), t);
        assertTrue(d.treasuryDonationsMoved);
        // nothing lost, nothing duplicated
        assertEquals(7, moved.size() + t.size());
    }

    @Test
    void runsOnlyOnce() {
        HonorData d = new HonorData();
        List<String> t = new ArrayList<>(List.of("bread", "emerald"));
        assertEquals(List.of("bread"), TreasuryMigration.runOnce(d, t, List.of("u1"), "bread"::equals));
        t.add("bread"); // later arrivals (e.g. a fine paid in bread) are not touched again
        assertTrue(TreasuryMigration.runOnce(d, t, List.of("u1"), "bread"::equals).isEmpty());
        assertEquals(List.of("emerald", "bread"), t);
    }

    @Test
    void flagSurvivesSaveAndLoad() {
        HonorData d = new HonorData();
        d.treasuryDonationsMoved = true;
        HonorData back = HonorData.parse(new com.google.gson.Gson().toJson(d));
        assertTrue(back.treasuryDonationsMoved);
        assertFalse(HonorData.parse("{}").treasuryDonationsMoved); // 1.6.1 data: not run yet
    }

    @Test
    void withNobodyToShareWithNothingIsTouchedAndItRetriesLater() {
        HonorData d = new HonorData();
        List<String> t = new ArrayList<>(List.of("bread", "emerald"));
        assertTrue(TreasuryMigration.runOnce(d, t, List.of(), "bread"::equals).isEmpty());
        assertEquals(List.of("bread", "emerald"), t);
        assertFalse(d.treasuryDonationsMoved);
        assertEquals(List.of("bread"), TreasuryMigration.runOnce(d, t, List.of("u1"), "bread"::equals));
    }

    @Test
    void movedStacksAreSharedWithTheNormalSplit() {
        Map<String, Integer> rotation = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("minecraft:bread", 64L + 17L);
        counts.put("minecraft:golden_apple", 2L);
        var shares = DonationRotation.share(rotation, counts, List.of("u1", "u2", "u3"));
        long bread = 0, apples = 0;
        for (var m : shares.values()) {
            bread += m.getOrDefault("minecraft:bread", 0L);
            apples += m.getOrDefault("minecraft:golden_apple", 0L);
        }
        assertEquals(81, bread);
        assertEquals(2, apples);
        assertEquals(27, shares.get("u1").get("minecraft:bread"));
        assertEquals(1, shares.get("u1").get("minecraft:golden_apple"));
        assertEquals(1, shares.get("u2").get("minecraft:golden_apple"));
        assertNull(shares.get("u3").get("minecraft:golden_apple"));
    }

    /** No donation path (donate screen, claim boxes, leftover pots) may mention the treasury. */
    @Test
    void noDonationPathReachesTheTreasury() throws Exception {
        Path root = Path.of("src/main/java/com/honorsmp");
        for (String f : List.of("DonationBox.java", "logic/DonationRotation.java",
                "logic/DonateMath.java", "logic/BountyEscrow.java")) {
            String src = Files.readString(root.resolve(f));
            assertFalse(src.toLowerCase().replaceAll("(?s)/\\*.*?\\*/|//[^\n]*", "").contains("treasury"), f);
        }
        // Donations.java may only touch the treasury in the one-time migration
        String don = Files.readString(root.resolve("Donations.java")).replaceAll("(?s)/\\*.*?\\*/|//[^\n]*", "");
        int count = don.split("h\\.treasury", -1).length - 1;
        assertEquals(1, count);
        assertTrue(don.contains("h.treasury.closedItems()"));
        // the only code that adds to the treasury is fines and the civ upgrade upkeep
        try (var files = Files.walk(root)) {
            for (Path p : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                String name = p.getFileName().toString();
                String src = Files.readString(p).replaceAll("(?s)/\\*.*?\\*/|//[^\n]*", "");
                if (src.contains("treasury.addItem") || src.contains("treasury.addEmeralds") || src.contains("h.treasury::addEmeralds")) {
                    assertTrue(name.equals("Fines.java") || name.equals("UpkeepPay.java") || name.equals("Cmds.java"), name);
                }
            }
        }
    }
}
