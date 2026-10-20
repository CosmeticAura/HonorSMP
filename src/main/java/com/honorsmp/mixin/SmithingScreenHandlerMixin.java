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
import net.minecraft.entity.player.PlayerEntity;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Safety net: if a restricted netherite result is ever taken (click or shift-click), vanilla would consume
     * template, base and ingot. Instead cancel it, hand every input back (overflow drops at the feet) and
     * void the taken stack, so nothing is lost or duplicated.
     */
    @Inject(method = "onTakeOutput", at = @At("HEAD"), cancellable = true)
    private void honorsmp$returnInputs(PlayerEntity player, ItemStack taken, CallbackInfo ci) {
        if (taken.isEmpty() || !NetheriteRules.isRestricted(Registries.ITEM.getId(taken.getItem()).toString())) return;
        ForgingScreenHandler self = (ForgingScreenHandler) (Object) this;
        List<NetheriteRules.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ItemStack s = self.slots.get(i).getStack();
            entries.add(new NetheriteRules.Entry(Registries.ITEM.getId(s.getItem()).toString(), s.isEmpty() ? 0 : s.getCount()));
        }
        if (NetheriteRules.smithingReturns(entries, Registries.ITEM.getId(taken.getItem()).toString()).isEmpty()) return;
        for (int i = 0; i < 3; i++) {
            Slot in = self.slots.get(i);
            ItemStack s = in.getStack();
            if (s.isEmpty()) continue;
            in.setStack(ItemStack.EMPTY);
            player.giveItemStack(s);
            if (!s.isEmpty()) player.dropItem(s, false);
        }
        taken.setCount(0);
        self.onContentChanged(self.slots.get(0).inventory);
        ci.cancel();
    }
}
