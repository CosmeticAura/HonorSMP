package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.DonateMath;
import com.honorsmp.logic.DonationRules;
import com.honorsmp.logic.HonorConfig;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Ores, ingots and blocks (1.6.0): values, ore = drop, caps, plain-only and config migration. */
class DonateMaterialsTest {
    HonorConfig cfg = new HonorConfig();
    HonorData.PlayerRecord r = new HonorData.PlayerRecord();
    String d1 = DonateMath.todayKey(LocalDate.of(2026, 10, 21));
    static final String C = "common_materials", R = "rare_materials";

    static Object[][] table() {
        return new Object[][] {
            {"coal", 1, C}, {"coal_block", 9, C},
            {"raw_copper", 1, C}, {"copper_ingot", 1, C}, {"copper_block", 9, C}, {"raw_copper_block", 9, C},
            {"raw_iron", 5, C}, {"iron_ingot", 5, C}, {"iron_block", 45, C}, {"raw_iron_block", 45, C},
            {"raw_gold", 5, C}, {"gold_ingot", 5, C}, {"gold_block", 45, C}, {"raw_gold_block", 45, C},
            {"redstone", 1, C}, {"redstone_block", 9, C},
            {"lapis_lazuli", 2, C}, {"lapis_block", 18, C},
            {"quartz", 1, C}, {"quartz_block", 4, C}, {"amethyst_shard", 1, C},
            {"emerald", 20, R}, {"emerald_block", 180, R},
            {"diamond", 50, R}, {"diamond_block", 450, R},
            {"netherite_scrap", 150, R}, {"netherite_ingot", 600, R}, {"netherite_block", 5400, R},
        };
    }

    static Object[][] ores() {
        return new Object[][] {
            {"coal_ore", "coal"}, {"deepslate_coal_ore", "coal"},
            {"iron_ore", "raw_iron"}, {"deepslate_iron_ore", "raw_iron"},
            {"copper_ore", "raw_copper"}, {"deepslate_copper_ore", "raw_copper"},
            {"gold_ore", "raw_gold"}, {"deepslate_gold_ore", "raw_gold"}, {"nether_gold_ore", "raw_gold"},
            {"redstone_ore", "redstone"}, {"deepslate_redstone_ore", "redstone"},
            {"lapis_ore", "lapis_lazuli"}, {"deepslate_lapis_ore", "lapis_lazuli"},
            {"diamond_ore", "diamond"}, {"deepslate_diamond_ore", "diamond"},
            {"emerald_ore", "emerald"}, {"deepslate_emerald_ore", "emerald"},
            {"nether_quartz_ore", "quartz"}, {"ancient_debris", "netherite_scrap"},
        };
    }

    int pts(HonorConfig c, String id, boolean plain) {
        return DonationRules.pointsPerItem("minecraft:" + id, -1, plain, c);
    }

