package io.github.gaming32.qkdeathswap.imm_ptrl

import io.github.gaming32.qkdeathswap.SwapForward
import net.minecraft.server.network.ServerPlayerEntity
import qouteall.imm_ptl.core.api.PortalAPI
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader
import qouteall.imm_ptl.core.chunk_loading.DimensionalChunkPos

class ImmPtrlSwapForward(thisPlayer: ServerPlayerEntity, nextPlayer: ServerPlayerEntity) : SwapForward(thisPlayer, nextPlayer) {

    private val chunkLoader = ChunkLoader(
        DimensionalChunkPos(
            pos.world,
            pos.x.toInt() shr 4,
            pos.z.toInt() shr 4
        ),
        4
    )

    override fun preSwap() {
        PortalAPI.addChunkLoaderForPlayer(
            thisPlayer,
            chunkLoader
        )
    }

    override fun swap(moreThanTwoPlayers: Boolean) {
        super.swap(moreThanTwoPlayers)
        PortalAPI.removeChunkLoaderForPlayer(
            thisPlayer,
            chunkLoader
        )
    }
}