package io.github.gaming32.qkdeathswap.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.gaming32.qkdeathswap.DeathSwapStateManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public class MixinEntity {
    @WrapOperation(
        method = "handleNetherPortal",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;"
        )
    )
    private ResourceKey<Level> specialDimensionType(Level instance, Operation<ResourceKey<Level>> original) {
        final ResourceLocation originalLevel = DeathSwapStateManager.INSTANCE.findOriginalLevel((ServerLevel)instance);
        return originalLevel != null ? ResourceKey.create(Registries.DIMENSION, originalLevel) : original.call(instance);
    }
}
