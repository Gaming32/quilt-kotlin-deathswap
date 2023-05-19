package io.github.gaming32.qkdeathswap.mixin;

import io.github.gaming32.qkdeathswap.DeathSwapStateManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public class MixinResultSlot {
    @Shadow
    @Final
    private Player player;

    @Inject(
        method = "checkTakeAchievements",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;onCraftedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;I)V"
        )
    )
    private void onCrafted(ItemStack stack, CallbackInfo ci) {
        if (this.player instanceof ServerPlayer serverPlayer) {
            DeathSwapStateManager.INSTANCE.onCraft(serverPlayer, stack);
        }
    }
}
