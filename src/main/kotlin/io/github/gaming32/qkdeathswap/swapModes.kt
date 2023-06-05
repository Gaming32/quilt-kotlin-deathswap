package io.github.gaming32.qkdeathswap

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import qouteall.imm_ptl.core.api.PortalAPI
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader
import qouteall.imm_ptl.core.chunk_loading.DimensionalChunkPos

interface SwapMode {
    val preSwapHappensAtPrepare: Boolean

    fun prepareSwap(server: MinecraftServer) = Unit

    fun beforeSwap(server: MinecraftServer) = Unit

    fun endMatch(server: MinecraftServer) = Unit

    object Simple : SwapMode {
        override val preSwapHappensAtPrepare get() = true
    }

    object ImmersivePortals : SwapMode {
        private val chunkLoaders = mutableMapOf<ServerPlayer, ChunkLoader>()

        override val preSwapHappensAtPrepare get() = false

        override fun prepareSwap(server: MinecraftServer) {
            val players = DeathSwapStateManager.shuffledPlayers!!
            for (i in 1 until players.size) {
                chunkLoad(players[i - 1], players[i])
            }
            chunkLoad(players.last(), players[0])
        }

        private fun chunkLoad(player: ServerPlayer, to: ServerPlayer) {
            chunkLoaders.remove(player)?.let { PortalAPI.removeChunkLoaderForPlayer(player, it) }
            val loader = ChunkLoader(DimensionalChunkPos(to.level.dimension(), to.chunkPosition()), 4, true)
            PortalAPI.addChunkLoaderForPlayer(player, loader)
            chunkLoaders[player] = loader
        }

        override fun beforeSwap(server: MinecraftServer) = clearChunkLoaders()

        override fun endMatch(server: MinecraftServer) = clearChunkLoaders()

        private fun clearChunkLoaders() {
            chunkLoaders.forEach(PortalAPI::removeChunkLoaderForPlayer)
            chunkLoaders.clear()
        }
    }
}
