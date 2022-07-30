package io.github.gaming32.qkdeathswap.mixin.quickswap;

import io.github.gaming32.qkdeathswap.quickswap.QuickSwap;
import net.minecraft.network.Packet;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerPlayNetworkHandler.class)
public class MixinServerPlayNetworkHandler {
	@Shadow
	public ServerPlayerEntity player;

	@ModifyVariable(method = "sendPacket(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;)V", at = @At("HEAD"), argsOnly = true)
	private Packet<?> modifyPacket(Packet<?> packet) {
		return QuickSwap.INSTANCE.modifyOutgoingPacket(this.player, packet);
	}
}
