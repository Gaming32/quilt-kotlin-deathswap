package io.github.gaming32.qkdeathswap.mixin.quickswap.packet;

import io.github.gaming32.qkdeathswap.quickswap.IPositionedPacket;
import net.minecraft.network.packet.s2c.play.WorldBorderCenterChangedS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldBorderInitializeS2CPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({
	WorldBorderInitializeS2CPacket.class,
	WorldBorderCenterChangedS2CPacket.class,
})
public class MixinWorldBorderCenterPackets implements IPositionedPacket {
	@Shadow
	@Final
	@Mutable
	private double centerX;
	@Shadow
	@Final
	@Mutable
	private double centerZ;

	@Override
	public void move(int deltaChunkX, int deltaChunkZ) {
		this.centerX += deltaChunkX * 16;
		this.centerZ += deltaChunkZ * 16;
	}

	@NotNull
	@Override
	public ChunkPos getChunkPos() {
		return new ChunkPos(MathHelper.floor(this.centerX * (1.0/16)), MathHelper.floor(this.centerZ * (1.0/16)));
	}
}
