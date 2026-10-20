package com.honorsmp.mixin;

import com.honorsmp.ItemFixer;
import java.util.function.Consumer;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every loot table path (chests, mobs, fishing, gifts) ends in generateUnprocessedLoot(LootContext, Consumer). */
@Mixin(LootTable.class)
public abstract class LootTableMixin {
    @ModifyVariable(method = "generateUnprocessedLoot(Lnet/minecraft/loot/context/LootContext;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true)
    private Consumer<ItemStack> honorsmp$capLoot(Consumer<ItemStack> original) {
        return stack -> {
            ItemFixer.fixCaps(stack);
            original.accept(stack);
        };
    }
}
