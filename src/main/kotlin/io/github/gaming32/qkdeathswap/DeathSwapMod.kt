package io.github.gaming32.qkdeathswap

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.minecraft.entity.EntityType
import net.minecraft.entity.LightningEntity
import net.minecraft.world.GameMode
import org.quiltmc.loader.api.ModContainer
import org.quiltmc.qkl.wrapper.minecraft.brigadier.literal
import org.quiltmc.qkl.wrapper.minecraft.brigadier.register
import org.quiltmc.qkl.wrapper.qsl.EventRegistration
import org.quiltmc.qkl.wrapper.qsl.commands.onCommandRegistration
import org.quiltmc.qkl.wrapper.qsl.lifecycle.onServerTickEnd
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer
import org.slf4j.LoggerFactory

object DeathSwapMod : ModInitializer {
    @JvmField val LOGGER = LoggerFactory.getLogger("qkdeathswap")

    override fun onInitialize(mod: ModContainer) {
        EventRegistration.onCommandRegistration { buildContext, environment ->
            register("deathswap") {
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
            }
        }

        ServerPlayerEvents.ALLOW_DEATH.register { player, source, amount ->
            if (!DeathSwapStateManager.hasBegun()) {
                return@register true
            }
            player.world.spawnEntity(LightningEntity(EntityType.LIGHTNING_BOLT, player.world).also { it.setCosmetic(true) })
            player.health = 20f
            player.changeGameMode(GameMode.SPECTATOR)
            DeathSwapStateManager.removePlayer(player)
            false
        }

        EventRegistration.onServerTickEnd {
            if (DeathSwapStateManager.hasBegun()) {
                DeathSwapStateManager.tick(this)
            }
        }

        LOGGER.info("qkdeathswap initialized!")
    }
}
