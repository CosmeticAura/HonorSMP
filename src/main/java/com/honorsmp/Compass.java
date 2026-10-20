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

    public static final String RANK_MSG = "You must be Noble or higher to use this";
    public static final String NONE_MSG = "No wanted players nearby";

    public static void updateAll(Honor h) {
        for (ServerPlayerEntity p : h.server.getPlayerManager().getPlayerList()) {
            for (int i = 0; i < p.getInventory().size(); i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (isCompass(s)) update(h, p, s, isHeld(p, s));
            }
        }
    }

    /** Right click with a compass: re-check rank and target now. */
    public static void onUse(Honor h, ServerPlayerEntity p, ItemStack s) {
        update(h, p, s, true);
    }

    private static boolean isHeld(ServerPlayerEntity p, ItemStack s) {
        return p.getMainHandStack() == s || p.getOffHandStack() == s;
    }

    private static void actionBar(ServerPlayerEntity p, String msg) {
        p.sendMessage(Text.literal(msg).formatted(Formatting.RED), true);
    }

    private static void update(Honor h, ServerPlayerEntity p, ItemStack s, boolean held) {
        Tier t = h.tier(p);
        if (!t.isNobleOrHigher()) {
            clear(s);
            if (held) actionBar(p, RANK_MSG);
            return;
        }
        ServerPlayerEntity best = null;
        double bd = Double.MAX_VALUE;
        if (p.getEntityWorld().getRegistryKey() == World.OVERWORLD) {
            for (ServerPlayerEntity o : h.server.getPlayerManager().getPlayerList()) {
                if (o == p || o.getEntityWorld().getRegistryKey() != World.OVERWORLD) continue;
                if (!h.tier(o).isDishonored()) continue;
                if (!h.bounties.hasBounty(o.getUuid().toString())) continue;
                double d = o.squaredDistanceTo(p);
                if (d < bd) {
                    bd = d;
                    best = o;
                }
            }
        }
        if (best == null) {
            clear(s);
            if (held) actionBar(p, NONE_MSG);
        } else {
            s.set(DataComponentTypes.LODESTONE_TRACKER,
                    new LodestoneTrackerComponent(Optional.of(GlobalPos.create(World.OVERWORLD, best.getBlockPos())), false));
        }
    }

    /** No target: an empty tracker makes the needle spin instead of pointing at an old spot. */
    private static void clear(ItemStack s) {
        s.set(DataComponentTypes.LODESTONE_TRACKER, new LodestoneTrackerComponent(Optional.empty(), false));
    }
}
