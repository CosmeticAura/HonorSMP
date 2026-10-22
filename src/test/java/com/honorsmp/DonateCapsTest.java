package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.honorsmp.logic.DonateMath;
import com.honorsmp.logic.DonationRules;
import com.honorsmp.logic.HonorConfig;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Per-type daily caps together with the overall cap, leftover carry, preview, midnight reset and config migration. */
class DonateCapsTest {
    HonorConfig cfg = new HonorConfig();
    HonorData.PlayerRecord r = new HonorData.PlayerRecord();
    String d1 = DonateMath.todayKey(LocalDate.of(2026, 10, 20));
    String d2 = DonateMath.todayKey(LocalDate.of(2026, 10, 21));

    static Map<String, Long> pts(Object... kv) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).longValue());
        return m;
    }

    DonateMath.Quote donate(Map<String, Long> p) {
        DonateMath.rollDay(r, d1);
        DonateMath.Quote q = DonateMath.quote(p, r, d1, cfg, false);
        DonateMath.apply(q, r, d1);
        return q;
    }

    @Test void typeCapAlone() {
        // 40 golden apples = 160 honor worth, cap 20
        var q = donate(pts("golden_apple", 40 * 40));
        assertEquals(20, q.honor());
        assertEquals(20, q.byType().get("golden_apple"));
        assertTrue(q.capReached());
        assertEquals(0, DonateMath.earnableToday(r, d1, cfg, "golden_apple"));
        // more is accepted, earns nothing
        assertEquals(0, donate(pts("golden_apple", 400)).honor());
        assertEquals(20, r.donatedHonorToday);
    }

    @Test void overallCapAlone() {
        cfg.donateItemDailyCaps.clear(); // no per-type caps
        var q = donate(pts("food", 5000));
        assertEquals(100, q.honor());
        assertEquals(0, donate(pts("food", 500)).honor());
        assertEquals(0, DonateMath.remainingToday(r, d1, cfg));
    }

    @Test void typeCapNotReachedButOverallReached() {
        // 90 honor already earned from totems and apples (all under their own caps), 10 left overall
        r.donateDayKey = d1;
        r.donatedHonorToday = 90;
        r.donatedTypeToday.put("totem_of_undying", 30);
        r.donatedTypeToday.put("enchanted_golden_apple", 30);
        r.donatedTypeToday.put("golden_apple", 20);
        r.donatedTypeToday.put("food", 10);
        var q = donate(pts("food", 1000)); // food has 50 left under its cap, only 10 overall
        assertEquals(10, q.honor());
        assertEquals(10, q.byType().get("food"));
        assertEquals(20, r.donatedTypeToday.get("food"));
        assertEquals(100, r.donatedHonorToday);
        assertEquals(0, q.newCarry().get("food"));
    }

    @Test void typeCapReachedWithOverallLeft() {
        var q = donate(pts("food", 5000));
        assertEquals(60, q.honor());
        assertEquals(40, DonateMath.remainingToday(r, d1, cfg));
        assertEquals(0, DonateMath.earnableToday(r, d1, cfg, "food"));
        assertEquals(32, DonateMath.earnableToday(r, d1, cfg, "totem_of_undying"));
        // totems still earn: 3 totems = 48 honor worth, cap 32
        assertEquals(32, donate(pts("totem_of_undying", 480)).honor());
        // overall 100 - 60 - 32 = 8 left; pearls cap 16 but only 8 overall
        assertEquals(8, donate(pts("ender_pearl", 100 * 5)).honor());
        assertEquals(100, r.donatedHonorToday);
    }

    @Test void breadCase3338() {
        long p = DonationRules.points("minecraft:bread", 5, true, 3338, cfg); // 16690 points
        var q = donate(pts("food", p));
        assertEquals(60, q.honor());
        assertEquals(40, DonateMath.remainingToday(r, d1, cfg));
        assertEquals(40, DonateMath.earnableToday(r, d1, cfg, "enchanted_golden_apple") + 10); // 30 cap + 10 = 40 left overall
    }

    @Test void severalTypesInOneDonation() {
        var q = donate(pts("food", 200, "golden_apple", 40, "ender_pearl", 10));
        assertEquals(20 + 4 + 1, q.honor());
        assertEquals(20, q.byType().get("food"));
        assertEquals(4, q.byType().get("golden_apple"));
        assertEquals(1, q.byType().get("ender_pearl"));
        assertEquals(25, q.byType().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(25, r.donatedHonorToday);
    }

    @Test void severalTypesShareTheOverallCap() {
        // configured cap order: food 60, golden apple 20 (16), enchanted 30 (30), totem 32 (32) -> 100 reached by the pearls' turn
        var q = donate(pts("food", 600, "golden_apple", 800, "enchanted_golden_apple", 600, "totem_of_undying", 480, "ender_pearl", 500));
        assertEquals(100, q.honor());
        assertEquals(60, q.byType().get("food"));
        assertEquals(20, q.byType().get("golden_apple"));
        assertEquals(20, q.byType().get("enchanted_golden_apple"));
        assertEquals(0, q.byType().get("totem_of_undying"));
        assertEquals(0, q.byType().get("ender_pearl"));
    }

    @Test void foodIsOneCombinedType() {
        assertEquals("food", DonationRules.category("minecraft:bread", 5, cfg));
        assertEquals("food", DonationRules.category("minecraft:cooked_beef", 8, cfg));
        assertEquals("food", DonationRules.category("minecraft:cake", -1, cfg));
        assertEquals("golden_apple", DonationRules.category("minecraft:golden_apple", 4, cfg));
        assertEquals("enchanted_golden_apple", DonationRules.category("minecraft:enchanted_golden_apple", 4, cfg));
        assertEquals("totem_of_undying", DonationRules.category("minecraft:totem_of_undying", -1, cfg));
        assertEquals("rare_materials", DonationRules.category("minecraft:diamond", -1, cfg));
        assertEquals("bedrock", DonationRules.category("minecraft:bedrock", -1, cfg)); // other extra items: own id
        // bread and steak share the 60
        var q = donate(pts("food", 5L * 400 + 8L * 400));
        assertEquals(60, q.honor());
        assertEquals(1, q.byType().size());
    }

    @Test void uncappedTypeHasNoTypeCapButOverallStillApplies() {
        assertEquals(-1, DonateMath.typeCap(cfg, "bedrock"));
        assertEquals(100, donate(pts("bedrock", 5000)).honor());
    }

    @Test void negativeCapMeansNoTypeCap() {
        cfg.donateItemDailyCaps.put("food", -1);
        assertEquals(100, donate(pts("food", 5000)).honor());
    }

    @Test void carryOnlyForUncappedTypes() {
        // food 65 points -> 6 honor, 5 left over; golden apple capped by cap 1
        cfg.donateItemDailyCaps.put("golden_apple", 1);
        var q = donate(pts("food", 65, "golden_apple", 75)); // 7 honor raw for apples, cap 1
        assertEquals(6 + 1, q.honor());
        assertEquals(5, q.newCarry().get("food"));
        assertEquals(0, q.newCarry().get("golden_apple")); // capped: leftover dropped
        assertEquals(5, r.donateCarryTypes.get("food"));
        assertFalse(r.donateCarryTypes.containsKey("golden_apple"));
        // next time the leftover food points count, apples stay capped
        var q2 = donate(pts("food", 5, "golden_apple", 5));
        assertEquals(1, q2.honor());
        assertEquals(0, q2.byType().get("golden_apple"));
        assertEquals(0, r.donateCarryTypes.getOrDefault("golden_apple", 0));
    }

    @Test void typeThatReachesItsCapExactlyDropsLeftover() {
        cfg.donateItemDailyCaps.put("food", 5);
        var q = donate(pts("food", 59)); // 5 honor + 9 left, but now at cap
        assertEquals(5, q.honor());
        assertEquals(0, q.newCarry().get("food"));
    }

    @Test void overallCapUsedUpDropsAllCarry() {
        cfg.donateDailyCap = 3;
        var q = donate(pts("food", 35, "ender_pearl", 9)); // food 3 honor, pearls 0 left; carry dropped everywhere
        assertEquals(3, q.honor());
        assertEquals(0, q.newCarry().get("food"));
        assertEquals(0, q.newCarry().get("ender_pearl"));
    }

    @Test void previewMatchesAward() {
        var in = pts("food", 777, "golden_apple", 90, "totem_of_undying", 5000, "ender_pearl", 12);
        var preview = DonateMath.quote(in, r, d1, cfg, false);
        var actual = donate(in);
        assertEquals(preview, actual);
        assertEquals(preview.honor(), r.donatedHonorToday);
        assertEquals(preview.byType().get("totem_of_undying"), r.donatedTypeToday.get("totem_of_undying"));
        // and again with partially used caps
        var in2 = pts("food", 300, "golden_apple", 400);
        assertEquals(DonateMath.quote(in2, r, d1, cfg, false), donate(in2));
    }

    @Test void midnightResetsTypeTotals() {
        donate(pts("food", 5000));
        assertEquals(60, r.donatedTypeToday.get("food"));
        assertEquals(0, DonateMath.earnedToday(r, d2, "food")); // other day: nothing counted
        assertEquals(60, DonateMath.earnableToday(r, d2, cfg, "food"));
        DonateMath.rollDay(r, d2);
        assertTrue(r.donatedTypeToday.isEmpty());
        assertEquals(0, r.donatedHonorToday);
        var q = DonateMath.quote(pts("food", 5000), r, d2, cfg, false);
        assertEquals(60, q.honor());
    }

    // ------------------------------------------------------------ data and config loading

    @Test void oldPlayerDataLoadsWithEmptyTypeTotals() {
        HonorData d = HonorData.parse("{\"players\":{\"a\":{\"uuid\":\"a\",\"name\":\"A\",\"donateDayKey\":\"2026-10-20\",\"donatedHonorToday\":12,\"donateCarry\":4}}}");
        var p = d.players.get("a");
        assertNotNull(p.donatedTypeToday);
        assertTrue(p.donatedTypeToday.isEmpty());
        assertNotNull(p.donateCarryTypes);
        assertEquals(12, p.donatedHonorToday);
        assertEquals(4, DonateMath.carryOf(p, "food"));
        assertEquals(0, DonateMath.earnedToday(p, "2026-10-20", "food"));
    }

    @Test void configWithoutMapGetsDefaults() {
        HonorConfig c = Honor.GSON.fromJson("{\"minScore\":-500}", HonorConfig.class);
        assertEquals(60, c.donateItemDailyCaps.get("food"));
        assertEquals(7, c.donateItemDailyCaps.size());
        c.migrate();
        assertEquals(7, c.donateItemDailyCaps.size());
        HonorConfig n = Honor.GSON.fromJson("{\"donateItemDailyCaps\":null}", HonorConfig.class);
        n.migrate();
        assertEquals(7, n.donateItemDailyCaps.size());
    }

    @Test void partialMapKeepsUserEntriesAndFillsMissingDefaults() {
        HonorConfig c = Honor.GSON.fromJson("{\"donateItemDailyCaps\":{\"food\":40,\"ender_pearl\":-1,\"diamond\":3}}", HonorConfig.class);
        c.migrate();
        assertEquals(40, c.donateItemDailyCaps.get("food"));
        assertEquals(-1, c.donateItemDailyCaps.get("ender_pearl"));
        assertEquals(3, c.donateItemDailyCaps.get("diamond"));
        assertEquals(20, c.donateItemDailyCaps.get("golden_apple"));
        assertEquals(30, c.donateItemDailyCaps.get("enchanted_golden_apple"));
        assertEquals(32, c.donateItemDailyCaps.get("totem_of_undying"));
    }

    private HonorConfig load(String json) {
        HonorConfig c = Honor.GSON.fromJson(json, HonorConfig.class);
        c.migrate();
        return c;
    }

    @Test void migrationOldValuesBecomeNew() {
        HonorConfig c = load("{\"honorPerHungerPoints\":20,\"donateDailyCap\":25,\"minScore\":-700}");
        assertEquals(10, c.honorPerHungerPoints);
        assertEquals(100, c.donateDailyCap);
        assertEquals(-700, c.minScore);
    }

    @Test void migrationOldFileWithoutKeysGetsNewDefaults() {
        HonorConfig c = load("{\"minScore\":-700}");
        assertEquals(10, c.honorPerHungerPoints);
        assertEquals(100, c.donateDailyCap);
    }

    @Test void migrationLeavesCustomValuesAlone() {
        HonorConfig a = load("{\"honorPerHungerPoints\":15,\"donateDailyCap\":40}");
        assertEquals(15, a.honorPerHungerPoints);
        assertEquals(40, a.donateDailyCap);
        // per key: 20 is the old default and changes, the custom 50 does not
        HonorConfig b = load("{\"honorPerHungerPoints\":20,\"donateDailyCap\":50}");
        assertEquals(10, b.honorPerHungerPoints);
        assertEquals(50, b.donateDailyCap);
        HonorConfig c = load("{\"honorPerHungerPoints\":12,\"donateDailyCap\":25}");
        assertEquals(12, c.honorPerHungerPoints);
        assertEquals(100, c.donateDailyCap);
    }

    @Test void migrationIsIdempotentThroughSaveAndReload() {
        HonorConfig c = load("{\"honorPerHungerPoints\":20,\"donateDailyCap\":25}");
        assertFalse(c.migrate());
        String saved = Honor.GSON.toJson(c);
        HonorConfig again = load(saved);
        assertEquals(10, again.honorPerHungerPoints);
        assertEquals(100, again.donateDailyCap);
        assertEquals(Honor.GSON.toJson(c), Honor.GSON.toJson(again));
        // an admin who later sets 20 on purpose is not reset on the next load
        again.honorPerHungerPoints = 20;
        HonorConfig reloaded = load(Honor.GSON.toJson(again));
        assertEquals(20, reloaded.honorPerHungerPoints);
    }

    @Test void newDefaultsUnchanged() {
        HonorConfig c = new HonorConfig();
        c.migrate();
        assertEquals(10, c.honorPerHungerPoints);
        assertEquals(100, c.donateDailyCap);
        HonorConfig loaded = load("{\"honorPerHungerPoints\":10,\"donateDailyCap\":100}");
        assertEquals(10, loaded.honorPerHungerPoints);
        assertEquals(100, loaded.donateDailyCap);
    }
}
