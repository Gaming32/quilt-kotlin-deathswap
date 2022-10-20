package io.github.gaming32.qkdeathswap

import io.github.gaming32.qkdeathswap.DeathSwapConfig.DeathSwapConfigStatic.writeDefaultKit
import io.github.gaming32.qkdeathswap.mixin.ScoreboardCriterionAccessor
import net.minecraft.command.CommandException
import net.minecraft.command.CommandSource
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtList
import net.minecraft.network.PacketSendListener
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket
import net.minecraft.scoreboard.AbstractTeam.VisibilityRule
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.HoverEvent
import net.minecraft.text.ScreenTexts
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.GameMode
import net.minecraft.world.GameRules
import net.minecraft.world.dimension.DimensionTypes
import org.quiltmc.loader.api.ModContainer
import org.quiltmc.loader.api.QuiltLoader
import org.quiltmc.qkl.wrapper.minecraft.brigadier.argument.literal
import org.quiltmc.qkl.wrapper.minecraft.brigadier.argument.player
import org.quiltmc.qkl.wrapper.minecraft.brigadier.argument.string
import org.quiltmc.qkl.wrapper.minecraft.brigadier.argument.value
import org.quiltmc.qkl.wrapper.minecraft.brigadier.execute
import org.quiltmc.qkl.wrapper.minecraft.brigadier.optional
import org.quiltmc.qkl.wrapper.minecraft.brigadier.register
import org.quiltmc.qkl.wrapper.minecraft.brigadier.required
import org.quiltmc.qkl.wrapper.qsl.commands.onCommandRegistration
import org.quiltmc.qkl.wrapper.qsl.lifecycle.onServerTickEnd
import org.quiltmc.qkl.wrapper.qsl.networking.allPlayers
import org.quiltmc.qkl.wrapper.qsl.networking.onPlayReady
import org.quiltmc.qkl.wrapper.qsl.registerEvents
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer
import org.quiltmc.qsl.entity_events.api.EntityReviveEvents.BEFORE_TOTEM
import org.quiltmc.qsl.entity_events.api.EntityWorldChangeEvents.AFTER_PLAYER_WORLD_CHANGE
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists


const val MOD_ID = "qkdeathswap"

object DeathSwapMod : ModInitializer {
    @JvmField val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    val configDir: Path = QuiltLoader.getConfigDir().resolve(MOD_ID)
    val configFile: Path = configDir.resolve("deathswap.toml")

    val presetsDir: Path = configDir.resolve("presets")

    val defaultKitStoreLocation: File = configDir.resolve("default_kit.dat").toFile()

    val itemCountCriterion = ScoreboardCriterionAccessor.callCreate("$MOD_ID:item_count")!!

