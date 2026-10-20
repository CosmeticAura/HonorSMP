package com.honorsmp;

import com.honorsmp.logic.Tier;
import java.util.Optional;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

/** The Bounty Compass: a lodestone-tracking compass that points at the nearest dishonored player with a bounty. */
public final class Compass {
    private Compass() {}

    public static final String TAG = "honorsmp_compass";

    public static ItemStack make() {
        ItemStack s = new ItemStack(Items.COMPASS);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Bounty Compass").formatted(Formatting.GOLD).styled(st -> st.withItalic(false)));
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(new net.minecraft.nbt.NbtCompound()).apply(n -> n.putBoolean(TAG, true)));
        s.set(DataComponentTypes.LODESTONE_TRACKER, new LodestoneTrackerComponent(Optional.empty(), false));
        return s;
    }

    public static boolean isCompass(ItemStack s) {
        if (!s.isOf(Items.COMPASS)) return false;
        NbtComponent c = s.get(DataComponentTypes.CUSTOM_DATA);
        return c != null && c.copyNbt().getBoolean(TAG, false);
    }

    public static void updateAll(Honor h) {
        for (ServerPlayerEntity p : h.server.getPlayerManager().getPlayerList()) {
            for (int i = 0; i < p.getInventory().size(); i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (isCompass(s)) update(h, p, s);
            }
        }
    }

    private static void update(Honor h, ServerPlayerEntity p, ItemStack s) {
        Tier t = h.tier(p);
        ServerPlayerEntity best = null;
        double bd = Double.MAX_VALUE;
        if (p.getEntityWorld().getRegistryKey() == World.OVERWORLD && t.isNobleOrHigher()) {
            for (ServerPlayerEntity o : h.server.getPlayerManager().getPlayerList()) {
                if (o == p || o.getEntityWorld().getRegistryKey() != World.OVERWORLD) continue;
                if (!h.tier(o).isDishonored()) continue;
                if (h.data.bounties.getOrDefault(o.getUuid().toString(), 0) <= 0) continue;
                double d = o.squaredDistanceTo(p);
                if (d < bd) {
                    bd = d;
                    best = o;
                }
            }
        }
        if (best == null) {
            s.set(DataComponentTypes.LODESTONE_TRACKER, new LodestoneTrackerComponent(Optional.empty(), false));
        } else {
            s.set(DataComponentTypes.LODESTONE_TRACKER,
                    new LodestoneTrackerComponent(Optional.of(GlobalPos.create(World.OVERWORLD, best.getBlockPos())), false));
        }
    }
}
