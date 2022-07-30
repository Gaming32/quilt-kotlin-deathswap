package io.github.gaming32.qkdeathswap

import net.minecraft.entity.EntityPose
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import net.minecraft.util.registry.RegistryKey
import net.minecraft.world.World

data class Location(
    val world: RegistryKey<World>? = null,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float? = null,
    val pitch: Float? = null,
    val pose: EntityPose? = null,
) {
    constructor(
        world: World? = null,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float? = null,
        pitch: Float? = null,
        pose: EntityPose? = null
    ) : this(world?.registryKey, x, y, z, yaw, pitch, pose)

    constructor(
        world: RegistryKey<World>? = null,
        pos: Vec3i,
        yaw: Float? = null,
        pitch: Float? = null,
        pose: EntityPose? = null,
    ) : this(world, pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, yaw, pitch, pose)

    constructor(
        world: World? = null,
        pos: Vec3i,
        yaw: Float? = null,
        pitch: Float? = null,
        pose: EntityPose? = null,
    ) : this(world?.registryKey, pos, yaw, pitch, pose)

    fun getWorld(server: MinecraftServer): World? {
        return world?.let(server::getWorld)
    }

    val pos: Vec3d
        get() = Vec3d(x, y, z)

    val blockPos: BlockPos
        get() = BlockPos(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z))
}
