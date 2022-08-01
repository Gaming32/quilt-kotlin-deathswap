package io.github.gaming32.qkdeathswap

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import net.minecraft.command.argument.DimensionArgumentType
import net.minecraft.command.argument.TimeArgumentType
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtList
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.world.World
import org.quiltmc.loader.impl.lib.electronwill.nightconfig.core.CommentedConfig
import org.quiltmc.loader.impl.lib.electronwill.nightconfig.toml.TomlFormat
import org.quiltmc.loader.impl.lib.electronwill.nightconfig.toml.TomlParser
import xyz.wagyourtail.betterconfig.BaseConfig
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

private val INVALID_ENUM_EXCEPTION = DynamicCommandExceptionType { Text.translatable("argument.enum.invalid", it) }

open class DeathSwapConfig(
    configToml: CommentedConfig,
    saveStreamer: () -> OutputStream?
) : BaseConfig<DeathSwapConfig>("Quilt Deathswap Config", configToml, saveStreamer) {
    companion object DeathSwapConfigStatic : DeathSwapConfig(
        if (DeathSwapMod.configFile.exists()) {
            DeathSwapMod.configFile.inputStream().use {
                TomlParser().parse(it)
            }
        } else {
            TomlFormat.newConfig()
        },
        {
            DeathSwapMod.configFile.outputStream(
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
        }
    )

    constructor(input: InputStream, output: () -> OutputStream? = { null }) : this(TomlParser().parse(input), output)

    private val swapTimeGroup = group(
        "swap_time",
        "The amount of time between swaps, in ticks\n" +
                "Default 1-3 minutes"
    )

    val minSwapTime = swapTimeGroup.setting(
        "min",
        20 * 60,
        TimeArgumentType.time(),
        "The minimum time between swaps"
    )

    val maxSwapTime = swapTimeGroup.setting(
        "max",
        20 * 180,
        TimeArgumentType.time(),
        "The maximum time between swaps"
    )

    val warnTime = swapTimeGroup.setting(
        "warn",
        0,
        TimeArgumentType.time(),
        "The time before a swap that a warning will be sent to the player"
    )

    var swapTime: IntRange
        get() = minSwapTime.value..maxSwapTime.value
        set(range) {
            minSwapTime.value = range.first
            maxSwapTime.value = range.last
        }

    private val spreadDistanceGroup = group(
        "spread_distance",
        "The distance from 0,0 players are teleported to"
    )

    val minSpreadDistance = spreadDistanceGroup.setting(
        "min",
        10_000,
        IntegerArgumentType.integer(0),
        "The minimum distance players are teleported to"
    )

    val maxSpreadDistance = spreadDistanceGroup.setting(
        "max",
        20_000,
        IntegerArgumentType.integer(0),
        "The maximum distance players are teleported to"
    )

    var spreadDistance: IntRange
        get() = minSpreadDistance.value..maxSpreadDistance.value
        set(range) {
            minSpreadDistance.value = range.first
            maxSpreadDistance.value = range.last
        }

    val dimension = setting("dimension",
        World.OVERWORLD.value,
        DimensionArgumentType.dimension(),
        "The dimension players are teleported to",
        serializer = { it.toString() },
        deserializer = { (it as? String)?.let(::Identifier) },
        brigadierFilter = { source, value ->
            source.worldKeys.any {
                it.value == value
            }
        }
    )

    val resistanceTime = setting(
        "resistance_time",
        20 * 15,
        TimeArgumentType.time(),
        "The number of ticks of resistance players will get at the beginning of the deathswap\n" +
                "Default 15 seconds"
    )

    private val swapOptionsGroup = group(
        "swap_options",
        "Options for modifiers on the swap"
    )

    val swapMount = swapOptionsGroup.setting(
        "mount",
        true,
        BoolArgumentType.bool()
    )


    val swapHealth = swapOptionsGroup.setting(
        "health",
        false,
        BoolArgumentType.bool()
    )

    val swapMobAggression = swapOptionsGroup.setting(
        "mob_aggression",
        false,
        BoolArgumentType.bool()
    )

    val swapHunger = swapOptionsGroup.setting(
        "hunger",
        false,
        BoolArgumentType.bool()
    )

    val swapFire = swapOptionsGroup.setting(
        "fire",
        false,
        BoolArgumentType.bool()
    )

    val swapAir = swapOptionsGroup.setting(
        "air",
        false,
        BoolArgumentType.bool()
    )

    val swapFrozen = swapOptionsGroup.setting(
        "frozen",
        false,
        BoolArgumentType.bool()
    )

    val swapPotionEffects = swapOptionsGroup.setting(
        "potion_effects",
        false,
        BoolArgumentType.bool()
    )

    val swapInventory = swapOptionsGroup.setting(
        "inventory",
        false,
        BoolArgumentType.bool()
    )

    val teleportLoadTime = setting(
        "teleport_load_time",
        20 * 5,
        TimeArgumentType.time(),
        "The number of ticks it takes for the player to load after teleporting\n" +
                "Default 5 seconds"
    )

    val enableDebug = setting<Boolean, Unit>(
        "enable_debug",
        false,
        null
    )

    var defaultKit: PlayerInventory = loadKit()

    private fun loadKit(): PlayerInventory {
        return PlayerInventory(null).apply {
            if (DeathSwapMod.defaultKitStoreLocation.exists()) {
                readNbt(
                    NbtIo.readCompressed(DeathSwapMod.defaultKitStoreLocation)
                        .getList("Inventory", NbtElement.COMPOUND_TYPE.toInt())
                )
            }
        }
    }

    fun writeDefaultKit() {
        NbtIo.writeCompressed(NbtCompound().apply {
            put("Inventory", NbtList().apply {
                DeathSwapConfig.defaultKit.writeNbt(this)
            })
        }, DeathSwapMod.defaultKitStoreLocation)
    }

    override fun copyFrom(other: DeathSwapConfig) {
        val debug = enableDebug.value
        super.copyFrom(other)
        defaultKit = loadKit()
        enableDebug.value = debug
    }

    override fun save() {
        super.save()
        writeDefaultKit()
    }
}