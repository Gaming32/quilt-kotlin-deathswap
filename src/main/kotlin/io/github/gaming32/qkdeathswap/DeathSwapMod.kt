package io.github.gaming32.qkdeathswap

import io.github.gaming32.qkdeathswap.DeathSwapConfig.DeathSwapConfigStatic.writeDefaultKit
import io.github.gaming32.qkdeathswap.map.drawImage
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandRuntimeException
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.GameType
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import org.quiltmc.loader.api.ModContainer
import org.quiltmc.loader.api.QuiltLoader
import org.quiltmc.qkl.library.brigadier.*
import org.quiltmc.qkl.library.brigadier.argument.*
import org.quiltmc.qkl.library.commands.onCommandRegistration
import org.quiltmc.qkl.library.lifecycle.onServerTickEnd
import org.quiltmc.qkl.library.networking.allPlayers
import org.quiltmc.qkl.library.networking.onPlayReady
import org.quiltmc.qkl.library.registerEvents
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer
import org.quiltmc.qsl.entity.event.api.EntityWorldChangeEvents.AFTER_PLAYER_WORLD_CHANGE
import org.quiltmc.qsl.entity.event.api.LivingEntityDeathCallback
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import kotlin.io.path.createDirectories
import kotlin.io.path.div

const val MOD_ID = "qkdeathswap"

object DeathSwapMod : ModInitializer {
    val modContainer = QuiltLoader.getModContainer(MOD_ID).get()

    @JvmField val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    val configDir = QuiltLoader.getConfigDir() / MOD_ID
    val configFile = configDir / "deathswap.toml"

    val cacheDir = QuiltLoader.getGameDir() / "cache" / MOD_ID

    val presetsDir = configDir / "presets"

    val defaultKitStoreLocation: File = (configDir / "default_kit.dat").toFile()

    val itemCountCriterion = ObjectiveCriteria.registerCustom("$MOD_ID:item_count")!!

    val swapMode = if (QuiltLoader.isModLoaded("imm_ptl_core")) SwapMode.ImmersivePortals else SwapMode.Simple

