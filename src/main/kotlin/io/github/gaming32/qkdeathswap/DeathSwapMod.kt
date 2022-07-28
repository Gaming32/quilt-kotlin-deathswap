package io.github.gaming32.qkdeathswap

import com.mojang.brigadier.arguments.ArgumentType
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.ALLOW_DEATH
import net.minecraft.command.CommandException
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtList
import net.minecraft.network.MessageType
import net.minecraft.scoreboard.AbstractTeam.VisibilityRule
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.ScreenHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.GameMode
import net.minecraft.world.GameRules
import org.quiltmc.config.api.values.TrackedValue
import org.quiltmc.loader.api.ModContainer
import org.quiltmc.loader.api.QuiltLoader
import org.quiltmc.qkl.wrapper.minecraft.brigadier.*
import org.quiltmc.qkl.wrapper.minecraft.brigadier.argument.literal
import org.quiltmc.qkl.wrapper.minecraft.brigadier.argument.player
import org.quiltmc.qkl.wrapper.minecraft.brigadier.argument.value
import org.quiltmc.qkl.wrapper.qsl.commands.onCommandRegistration
import org.quiltmc.qkl.wrapper.qsl.lifecycle.onServerTickEnd
import org.quiltmc.qkl.wrapper.qsl.networking.allPlayers
import org.quiltmc.qkl.wrapper.qsl.networking.onPlayReady
import org.quiltmc.qkl.wrapper.qsl.registerEvents
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

const val MOD_ID = "qkdeathswap"

object DeathSwapMod : ModInitializer {
    @JvmField val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
    val defaultKitStoreLocation: File = QuiltLoader.getConfigDir()
        .resolve(MOD_ID)
        .resolve("default_kit.dat")
        .toFile()

