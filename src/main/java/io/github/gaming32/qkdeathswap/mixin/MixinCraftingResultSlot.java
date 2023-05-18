package io.github.gaming32.qkdeathswap.mixin;

import io.github.gaming32.qkdeathswap.DeathSwapStateManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingResultSlot.class)
public class MixinCraftingResultSlot {
    @Shadow
    @Final
    private PlayerEntity player;

    @Inject(method = "onCrafted(Lnet/minecraft/item/ItemStack;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;onCraft(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;I)V"))
    private void onCrafted(ItemStack stack, CallbackInfo ci) {
        if (this.player instanceof ServerPlayerEntity serverPlayer) {
            DeathSwapStateManager.INSTANCE.onCraft(serverPlayer, stack);
        }
    }
}
