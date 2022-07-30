package io.github.gaming32.qkdeathswap

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.command.argument.DimensionArgumentType
import net.minecraft.command.argument.TimeArgumentType
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtList
import net.minecraft.util.Identifier
import net.minecraft.world.World
import org.quiltmc.loader.impl.lib.electronwill.nightconfig.core.CommentedConfig
import org.quiltmc.loader.impl.lib.electronwill.nightconfig.toml.TomlFormat
import org.quiltmc.loader.impl.lib.electronwill.nightconfig.toml.TomlParser
import xyz.wagyourtail.betterconfig.BetterConfig
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

open class DeathSwapConfig(
    configToml: CommentedConfig,
    saveStreamer: () -> OutputStream?
) : BetterConfig<DeathSwapConfig>(configToml, saveStreamer) {
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
    ) {
        private fun loadInstance(): DeathSwapConfig {
            return if (DeathSwapMod.configFile.exists()) {
                println("Loading config from file")
                DeathSwapMod.configFile.inputStream().use {
                    println("Parsing config")
                    DeathSwapConfig(it)
                }
            } else {
                DeathSwapConfig(TomlFormat.newConfig()) {
                    DeathSwapMod.configFile.outputStream(
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    )
                }
            }
        }
    }
    constructor(input: InputStream, output: () -> OutputStream? = { null }) : this(TomlParser().parse(input), output)

    private val swapTimeGroup = register(
        "swap_time",
        "The amount of time between swaps, in ticks\n" +
                "Default 1-3 minutes"
    )

    val minSwapTime = swapTimeGroup.register(
        "min",
        20 * 60,
        TimeArgumentType.time(),
        "The minimum time between swaps"
    )

    val maxSwapTime = swapTimeGroup.register(
        "max",
        20 * 180,
        TimeArgumentType.time(),
        "The maximum time between swaps"
    )

    val warnTime = swapTimeGroup.register(
        "warn",
        0,
        TimeArgumentType.time(),
        "The time before a swap that a warning will be sent to the player"
    )

    var swapTime: IntRange
        get() = minSwapTime.value!!..maxSwapTime.value!!
        set(range) {
            minSwapTime.value = range.first
            maxSwapTime.value = range.last
        }

    private val spreadDistanceGroup = register(
        "spread_distance",
        "The distance from 0,0 players are teleported to"
    )

    val minSpreadDistance = spreadDistanceGroup.register(
        "min",
        10_000,
        IntegerArgumentType.integer(0),
        "The minimum distance players are teleported to"
    )

    val maxSpreadDistance = spreadDistanceGroup.register(
        "max",
        20_000,
        IntegerArgumentType.integer(0),
        "The maximum distance players are teleported to"
    )

    var spreadDistance: IntRange
        get() = minSpreadDistance.value!!..maxSpreadDistance.value!!
        set(range) {
            minSpreadDistance.value = range.first
            maxSpreadDistance.value = range.last
        }

    val dimension = register("dimension",
        World.OVERWORLD.value,
        DimensionArgumentType.dimension(),
        "The dimension players are teleported to",
        serializer = { it.toString() },
        deserializer = { (it as? String)?.let(::Identifier) },
        brigadierFilter = { source, value -> source.worldKeys.any {
            it.value == value
        } }
    )

    val resistanceTime = register(
        "resistance_time",
        20 * 15,
        TimeArgumentType.time(),
        "The number of ticks of resistance players will get at the beginning of the deathswap\n" +
                "Default 15 seconds"
    )

    private val swapOptionsGroup = register(
        "swap_options",
        "Options for modifiers on the swap"
    )

    val swapMount = swapOptionsGroup.register(
        "mount",
        true,
        BoolArgumentType.bool()
    )


    val swapHealth = swapOptionsGroup.register(
        "health",
        false,
        BoolArgumentType.bool()
    )

    val swapMobAggression = swapOptionsGroup.register(
        "mob_aggression",
        false,
        BoolArgumentType.bool()
    )

    val swapHunger = swapOptionsGroup.register(
        "hunger",
        false,
        BoolArgumentType.bool()
    )

    val swapFire = swapOptionsGroup.register(
        "fire",
        false,
        BoolArgumentType.bool()
    )

    val swapAir = swapOptionsGroup.register(
        "air",
        false,
        BoolArgumentType.bool()
    )

    val swapFrozen = swapOptionsGroup.register(
        "frozen",
        false,
        BoolArgumentType.bool()
    )

    val swapPotionEffects = swapOptionsGroup.register(
        "potion_effects",
        false,
        BoolArgumentType.bool()
    )

    val swapInventory = swapOptionsGroup.register(
        "inventory",
        false,
        BoolArgumentType.bool()
    )

    val teleportLoadTime = register(
        "teleport_load_time",
        20 * 5,
        TimeArgumentType.time(),
        "The number of ticks it takes for the player to load after teleporting\n" +
                "Default 5 seconds"
    )

    val enableDebug = register<Boolean, Unit>(
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
        super.copyFrom(other)
        defaultKit = loadKit()
    }

    fun save() {
        save(null)
        writeDefaultKit()
    }
}