    override fun onInitialize(mod: ModContainer) {
        configDir.createDirectories()
        cacheDir.createDirectories()
        presetsDir.createDirectories()

        registerEvents {
            onCommandRegistration { _, _ ->
                register("deathswap") {
                    requires { it.hasPermission(2) }
                    required(literal("start")) {
                        executeWithResult {
                            try {
                                if (!QuiltLoader.isDevelopmentEnvironment() && source.server.allPlayers.size < 2) {
                                    throw CommandRuntimeException(Component.literal("Cannot start a DeathSwap with less than 2 players."))
                                }
                                DeathSwapStateManager.begin(source.server)
                                source.server.broadcast("Deathswap started!")
                                CommandResult.success()
                            } catch (e: Exception) {
                                LOGGER.error("Error starting DeathSwap", e)
                                if (e is CommandRuntimeException) {
                                    throw e
                                }
                                CommandResult.failure(Component.literal("Failed to start deathswap: ${e.message}"))
                            }
                        }
                    }
                    required(literal("stop")) {
                        execute {
                            DeathSwapStateManager.endGame(source.server, natural = false)
                            source.server.broadcast("Deathswap ended!")
                        }
                    }
                    required(literal("config")) {
                        DeathSwapConfig.buildArguments(this)
                    }
                    required(literal("presets")) {
                        required(literal("load")) {
                            required(string("preset")) { getPreset ->
                                suggests { _, builder ->
                                    SharedSuggestionProvider.suggest(
                                        Presets.list(),
                                        builder
                                    )
                                }
                                execute {
                                    val preset = getPreset().value()
                                    if (Presets.load(preset)) {
                                        source.sendSuccess({ Component.literal("Successfully loaded preset ").append(Component.literal(preset).withStyle(ChatFormatting.GREEN)) }, true)
                                    } else {
                                        source.sendSuccess({ Component.literal("Failed to load preset ").append(Component.literal(preset).withStyle(ChatFormatting.RED)) }, false)
                                    }
                                }
                            }
                        }
                        required(literal("delete")) {
                            required(string("preset")) { getPreset ->
                                suggests { _, builder ->
                                    SharedSuggestionProvider.suggest(
                                        Presets.list(),
                                        builder
                                    )
                                }
                                execute {
                                    val preset = getPreset().value()
                                    if (Presets.delete(preset)) {
                                        source.sendSuccess({ Component.literal("Successfully deleted preset ").append(Component.literal(preset).withStyle(ChatFormatting.GREEN)) }, true)
                                    } else {
                                        if (preset in Presets.builtin) {
                                            source.sendSuccess({ Component.literal("Cannot delete builtin preset ").append(Component.literal(preset).withStyle(ChatFormatting.RED)) }, true)
                                        } else {
                                            source.sendSuccess({ Component.literal("Failed to delete preset ").append(Component.literal(preset).withStyle(ChatFormatting.RED)).append(Component.literal(". does it exist?")) }, false)
                                        }
                                    }
                                }
                            }
                        }
                        required(literal("save")) {
                            required(string("preset")) { getPreset ->
                                execute {
                                    val preset = getPreset().value()
                                    try {
                                        Presets.save(preset)
                                        source.sendSuccess(
                                            { Component.literal("Successfully saved preset ")
                                                .append(Component.literal(preset).withStyle(ChatFormatting.GREEN)) }, true
                                        )
                                    } catch (e: IOException) {
                                        source.sendSuccess(
                                            { Component.literal("Failed to save preset ")
                                                .append(Component.literal(preset).withStyle(ChatFormatting.RED))
                                                .append(Component.literal(". ").append(Component.literal(e.message ?: "").withStyle(ChatFormatting.RED))) }, false
                                        )
                                    }
                                }
                            }
                        }
                        required(literal("preview")) {
                            required(string("preset")) { getPreset ->
                                suggests { _, builder ->
                                    SharedSuggestionProvider.suggest(
                                        Presets.list(),
                                        builder
                                    )
                                }
                                optional(literal("kit")) { getKit ->
                                    execute {
                                        val preset = getPreset().value()
                                        if (getKit != null) {
                                            val kit = Presets.previewKit(preset)
                                            if (kit != null) {
                                                viewKit(source.playerOrException, kit)
                                            } else {
                                                source.sendSuccess({ Component.literal("No kit found for preset ").append(Component.literal(preset).withStyle(ChatFormatting.RED)).append(Component.literal(", or preset doesn't exist.")) }, false)
                                            }
                                        } else {
                                            val values = Presets.preview(preset)
                                            if (values != null) {
                                                source.sendSuccess(
                                                    { Component.literal("Previewing preset ")
                                                        .append(Component.literal(preset).withStyle(ChatFormatting.GREEN))
                                                        .append(Component.literal(":")) }, true
                                                )
                                                source.sendSuccess({ values }, false)
                                            } else {
                                                source.sendSuccess({ Component.literal("No preset found for ").append(Component.literal(preset).withStyle(ChatFormatting.RED)) }, false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    required(literal("default_kit")) {
                        required(literal("clear")) {
                            execute {
                                DeathSwapConfig.defaultKit.clearContent()
                                NbtIo.writeCompressed(CompoundTag().apply {
                                    put("Inventory", ListTag())
                                }, defaultKitStoreLocation)
                                source.sendSuccess({ Component.literal("Cleared the default kit") }, true)
                            }
                        }
                        required(literal("set_from_player")) {
                            optional(player("player")) { getPlayer ->
                                execute {
                                    val actualPlayer = getPlayer?.invoke(this)?.value() ?: source.playerOrException
                                    DeathSwapConfig.defaultKit.copyFrom(actualPlayer.inventory)
                                    DeathSwapConfig.writeDefaultKit()
                                    source.sendSuccess(
                                        { if (actualPlayer === source) {
                                            Component.literal("Set the default kit to your current inventory")
                                        } else {
                                            Component.literal("Set the default kit to ")
                                                .append(actualPlayer.displayName)
                                                .append("'s current inventory")
                                        } }, true
                                    )
                                }
                            }
                        }
                        required(literal("load")) {
                            execute {
                                source.playerOrException.inventory.copyFrom(DeathSwapConfig.defaultKit)
                            }
                        }
                        required(literal("view")) {
                            execute {
                                viewKit(source.playerOrException, DeathSwapConfig.defaultKit)
                            }
                        }
                    }
                    required(literal("reloadtextures")) {
                        execute {
                            ResourcePackManager.reloadTextures()
                            source.sendSuccess({ Component.literal("Reloaded textures") }, true)
                        }
                    }
                    if (DeathSwapConfig.enableDebug.value) {
                        required(literal("debug")) {
                            required(literal("swap_now")) {
                                execute {
                                    DeathSwapStateManager.timeSinceLastSwap = DeathSwapStateManager.timeToSwap - 1
                                }
                            }
                            required(literal("swap_at")) {
                                execute {
                                    source.sendSuccess({ Component.literal(
                                        "Will swap at: ${ticksToMinutesSeconds(DeathSwapStateManager.timeToSwap)}"
                                    ) }, false)
                                }
                            }
                            required(literal("texture_map")) {
                                required(identifier("texture")) { getTexture ->
                                    execute {
                                        val player = source.playerOrException
                                        val stack = MapItem.create(player.serverLevel(), 0, 0, 0, false, false)
                                        MapItem.lockMap(player.serverLevel(), stack)
                                        player.inventory.add(stack)
                                        val image = ResourcePackManager.getTexture(getTexture().value())
                                        MapItem.getSavedData(stack, player.serverLevel())!!
                                            .drawImage(image, scale = 128 / maxOf(image.width, image.height))
                                        player.inventoryMenu.broadcastChanges()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            LivingEntityDeathCallback.EVENT.register { entity, _ ->
                if (entity !is ServerPlayer || DeathSwapStateManager.state == GameState.NOT_STARTED) return@register
                if (DeathSwapStateManager.state == GameState.STARTING) {
                    val newEntity = entity.server.playerList.respawn(entity, true)
                    newEntity.health = newEntity.maxHealth
                } else {
                    DeathSwapStateManager.removePlayer(entity)
                }
            }

            onServerTickEnd {
                if (DeathSwapStateManager.state > GameState.NOT_STARTED) {
                    DeathSwapStateManager.tick(this)
                }
            }

            onPlayReady { _, _ ->
                if (DeathSwapStateManager.state > GameState.NOT_STARTED && !DeathSwapStateManager.livingPlayers.containsKey(player.uuid)) {
                    DeathSwapStateManager.resetPlayer(player, gamemode = GameType.SPECTATOR)
                }
            }

            AFTER_PLAYER_WORLD_CHANGE.register { player, origin, destination ->
                if (
                    DeathSwapStateManager.state >= GameState.STARTED &&
                    origin.dimensionTypeId() == BuiltinDimensionTypes.END &&
                    DeathSwapStateManager.livingPlayers.containsKey(player.uuid)
                ) {
                    // Teleport player, so they aren't at spawn in the overworld
                    val holder = DeathSwapStateManager.livingPlayers[player.uuid]
                    if (holder != null) {
                        val loc = holder.startLocation
                        if (holder.startLocation.level == origin) {
                            var spawnPos = loc.getPos()
                            val newLoc = PlayerStartLocation(destination, spawnPos.x, spawnPos.z)
                            val startTime = System.currentTimeMillis()
                            while (!newLoc.tick()) {
                                val searchTime = System.currentTimeMillis() - startTime
                                if (searchTime > DeathSwapConfig.maxStartFindTime.value * 50) {
                                    player.sendSystemMessage(
                                        Component.literal(
                                            "Took too long to find a respawn location! Going with what we've got."
                                        ).withStyle(ChatFormatting.RED)
                                    )
                                    newLoc.forceFinalize()
                                }
                            }
                            spawnPos = newLoc.getPos()
                            player.teleportTo(
                                newLoc.level,
                                spawnPos.x.toDouble(),
                                spawnPos.y.toDouble(),
                                spawnPos.z.toDouble(),
                                0f, 0f
                            )
                            player.connection.resetPosition()
                        }
                        // else is handled by the setspawn code on itemcount's branch
                    }
                }
            }

        }

        LOGGER.info("$MOD_ID initialized!")
    }

    fun viewKit(player: ServerPlayer, kit: Inventory) {
        player.openMenu(object : MenuProvider {
            override fun createMenu(
                syncId: Int,
                playerInventory: Inventory,
                playerEntity: Player
            ): AbstractContainerMenu {
                val screenHandler = object : ChestMenu(
                    MenuType.GENERIC_9x5,
                    syncId,
                    playerInventory,
                    SimpleContainer(9 * 5),
                    5
                ) {
                    override fun clicked(slotIndex: Int, button: Int, actionType: ClickType, player: Player) {
                        if (slotIndex in 5 until 9) {
                            carried = ItemStack.EMPTY
                            broadcastFullState()
                            return
                        }
                        super.clicked(slotIndex, button, actionType, player)
                    }

                    override fun removed(player: Player) {
                        for (i in 0 until 4) {
                            for (j in 0 until 9) {
                                kit.setItem(
                                    i * 9 + j,
                                    getSlot((4 - i) * 9 + j).item.copy()
                                )
                            }
                        }
                        for (i in 0 until 5) {
                            kit.setItem(36 + i, getSlot(i).item.copy())
                        }
                        if (kit == DeathSwapConfig.defaultKit) {
                            writeDefaultKit()
                        }
                    }
                }
                for (i in 0 until 4) {
                    for (j in 0 until 9) {
                        screenHandler.container.setItem(
                            (4 - i) * 9 + j,
                            kit.getItem(i * 9 + j).copy()
                        )
                    }
                }
                for (i in 0 until 5) {
                    screenHandler.container.setItem(i, kit.getItem(36 + i).copy())
                }
                for (i in 5 until 9) {
                    screenHandler.container.setItem(i, ItemStack(Items.BARRIER))
                }
                return screenHandler
            }

            override fun getDisplayName(): Component = Component.literal("Default kit")
        })
    }
}
