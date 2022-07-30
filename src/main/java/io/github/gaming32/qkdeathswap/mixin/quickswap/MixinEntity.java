package io.github.gaming32.qkdeathswap.mixin.quickswap;

import io.github.gaming32.qkdeathswap.quickswap.QuickSwap;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class MixinEntity {
	@Inject(method = "moveToWorld", at = @At("HEAD"), cancellable = true)
	private void disableChangeDimensionOnQuickSwap(CallbackInfoReturnable<Entity> cir) {
		if (QuickSwap.INSTANCE.isEnabled()) {
			cir.setReturnValue(null);
		}
	}
}
