package com.honorsmp.logic;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure enchantment cap logic: lowers only the capped enchants, leaves everything else untouched. */
public final class EnchantCap {
    private EnchantCap() {}

    public record Result(Map<String, Integer> enchants, boolean changed) {}

    public static Result apply(Map<String, Integer> enchants, Map<String, Integer> caps) {
        Map<String, Integer> out = new LinkedHashMap<>();
        boolean changed = false;
        if (enchants == null) return new Result(out, false);
        for (Map.Entry<String, Integer> e : enchants.entrySet()) {
            int level = e.getValue();
            Integer cap = caps == null ? null : caps.get(e.getKey());
            if (cap != null && cap >= 0 && level > cap) {
                level = cap;
                changed = true;
            }
            out.put(e.getKey(), level);
        }
        return new Result(out, changed);
    }
}