    override fun onInitialize(mod: ModContainer) {


        if (!configDir.exists()) {
            configDir.toFile().mkdirs()
        }

        if (!presetsDir.exists()) {
            presetsDir.toFile().mkdirs()
        }


        registerEvents {
            onCommandRegistration { _, _ ->
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
                            DeathSwapStateManager.endGame(source.server, natural = false)
                            source.server.broadcast("Deathswap ended!")
                        }
                    }
                    required(literal("config")) {
                        DeathSwapConfig.buildArguments(this)
                    }
                    required(literal("presets")) {
                        required(literal("load")) {
                            required(string("preset")) { presetAccess ->
                                suggests { _, builder ->
                                    CommandSource.suggestMatching(
                                        Presets.list(),
                                        builder
                                    )
                                }
                                execute {
                                    val preset = presetAccess.invoke(this).value()
                                    if (Presets.load(preset)) {
                                        source.sendFeedback(Text.literal("Successfully loaded preset ").append(Text.literal(preset).formatted(Formatting.GREEN)), true)
                                    } else {
                                        source.sendFeedback(Text.literal("Failed to load preset ").append(Text.literal(preset).formatted(Formatting.RED)), false)
                                    }
                                }
                            }
                        }
                        required(literal("delete")) {
                            required(string("preset")) { presetAccess ->
                                suggests { _, builder ->
                                    CommandSource.suggestMatching(
                                        Presets.list(),
                                        builder
                                    )
                                }
                                execute {
                                    val preset = presetAccess.invoke(this).value()
                                    if (Presets.delete(preset)) {
                                        source.sendFeedback(Text.literal("Successfully deleted preset ").append(Text.literal(preset).formatted(Formatting.GREEN)), true)
                                    } else {
                                        if (preset in Presets.builtin) {
                                            source.sendFeedback(Text.literal("Cannot delete builtin preset ").append(Text.literal(preset).formatted(Formatting.RED)), true)
                                        } else {
                                            source.sendFeedback(Text.literal("Failed to delete preset ").append(Text.literal(preset).formatted(Formatting.RED)).append(Text.literal(". does it exist?")), false)
                                        }
                                    }
                                }
                            }
                        }
                        required(literal("save")) {
                            required(string("preset")) { presetAccess ->
                                execute {
                                    val preset = presetAccess.invoke(this).value()
                                    try {
                                        Presets.save(preset)
                                        source.sendFeedback(
                                            Text.literal("Successfully saved preset ")
                                                .append(Text.literal(preset).formatted(Formatting.GREEN)), true
                                        )
                                    } catch (e: IOException) {
                                        source.sendFeedback(
                                            Text.literal("Failed to save preset ")
                                                .append(Text.literal(preset).formatted(Formatting.RED))
                                                .append(Text.literal(". ").append(Text.literal(e.message).formatted(Formatting.RED))), false
                                        )
                                    }
                                }
                            }
                        }
                        required(literal("preview")) {
                            required(string("preset")) { presetAccess ->
                                suggests { _, builder ->
                                    CommandSource.suggestMatching(
                                        Presets.list(),
                                        builder
                                    )
                                }
                                optional(literal("kit")) { kitAccess ->
                                    execute {
                                        val preset = presetAccess.invoke(this).value()
                                        if (kitAccess != null) {
                                            val kit = Presets.previewKit(preset)
                                            if (kit != null) {
                                                viewKit(source.player, kit)
                                            } else {
                                                source.sendFeedback(Text.literal("No kit found for preset ").append(Text.literal(preset).formatted(Formatting.RED)).append(Text.literal(", or preset doesn't exist.")), false)
                                            }
                                        } else {
                                            val values = Presets.preview(preset)
                                            if (values != null) {
                                                source.sendFeedback(
                                                    Text.literal("Previewing preset ")
                                                        .append(Text.literal(preset).formatted(Formatting.GREEN))
                                                        .append(Text.literal(":")), true
                                                )
                                                source.sendFeedback(values, false)
                                            } else {
                                                source.sendFeedback(Text.literal("No preset found for ").append(Text.literal(preset).formatted(Formatting.RED)), false)
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
                                    DeathSwapConfig.writeDefaultKit()
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
                                viewKit(source.player, DeathSwapConfig.defaultKit)
                            }
                        }
                    }
                    if (DeathSwapConfig.enableDebug.value) {
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

            BEFORE_TOTEM.register { entity, _ ->
                if (entity !is ServerPlayerEntity) return@register false
                if (!DeathSwapStateManager.hasBegun()) {
                    return@register true
                }
                val showDeathMessages = entity.world.gameRules.getBoolean(GameRules.SHOW_DEATH_MESSAGES)
                if (showDeathMessages) {
                    val text = entity.damageTracker.deathMessage
                    entity.networkHandler
                        .m_hezgjyfd(
                            DeathMessageS2CPacket(entity.damageTracker, text),
                            PacketSendListener.toSendIfFailed {
                                val string = text.asTruncatedString(256)
                                val text2 = Text.translatable(
                                    "death.attack.message_too_long",
                                    *arrayOf<Any>(
                                        Text.literal(string).formatted(Formatting.YELLOW)
                                    )
                                )
                                val text3 = Text.translatable(
                                    "death.attack.even_more_magic",
                                    *arrayOf(entity.displayName)
                                )
                                    .styled { style: Style ->
                                        style.withHoverEvent(
                                            HoverEvent(
                                                HoverEvent.Action.SHOW_TEXT,
                                                text2
                                            )
                                        )
                                    }
                                DeathMessageS2CPacket(entity.damageTracker, text3)
                            }
                        )
                    val abstractTeam = entity.scoreboardTeam
                    if (abstractTeam == null || abstractTeam.deathMessageVisibilityRule == VisibilityRule.ALWAYS) {
                        entity.server.playerManager.m_bgctehjy(text, false)
                    } else if (abstractTeam.deathMessageVisibilityRule == VisibilityRule.HIDE_FOR_OTHER_TEAMS) {
                        entity.server.playerManager.sendSystemMessageToTeam(entity, text)
                    } else if (abstractTeam.deathMessageVisibilityRule == VisibilityRule.HIDE_FOR_OWN_TEAM) {
                        entity.server.playerManager.sendSystemMessageToOtherTeams(entity, text)
                    }
                } else {
                    entity.networkHandler.sendPacket(DeathMessageS2CPacket(entity.damageTracker, ScreenTexts.EMPTY))
                }
                DeathSwapStateManager.removePlayer(entity)
                !showDeathMessages
            }

            onServerTickEnd {
                if (DeathSwapStateManager.hasBegun()) {
                    DeathSwapStateManager.tick(this)
                }
            }

            onPlayReady { _, _ ->
                if (DeathSwapStateManager.hasBegun() && !DeathSwapStateManager.livingPlayers.containsKey(player.uuid)) {
                    DeathSwapStateManager.resetPlayer(player, gamemode = GameMode.SPECTATOR)
                }
            }

            AFTER_PLAYER_WORLD_CHANGE.register { player, origin, destination ->
                if (DeathSwapStateManager.hasBegun()) {
                    if (origin.m_fzgkmzuh() == DimensionTypes.THE_END) {
                        if (DeathSwapStateManager.livingPlayers.containsKey(player.uuid)) {
                            // Teleport player, so they aren't at spawn in the overworld
                            val holder = DeathSwapStateManager.livingPlayers[player.uuid]
                            if (holder != null) {
                                val loc = holder.startLocation
                                if (holder.startLocation.world != destination) {
                                    val newLoc = PlayerStartLocation(destination, loc.x, loc.z)
                                    while (!loc.tick()) {}
                                    player.teleport(
                                        newLoc.world,
                                        newLoc.x.toDouble(),
                                        newLoc.y.toDouble(),
                                        newLoc.z.toDouble(),
                                        0f, 0f
                                    )
                                }
                                // else is handled by the setspawn code on itemcount's branch
                            }
                        }
                    }
                }
            }

        }

        LOGGER.info("$MOD_ID initialized!")
    }

    fun viewKit(player: ServerPlayerEntity, kit: PlayerInventory) {
        player.openHandledScreen(object : NamedScreenHandlerFactory {
            override fun createMenu(
                syncId: Int,
                playerInventory: PlayerInventory,
                playerEntity: PlayerEntity
            ): ScreenHandler {
                val screenHandler = object : GenericContainerScreenHandler(
                    ScreenHandlerType.GENERIC_9X5,
                    syncId,
                    playerInventory,
                    SimpleInventory(9 * 5),
                    5
                ) {
                    override fun onSlotClick(
                        slotIndex: Int,
                        button: Int,
                        actionType: SlotActionType?,
                        player: PlayerEntity?
                    ) {
                        if (slotIndex in 5 until 9) {
                            cursorStack = ItemStack.EMPTY
                            updateToClient()
                            return
                        }
                        super.onSlotClick(slotIndex, button, actionType, player)
                    }

                    override fun close(player: PlayerEntity) {
                        for (i in 0 until 4) {
                            for (j in 0 until 9) {
                                kit.setStack(
                                    i * 9 + j,
                                    getSlot((4 - i) * 9 + j).stack.copy()
                                )
                            }
                        }
                        for (i in 0 until 5) {
                            kit.setStack(36 + i, getSlot(i).stack.copy())
                        }
                        if (kit == DeathSwapConfig.defaultKit) {
                            writeDefaultKit()
                        }
                    }
                }
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
                for (i in 5 until 9) {
                    screenHandler.inventory.setStack(i, ItemStack(Items.BARRIER))
                }
                return screenHandler
            }

            override fun getDisplayName(): Text = Text.literal("Default kit")
        })
    }
}
