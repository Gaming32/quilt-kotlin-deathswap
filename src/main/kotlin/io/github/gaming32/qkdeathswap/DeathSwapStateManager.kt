package io.github.gaming32.qkdeathswap

import net.minecraft.command.CommandException
import net.minecraft.entity.EntityType
import net.minecraft.entity.LightningEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.GameMode
import net.minecraft.world.World
import org.quiltmc.qkl.wrapper.qsl.networking.allPlayers
import kotlin.random.Random
import kotlin.random.nextInt

enum class GameState {
    NOT_STARTED, STARTED
}

object DeathSwapStateManager {
    var state = GameState.NOT_STARTED
    private set

    const val MIN_SWAP_TIME = 20 * 60
    const val MAX_SWAP_TIME = 20 * 180

    var timeSinceLastSwap = 0
    var timeToSwap = 0

    val livingPlayers = mutableSetOf<ServerPlayerEntity>()

    fun hasBegun(): Boolean {
        return state == GameState.STARTED
    }

    fun begin(server: MinecraftServer) {
        if (hasBegun()) {
            throw CommandException(Text.literal("Game already begun"))
        }

        state = GameState.STARTED
        livingPlayers.clear()
        server.allPlayers.forEach { player ->
            livingPlayers.add(player)
            resetPlayer(player)
        }
        server.worlds.forEach { world ->
            world.setWeather(6000, 0, false, false)
            world.timeOfDay = 0
        }
        timeSinceLastSwap = 0
        timeToSwap = Random.nextInt(MIN_SWAP_TIME..MAX_SWAP_TIME)
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

    fun resetPlayer(player: ServerPlayerEntity, gamemode: GameMode = GameMode.SURVIVAL) {
        player.changeGameMode(gamemode)
        player.health = player.maxHealth
        player.setExperienceLevel(0)
        player.setExperiencePoints(0)
        with(player.hungerManager) {
            foodLevel = 20
            saturationLevel = 5f
            exhaustion = 0f
        }
        player.inventory.clear()
        player.enderChestInventory.clear()
        player.setSpawnPoint(null, null, 0f, false, false) // If pos is null, the rest of the arguments are ignored
        player.clearStatusEffects()
    }

    fun tick(server: MinecraftServer) {
        timeSinceLastSwap++
        if (timeSinceLastSwap > timeToSwap) {
            server.broadcast("Swapping!")

            val shuffledPlayers = livingPlayers.shuffled()
            val firstPlayerLocation = shuffledPlayers[0].location
            for (i in 1 until shuffledPlayers.size) {
                shuffledPlayers[i - 1].teleport(shuffledPlayers[i].location)
            }
            shuffledPlayers.last().teleport(firstPlayerLocation)

            timeSinceLastSwap = 0
            timeToSwap = Random.nextInt(MIN_SWAP_TIME..MAX_SWAP_TIME)
        }
        if (timeSinceLastSwap % 20 == 0) {
            server.allPlayers.forEach { player ->
                player.sendMessage(
                    Text.literal(
                        "Time since last swap: ${ticksToMinutesSeconds(timeSinceLastSwap)}"
                    ).formatted(
                        if (timeSinceLastSwap >= MIN_SWAP_TIME) Formatting.RED else Formatting.GREEN
                    ),
                    true
                )
            }
        }
    }
}
