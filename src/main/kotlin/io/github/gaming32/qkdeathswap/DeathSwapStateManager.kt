package io.github.gaming32.qkdeathswap

import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandRuntimeException
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.StringRepresentable
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.entity.EntityTypeTest
import org.quiltmc.qkl.library.networking.allPlayers
import java.text.DecimalFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.random.nextInt

private val ONE_DIGIT_FORMAT = DecimalFormat("0.0")

enum class GameState {
    NOT_STARTED, STARTING, STARTED, TELEPORTING;
}

class PlayerHolder(serverPlayerEntity: ServerPlayer, var startLocation: PlayerStartLocation) {
    var displayName: Component = serverPlayerEntity.displayName
        get() {
            field = player?.displayName ?: field
            return field
        }
        private set
    private val uuid: UUID = serverPlayerEntity.uuid
    val server: MinecraftServer = serverPlayerEntity.server
    val player: ServerPlayer?
        get() = server.playerList.getPlayer(uuid)

    val itemsCollected = mutableSetOf<Item>()
    val itemsCrafted = mutableSetOf<Item>()
}

object DeathSwapStateManager {
    var state = GameState.NOT_STARTED
    private set

    var swapCount = 0
    var timeSinceLastSwap = 0
    var timeToSwap = 0

    val livingPlayers = mutableMapOf<UUID, PlayerHolder>()

    var shuffledPlayers: List<ServerPlayer>? = null
        private set
    private val swapTargets = mutableSetOf<SwapForward>()

    fun hasBegun(): Boolean {
        return state != GameState.NOT_STARTED
    }

    fun begin(server: MinecraftServer) {
        if (hasBegun()) {
            throw CommandRuntimeException(Component.literal("Game already begun"))
        }

        state = GameState.STARTING
        livingPlayers.clear()
        var playerAngle = Random.nextDouble(0.0, PI * 2)
        val playerAngleChange = PI * 2 / server.allPlayers.size
        server.allPlayers.forEach { player ->
            val distance = Random.nextDouble(DeathSwapConfig.minSpreadDistance.value.toDouble(), DeathSwapConfig.maxSpreadDistance.value.toDouble())
            val x = (distance * cos(playerAngle)).toInt()
            val z = (distance * sin(playerAngle)).toInt()
            livingPlayers[player.uuid] = PlayerHolder(player, PlayerStartLocation(
                server.getLevel(ResourceKey.create(Registries.DIMENSION, DeathSwapConfig.dimension.value!!)) ?: server.getLevel(Level.OVERWORLD)!!,
                x, z
            ))
            playerAngle += playerAngleChange
        }
        server.allLevels.forEach { world ->
            world.setWeatherParameters(0, 0, false, false)
            world.dayTime = 0
        }

        swapCount = 0
        timeSinceLastSwap = 0
        timeToSwap = Random.nextInt(DeathSwapConfig.swapTime)
    }

    private fun removePlayer(player: UUID, reason: Component) {
        val holder = livingPlayers.remove(player) ?: return
        holder.server.broadcast(holder.displayName.copy().withStyle(ChatFormatting.GREEN).append(reason))
        if (livingPlayers.size < 2) {
            endGame(holder.server)
        }
    }

    fun removePlayer(player: ServerPlayer) {
        player.level.addFreshEntity(
            LightningBolt(
                EntityType.LIGHTNING_BOLT,
                player.level
            ).apply { setVisualOnly(true) }
        )
        resetPlayer(player, gamemode = GameType.SPECTATOR)
        livingPlayers.remove(player.uuid)
        if (livingPlayers.size < 2) {
            endGame(player.server!!)
        }
    }

