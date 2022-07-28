package io.github.gaming32.qkdeathswap

import net.minecraft.command.CommandException
import net.minecraft.entity.EntityType
import net.minecraft.entity.LightningEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.GameMode
import net.minecraft.world.World
import org.quiltmc.qkl.wrapper.qsl.networking.allPlayers
import java.text.DecimalFormat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.random.nextInt

enum class GameState {
    NOT_STARTED, STARTING, STARTED, TELEPORTING;
}

private val ONE_DIGIT_FORMAT = DecimalFormat(".0")

object DeathSwapStateManager {
    var state = GameState.NOT_STARTED
    private set

    var timeSinceLastSwap = 0
    var timeToSwap = 0

    private val playerStartLocation = mutableSetOf<PlayerStartLocation>()

    val livingPlayers = mutableSetOf<ServerPlayerEntity>()

    private val swapTargets = mutableSetOf<SwapForward>()

    fun hasBegun(): Boolean {
        return state != GameState.NOT_STARTED
    }

    fun begin(server: MinecraftServer) {
        if (hasBegun()) {
            throw CommandException(Text.literal("Game already begun"))
        }

        state = GameState.STARTING
        livingPlayers.clear()
        var playerAngle = Random.nextDouble(0.0, PI * 2)
        val playerAngleChange = PI * 2 / server.allPlayers.size
        playerStartLocation.clear()
        server.allPlayers.forEach { player ->
            livingPlayers.add(player)
            val distance = Random.nextDouble(DeathSwapConfig.minSpreadDistance.toDouble(), DeathSwapConfig.maxSpreadDistance.toDouble())
            val x = (distance * cos(playerAngle)).toInt()
            val z = (distance * sin(playerAngle)).toInt()
            playerStartLocation.add(PlayerStartLocation(server.getWorld(DeathSwapConfig.dimension) ?: server.getWorld(World.OVERWORLD)!!, player, x, z))
            playerAngle += playerAngleChange
        }
        server.worlds.forEach { world ->
            world.setWeather(0, 0, false, false)
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
            player.inventory.copyFrom(DeathSwapConfig.defaultKit)
            player.enderChestInventory.clear()
        }
        player.setSpawnPoint(null, null, 0f, false, false) // If pos is null, the rest of the arguments are ignored
        player.clearStatusEffects()
    }

    fun tick(server: MinecraftServer) {
        timeSinceLastSwap++
        if (state == GameState.STARTING) {
            if (playerStartLocation.all { it.tick() }) {
                playerStartLocation.forEach { loc ->
                    resetPlayer(loc.player, includeInventory = true)
                    loc.player.addStatusEffect(StatusEffectInstance(StatusEffects.RESISTANCE, DeathSwapConfig.resistanceTime, 255, true, false, true))
                    loc.player.teleport(
                        loc.world,
                        loc.x.toDouble(),
                        loc.y.toDouble(),
                        loc.z.toDouble(),
                        0f, 0f
                    )
                }
                timeSinceLastSwap = 0
                state = GameState.STARTED
            }
            if (timeSinceLastSwap % 20 == 0) {
                val starting = Text.literal("Finding start locations: ").append(Text.literal(ticksToMinutesSeconds(timeSinceLastSwap)).formatted(Formatting.YELLOW))
                server.allPlayers.forEach { player ->
                    player.sendMessage(starting, true)
                }
            }
            return
        }

        if (timeSinceLastSwap > DeathSwapConfig.teleportLoadTime) {
            for (player in swapTargets) {
                player.swap()
            }
            swapTargets.clear()
            state = GameState.STARTED
        }

        if (timeSinceLastSwap > timeToSwap) {
            server.broadcast("Swapping!")

            val shuffledPlayers = livingPlayers.shuffled()
            swapTargets.clear()

            for (i in 1 until shuffledPlayers.size) {
                swapTargets.add(SwapForward(shuffledPlayers[i - 1], shuffledPlayers[i]))
            }
            swapTargets.add(SwapForward(shuffledPlayers.last(), shuffledPlayers[0]))
            state = GameState.TELEPORTING
            swapTargets.forEach {
                it.preSwap()
            }

            timeSinceLastSwap = 0
            timeToSwap = Random.nextInt(DeathSwapConfig.swapTime)
        }
        val withinWarnTime = timeToSwap - timeSinceLastSwap <= DeathSwapConfig.warnTime
        if (withinWarnTime || timeSinceLastSwap % 20 == 0) {
            val text = Text.literal(
                "Time since last swap: ${ticksToMinutesSeconds(timeSinceLastSwap)}"
            ).formatted(
                if (timeSinceLastSwap >= DeathSwapConfig.minSwapTime) Formatting.RED else Formatting.GREEN
            )
            server.allPlayers.forEach { player ->
                val text2 = text.copy()
                if (player.isSpectator) {
                    text2.append(Text.literal("/${ticksToMinutesSeconds(timeToSwap)}").formatted(Formatting.YELLOW))
                }
                if (withinWarnTime) {
                    text2.append(
                        Text.literal(" ${ONE_DIGIT_FORMAT.format((timeToSwap - timeSinceLastSwap) / 20.0)} seconds")
                            .formatted(Formatting.DARK_RED)
                    )
                }
                player.sendMessage(text2, true)
            }
        }
    }
}
