package com.honorsmp.mixin;

import com.honorsmp.Honor;
import com.honorsmp.logic.TradeCooldownRules;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.6.4: trades never lock (the old NoTradeCooldown mod) unless the sell item is blocklisted; see TradeCooldownRules. */
@Mixin(TradeOffer.class)
public abstract class TradeOfferMixin {
    @Shadow
    @org.spongepowered.asm.mixin.Final
    private ItemStack sellItem;

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void honorsmp$noCooldown(CallbackInfo ci) {
        Honor h = Honor.I;
        if (h == null || sellItem == null || sellItem.isEmpty()) return; // not running a server: vanilla
        try {
            if (!TradeCooldownRules.locks(Registries.ITEM.getId(sellItem.getItem()).toString(), tagIds(sellItem), nutrition(sellItem),
                    enchantedGear(sellItem), h.config)) ci.cancel();
        } catch (RuntimeException e) {
            Honor.LOG.error("Trade cooldown check failed, using vanilla locking", e);
        }
    }

    private static List<String> tagIds(ItemStack s) {
        return s.streamTags().map(t -> t.id().toString()).toList();
    }

    private static int nutrition(ItemStack s) {
        var f = s.get(DataComponentTypes.FOOD);
        return f == null ? -1 : f.nutrition();
    }

    private static boolean enchantedGear(ItemStack s) {
        if (!s.hasEnchantments()) return false;
        return s.isDamageable() || s.contains(DataComponentTypes.TOOL) || s.contains(DataComponentTypes.WEAPON)
                || s.contains(DataComponentTypes.EQUIPPABLE);
    }
}
