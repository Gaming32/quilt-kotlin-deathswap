package io.github.gaming32.qkdeathswap

import net.minecraft.command.CommandException
import net.minecraft.entity.EntityType
import net.minecraft.entity.LightningEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.unmapped.C_wmzutndm
import net.minecraft.util.Formatting
import net.minecraft.util.StringIdentifiable
import net.minecraft.util.registry.Registry
import net.minecraft.util.registry.RegistryKey
import net.minecraft.world.GameMode
import net.minecraft.world.World
import org.quiltmc.qkl.wrapper.qsl.networking.allPlayers
import java.text.DecimalFormat
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.random.nextInt

private val ONE_DIGIT_FORMAT = DecimalFormat(".0")

enum class GameState {
    NOT_STARTED, STARTING, STARTED, TELEPORTING;
}

class PlayerHolder(serverPlayerEntity: ServerPlayerEntity, val startLocation: PlayerStartLocation) {
    var displayName: Text = serverPlayerEntity.displayName
        get() {
            field = player?.displayName ?: field
            return field
        }
        private set
    private val uuid: UUID = serverPlayerEntity.uuid
    val server: MinecraftServer = serverPlayerEntity.server
    val player: ServerPlayerEntity?
        get() = server.playerManager.getPlayer(uuid)

    val itemsCollected = mutableSetOf<Item>()
}

object DeathSwapStateManager {
    var state = GameState.NOT_STARTED
    private set

    var swapCount = 0
    var timeSinceLastSwap = 0
    var timeToSwap = 0

    val livingPlayers = mutableMapOf<UUID, PlayerHolder>()

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
        server.allPlayers.forEach { player ->
            val distance = Random.nextDouble(DeathSwapConfig.minSpreadDistance.value!!.toDouble(), DeathSwapConfig.maxSpreadDistance.value!!.toDouble())
            val x = (distance * cos(playerAngle)).toInt()
            val z = (distance * sin(playerAngle)).toInt()
            livingPlayers[player.uuid] = PlayerHolder(player, PlayerStartLocation(
                server.getWorld(RegistryKey.of(Registry.WORLD_KEY, DeathSwapConfig.dimension.value!!)) ?: server.getWorld(World.OVERWORLD)!!,
                x,
                z
            ))
            player.spawnLocation = player.location
            playerAngle += playerAngleChange
        }
        server.worlds.forEach { world ->
            world.setWeather(0, 0, false, false)
            world.timeOfDay = 0
        }

