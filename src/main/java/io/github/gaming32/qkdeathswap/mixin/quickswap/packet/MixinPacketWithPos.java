package io.github.gaming32.qkdeathswap.mixin.quickswap.packet;

import io.github.gaming32.qkdeathswap.quickswap.IPositionedPacket;
import net.minecraft.network.packet.c2s.play.JigsawGeneratingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.QueryBlockNbtC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateCommandBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateJigsawC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateStructureBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.SignEditorOpenS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({
	QueryBlockNbtC2SPacket.class,
	JigsawGeneratingC2SPacket.class,
	UpdateCommandBlockC2SPacket.class,
	UpdateStructureBlockC2SPacket.class,
	UpdateSignC2SPacket.class,
	BlockBreakingProgressS2CPacket.class,
	BlockEventS2CPacket.class,
	SignEditorOpenS2CPacket.class,
	PlayerActionC2SPacket.class,
	UpdateJigsawC2SPacket.class,
	BlockEntityUpdateS2CPacket.class,
	BlockUpdateS2CPacket.class,
	WorldEventS2CPacket.class,
	PlayerSpawnPositionS2CPacket.class,
})
public class MixinPacketWithPos implements IPositionedPacket {
	@Shadow
	@Final
	@Mutable
	private BlockPos pos;

	@Override
	public void move(int deltaChunkX, int deltaChunkZ) {
		this.pos = this.pos.add(deltaChunkX * 16, 0, deltaChunkZ * 16);
	}

	@NotNull
	@Override
	public ChunkPos getChunkPos() {
		return new ChunkPos(this.pos);
	}
}
