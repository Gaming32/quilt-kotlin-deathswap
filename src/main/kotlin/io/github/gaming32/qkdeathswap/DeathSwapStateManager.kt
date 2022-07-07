package io.github.gaming32.qkdeathswap

import net.minecraft.command.CommandException
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LightningEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import net.minecraft.world.World
import org.quiltmc.qkl.wrapper.qsl.networking.allPlayers
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.random.nextInt

enum class GameState {
    NOT_STARTED, STARTED
}

object DeathSwapStateManager {
    var state = GameState.NOT_STARTED
    private set

    var timeSinceLastSwap = 0
    var timeToSwap = 0
    var teleportLoadTimer = -1

    val livingPlayers = mutableSetOf<ServerPlayerEntity>()

    val teleportTargets = mutableMapOf<UUID, Vec3d>()

    fun hasBegun(): Boolean {
        return state == GameState.STARTED
    }

    fun begin(server: MinecraftServer) {
        if (hasBegun()) {
            throw CommandException(Text.literal("Game already begun"))
        }

        state = GameState.STARTED
        livingPlayers.clear()
        var playerAngle = Random.nextDouble(0.0, PI * 2)
        val playerAngleChange = PI * 2 / server.allPlayers.size
        server.allPlayers.forEach { player ->
            livingPlayers.add(player)
            resetPlayer(player, includeInventory = true)
            player.addStatusEffect(StatusEffectInstance(StatusEffects.RESISTANCE, DeathSwapConfig.resistanceTime, 255, true, false, true))
            spreadPlayer(server.getWorld(DeathSwapConfig.dimension) ?: server.getWorld(World.OVERWORLD)!!, player, playerAngle)
            playerAngle += playerAngleChange
        }
        server.worlds.forEach { world ->
            world.setWeather(6000, 0, false, false)
            world.timeOfDay = 0
        }
        timeSinceLastSwap = 0
        timeToSwap = Random.nextInt(DeathSwapConfig.swapTime)
    }

    fun removePlayer(player: ServerPlayerEntity, strikeLightning: Boolean = true) {
        if (strikeLightning) {
            player.world.spawnEntity(
                LightningEntity(
                    EntityType.LIGHTNING_BOLT,
                    player.world
                ).apply { setCosmetic(true) })
        }
        resetPlayer(player, gamemode = GameMode.SPECTATOR)
        livingPlayers.remove(player)
        if (livingPlayers.size < 2) {
            player.server?.broadcast(
                Text.literal("Game over! ")
                    .append(livingPlayers.firstOrNull()?.displayName ?: Text.literal("Nobody"))
                    .append(" won")
            )
            endGame(player.server!!)
        }
    }

    fun endGame(server: MinecraftServer) {
        state = GameState.NOT_STARTED
        livingPlayers.clear()
        val destWorld = server.getWorld(World.OVERWORLD)!!
        server.allPlayers.forEach { player ->
            player.teleport(destWorld.spawnLocation.copy(pitch = 0f))
            resetPlayer(player)
        }
    }

    fun resetPlayer(
        player: ServerPlayerEntity,
        gamemode: GameMode = GameMode.SURVIVAL,
        includeInventory: Boolean = false
    ) {
        player.changeGameMode(gamemode)
        player.health = player.maxHealth
        with(player.hungerManager) {
            foodLevel = 20
            saturationLevel = 5f
            exhaustion = 0f
        }
        if (includeInventory) {
            player.server.commandManager.execute(player.server.commandSource, "advancement revoke ${player.entityName} everything")
            player.setExperienceLevel(0)
            player.setExperiencePoints(0)
            player.inventory.clear()
            player.enderChestInventory.clear()
        }
        player.setSpawnPoint(null, null, 0f, false, false) // If pos is null, the rest of the arguments are ignored
        player.clearStatusEffects()
    }

