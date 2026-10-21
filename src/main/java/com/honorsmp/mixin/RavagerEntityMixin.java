package com.honorsmp.mixin;

import com.honorsmp.Sieges;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.world.rule.GameRule;
import net.minecraft.world.rule.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Siege ravagers never destroy leaves/blocks: the vanilla mob-griefing check reads false for them. */
@Mixin(RavagerEntity.class)
public class RavagerEntityMixin {
    @Redirect(method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/rule/GameRules;getValue(Lnet/minecraft/world/rule/GameRule;)Ljava/lang/Object;"))
    private Object honorsmp$noSiegeGriefing(GameRules rules, GameRule<?> rule) {
        if (Sieges.isSiegeMob((RavagerEntity) (Object) this)) return Boolean.FALSE;
        return rules.getValue(rule);
    }
}
