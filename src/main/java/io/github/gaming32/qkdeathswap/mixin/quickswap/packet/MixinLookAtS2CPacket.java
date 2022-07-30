package io.github.gaming32.qkdeathswap.mixin.quickswap.packet;

import io.github.gaming32.qkdeathswap.quickswap.IPositionedPacket;
import net.minecraft.network.packet.s2c.play.LookAtS2CPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LookAtS2CPacket.class)
public class MixinLookAtS2CPacket implements IPositionedPacket {
	@Shadow
	@Final
	@Mutable
	private double targetX;
	@Shadow
	@Final
	@Mutable
	private double targetZ;

	@Override
	public void move(int deltaChunkX, int deltaChunkZ) {
		this.targetX += deltaChunkX * 16;
		this.targetZ += deltaChunkZ * 16;
	}

	@NotNull
	@Override
	public ChunkPos getChunkPos() {
		return new ChunkPos(MathHelper.floor(this.targetX * (1.0/16)), MathHelper.floor(this.targetZ * (1.0/16)));
	}
}
