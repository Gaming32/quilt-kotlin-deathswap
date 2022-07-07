package io.github.gaming32.qkdeathswap

import net.minecraft.entity.EntityPose
import net.minecraft.world.World

data class Location(
    val world: World? = null,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float? = null,
    val pitch: Float? = null,
    val pose: EntityPose? = null,
)
