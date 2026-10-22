package com.honorsmp;

import com.honorsmp.logic.KeepOnDeath;
import com.honorsmp.logic.RankPerks;
import com.honorsmp.logic.Tier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.rule.GameRules;

/** Rank keep-on-death: items/XP are taken out of the player just before the vanilla drops and given back on respawn. */
public final class KeepStash {
    private KeepStash() {}

    private record Stash(Map<Integer, ItemStack> items, boolean xp, int level, float progress, int total) {}

    private static final Map<UUID, Stash> STASH = new ConcurrentHashMap<>();

    public static void take(ServerPlayerEntity p) {
        Honor h = Honor.I;
        if (h == null || p.isSpectator() || STASH.containsKey(p.getUuid())) return;
        if (p.getEntityWorld().getGameRules().getValue(GameRules.KEEP_INVENTORY)) return; // vanilla keeps everything
        Tier t = h.perkTier(p);
        boolean hotbar = RankPerks.keepsHotbar(t, h.config);
        boolean xp = RankPerks.keepsXp(t, h.config);
        if (!hotbar && !xp) return;
        var inv = p.getInventory();
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < inv.size(); i++) all.add(inv.getStack(i));
        var res = KeepOnDeath.select(all, ItemStack::isEmpty, ItemStack.EMPTY, hotbar);
        Map<Integer, ItemStack> kept = new java.util.LinkedHashMap<>();
        for (var e : res.kept().entrySet()) {
            kept.put(e.getKey(), e.getValue().copy());
            inv.setStack(e.getKey(), ItemStack.EMPTY); // gone from the inventory, so dropAll cannot drop it
        }
        Stash s = new Stash(kept, xp, p.experienceLevel, p.experienceProgress, p.totalExperience);
        if (xp) { // nothing left to drop as orbs
            p.experienceLevel = 0;
            p.experienceProgress = 0f;
            p.totalExperience = 0;
        }
        if (!kept.isEmpty() || xp) STASH.put(p.getUuid(), s);
    }

    public static void restore(ServerPlayerEntity p) {
        Stash s = STASH.remove(p.getUuid());
        if (s == null) return;
        var inv = p.getInventory();
        for (var e : s.items().entrySet()) {
            if (inv.getStack(e.getKey()).isEmpty()) inv.setStack(e.getKey(), e.getValue());
            else inv.offerOrDrop(e.getValue());
        }
        if (s.xp()) {
            p.experienceLevel = s.level();
            p.experienceProgress = s.progress();
            p.totalExperience = s.total();
        }
        p.currentScreenHandler.sendContentUpdates();
    }
}
