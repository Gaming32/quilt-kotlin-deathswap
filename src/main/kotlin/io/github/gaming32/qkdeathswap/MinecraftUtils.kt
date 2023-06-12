package io.github.gaming32.qkdeathswap

import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.Heightmap
import xyz.nucleoid.fantasy.Fantasy
import xyz.nucleoid.fantasy.RuntimeWorldConfig
import xyz.nucleoid.fantasy.RuntimeWorldHandle
import kotlin.jvm.optionals.getOrNull

fun MinecraftServer.broadcast(message: String) = broadcast(Component.literal(message))

fun MinecraftServer.broadcast(message: Component) = playerList.broadcastSystemMessage(message, false)

val Level.spawnLocation get() = Location(this, sharedSpawnPos, yaw = sharedSpawnAngle)

fun ServerPlayer.teleport(location: Location) {
    val world = (location.getWorld(server) ?: level) as ServerLevel

    if (isSleeping) {
        stopSleepInBed(true, true)
    }
    stopRiding()
    teleportTo(
        world,
        location.x,
        location.y,
        location.z,
        location.yaw ?: yRot,
        location.pitch ?: xRot
    )
}

val Entity.location get() = Location(level, x, y, z, yRot, xRot, pose)

var ServerPlayer.spawnLocation: Location?
    get() = respawnPosition?.let { spawnPos ->
        Location(server.getLevel(respawnDimension), spawnPos, yaw = respawnAngle)
    }
    set(location) {
        if (location == null) {
            setRespawnPosition(Level.OVERWORLD, null, 0.0f, false, false)
        } else {
            setRespawnPosition(location.world ?: Level.OVERWORLD, location.blockPos, location.yaw ?: 0.0f, true, false)
        }
    }

fun ticksToMinutesSeconds(ticks: Int): String {
    val minutes = ticks / 1200
    val seconds = ticks / 20 - minutes * 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

fun LevelChunk.getFirstAvailable(x: Int, z: Int): Int {
    return getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE).getFirstAvailable(x, z)
}

fun Container.copyFrom(other: Container) {
    for (i in 0 until containerSize) {
        setItem(i, other.getItem(i).copy())
    }
}

fun getDimensionType(server: MinecraftServer, id: ResourceLocation) = server
    .registryAccess()
    .registryOrThrow(Registries.DIMENSION_TYPE)
    .getHolder(ResourceKey.create(Registries.DIMENSION_TYPE, id))
    .getOrNull()

fun MinecraftServer.createFantasyWorld(fromLevel: ServerLevel, seed: Long): RuntimeWorldHandle {
    DeathSwapStateManager.isCreatingFantasyWorld.set(true)
    val newWorld = try {
        Fantasy.get(this).openTemporaryWorld(
            RuntimeWorldConfig()
                .setSeed(seed)
                .setShouldTickTime(true)
                .setDimensionType(fromLevel.dimensionTypeRegistration())
                .setDifficulty(DeathSwapConfig.fantasyDifficulty.value)
                .setGenerator(fromLevel.chunkSource.generator)
        )
    } finally {
        DeathSwapStateManager.isCreatingFantasyWorld.set(false)
    }
    DeathSwapStateManager.fantasyWorlds.put(fromLevel.dimension().location(), newWorld)?.delete()
    DeathSwapMod.swapMode.dimensionsCreated(this)
    return newWorld
}
