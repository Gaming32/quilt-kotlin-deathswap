package io.github.gaming32.qkdeathswap.mixin.quickswap;

import io.github.gaming32.qkdeathswap.quickswap.QuickSwap;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.Packet;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkThreadUtils.class)
public class MixinNetworkThreadUtils {
	@Inject(method = "method_11072(Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/Packet;)V",
			at = @At("HEAD"))
	private static void onHandlePacket(PacketListener listener, Packet<?> packet, CallbackInfo ci) {
		if (listener instanceof ServerPlayNetworkHandler serverListener) {
			QuickSwap.INSTANCE.modifyIncomingPacket(serverListener.player, packet);
		}
	}
}
