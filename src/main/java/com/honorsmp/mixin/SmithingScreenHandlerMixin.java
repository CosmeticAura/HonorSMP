package com.honorsmp.mixin;

import com.honorsmp.logic.NetheriteRules;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmithingScreenHandler.class)
public abstract class SmithingScreenHandlerMixin {
    @Inject(method = "updateResult", at = @At("TAIL"))
    private void honorsmp$noNetherite(CallbackInfo ci) {
        ForgingScreenHandler self = (ForgingScreenHandler) (Object) this;
        Slot slot = self.slots.get(self.getResultSlotIndex());
        ItemStack out = slot.getStack();
        if (!out.isEmpty() && NetheriteRules.isRestricted(Registries.ITEM.getId(out.getItem()).toString())) {
            slot.setStack(ItemStack.EMPTY);
            if (((ForgingScreenHandlerAccessor) self).honorsmp$getPlayer() instanceof ServerPlayerEntity sp) {
                sp.sendMessage(Text.literal("Netherite swords, axes and armor are disabled"), true);
            }
        }
    }
}
