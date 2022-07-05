package io.github.gaming32.qkdeathswap

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.minecraft.network.MessageType
import net.minecraft.scoreboard.AbstractTeam.VisibilityRule
import net.minecraft.text.Text
import net.minecraft.world.GameMode
import net.minecraft.world.GameRules
import org.quiltmc.loader.api.ModContainer
import org.quiltmc.loader.api.QuiltLoader
import org.quiltmc.qkl.wrapper.minecraft.brigadier.literal
import org.quiltmc.qkl.wrapper.minecraft.brigadier.register
import org.quiltmc.qkl.wrapper.qsl.EventRegistration
import org.quiltmc.qkl.wrapper.qsl.commands.onCommandRegistration
import org.quiltmc.qkl.wrapper.qsl.lifecycle.onServerTickEnd
import org.quiltmc.qkl.wrapper.qsl.networking.onPlayDisconnect
import org.quiltmc.qkl.wrapper.qsl.networking.onPlayReady
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object DeathSwapMod : ModInitializer {
    @JvmField val LOGGER: Logger = LoggerFactory.getLogger("qkdeathswap")

    override fun onInitialize(mod: ModContainer) {
        EventRegistration.onCommandRegistration { buildContext, environment ->
            register("deathswap") {
                requires { it.hasPermissionLevel(1) }
                literal("start") {
                    executes { ctx ->
                        DeathSwapStateManager.begin(ctx.source.server)
                        ctx.source.server.broadcast("Deathswap started!")
                        1
                    }
                }
                literal("stop") {
                    executes { ctx ->
                        DeathSwapStateManager.endGame(ctx.source.server)
                        ctx.source.server.broadcast("Deathswap ended!")
                        1
                    }
                }
                if (QuiltLoader.isDevelopmentEnvironment()) {
                    literal("debug") {
                        literal("swap_now") {
                            executes { ctx ->
                                DeathSwapStateManager.timeToSwap = DeathSwapStateManager.timeSinceLastSwap
                                1
                            }
                        }
                        literal("swap_at") {
                            executes { ctx ->
                                ctx.source.sendFeedback(Text.literal(
                                    "Will swap at: ${ticksToMinutesSeconds(DeathSwapStateManager.timeToSwap)}"
                                ), true)
                                1
                            }
                        }
                    }
                }
            }
        }

        ServerPlayerEvents.ALLOW_DEATH.register { player, source, amount ->
            if (!DeathSwapStateManager.hasBegun()) {
                return@register true
            }
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
            DeathSwapStateManager.removePlayer(player)
            false
        }

        EventRegistration.onServerTickEnd {
            if (DeathSwapStateManager.hasBegun()) {
                DeathSwapStateManager.tick(this)
            }
        }

        EventRegistration.onPlayReady { packetSender, server ->
            if (DeathSwapStateManager.hasBegun()) {
                DeathSwapStateManager.resetPlayer(player, gamemode = GameMode.SPECTATOR)
            }
        }

        EventRegistration.onPlayDisconnect {
            if (DeathSwapStateManager.hasBegun()) {
                DeathSwapStateManager.removePlayer(player, strikeLightning = false)
            }
        }

        LOGGER.info("qkdeathswap initialized!")
    }
}
