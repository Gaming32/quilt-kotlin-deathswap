package io.github.gaming32.qkdeathswap

import net.minecraft.command.CommandException
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
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

    val livingPlayers = mutableSetOf<PlayerEntity>()

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
            player.changeGameMode(GameMode.SURVIVAL)
        }
        server.worlds.forEach { world ->
            world.setWeather(6000, 0, false, false)
            world.timeOfDay = 0
        }
        timeSinceLastSwap = 0
        timeToSwap = Random.nextInt(MIN_SWAP_TIME..MAX_SWAP_TIME)
    }

    fun removePlayer(player: PlayerEntity) {
        livingPlayers.remove(player)
        if (livingPlayers.size < 2) {
            endGame(player.server!!)
            player.server?.broadcast("Game over!")
        }
    }

    fun endGame(server: MinecraftServer) {
        state = GameState.NOT_STARTED
        livingPlayers.clear()
        val destWorld = server.getWorld(World.OVERWORLD)!!
        server.allPlayers.forEach { player ->
            livingPlayers.add(player)
            player.changeGameMode(GameMode.SURVIVAL)
            player.teleport(
                destWorld,
                destWorld.spawnPos.x.toDouble(),
                destWorld.spawnPos.y.toDouble(),
                destWorld.spawnPos.z.toDouble(),
                destWorld.spawnAngle, 0f
            )
        }
    }

    fun tick(server: MinecraftServer) {
        timeSinceLastSwap++
        if (timeSinceLastSwap > timeToSwap) {
            server.broadcast("Swapping!")

            timeSinceLastSwap = 0
            timeToSwap = Random.nextInt(MIN_SWAP_TIME..MAX_SWAP_TIME)
        }
        if (timeSinceLastSwap % 20 == 0) {
            val minutes = timeSinceLastSwap / 1200
            val seconds = timeSinceLastSwap / 20 - minutes * 60
            server.allPlayers.forEach { player ->
                player.sendMessage(
                    Text.literal(
                        "Time since last swap: ${minutes}:${seconds.toString().padStart(2, '0')}"
                    ).formatted(
                        if (timeSinceLastSwap >= MIN_SWAP_TIME) Formatting.RED else Formatting.GREEN
                    ),
                    true
                )
            }
        }
    }
}
