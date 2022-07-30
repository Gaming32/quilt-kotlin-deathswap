package io.github.gaming32.qkdeathswap.mixin.quickswap.packet;

import io.github.gaming32.qkdeathswap.quickswap.IPositionedPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(UnloadChunkS2CPacket.class)
public class MixinUnloadChunkS2CPacket implements IPositionedPacket {
	@Shadow
	@Final
	@Mutable
	private int x;
	@Shadow
	@Final
	@Mutable
	private int z;

	@Override
	public void move(int deltaChunkX, int deltaChunkZ) {
		this.x += deltaChunkX;
		this.z += deltaChunkZ;
	}

	@NotNull
	@Override
	public ChunkPos getChunkPos() {
		return new ChunkPos(this.x, this.z);
	}
}