    static Map<String, Long> m(Object... kv) {
        Map<String, Long> x = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) x.put((String) kv[i], ((Number) kv[i + 1]).longValue());
        return x;
    }

    DonateMath.Quote donate(Map<String, Long> p) {
        DonateMath.rollDay(r, d1);
        var q = DonateMath.quote(p, r, d1, cfg, false);
        DonateMath.apply(q, r, d1);
        return q;
    }

    @Test void everyListedValueAndCapType() {
        for (Object[] row : table()) {
            String id = (String) row[0];
            assertEquals((int) (Integer) row[1], pts(cfg, id, true), id);
            assertEquals(row[2], DonationRules.category("minecraft:" + id, -1, cfg), id);
        }
    }

    @Test void oresEqualTheirDrop() {
        for (Object[] row : ores()) {
            String ore = (String) row[0], drop = (String) row[1];
            assertEquals(pts(cfg, drop, true), pts(cfg, ore, true), ore);
            assertEquals(DonationRules.category("minecraft:" + drop, -1, cfg), DonationRules.category("minecraft:" + ore, -1, cfg), ore);
        }
        assertEquals(19, ores().length);
    }

    @Test void plainItemsOnly() {
        assertEquals(-1, pts(cfg, "diamond", false));
        assertEquals(-1, pts(cfg, "diamond_ore", false));
        assertEquals(-1, pts(cfg, "bedrock", true));
    }

    @Test void defaultCaps() {
        assertEquals(30, DonateMath.typeCap(cfg, C));
        assertEquals(40, DonateMath.typeCap(cfg, R));
    }

    @Test void commonCapThirtyHonor() {
        // 20 iron blocks = 900 points = 90 honor worth
        var q = donate(m(C, 900));
        assertEquals(30, q.honor());
        assertTrue(q.capReached());
        assertEquals(0, donate(m(C, 900)).honor());
        assertEquals(30, r.donatedHonorToday);
        assertEquals(30, r.donatedTypeToday.get(C));
    }

    @Test void rareCapFortyHonor() {
        var q = donate(m(R, 5400)); // netherite block = 540 honor worth
        assertEquals(40, q.honor());
        assertEquals(0, donate(m(R, 5400)).honor());
        assertEquals(0, DonateMath.earnableToday(r, d1, cfg, R));
        assertEquals(30, DonateMath.earnableToday(r, d1, cfg, C));
    }

    @Test void capsAreIndependentAndOverallStillApplies() {
        var q = donate(m(C, 900, R, 5400, "bedrock", 5000));
        assertEquals(30, q.byType().get(C));
        assertEquals(40, q.byType().get(R));
        assertEquals(30, q.byType().get("bedrock")); // 100 overall - 70
        assertEquals(100, q.honor());
        assertEquals(0, donate(m(C, 900)).honor());
    }

    @Test void overallCapLimitsMaterials() {
        r.donateDayKey = d1;
        r.donatedHonorToday = 95;
        assertEquals(5, donate(m(R, 5400)).honor());
        assertEquals(100, r.donatedHonorToday);
    }

    @Test void midnightResetsMaterialCaps() {
        donate(m(C, 900));
        String d2 = DonateMath.todayKey(LocalDate.of(2026, 10, 22));
        assertEquals(30, DonateMath.earnableToday(r, d2, cfg, C));
    }

    @Test void carryForMaterials() {
        var q = donate(m(C, 7)); // 0 honor, 7 points kept
        assertEquals(0, q.honor());
        assertEquals(7, r.donateCarryTypes.get(C));
        assertEquals(1, donate(m(C, 3)).honor());
    }

    @Test void oldConfigGetsNewDefaultsWithoutOverwritingEdits() {
        HonorConfig c = Honor.GSON.fromJson("{\"donateExtraItems\":{\"minecraft:golden_apple\":40,\"minecraft:ender_pearl\":7,\"minecraft:diamond\":60},"
                + "\"donateItemDailyCaps\":{\"food\":60,\"golden_apple\":20,\"enchanted_golden_apple\":30,\"totem_of_undying\":32,\"ender_pearl\":16},\"donateRewardsVersion\":1,\"perksVersion\":1}", HonorConfig.class);
        assertTrue(c.migrate());
        assertEquals(60, pts(c, "diamond", true)); // admin edit kept
        assertEquals(7, c.donateExtraItems.get("minecraft:ender_pearl"));
        assertEquals(5400, pts(c, "netherite_block", true));
        assertEquals(150, pts(c, "ancient_debris", true));
        assertEquals(30, c.donateItemDailyCaps.get(C));
        assertEquals(40, c.donateItemDailyCaps.get(R));
        assertEquals(1, c.donateMaterialsVersion);
        for (Object[] row : table()) if (!row[0].equals("diamond")) assertEquals((int) (Integer) row[1], pts(c, (String) row[0], true), (String) row[0]);
        // idempotent, and an admin removal or edit sticks on the next load
        assertFalse(c.migrate());
        c.donateExtraItems.remove("minecraft:coal");
        c.donateExtraItems.put("minecraft:emerald", 25);
        HonorConfig again = Honor.GSON.fromJson(Honor.GSON.toJson(c), HonorConfig.class);
        assertFalse(again.migrate());
        assertEquals(-1, pts(again, "coal", true));
        assertEquals(25, pts(again, "emerald", true));
    }

    @Test void completelyOldFileGetsEverything() {
        HonorConfig c = Honor.GSON.fromJson("{\"minScore\":-500}", HonorConfig.class);
        c.migrate();
        for (Object[] row : table()) assertEquals((int) (Integer) row[1], pts(c, (String) row[0], true), (String) row[0]);
        assertEquals(30, c.donateItemDailyCaps.get(C));
    }
}
