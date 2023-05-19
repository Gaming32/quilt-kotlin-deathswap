package io.github.gaming32.qkdeathswap

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Mth
import net.minecraft.world.entity.Pose
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

data class Location(
    val world: ResourceKey<Level>? = null,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float? = null,
    val pitch: Float? = null,
    val pose: Pose? = null,
) {
    constructor(
        world: Level? = null,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float? = null,
        pitch: Float? = null,
        pose: Pose? = null
    ) : this(world?.dimension(), x, y, z, yaw, pitch, pose)

    constructor(
        world: ResourceKey<Level>? = null,
        pos: Vec3i,
        yaw: Float? = null,
        pitch: Float? = null,
        pose: Pose? = null,
    ) : this(world, pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, yaw, pitch, pose)

    constructor(
        world: Level? = null,
        pos: Vec3i,
        yaw: Float? = null,
        pitch: Float? = null,
        pose: Pose? = null,
    ) : this(world?.dimension(), pos, yaw, pitch, pose)

    fun getWorld(server: MinecraftServer) = world?.let(server::getLevel)

    val pos get() = Vec3(x, y, z)

    val blockPos get() = BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z))
}