    fun spreadPlayer(world: ServerWorld, player: ServerPlayerEntity, angle: Double) {
        val distance = Random.nextDouble(DeathSwapConfig.minSpreadDistance.toDouble(), DeathSwapConfig.maxSpreadDistance.toDouble())
        var x = (distance * cos(angle)).toInt()
        var z = (distance * sin(angle)).toInt()
        if (world.dimension.hasCeiling) {
            val topY = world.dimension.logicalHeight + world.dimension.minimumY
            val blockPos = BlockPos.Mutable()
            searchLoop@ while (true) {
                blockPos.set(x, topY, z)
                for (i in topY downTo world.dimension.minimumY) {
                    val state = world.getBlockState(blockPos.setY(i - 2))
                    val solid = state.isSolidBlock(world, blockPos)
                    if (world.getBlockState(blockPos.setY(i)).isAir && world.getBlockState(blockPos.setY(i - 1)).isAir && !state.isAir && solid) {
                        break@searchLoop
                    }
                }
                x = Random.nextInt(x-16..x+16)
                z = Random.nextInt(z-16..z+16)
            }

            player.teleport(
                world,
                x.toDouble(),
                blockPos.y.toDouble(),
                z.toDouble(),
                0f, 0f
            )
        } else {
            player.teleport(
                world,
                x.toDouble(),
                (world.getChunk(x shr 4, z shr 4).getTopBlock(x and 0xf, z and 0xf) + 1).toDouble(),
                z.toDouble(),
                0f, 0f
            )
        }
    }

    fun tick(server: MinecraftServer) {
        if (teleportLoadTimer >= 0) {
            if (--teleportLoadTimer < 0) {
                for (world in server.worlds) {
                    for (entity in world.iterateEntities()) {
                        if (entity.scoreboardTags.contains("teleport_subst")) {
                            entity.remove(Entity.RemovalReason.DISCARDED)
                        }
                    }
                }
                for (player in livingPlayers) {
                    teleportTargets[player.uuid]?.let { pos ->
                        player.velocity = Vec3d.ZERO
                        player.fallDistance = 0f
                        player.networkHandler.requestTeleport(pos.x, pos.y, pos.z, player.yaw, player.pitch)
                    }
                }
            }
        }

        timeSinceLastSwap++
        if (timeSinceLastSwap > timeToSwap) {
            server.broadcast("Swapping!")

            val shuffledPlayers = livingPlayers.shuffled()
            val firstPlayerLocation = shuffledPlayers[0].location
            val firstPlayerVehicle = shuffledPlayers[0].vehicle
            var nextPlayerVehicle: Entity?
            for (i in 1 until shuffledPlayers.size) {
                shuffledPlayers[i - 1].teleport(shuffledPlayers[i].location)
                if (DeathSwapConfig.rideOpponentEntityOnTeleport) {
                    nextPlayerVehicle = shuffledPlayers[i].vehicle
                    nextPlayerVehicle?.stopRiding()
                    if (nextPlayerVehicle != null) {
                        shuffledPlayers[i - 1].startRiding(nextPlayerVehicle, true)
                    }
                }

                shuffledPlayers[i - 1].sendMessage(
                    Text.literal("You were teleported to ")
                        .append(shuffledPlayers[i].displayName.copy().formatted(Formatting.GREEN)),
                    false
                )
            }
            shuffledPlayers.last().teleport(firstPlayerLocation)
            if (DeathSwapConfig.rideOpponentEntityOnTeleport && firstPlayerVehicle != null) {
                shuffledPlayers.last().startRiding(firstPlayerVehicle, true)
            }

            shuffledPlayers.last().sendMessage(
                Text.literal("You were teleported to ")
                    .append(shuffledPlayers[0].displayName.copy().formatted(Formatting.GREEN)),
                false
            )

            timeSinceLastSwap = 0
            timeToSwap = Random.nextInt(DeathSwapConfig.swapTime)
            teleportLoadTimer = DeathSwapConfig.teleportLoadTime
        }
        if (timeSinceLastSwap % 20 == 0) {
            server.allPlayers.forEach { player ->
                var text = Text.literal(
                    "Time since last swap: ${ticksToMinutesSeconds(timeSinceLastSwap)}"
                ).formatted(
                    if (timeSinceLastSwap >= DeathSwapConfig.minSwapTime) Formatting.RED else Formatting.GREEN
                )
                if (player.isSpectator) {
                    text = text.append(Text.literal("/${ticksToMinutesSeconds(timeToSwap)}").formatted(Formatting.YELLOW))
                }
                player.sendMessage(text, true)
            }
        }
    }
}
