package io.github.gaming32.qkdeathswap

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.MessageType
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.world.World

fun MinecraftServer.broadcast(message: String) {
    broadcast(Text.literal(message))
}

fun MinecraftServer.broadcast(message: Text) {
    playerManager.broadcastSystemMessage(message, MessageType.SYSTEM)
}

val World.spawnLocation: Location
    get() = Location(this, spawnPos.x.toDouble(), spawnPos.y.toDouble(), spawnPos.z.toDouble(), spawnAngle)

fun ServerPlayerEntity.teleport(location: Location) {
    teleport(
        (location.world ?: world) as ServerWorld,
        location.x,
        location.y,
        location.z,
        location.yaw ?: yaw,
        location.pitch ?: pitch
    )
}

val PlayerEntity.location: Location
    get() = Location(world, x, y, z, yaw, pitch)

fun ticksToMinutesSeconds(ticks: Int): String {
    val minutes = ticks / 1200
    val seconds = ticks / 20 - minutes * 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}
