package io.github.gaming32.qkdeathswap

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import io.github.gaming32.qkdeathswap.mixin.EntityAccessor
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.MessageType
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Heightmap
import net.minecraft.world.World
import net.minecraft.world.chunk.WorldChunk
import org.quiltmc.qkl.wrapper.minecraft.brigadier.RequiredArgumentAction

fun MinecraftServer.broadcast(message: String) {
    broadcast(Text.literal(message))
}

fun MinecraftServer.broadcast(message: Text) {
    playerManager.broadcastSystemMessage(message, MessageType.SYSTEM)
}

val World.spawnLocation: Location
    get() = Location(this, spawnPos.x.toDouble(), spawnPos.y.toDouble(), spawnPos.z.toDouble(), spawnAngle)

fun ServerPlayerEntity.teleport(location: Location) {
    val world = (location.world ?: world) as ServerWorld

    if (isSleeping) {
        wakeUp(true, true)
    }
    stopRiding()
    teleport(
        world,
        location.x,
        10000.0,
        location.z,
        location.yaw ?: yaw,
        location.pitch ?: pitch
    )

    world.spawnEntity(ArmorStandEntity(world, location.x, location.y, location.z).apply {
        (this as EntityAccessor).setDimensions(this@teleport.getDimensions(location.pose ?: this@teleport.pose))
        isInvulnerable = true
        setNoGravity(true)
        addScoreboardTag("teleport_subst")
    })

    DeathSwapStateManager.teleportTargets[uuid] = Vec3d(location.x, location.y, location.z)
}

val PlayerEntity.location: Location
    get() = Location(world, x, y, z, yaw, pitch, pose)

fun ticksToMinutesSeconds(ticks: Int): String {
    val minutes = ticks / 1200
    val seconds = ticks / 20 - minutes * 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

fun WorldChunk.getTopBlock(x: Int, z: Int): Int {
    return getHeightmap(Heightmap.Type.WORLD_SURFACE).get(x, z)
}

fun <S, A, T : ArgumentBuilder<S, T>?> ArgumentBuilder<S, T>.argument(
    name: String,
    type: ArgumentType<A>,
    action: RequiredArgumentAction<S>
) {
    val argument = RequiredArgumentBuilder.argument<S, A>(
        name,
        type
    )
    argument.apply(action)
    then(argument)
}

