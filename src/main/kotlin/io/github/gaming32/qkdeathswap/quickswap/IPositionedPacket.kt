package io.github.gaming32.qkdeathswap.quickswap

import net.minecraft.network.NetworkSide
import net.minecraft.network.NetworkState
import net.minecraft.network.Packet
import net.minecraft.util.math.ChunkPos
import org.quiltmc.qsl.networking.api.PacketByteBufs

interface IPositionedPacket {
    fun move(deltaChunkX: Int, deltaChunkZ: Int)
    val chunkPos: ChunkPos?
}

fun Packet<*>.move(deltaChunkX: Int, deltaChunkZ: Int) {
    (this as? IPositionedPacket)?.move(deltaChunkX, deltaChunkZ)
}

val Packet<*>.chunkPos: ChunkPos?
    get() = (this as? IPositionedPacket)?.chunkPos

/**
 * Creates a copy of the packet if necessary and then moves it. This is useful if the same packet instance is
 * sent to multiple players.
 */
fun Packet<*>.moved(deltaChunkX: Int, deltaChunkZ: Int, side: NetworkSide): Packet<*> {
    if (this !is IPositionedPacket) {
        return this
    }

    // copy the packet
    val packetId = NetworkState.PLAY.getPacketId(side, this)
        ?: throw IllegalStateException("Packet ${this.javaClass} is not registered on $side")
    val buf = PacketByteBufs.create()
    this.write(buf)
    val copy = NetworkState.PLAY.getPacketHandler(side, packetId, buf)
        ?: throw IllegalStateException("Packet ${this.javaClass} is not registered on $side")

    // move the copied packet
    copy.move(deltaChunkX, deltaChunkZ)

    return copy
}
