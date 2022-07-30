package io.github.gaming32.qkdeathswap.mixin.quickswap.packet;

import io.github.gaming32.qkdeathswap.quickswap.IPositionedPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PlayerInteractBlockC2SPacket.class)
public class MixinPlayerInteractBlockC2SPacket implements IPositionedPacket {
	@Shadow
	@Final
	@Mutable
	private BlockHitResult blockHitResult;

	@Override
	public void move(int deltaChunkX, int deltaChunkZ) {
		this.blockHitResult = new BlockHitResult(
			this.blockHitResult.getPos().add(deltaChunkX * 16, 0, deltaChunkZ * 16),
			this.blockHitResult.getSide(),
			this.blockHitResult.getBlockPos().add(deltaChunkX * 16, 0, deltaChunkZ * 16),
			this.blockHitResult.isInsideBlock()
		);
	}

	@NotNull
	@Override
	public ChunkPos getChunkPos() {
		return new ChunkPos(this.blockHitResult.getBlockPos());
	}
}