    fun endGame(server: MinecraftServer, natural: Boolean = true) {
        DeathSwapMod.swapMode.endMatch(server)

        if (natural) {
            val winner = when (DeathSwapConfig.gameMode.value) {
                DeathSwapGameMode.ITEM_COUNT -> livingPlayers.values.maxByOrNull { it.itemsCollected.size }
                else -> livingPlayers.values.firstOrNull()
            }
            val name = winner?.displayName ?: Component.literal("Nobody")

            server.broadcast(
                Component.literal("Game over! ")
                    .append(name.copy().withStyle(ChatFormatting.GREEN))
                    .append(" won")
            )
        }

        state = GameState.NOT_STARTED
        livingPlayers.clear()
        val destWorld = server.getLevel(Level.OVERWORLD)!!
        server.allPlayers.forEach { player ->
            player.teleport(destWorld.spawnLocation.copy(pitch = 0f))
            resetPlayer(player)
        }
    }

    fun resetPlayer(
        player: ServerPlayer,
        gamemode: GameType = GameType.SURVIVAL,
        includeInventory: Boolean = false
    ) {
        player.setGameMode(gamemode)
        player.health = player.maxHealth
        with(player.foodData) {
            foodLevel = 20
            setSaturation(5f)
            setExhaustion(0f)
        }
        if (includeInventory) {
            player.server.commands.performPrefixedCommand(player.server.createCommandSourceStack(), "advancement revoke ${player.scoreboardName} everything")
            player.experienceLevel = 0
            player.setExperiencePoints(0)
            player.inventory.copyFrom(DeathSwapConfig.defaultKit)
            player.enderChestInventory.removeAllItems()
        }
        player.setRespawnPosition(
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            null, // If pos is null, the rest of the arguments are ignored
            null, 0f, false, false
        )
        player.removeAllEffects()
    }

