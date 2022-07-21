package io.github.gaming32.qkdeathswap

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import kotlin.random.Random
import kotlin.random.nextInt

class PlayerStartLocation(val world: ServerWorld, val player: ServerPlayerEntity, var x: Int, var z: Int) {
    var y = 0
    private var ready = false

    init {
        if (world.dimension.hasCeiling) {
            ceilingSearch()
        } else {
            normalSearch()
        }
    }

    fun tick(): Boolean {
        if (ready) return true
        for (i in 0..10) {
            if (world.dimension.hasCeiling) {
                ceilingSearch()
            } else {
                normalSearch()
            }
            if (ready) return true
        }
        return false
    }

    private fun ceilingSearch() {
        val topY = world.dimension.logicalHeight + world.dimension.minimumY
        val blockPos = BlockPos.Mutable(x, 0, z)
        for (i in topY downTo world.dimension.minimumY) {
            val state = world.getBlockState(blockPos.setY(i - 2))
            val solid = state.isSolidBlock(world, blockPos)
            if (world.getBlockState(blockPos.setY(i)).isAir && world.getBlockState(blockPos.setY(i - 1)).isAir && !state.isAir && solid) {
                y = i - 1
                ready = true
                return
            }
        }
        x = Random.nextInt(x-16..x+16)
        z = Random.nextInt(z-16..z+16)
    }

    private fun normalSearch() {
        y = (world.getChunk(x shr 4, z shr 4).getTopBlock(x and 0xf, z and 0xf))
        if (y != world.dimension.minimumY) {
            ready = true
            return
        }
        x = Random.nextInt(x-16..x+16)
        z = Random.nextInt(z-16..z+16)
    }
}
