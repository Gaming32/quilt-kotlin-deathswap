package io.github.gaming32.qkdeathswap.mixin.quickswap.packet;

import io.github.gaming32.qkdeathswap.quickswap.IPositionedPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PlayerInteractEntityC2SPacket.class)
public class MixinPlayerInteractEntityC2SPacket implements IPositionedPacket {
	@Shadow
	@Final
	private PlayerInteractEntityC2SPacket.InteractTypeHandler type;

	@Override
	public void move(int deltaChunkX, int deltaChunkZ) {
		if (this.type instanceof IPositionedPacket positioned) {
			positioned.move(deltaChunkX, deltaChunkZ);
		}
	}

	@Nullable
	@Override
	public ChunkPos getChunkPos() {
		if (this.type instanceof IPositionedPacket positioned) {
			return positioned.getChunkPos();
		}
		return null;
	}

	@Mixin(targets = "net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket$InteractAtHandler")
	public static class MixinInteractAtHandler implements IPositionedPacket {
		@Shadow
		@Final
		@Mutable
		private Vec3d pos;

		@Override
		public void move(int deltaChunkX, int deltaChunkZ) {
			this.pos = this.pos.add(deltaChunkX * 16, 0, deltaChunkZ * 16);
		}

		@NotNull
		@Override
		public ChunkPos getChunkPos() {
			return new ChunkPos(MathHelper.floor(this.pos.getX() * (1.0/16)), MathHelper.floor(this.pos.getZ() * (1.0/16)));
		}
	}
}