    fun tick(server: MinecraftServer) {
        timeSinceLastSwap++

        if (state == GameState.STARTING) {
            tickStartingPositions(server)
            return
        }

        val shouldSwap = if (DeathSwapMod.swapMode.preSwapHappensAtPrepare) {
            timeSinceLastSwap > DeathSwapConfig.teleportLoadTime.value && state == GameState.TELEPORTING
        } else {
            timeSinceLastSwap > timeToSwap
        }
        if (shouldSwap) {
            if (!DeathSwapMod.swapMode.preSwapHappensAtPrepare) {
                preSwap(server)
            }
            DeathSwapMod.swapMode.beforeSwap(server)
            swapTargets.forEach { it.swap(livingPlayers.size > 2) }
            swapTargets.clear()
            shuffledPlayers = null
            state = GameState.STARTED
            if (!DeathSwapMod.swapMode.preSwapHappensAtPrepare) {
                endSwap()
            }
        }

        var beginTime = timeToSwap
        if (!DeathSwapMod.swapMode.preSwapHappensAtPrepare) {
            beginTime -= DeathSwapConfig.teleportLoadTime.value
        }
        if (timeSinceLastSwap > beginTime) {
            prepareSwap(server)
        }

        val withinWarnTime = timeToSwap - timeSinceLastSwap < DeathSwapConfig.warnTime.value
        if (withinWarnTime || timeSinceLastSwap % 20 == 0) {
            var text = Component.literal(
                "Time since last swap: ${ticksToMinutesSeconds(timeSinceLastSwap)}"
            ).withStyle(
                if (timeSinceLastSwap >= DeathSwapConfig.minSwapTime.value) ChatFormatting.RED else ChatFormatting.GREEN
            )

            if (DeathSwapConfig.gameMode.value.limitedSwapCount) {
                text = Component.literal("Swaps: $swapCount/${DeathSwapConfig.swapLimit.value} | ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(text)
            }

            server.allPlayers.forEach { player ->
                val text2 = text.copy()
                if (player.isSpectator) {
                    text2.append(Component.literal("/${ticksToMinutesSeconds(timeToSwap)}").withStyle(ChatFormatting.YELLOW))
                }
                if (withinWarnTime) {
                    text2.append(
                        Component.literal("  Swap in ${ONE_DIGIT_FORMAT.format((timeToSwap - timeSinceLastSwap) / 20.0)} seconds")
                            .withStyle(ChatFormatting.DARK_RED)
                    )
                }

                if (DeathSwapConfig.gameMode.value == DeathSwapGameMode.ITEM_COUNT) {
                    livingPlayers[player.uuid]?.let { holder ->
                        text2.append(
                            Component.literal(" | Items Obtained: ${holder.itemsCollected.size}")
                                .withStyle(ChatFormatting.YELLOW)
                        )
                    }
                }

                player.displayClientMessage(text2, true)
            }
        }
    }

    private fun tickStartingPositions(server: MinecraftServer) {
        if (livingPlayers.values.all { it.startLocation.tick() }) {
            livingPlayers.forEach { entry ->
                val loc = entry.value.startLocation
                val entity = entry.value.player
                if (entity == null) {
                    removePlayer(entry.key, Component.literal(" timed out during swap").withStyle(ChatFormatting.RED))
                } else {
                    resetPlayer(entity, includeInventory = true)
                    entity.addEffect(
                        MobEffectInstance(
                            MobEffects.DAMAGE_RESISTANCE,
                            DeathSwapConfig.resistanceTime.value,
                            255,
                            true,
                            false,
                            true
                        )
                    )
                    entity.teleportTo(
                        loc.world,
                        loc.x.toDouble(),
                        loc.y.toDouble(),
                        loc.z.toDouble(),
                        0f, 0f
                    )

                    entity.spawnLocation = entity.location
                }
            }
            timeSinceLastSwap = 0
            state = GameState.STARTED
        }
        if (timeSinceLastSwap % 20 == 0) {
            val starting = Component.literal("Finding start locations: ").append(Component.literal(ticksToMinutesSeconds(timeSinceLastSwap)).withStyle(ChatFormatting.YELLOW))
            server.allPlayers.forEach { player ->
                player.displayClientMessage(starting, true)
            }
        }
    }

    private fun prepareSwap(server: MinecraftServer) {
        val shuffledPlayers = livingPlayers.entries.shuffled().mapNotNull { player ->
            val entity = player.value.player
            if (entity == null) {
                removePlayer(player.key, Component.literal(" timed out during swap").withStyle(ChatFormatting.RED))
            }
            entity
        }
        this.shuffledPlayers = shuffledPlayers

        if (DeathSwapMod.swapMode.preSwapHappensAtPrepare) {
            preSwap(server)
        }
        DeathSwapMod.swapMode.prepareSwap(server)

        if (DeathSwapMod.swapMode.preSwapHappensAtPrepare) {
            endSwap()
        }
    }

    private fun endSwap() {
        timeSinceLastSwap = 0
        timeToSwap = Random.nextInt(DeathSwapConfig.swapTime)
    }

    private fun preSwap(server: MinecraftServer) {
        if (DeathSwapConfig.gameMode.value.limitedSwapCount && swapCount++ >= (DeathSwapConfig.swapLimit.value)) {
            endGame(server)
            return
        }

        server.broadcast("Swapping!")

        if (DeathSwapConfig.destroyItemsDuringSwap.value) {
            for (world in server.allLevels) {
                for (entity in world.getEntities(EntityTypeTest.forClass(ItemEntity::class.java)) { true }) {
                    entity.discard()
                }
            }
        }

        val shuffledPlayers = this.shuffledPlayers!!

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
    }

    fun onInventoryChanged(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) {
            return
        }

        livingPlayers[player.uuid]?.let { holder ->
            if (!DeathSwapConfig.craftingCountsTowardsItemCount.value && stack.item in holder.itemsCrafted) {
                return@let
            }
            holder.itemsCollected += stack.item
            holder.player?.scoreboard
                ?.forAllObjectives(DeathSwapMod.itemCountCriterion, holder.player!!.scoreboardName) {
                    it.score = holder.itemsCollected.size
                }
        }
    }

    fun onCraft(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) {
            return
        }

        livingPlayers[player.uuid]?.let { holder ->
            holder.itemsCrafted += stack.item
        }
    }
}

enum class DeathSwapGameMode(val allowDeath: Boolean, val limitedSwapCount: Boolean) : StringRepresentable {
    NORMAL(false, false),
    ITEM_COUNT(true, true),
    ;

    private val id = name.lowercase()
    private val presentableName = id.replaceFirstChar { it.uppercaseChar() }.replace('_', ' ')

    override fun getSerializedName() = id

    override fun toString() = presentableName
}
