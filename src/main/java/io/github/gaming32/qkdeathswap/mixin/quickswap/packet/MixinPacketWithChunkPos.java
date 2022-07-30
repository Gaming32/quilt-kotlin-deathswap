package io.github.gaming32.qkdeathswap.mixin.quickswap.packet;

import io.github.gaming32.qkdeathswap.quickswap.IPositionedPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkRenderDistanceCenterS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({
	ChunkRenderDistanceCenterS2CPacket.class,
	ChunkDataS2CPacket.class,
	LightUpdateS2CPacket.class,
})
public class MixinPacketWithChunkPos implements IPositionedPacket {
	@Shadow
	@Final
	@Mutable
	private int chunkX;
	@Shadow
	@Final
	@Mutable
	private int chunkZ;

	@Override
	public void move(int deltaChunkX, int deltaChunkZ) {
		this.chunkX += deltaChunkX;
		this.chunkZ += deltaChunkZ;
	}

	@NotNull
	@Override
	public ChunkPos getChunkPos() {
		return new ChunkPos(this.chunkX, this.chunkZ);
	}
}
