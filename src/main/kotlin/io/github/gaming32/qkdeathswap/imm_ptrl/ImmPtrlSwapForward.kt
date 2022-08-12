package io.github.gaming32.qkdeathswap.imm_ptrl

import io.github.gaming32.qkdeathswap.SwapForward
import io.github.gaming32.qkdeathswap.SwapForwardData
import io.github.gaming32.qkdeathswap.location
import net.minecraft.server.network.ServerPlayerEntity
import qouteall.imm_ptl.core.api.PortalAPI
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader
import qouteall.imm_ptl.core.chunk_loading.DimensionalChunkPos

class ImmPtrlSwapForward(thisPlayer: ServerPlayerEntity, nextPlayer: ServerPlayerEntity) : SwapForward(thisPlayer, nextPlayer) {

    private val chunkLoader = ChunkLoader(
        DimensionalChunkPos(
            nextPlayer.location.world,
        nextPlayer.location.x.toInt() shr 4,
        nextPlayer.location.z.toInt() shr 4
        ),
        4
    )

    override fun preLoad() {
        PortalAPI.addChunkLoaderForPlayer(
            thisPlayer,
            chunkLoader
        )
    }

    override fun preSwap() {
        pos = nextPlayer.location
        swapData = SwapForwardData(thisPlayer, nextPlayer)
    }

    override fun swap(moreThanTwoPlayers: Boolean) {
        super.swap(moreThanTwoPlayers)
        PortalAPI.removeChunkLoaderForPlayer(
            thisPlayer,
            chunkLoader
        )
    }
}