package io.github.gaming32.qkdeathswap.mixin.quickswap.packet;

import io.github.gaming32.qkdeathswap.quickswap.IPositionedPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ExperienceOrbSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({
	PlayerMoveC2SPacket.class,
	VehicleMoveC2SPacket.class,
	ExperienceOrbSpawnS2CPacket.class,
	VehicleMoveS2CPacket.class,
	EntityPositionS2CPacket.class,
	EntitySpawnS2CPacket.class,
	PlayerSpawnS2CPacket.class,
	ParticleS2CPacket.class,
	PlayerPositionLookS2CPacket.class,
})
public class MixinPacketWithXZDouble implements IPositionedPacket {
	@Shadow
	@Final
	@Mutable
	protected double x;
	@Shadow
	@Final
	@Mutable
	protected double z;

	@Override
	public void move(int deltaChunkX, int deltaChunkZ) {
		this.x += deltaChunkX * 16;
		this.z += deltaChunkZ * 16;
	}

	@NotNull
	@Override
	public ChunkPos getChunkPos() {
		return new ChunkPos(MathHelper.floor(this.x * (1.0/16)), MathHelper.floor(this.z * (1.0/16)));
	}
}
