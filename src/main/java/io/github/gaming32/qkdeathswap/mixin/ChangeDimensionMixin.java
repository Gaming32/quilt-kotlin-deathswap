package io.github.gaming32.qkdeathswap.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.gaming32.qkdeathswap.DeathSwapStateManager;
import io.github.gaming32.qkdeathswap.MinecraftUtilsKt;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

@Mixin(value = {Entity.class, ServerPlayer.class})
public abstract class ChangeDimensionMixin {
    @ModifyVariable(method = "changeDimension", at = @At("HEAD"), argsOnly = true)
    private ServerLevel useDeathswapLevel(ServerLevel destination) {
        final Entity eThis = (Entity)(Object)this;
        final ServerLevel current = (ServerLevel)eThis.level();
        if (!DeathSwapStateManager.INSTANCE.isFantasyWorld(current)) {
            return destination;
        }
        if (eThis instanceof ServerPlayer && !DeathSwapStateManager.INSTANCE.getLivingPlayers().containsKey(eThis.getUUID())) {
            return destination;
        }
        RuntimeWorldHandle handle = DeathSwapStateManager.INSTANCE.getFantasyWorlds().get(destination.dimension().location());
        if (handle == null) {
            handle = MinecraftUtilsKt.createFantasyWorld(destination.getServer(), destination, current.getSeed());
        }
        return handle.asWorld();
    }

    @WrapOperation(
        method = {"changeDimension", "findDimensionEntryPoint"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;"
        )
    )
    private ResourceKey<Level> specialDimensionType(ServerLevel instance, Operation<ResourceKey<Level>> original) {
        final ResourceLocation originalLevel = DeathSwapStateManager.INSTANCE.findOriginalLevel(instance);
        return originalLevel != null ? ResourceKey.create(Registries.DIMENSION, originalLevel) : original.call(instance);
    }

    @WrapOperation(
        method = {"changeDimension", "findDimensionEntryPoint"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;"
        )
    )
    private ResourceKey<Level> specialDimensionTypePlainLevel(Level instance, Operation<ResourceKey<Level>> original) {
        final ResourceLocation originalLevel = DeathSwapStateManager.INSTANCE.findOriginalLevel((ServerLevel)instance);
        return originalLevel != null ? ResourceKey.create(Registries.DIMENSION, originalLevel) : original.call(instance);
    }
}
