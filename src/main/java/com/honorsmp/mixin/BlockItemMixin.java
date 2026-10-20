package com.honorsmp.mixin;

import com.honorsmp.Honor;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {
    @Inject(method = "postPlacement", at = @At("HEAD"))
    private void honorsmp$placed(BlockPos pos, World world, PlayerEntity player, ItemStack stack, BlockState state,
                                 CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayerEntity sp && Honor.I != null) {
            Honor.I.onBlockChanged(sp, world, pos);
        }
    }
}
