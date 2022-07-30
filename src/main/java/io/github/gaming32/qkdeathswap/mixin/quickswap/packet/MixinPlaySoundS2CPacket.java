package io.github.gaming32.qkdeathswap.mixin.quickswap.packet;

import io.github.gaming32.qkdeathswap.quickswap.IPositionedPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundIdS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({
	PlaySoundIdS2CPacket.class,
	PlaySoundS2CPacket.class,
})
public class MixinPlaySoundS2CPacket implements IPositionedPacket {
	@Shadow
	@Final
	@Mutable
	private int fixedX;
	@Shadow
	@Final
	@Mutable
	private int fixedZ;

	@Override
	public void move(int deltaChunkX, int deltaChunkZ) {
		this.fixedX += deltaChunkX * 128;
		this.fixedZ += deltaChunkZ * 128;
	}

	@NotNull
	@Override
	public ChunkPos getChunkPos() {
		return new ChunkPos(this.fixedX >> 7, this.fixedZ >> 7);
	}
}
