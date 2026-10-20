package com.honorsmp.mixin;

import com.honorsmp.Honor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
    @ModifyVariable(method = "addExhaustion", at = @At("HEAD"), argsOnly = true)
    private float honorsmp$slowHunger(float exhaustion) {
        if ((Object) this instanceof ServerPlayerEntity sp && Honor.I != null) {
            return exhaustion * Honor.I.hungerFactor(sp);
        }
        return exhaustion;
    }

    private boolean honorsmp$combatLocked() {
        if ((Object) this instanceof ServerPlayerEntity sp && Honor.I != null) {
            int left = Honor.I.combat.remainingSeconds(sp.getUuid().toString(), System.currentTimeMillis(), Honor.I.config.combatElytraSeconds);
            if (left > 0) {
                sp.sendMessage(net.minecraft.text.Text.literal("You can't use an elytra in combat for " + left + " more second" + (left == 1 ? "" : "s")), true);
                return true;
            }
        }
        return false;
    }

    @Inject(method = "checkGliding", at = @At("HEAD"), cancellable = true)
    private void honorsmp$noGlideInCombat(CallbackInfoReturnable<Boolean> cir) {
        if (honorsmp$combatLocked()) cir.setReturnValue(false);
    }

    @Inject(method = "startGliding", at = @At("HEAD"), cancellable = true)
    private void honorsmp$noStartGlideInCombat(CallbackInfo ci) {
        if (honorsmp$combatLocked()) ci.cancel();
    }
}
