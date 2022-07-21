package io.github.gaming32.qkdeathswap.mixin;

import io.github.gaming32.qkdeathswap.DeathSwapStateManager;
import io.github.gaming32.qkdeathswap.GameState;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public class MixinMobEntity {
    @Inject(at = @At("HEAD"), method = "checkDespawn", cancellable = true)
    private void onCheckDespawn(CallbackInfo ci) {
        if (DeathSwapStateManager.INSTANCE.getState() == GameState.TELEPORTING) {
            ci.cancel();
        }
    }
}
