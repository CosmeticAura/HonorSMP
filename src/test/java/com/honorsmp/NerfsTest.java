package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.CombatTimer;
import com.honorsmp.logic.EnchantCap;
import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.NetheriteRules;
import com.honorsmp.logic.NetheriteRules.ItemData;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NerfsTest {
    private static final Map<String, Integer> CAPS = HonorConfig.defaultEnchantCaps();

    @Test
    void onlyCappedEnchantChanges() {
        Map<String, Integer> in = new LinkedHashMap<>();
        in.put("minecraft:sharpness", 5);
        in.put("minecraft:unbreaking", 3);
        in.put("minecraft:mending", 1);
        in.put("minecraft:fire_aspect", 2);
        EnchantCap.Result r = EnchantCap.apply(in, CAPS);
        assertTrue(r.changed());
        assertEquals(4, r.enchants().get("minecraft:sharpness"));
        assertEquals(3, r.enchants().get("minecraft:unbreaking"));
        assertEquals(1, r.enchants().get("minecraft:mending"));
        assertEquals(2, r.enchants().get("minecraft:fire_aspect"));
        assertEquals(in.keySet(), r.enchants().keySet());
        assertEquals(5, in.get("minecraft:sharpness")); // input untouched
    }

    @Test
    void atOrBelowCapUnchanged() {
        Map<String, Integer> in = Map.of("minecraft:protection", 3, "minecraft:breach", 2, "minecraft:density", 4);
        EnchantCap.Result r = EnchantCap.apply(in, CAPS);
        assertFalse(r.changed());
        assertEquals(in, r.enchants());
    }

    @Test
    void defaultsAndMultipleCaps() {
        assertEquals(Map.of("minecraft:sharpness", 4, "minecraft:protection", 3, "minecraft:density", 4, "minecraft:breach", 3), CAPS);
        EnchantCap.Result r = EnchantCap.apply(Map.of("minecraft:protection", 4, "minecraft:breach", 4, "minecraft:density", 8, "minecraft:efficiency", 5), CAPS);
        assertEquals(3, r.enchants().get("minecraft:protection"));
        assertEquals(3, r.enchants().get("minecraft:breach"));
        assertEquals(4, r.enchants().get("minecraft:density"));
        assertEquals(5, r.enchants().get("minecraft:efficiency"));
    }

    @Test
    void netherite_mapping() {
        assertEquals("minecraft:diamond_sword", NetheriteRules.diamondFor("minecraft:netherite_sword"));
        assertEquals("minecraft:diamond_axe", NetheriteRules.diamondFor("minecraft:netherite_axe"));
        assertEquals("minecraft:diamond_helmet", NetheriteRules.diamondFor("minecraft:netherite_helmet"));
        assertEquals("minecraft:diamond_chestplate", NetheriteRules.diamondFor("minecraft:netherite_chestplate"));
        assertEquals("minecraft:diamond_leggings", NetheriteRules.diamondFor("minecraft:netherite_leggings"));
        assertEquals("minecraft:diamond_boots", NetheriteRules.diamondFor("minecraft:netherite_boots"));
        for (String ok : new String[] {"minecraft:netherite_pickaxe", "minecraft:netherite_shovel", "minecraft:netherite_hoe",
                "minecraft:netherite_spear", "minecraft:netherite_ingot", "minecraft:diamond_sword"}) {
            assertNull(NetheriteRules.diamondFor(ok), ok);
            assertFalse(NetheriteRules.isRestricted(ok));
        }
    }

    @Test
    void conversionKeepsEnchantsDurabilityName() {
        Map<String, Integer> ench = new LinkedHashMap<>();
        ench.put("minecraft:sharpness", 4);
        ench.put("minecraft:mending", 1);
        ItemData in = new ItemData("minecraft:netherite_sword", ench, 300, "Excalibur");
        ItemData out = NetheriteRules.convert(in, NetheriteRules.diamondMaxDamage("minecraft:diamond_sword"));
        assertEquals("minecraft:diamond_sword", out.id());
        assertEquals(ench, out.enchants());
        assertEquals(300, out.damage());
        assertEquals("Excalibur", out.name());
    }

    @Test
    void conversionClampsDamageToDiamondMax() {
        ItemData in = new ItemData("minecraft:netherite_helmet", Map.of(), 400, "");
        ItemData out = NetheriteRules.convert(in, NetheriteRules.diamondMaxDamage("minecraft:diamond_helmet"));
        assertEquals("minecraft:diamond_helmet", out.id());
        assertEquals(362, out.damage());
        ItemData pick = new ItemData("minecraft:netherite_pickaxe", Map.of(), 10, "");
        assertSame(pick, NetheriteRules.convert(pick, 100));
    }

    @Test
    void combatTimer() {
        CombatTimer t = new CombatTimer();
        assertEquals(0, t.remainingSeconds("a", 1000, 30));
        t.record("a", 1000);
        assertEquals(30, t.remainingSeconds("a", 1000, 30));
        assertEquals(30, t.remainingSeconds("a", 1500, 30));
        assertEquals(1, t.remainingSeconds("a", 30_500, 30));
        assertEquals(0, t.remainingSeconds("a", 31_000, 30));
        assertEquals(0, t.remainingSeconds("b", 1000, 30));
        assertEquals(0, t.remainingSeconds("a", 1000, 0)); // disabled
        t.clear("a");
        assertEquals(0, t.remainingSeconds("a", 1000, 30));
    }

    @Test
    void blockedNetheriteSmithingReturnsTemplateBaseAndIngot() {
        var in = java.util.List.of(
            new NetheriteRules.Entry(NetheriteRules.UPGRADE_TEMPLATE, 1),
            new NetheriteRules.Entry("minecraft:diamond_sword", 1),
            new NetheriteRules.Entry(NetheriteRules.NETHERITE_INGOT, 1));
        var back = NetheriteRules.smithingReturns(in, "minecraft:netherite_sword");
        assertEquals(in, back);
        assertTrue(NetheriteRules.smithingReturns(in, "minecraft:netherite_pickaxe").isEmpty());
        assertTrue(NetheriteRules.smithingReturns(
            java.util.List.of(new NetheriteRules.Entry("minecraft:air", 0)), "minecraft:netherite_axe").isEmpty());
    }
}
