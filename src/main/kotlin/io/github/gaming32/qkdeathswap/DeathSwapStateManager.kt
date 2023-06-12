package io.github.gaming32.qkdeathswap

import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandRuntimeException
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.StringRepresentable
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.entity.EntityTypeTest
import net.minecraft.world.level.levelgen.WorldOptions
import org.quiltmc.qkl.library.networking.allPlayers
import org.quiltmc.qkl.library.networking.playersTracking
import xyz.nucleoid.fantasy.Fantasy
import xyz.nucleoid.fantasy.RuntimeWorldConfig
import xyz.nucleoid.fantasy.RuntimeWorldHandle
import java.text.DecimalFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.milliseconds

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

    var spawnSearchStart = 0L

    var swapCount = 0
    var timeSinceLastSwap = 0
    var timeToSwap = 0

    val livingPlayers = mutableMapOf<UUID, PlayerHolder>()

    var shuffledPlayers: List<ServerPlayer>? = null
        private set
    private val swapTargets = mutableSetOf<SwapForward>()

    val isCreatingFantasyWorld = ThreadLocal.withInitial { false }!!
    var fantasyWorld: RuntimeWorldHandle? = null

    fun begin(server: MinecraftServer) {
        if (state > GameState.NOT_STARTED) {
            throw CommandRuntimeException(Component.literal("Game already begun"))
        }

        var levelToUse = server.getLevel(
            ResourceKey.create(Registries.DIMENSION, DeathSwapConfig.dimension.value!!)
        ) ?: server.overworld()

        fantasyWorld?.delete()
        if (DeathSwapConfig.fantasyEnabled.value) {
            isCreatingFantasyWorld.set(true)
            fantasyWorld = Fantasy.get(server).openTemporaryWorld(
                RuntimeWorldConfig()
                    .setSeed(WorldOptions.randomSeed())
                    .setShouldTickTime(true)
                    .setDimensionType(levelToUse.dimensionTypeRegistration())
                    .setDifficulty(DeathSwapConfig.fantasyDifficulty.value)
                    .setGenerator(levelToUse.chunkSource.generator)
            ).also { levelToUse = it.asWorld() }
            isCreatingFantasyWorld.set(false)
            DeathSwapMod.swapMode.dimensionsCreated(server)
        }

        spawnSearchStart = System.currentTimeMillis()
        timeSinceLastSwap = 0
        state = GameState.STARTING
        livingPlayers.clear()
        var playerAngle = Random.nextDouble(0.0, PI * 2)
        val playerAngleChange = PI * 2 / server.allPlayers.size
        server.allPlayers.forEach { player ->
            val distance = Random.nextDouble(DeathSwapConfig.minSpreadDistance.value.toDouble(), DeathSwapConfig.maxSpreadDistance.value.toDouble())
            val x = (distance * cos(playerAngle)).toInt()
            val z = (distance * sin(playerAngle)).toInt()
            livingPlayers[player.uuid] = PlayerHolder(player, PlayerStartLocation(levelToUse, x, z))
            playerAngle += playerAngleChange
//            player.setGameMode(GameType.SPECTATOR)
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
            ).apply {
                setPos(player.position())
                setVisualOnly(true)
            }
        )
        val tracking = player.playersTracking
        player.server.allPlayers.forEach {
            if (it == player || it in tracking) return@forEach // It already played for them
            it.playNotifySound(
                SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER,
                10000.0f,
                0.8f + player.level.random.nextFloat() * 0.2f
            )
        }
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
        val destWorld = server.overworld()
        server.allPlayers.forEach { player ->
            player.teleport(destWorld.spawnLocation.copy(pitch = 0f))
            resetPlayer(player)
        }

        fantasyWorld?.delete()
        fantasyWorld = null
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
        player.wardenSpawnTracker.get().reset()
        player.fallDistance = 0f
        player.connection.send(ClientboundContainerClosePacket(0))
    }

    fun tick(server: MinecraftServer) {
        if (state == GameState.STARTING) {
            tickStartingPositions(server)
            return
        }
        timeSinceLastSwap++

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
        DeathSwapMod.LOGGER.info("Finding starting positions tick #{}", ++timeSinceLastSwap)
        var success = false
        for (i in 1..DeathSwapConfig.mainThreadWeight.value) {
            if (livingPlayers.values.all { it.startLocation.tick() }) {
                success = true
                break
            }
        }
        if (success) {
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
                    val spawnPos = loc.getPos()
                    entity.teleportTo(
                        loc.level,
                        spawnPos.x + 0.5,
                        spawnPos.y.toDouble(),
                        spawnPos.z + 0.5,
                        0f, 0f
                    )

                    entity.spawnLocation = entity.location
                }
            }
            timeSinceLastSwap = 0
            if (state == GameState.STARTING) {
                // If all the players logged off and were removed, the game will be in a NOT_STARTED state.
                state = GameState.STARTED
            }
            return
        }
        val timeSearchingForSpawn = (System.currentTimeMillis() - spawnSearchStart).milliseconds
        val starting = Component.literal("Finding start locations: ")
            .append(Component.literal(
                "${timeSearchingForSpawn.inWholeMinutes}:" +
                        (timeSearchingForSpawn.inWholeSeconds % 60).toString().padStart(2, '0')
            ).withStyle(ChatFormatting.YELLOW)
        )
        server.allPlayers.forEach { player ->
            player.displayClientMessage(starting, true)
        }
        if (timeSearchingForSpawn > (DeathSwapConfig.maxStartFindTime.value * 50).milliseconds) {
            server.broadcast(
                Component.literal("Took too long to find start locations! Going with what we've got.")
                    .withStyle(ChatFormatting.RED)
            )
            livingPlayers.values.forEach { it.startLocation.forceFinalize() }
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

    companion object {
        val codec = StringRepresentable.fromEnum(::values)!!

        fun byName(name: String) = codec.byName(name)
    }

    private val id = name.lowercase()
    private val presentableName = id.replaceFirstChar { it.uppercaseChar() }.replace('_', ' ')

    override fun getSerializedName() = id

    override fun toString() = presentableName
}
