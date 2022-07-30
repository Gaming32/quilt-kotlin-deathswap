package io.github.gaming32.qkdeathswap

import net.minecraft.entity.Entity
import net.minecraft.inventory.Inventory
import net.minecraft.network.MessageType
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.world.Heightmap
import net.minecraft.world.World
import net.minecraft.world.chunk.WorldChunk

fun MinecraftServer.broadcast(message: String) {
    broadcast(Text.literal(message))
}

fun MinecraftServer.broadcast(message: Text) {
    playerManager.broadcastSystemMessage(message, MessageType.SYSTEM)
}

val World.spawnLocation: Location
    get() = Location(this, spawnPos, yaw = spawnAngle)

fun ServerPlayerEntity.teleport(location: Location) {
    val world = (location.getWorld(server) ?: world) as ServerWorld

    if (isSleeping) {
        wakeUp(true, true)
    }
    stopRiding()
    teleport(
        world,
        location.x,
        location.y,
        location.z,
        location.yaw ?: yaw,
        location.pitch ?: pitch
    )
}

val Entity.location: Location
    get() = Location(world, x, y, z, yaw, pitch, pose)

var ServerPlayerEntity.spawnLocation: Location?
    get() = spawnPointPosition?.let { spawnPos ->
        Location(server.getWorld(spawnPointDimension), spawnPos, yaw = spawnAngle)
    }
    set(location) {
        if (location == null) {
            setSpawnPoint(World.OVERWORLD, null, 0.0f, false, false)
        } else {
            setSpawnPoint(location.world ?: World.OVERWORLD, location.blockPos, location.yaw ?: 0.0f, true, false)
        }
    }

fun ticksToMinutesSeconds(ticks: Int): String {
    val minutes = ticks / 1200
    val seconds = ticks / 20 - minutes * 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

fun WorldChunk.getTopBlock(x: Int, z: Int): Int {
    return getHeightmap(Heightmap.Type.WORLD_SURFACE).get(x, z)
}

fun Inventory.copyFrom(other: Inventory) {
    for (i in 0 until size()) {
        setStack(i, other.getStack(i).copy())
    }
}
