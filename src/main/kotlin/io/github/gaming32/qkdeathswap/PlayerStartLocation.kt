package io.github.gaming32.qkdeathswap

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.world.level.chunk.ChunkStatus
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.random.Random

class PlayerStartLocation(val level: ServerLevel, private var x: Int, private var z: Int) {
    companion object {
        val disallowSpawn = TagKey.create(Registries.BIOME, ResourceLocation(MOD_ID, "disallow_spawn"))!!
    }

    enum class FindState(val maxSearchOffset: Int) {
        BIOME(64),
        Y_LEVEL(16),
        READY(-1);

        operator fun inc() =
            values().getOrNull(ordinal + 1)
                ?: throw IllegalArgumentException("Cannot increment FindState.$this")
    }

    private var y = 0
    private var state = FindState.BIOME
    private lateinit var pos: BlockPos

    fun tick(): Boolean {
        when (state) {
            FindState.BIOME -> biomeSearch()
            FindState.Y_LEVEL ->
                if (level.dimensionType().hasCeiling) {
                    ceilingSearch()
                } else {
                    normalSearch()
                }
            FindState.READY -> return true
        }
        return false
    }

    fun forceFinalize() {
        state = FindState.READY
    }

    fun getPos(): BlockPos {
        if (state != FindState.READY) {
            throw IllegalStateException("Player start position accessed before finalized!")
        }
        if (!this::pos.isInitialized) {
            pos = BlockPos(x, y, z)
        }
        return pos
    }

    private fun biomeSearch() {
        val checkY = level.chunkSource.generator.seaLevel
        if (!level.getBiome(BlockPos(x, checkY, z)).`is`(disallowSpawn)) {
            state++
            return
        }
        newPos()
    }

    private fun ceilingSearch() {
        val topY = level.dimensionType().logicalHeight + level.dimensionType().minY
        val blockPos = BlockPos.MutableBlockPos(x, 0, z)
        for (i in topY downTo level.dimensionType().minY) {
            val state = level.getBlockState(blockPos.setY(i - 2))
            if (
                !state.isAir && state.isFaceSturdy(level, blockPos, Direction.UP) &&
                level.getBlockState(blockPos.setY(i)).isAir && level.getBlockState(blockPos.setY(i - 1)).isAir
            ) {
                y = i - 1
                this.state++
                return
            }
        }
        newPos()
    }

    private fun normalSearch() {
        y = level.getChunk(x shr 4, z shr 4, ChunkStatus.HEIGHTMAPS)
            .getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE)
            .getFirstAvailable(x and 0xf, z and 0xf)
        if (y != level.dimensionType().minY) {
            val blockPos = BlockPos(x, y - 1, z)
            if (level.getBlockState(blockPos).isFaceSturdy(level, blockPos, Direction.UP)) {
                this.state++
                return
            }
        }
        newPos()
    }

    private fun newPos() {
        val searchOffset = state.maxSearchOffset
        x = Random.nextInt(x - searchOffset, x + searchOffset + 1)
        z = Random.nextInt(z - searchOffset, z + searchOffset + 1)
    }
}
