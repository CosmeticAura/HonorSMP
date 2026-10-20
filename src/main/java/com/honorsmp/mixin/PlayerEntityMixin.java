package com.honorsmp.mixin;

import com.honorsmp.Honor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
    @ModifyVariable(method = "addExhaustion", at = @At("HEAD"), argsOnly = true)
    private float honorsmp$slowHunger(float exhaustion) {
        if ((Object) this instanceof ServerPlayerEntity sp && Honor.I != null) {
            return exhaustion * Honor.I.hungerFactor(sp);
        }
        return exhaustion;
    }
}