        swapCount = 0
        timeSinceLastSwap = 0
        timeToSwap = Random.nextInt(DeathSwapConfig.swapTime)
    }

    private fun removePlayer(player: UUID, reason: Text) {
        val holder = livingPlayers.remove(player) ?: return
        holder.server.broadcast(holder.displayName.copy().formatted(Formatting.GREEN).append(reason))
        if (livingPlayers.size < 2) {
            endGame(holder.server)
        }
    }

    fun removePlayer(player: ServerPlayerEntity) {
        player.world.spawnEntity(
            LightningEntity(
                EntityType.LIGHTNING_BOLT,
                player.world
            ).apply { setCosmetic(true) })
        resetPlayer(player, gamemode = GameMode.SPECTATOR)
        livingPlayers.remove(player.uuid)
        if (livingPlayers.size < 2) {
            endGame(player.server!!)
        }
    }

    fun endGame(server: MinecraftServer, natural: Boolean = true) {
        if (natural) {
            val winner = when (DeathSwapConfig.gameMode.value) {
                DeathSwapGameMode.ITEM_COUNT -> livingPlayers.values.maxBy { it.itemsCollected.size }
                else -> livingPlayers.values.firstOrNull()
            }
            val name = winner?.displayName ?: Text.literal("Nobody")

            server.broadcast(
                Text.literal("Game over! ")
                    .append(name.copy().formatted(Formatting.GREEN))
                    .append(" won")
            )
        }

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
            tickStartingPositions(server)
            return
        }

        if (timeSinceLastSwap > DeathSwapConfig.teleportLoadTime.value!!) {
            swapTargets.forEach { it.swap(livingPlayers.size > 2) }
            swapTargets.clear()
            state = GameState.STARTED
        }

        if (timeSinceLastSwap > timeToSwap) {
            beginSwap(server)
        }

        val withinWarnTime = timeToSwap - timeSinceLastSwap <= DeathSwapConfig.warnTime.value!!
        if (withinWarnTime || timeSinceLastSwap % 20 == 0) {
            var text = Text.literal(
                "Time since last swap: ${ticksToMinutesSeconds(timeSinceLastSwap)}"
            ).formatted(
                if (timeSinceLastSwap >= DeathSwapConfig.minSwapTime.value!!) Formatting.RED else Formatting.GREEN
            )

            if (DeathSwapConfig.gameMode.value?.limitedSwapCount == true) {
                text = Text.literal("Swaps: $swapCount/${DeathSwapConfig.swapLimit.value} | ")
                    .formatted(Formatting.YELLOW)
                    .append(text)
            }

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

                if (DeathSwapConfig.gameMode.value == DeathSwapGameMode.ITEM_COUNT) {
                    livingPlayers[player.uuid]?.let { holder ->
                        text2.append(
                            Text.literal(" | Items Obtained: ${holder.itemsCollected.size}")
                                .formatted(Formatting.YELLOW)
                        )
                    }
                }

                player.sendMessage(text2, true)
            }
        }
    }

    private fun tickStartingPositions(server: MinecraftServer) {
        if (livingPlayers.values.all { it.startLocation.tick() }) {
            livingPlayers.forEach { entry ->
                val loc = entry.value.startLocation
                val entity = entry.value.player
                if (entity == null) {
                    removePlayer(entry.key, Text.literal(" timed out during swap").formatted(Formatting.RED))
                } else {
                    resetPlayer(entity, includeInventory = true)
                    entity.addStatusEffect(
                        StatusEffectInstance(
                            StatusEffects.RESISTANCE,
                            DeathSwapConfig.resistanceTime.value!!,
                            255,
                            true,
                            false,
                            true
                        )
                    )
                    entity.teleport(
                        loc.world,
                        loc.x.toDouble(),
                        loc.y.toDouble(),
                        loc.z.toDouble(),
                        0f, 0f
                    )
                }
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
    }

    private fun beginSwap(server: MinecraftServer) {
        if (DeathSwapConfig.gameMode.value?.limitedSwapCount == true
            && swapCount++ >= (DeathSwapConfig.swapLimit.value ?: DeathSwapConfig.swapLimit.default!!)
        ) {
            endGame(server)
            return
        }

        server.broadcast("Swapping!")

        val shuffledPlayers = livingPlayers.entries.shuffled().mapNotNull { player ->
            val entity = player.value.player
            if (entity == null) {
                removePlayer(player.key, Text.literal(" timed out during swap").formatted(Formatting.RED))
            }
            entity
        }

        if (shuffledPlayers.size < 2) {
            return
        }

        swapTargets.clear()

        for (i in 1 until shuffledPlayers.size) {
            swapTargets.add(SwapForward(shuffledPlayers[i - 1], shuffledPlayers[i]))
        }
        swapTargets.add(SwapForward(shuffledPlayers.last(), shuffledPlayers[0]))
        state = GameState.TELEPORTING
        swapTargets.forEach { it.preSwap() }

        timeSinceLastSwap = 0
        timeToSwap = Random.nextInt(DeathSwapConfig.swapTime)
    }

    fun onInventoryChanged(player: ServerPlayerEntity, stack: ItemStack) {
        livingPlayers[player.uuid]?.let { holder ->
            holder.itemsCollected += stack.item
        }
    }
}

enum class DeathSwapGameMode(val allowDeath: Boolean, val limitedSwapCount: Boolean) : StringIdentifiable {
    NORMAL(false, false),
    ITEM_COUNT(true, true),
    ;

    private val id = name.lowercase()

    override fun asString() = id
}
