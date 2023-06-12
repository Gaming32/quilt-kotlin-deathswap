package io.github.gaming32.qkdeathswap.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = ServerPlayer.class, priority = 999)
public class MixinServerPlayer {
    @ModifyArg(
        method = "changeDimension",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;<init>(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;JLnet/minecraft/world/level/GameType;Lnet/minecraft/world/level/GameType;ZZBLjava/util/Optional;)V"
        ),
        index = 1
    )
    private ResourceKey<Level> noSpecialDimensionType(ResourceKey<Level> original, @Local(ordinal = 0) ServerLevel destination) {
        // Effectively undoes ChangeDirectionMixin.specialDimensionType()
        return destination.dimension();
    }
}