    override fun onInitialize(mod: ModContainer) {
        if (defaultKitStoreLocation.exists()) {
            DeathSwapConfig.defaultKit.readNbt(
                NbtIo.readCompressed(defaultKitStoreLocation)
                    .getList("Inventory", NbtElement.COMPOUND_TYPE.toInt())
            )
        }

        registerEvents {
            onCommandRegistration { buildContext, environment ->
                register("deathswap") {
                    requires { it.hasPermissionLevel(1) }
                    required(literal("start")) {
                        execute {
                            try {
                                if (!QuiltLoader.isDevelopmentEnvironment() && source.server.allPlayers.size < 2) {
                                    throw CommandException(Text.literal("Cannot start a DeathSwap with less than 2 players."))
                                }
                                DeathSwapStateManager.begin(source.server)
                                source.server.broadcast("Deathswap started!")
                            } catch (e: Exception) {
                                LOGGER.error("Error starting DeathSwap", e)
                                throw e
                            }
                        }
                    }
                    required(literal("stop")) {
                        execute {
                            DeathSwapStateManager.endGame(source.server)
                            source.server.broadcast("Deathswap ended!")
                        }
                    }
                    required(literal("config")) {
                        DeathSwapConfig.CONFIG.values().forEach { option ->
                            if (option.key() !in DeathSwapConfig.CONFIG_TYPES) return@forEach
                            required(literal(option.key().toString())) {
                                execute {
                                    source.sendFeedback(Text.literal("${option.key()} -> ${option.value()}"), false)
                                }
                                val valueType = DeathSwapConfig.CONFIG_TYPES[option.key()]!!
                                required(argument("value", valueType.first as ArgumentType)) {
                                    @Suppress("UNCHECKED_CAST")
                                    execute {
                                        val newValue = (valueType.second as (Any) -> Any)(getArgument("value", Any::class.java))
                                        (option as TrackedValue<Any>).setValue(newValue, true)
                                        source.sendFeedback(Text.literal("Successfully set ${option.key()} to $newValue"), true)
                                    }
                                }
                            }
                        }
                        execute {
                            val result = Text.literal("Here are all the current config values:")
                            DeathSwapConfig.CONFIG.values().forEach { option ->
                                val name = option.key().toString()
                                result.append("\n$name -> ${DeathSwapConfig.CONFIG.getValue(option.key()).value()}")
                            }
                            source.sendFeedback(result, false)
                        }
                    }
                    required(literal("default_kit")) {
                        required(literal("clear")) {
                            execute {
                                DeathSwapConfig.defaultKit.clear()
                                NbtIo.writeCompressed(NbtCompound().apply {
                                    put("Inventory", NbtList())
                                }, defaultKitStoreLocation)
                                source.sendFeedback(Text.literal("Cleared the default kit"), true)
                            }
                        }
                        required(literal("set_from_player")) {
                            optional(player("player")) { player ->
                                execute {
                                    val actualPlayer = player?.invoke(this)?.value() ?: source.player
                                    DeathSwapConfig.defaultKit.copyFrom(actualPlayer.inventory)
                                    NbtIo.writeCompressed(NbtCompound().apply {
                                        put("Inventory", NbtList().apply {
                                            actualPlayer.inventory.writeNbt(this)
                                        })
                                    }, defaultKitStoreLocation)
                                    source.sendFeedback(
                                        if (actualPlayer === source) {
                                            Text.literal("Set the default kit to your current inventory")
                                        } else {
                                            Text.literal("Set the default kit to ")
                                                .append(actualPlayer.displayName)
                                                .append("'s current inventory")
                                        }, true
                                    )
                                }
                            }
                        }
                        required(literal("load")) {
                            execute {
                                source.player.inventory.copyFrom(DeathSwapConfig.defaultKit)
                            }
                        }
                        required(literal("view")) {
                            execute {
                                source.player.openHandledScreen(object : NamedScreenHandlerFactory {
                                    override fun createMenu(
                                        syncId: Int,
                                        playerInventory: PlayerInventory,
                                        playerEntity: PlayerEntity
                                    ): ScreenHandler {
                                        val screenHandler = GenericContainerScreenHandler.createGeneric9x5(syncId, playerInventory)
                                        val kit = DeathSwapConfig.defaultKit
                                        for (i in 0 until 4) {
                                            for (j in 0 until 9) {
                                                screenHandler.inventory.setStack(
                                                    (4 - i) * 9 + j,
                                                    kit.getStack(i * 9 + j).copy()
                                                )
                                            }
                                        }
                                        for (i in 0 until 5) {
                                            screenHandler.inventory.setStack(i, kit.getStack(36 + i).copy())
                                        }
                                        return screenHandler
                                    }

                                    override fun getDisplayName(): Text {
                                        return Text.literal("Default kit ")
                                            .append(
                                                Text.literal("(not editable)")
                                                    .formatted(Formatting.GOLD)
                                            )
                                    }
                                })
                            }
                        }
                    }
                    if (DeathSwapConfig.enableDebug) {
                        required(literal("debug")) {
                            required(literal("swap_now")) {
                                execute {
                                    DeathSwapStateManager.timeToSwap = DeathSwapStateManager.timeSinceLastSwap
                                }
                            }
                            required(literal("swap_at")) {
                                execute {
                                    source.sendFeedback(Text.literal(
                                        "Will swap at: ${ticksToMinutesSeconds(DeathSwapStateManager.timeToSwap)}"
                                    ), false)
                                }
                            }
                        }
                    }
                }
            }

            ALLOW_DEATH.register { player, source, amount ->
                if (!DeathSwapStateManager.hasBegun()) {
                    return@register true
                }
                val shouldCancelDeathScreen = DeathSwapStateManager.livingPlayers.size > 2
                if (shouldCancelDeathScreen) {
                    // Copy-paste (and cleanup/Kotlin conversion) from ServerPlayerEntity#onDeath
                    if (player.world.gameRules.getBoolean(GameRules.SHOW_DEATH_MESSAGES)) {
                        val text = player.damageTracker.deathMessage
                        val abstractTeam = player.scoreboardTeam
                        if (abstractTeam == null || abstractTeam.deathMessageVisibilityRule == VisibilityRule.ALWAYS) {
                            player.server.playerManager.broadcastSystemMessage(text, MessageType.SYSTEM)
                        } else if (abstractTeam.deathMessageVisibilityRule == VisibilityRule.HIDE_FOR_OTHER_TEAMS) {
                            player.server.playerManager.sendSystemMessageToTeam(player, text)
                        } else if (abstractTeam.deathMessageVisibilityRule == VisibilityRule.HIDE_FOR_OWN_TEAM) {
                            player.server.playerManager.sendSystemMessageToOtherTeams(player, text)
                        }
                    }
                }
                DeathSwapStateManager.removePlayer(player)
                !shouldCancelDeathScreen
            }

            onServerTickEnd {
                if (DeathSwapStateManager.hasBegun()) {
                    DeathSwapStateManager.tick(this)
                }
            }

            onPlayReady { packetSender, server ->
                if (DeathSwapStateManager.hasBegun()) {
                    DeathSwapStateManager.resetPlayer(player, gamemode = GameMode.SPECTATOR)
                }
            }
        }

        LOGGER.info("$MOD_ID initialized!")
    }

    fun onPlayDoneDisconnecting(player: ServerPlayerEntity) {
        if (DeathSwapStateManager.hasBegun()) {
            DeathSwapStateManager.removePlayer(player, strikeLightning = false)
        }
    }
}
