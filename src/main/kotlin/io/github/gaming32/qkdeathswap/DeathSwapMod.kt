package io.github.gaming32.qkdeathswap

import com.mojang.brigadier.arguments.ArgumentType
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.ALLOW_DEATH
import net.minecraft.command.CommandException
import net.minecraft.network.MessageType
import net.minecraft.scoreboard.AbstractTeam.VisibilityRule
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.world.GameMode
import net.minecraft.world.GameRules
import org.quiltmc.config.api.values.TrackedValue
import org.quiltmc.loader.api.ModContainer
import org.quiltmc.loader.api.QuiltLoader
import org.quiltmc.qkl.wrapper.minecraft.brigadier.argument
import org.quiltmc.qkl.wrapper.minecraft.brigadier.argument.literal
import org.quiltmc.qkl.wrapper.minecraft.brigadier.register
import org.quiltmc.qkl.wrapper.minecraft.brigadier.required
import org.quiltmc.qkl.wrapper.qsl.commands.onCommandRegistration
import org.quiltmc.qkl.wrapper.qsl.lifecycle.onServerTickEnd
import org.quiltmc.qkl.wrapper.qsl.networking.allPlayers
import org.quiltmc.qkl.wrapper.qsl.networking.onPlayDisconnect
import org.quiltmc.qkl.wrapper.qsl.networking.onPlayReady
import org.quiltmc.qkl.wrapper.qsl.registerEvents
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object DeathSwapMod : ModInitializer {
    @JvmField val LOGGER: Logger = LoggerFactory.getLogger("qkdeathswap")

    override fun onInitialize(mod: ModContainer) {
        registerEvents {
            onCommandRegistration { buildContext, environment ->
                register("deathswap") {
                    requires { it.hasPermissionLevel(1) }
                    required(literal("start")) {
                        executes { ctx ->
                            try {
                                if (!QuiltLoader.isDevelopmentEnvironment() && ctx.source.server.allPlayers.size < 2) {
                                    throw CommandException(Text.literal("Cannot start a DeathSwap with less than 2 players."))
                                }
                                DeathSwapStateManager.begin(ctx.source.server)
                                ctx.source.server.broadcast("Deathswap started!")
                            } catch (e: Exception) {
                                LOGGER.error("Error starting DeathSwap", e)
                                throw e
                            }
                            1
                        }
                    }
                    required(literal("stop")) {
                        executes { ctx ->
                            DeathSwapStateManager.endGame(ctx.source.server)
                            ctx.source.server.broadcast("Deathswap ended!")
                            1
                        }
                    }
                    required(literal("config")) {
                        DeathSwapConfig.CONFIG.values().forEach { option ->
                            required(literal(option.key().toString())) {
                                executes { ctx ->
                                    ctx.source.sendFeedback(Text.literal("${option.key()} -> ${option.value()}"), false)
                                    1
                                }
                                val valueType = DeathSwapConfig.CONFIG_TYPES[option.key()]!!
                                required(argument("value", valueType.first as ArgumentType)) { value ->
                                    @Suppress("UNCHECKED_CAST")
                                    executes { ctx ->
                                        val newValue = (valueType.second as (Any) -> Any)(ctx.getArgument("value", Any::class.java))
                                        (option as TrackedValue<Any>).setValue(newValue, true)
                                        ctx.source.sendFeedback(Text.literal("Successfully set ${option.key()} to $newValue"), true)
                                        1
                                    }
                                }
                            }
                        }
                        executes { ctx ->
                            val result = Text.literal("Here are all the current config values:")
                            DeathSwapConfig.CONFIG.values().forEach { option ->
                                val name = option.key().toString()
                                result.append("\n$name -> ${DeathSwapConfig.CONFIG.getValue(option.key()).value()}")
                            }
                            ctx.source.sendFeedback(result, false)
                            1
                        }
                    }
                    if (QuiltLoader.isDevelopmentEnvironment()) {
                        required(literal("debug")) {
                            required(literal("swap_now")) {
                                executes { ctx ->
                                    DeathSwapStateManager.timeToSwap = DeathSwapStateManager.timeSinceLastSwap
                                    1
                                }
                            }
                            required(literal("swap_at")) {
                                executes { ctx ->
                                    ctx.source.sendFeedback(Text.literal(
                                        "Will swap at: ${ticksToMinutesSeconds(DeathSwapStateManager.timeToSwap)}"
                                    ), false)
                                    1
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

        LOGGER.info("qkdeathswap initialized!")
    }

    fun onPlayDoneDisconnecting(player: ServerPlayerEntity) {
        if (DeathSwapStateManager.hasBegun()) {
            DeathSwapStateManager.removePlayer(player, strikeLightning = false)
        }
    }
}
