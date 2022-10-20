package io.github.gaming32.qkdeathswap.mixin;

import io.github.gaming32.qkdeathswap.DeathSwapStateManager;
import net.minecraft.advancement.criterion.InventoryChangedCriterion;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryChangedCriterion.class)
public class MixinInventoryChangedCriterion {
	@Inject(method = "trigger(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/item/ItemStack;)V", at = @At("HEAD"))
	private void onTrigger(ServerPlayerEntity player, PlayerInventory inventory, ItemStack stack, CallbackInfo ci) {
		DeathSwapStateManager.INSTANCE.onInventoryChanged(player, stack);
	}
}
