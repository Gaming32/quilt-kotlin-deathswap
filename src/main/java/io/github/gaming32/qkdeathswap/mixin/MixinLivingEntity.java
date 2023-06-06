package io.github.gaming32.qkdeathswap.mixin;

import io.github.gaming32.qkdeathswap.DeathSwapStateManager;
import io.github.gaming32.qkdeathswap.GameState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void dontDropLoot(DamageSource damageSource, CallbackInfo ci) {
        //noinspection ConstantValue
        if ((Object)this instanceof ServerPlayer && DeathSwapStateManager.INSTANCE.getState().compareTo(GameState.STARTED) >= 0) {
            ci.cancel();
        }
    }
}
