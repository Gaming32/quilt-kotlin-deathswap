package io.github.gaming32.qkdeathswap.quickswap

import io.github.gaming32.qkdeathswap.DeathSwapConfig
import io.github.gaming32.qkdeathswap.DeathSwapStateManager
import net.minecraft.network.Packet
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.ChunkPos
import java.util.UUID

object QuickSwap {
    val isEnabled: Boolean
        get() = DeathSwapStateManager.hasBegun() && DeathSwapConfig.enableQuickSwap

    private val posRemapInfo = mutableMapOf<UUID, PositionRemapInfo>()

    fun modifyOutgoingPacket(player: ServerPlayerEntity, packet: Packet<*>): Packet<*> {
        if (!isEnabled) {
            return packet
        }

        val posRemapInfo = this.posRemapInfo[player.uuid] ?: return packet
        val chunkPos = packet.chunkPos ?: return packet
        val newChunkPos = posRemapInfo.remap(chunkPos)
        return packet.moved(newChunkPos.x - chunkPos.x, newChunkPos.z - chunkPos.z)
    }

    fun modifyIncomingPacket(player: ServerPlayerEntity, packet: Packet<*>) {
        if (!isEnabled) {
            return
        }

        val posRemapInfo = this.posRemapInfo[player.uuid] ?: return
        val chunkPos = packet.chunkPos ?: return
        val newChunkPos = posRemapInfo.invRemap(chunkPos)
        packet.move(newChunkPos.x - chunkPos.x, newChunkPos.z - chunkPos.z)
    }
}

class PositionRemapInfo {
    fun remap(pos: ChunkPos): ChunkPos {

    }

    fun invRemap(pos: ChunkPos): ChunkPos {

    }
}
