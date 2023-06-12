package io.github.gaming32.qkdeathswap.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.gaming32.qkdeathswap.DeathSwapStateManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BaseFireBlock.class)
public class MixinBaseFireBlock {
    @ModifyReturnValue(
        method = "inPortalDimension",
        at = @At("RETURN")
    )
    private static boolean portalsWorkInDeathswap(boolean original, @Local Level level) {
        if (original || !(level instanceof ServerLevel serverLevel)) {
            return original;
        }
        final ResourceLocation originalLevel = DeathSwapStateManager.INSTANCE.findOriginalLevel(serverLevel);
        return originalLevel != null && (originalLevel.equals(Level.OVERWORLD.location()) || originalLevel.equals(Level.NETHER.location()));
    }
}
