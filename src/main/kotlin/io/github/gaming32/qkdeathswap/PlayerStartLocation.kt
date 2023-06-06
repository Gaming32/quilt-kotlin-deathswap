package io.github.gaming32.qkdeathswap

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import kotlin.random.Random
import kotlin.random.nextInt

class PlayerStartLocation(val level: ServerLevel, var x: Int, var z: Int) {
    companion object {
        const val searchOffset = 32
        val disallowSpawn = TagKey.create(Registries.BIOME, ResourceLocation(MOD_ID, "disallow_spawn"))!!
    }

    enum class FindState {
        BIOME, Y_LEVEL, READY;

        operator fun inc() =
            values().getOrNull(ordinal + 1)
                ?: throw IllegalArgumentException("Cannot increment FindState.$this")
    }

    var y = 0
    private var state = FindState.BIOME

    init {
        biomeSearch()
    }

    fun tick(): Boolean {
        repeat(10) {
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
        }
        return false
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
            val solid = state.isSolidRender(level, blockPos)
            if (level.getBlockState(blockPos.setY(i)).isAir && level.getBlockState(blockPos.setY(i - 1)).isAir && !state.isAir && solid) {
                y = i - 1
                this.state++
                return
            }
        }
        newPos()
    }

    private fun normalSearch() {
        y = (level.getChunk(x shr 4, z shr 4).getTopBlock(x and 0xf, z and 0xf))
        if (y != level.dimensionType().minY) {
            this.state++
            return
        }
        newPos()
    }

    private fun newPos() {
        x = Random.nextInt(x - searchOffset..x + searchOffset)
        z = Random.nextInt(z - searchOffset..z + searchOffset)
    }
}
