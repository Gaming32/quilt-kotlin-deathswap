package io.github.gaming32.qkdeathswap

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.TimeArgument
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.level.Level
import org.quiltmc.loader.api.QuiltLoader
import org.quiltmc.loader.impl.lib.electronwill.nightconfig.core.CommentedConfig
import org.quiltmc.loader.impl.lib.electronwill.nightconfig.toml.TomlFormat
import org.quiltmc.loader.impl.lib.electronwill.nightconfig.toml.TomlParser
import xyz.wagyourtail.betterconfig.BetterConfig
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

private val INVALID_ENUM_EXCEPTION = DynamicCommandExceptionType { Component.translatable("argument.enum.invalid", it) }

open class DeathSwapConfig(
    configToml: CommentedConfig,
    saveStreamer: () -> OutputStream?
) : BetterConfig<DeathSwapConfig>("Quilt Deathswap Config", configToml, saveStreamer) {
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

    private fun formatTime(value: Int) = if (value % 20 == 0) {
        Component.literal("${value}t (${value / 20}s)")
    } else {
        Component.literal("${value}t (${value / 20.0}s)")
    }

    constructor(input: InputStream, output: () -> OutputStream? = { null }) : this(TomlParser().parse(input), output)

    private val swapTimeGroup = group(
        "swap_time",
        "The amount of time between swaps, in ticks\n" +
                "Default 1-3 minutes"
    )

    val minSwapTime = swapTimeGroup.setting(
        "min",
        20 * 60,
        TimeArgument.time(),
        "The minimum time between swaps",
        textValue = ::formatTime
    )

    val maxSwapTime = swapTimeGroup.setting(
        "max",
        20 * 180,
        TimeArgument.time(),
        "The maximum time between swaps",
        textValue = ::formatTime
    )

    val warnTime = swapTimeGroup.setting(
        "warn",
        0,
        TimeArgument.time(),
        "The time before a swap that a warning will be sent to the player",
        textValue = ::formatTime
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

    val dimension = setting(
        "dimension",
        Level.OVERWORLD.location(),
        DimensionArgument.dimension(),
        "The dimension players are teleported to",
        serializer = { it.toString() },
        deserializer = { (it as? String)?.let(::ResourceLocation) },
        brigadierFilter = { source, value ->
            source.levels().any { it.location() == value }
        }
    )

    val resistanceTime = setting(
        "resistance_time",
        20 * 15,
        TimeArgument.time(),
        "The number of ticks of resistance players will get at the beginning of the deathswap\n" +
                "Default 15 seconds",
        textValue = ::formatTime
    )

    val maxStartFindTime = setting(
        "max_spawn_time",
        20 * 30,
        TimeArgument.time(),
        "The maximum time to search for valid start locations.\nDefault 30 seconds",
        textValue = ::formatTime
    )

    private val swapOptionsGroup = group(
        "swap_options",
        "Options for modifiers on the swap"
    )

    val swapVelocity = swapOptionsGroup.setting(
        "velocity",
        true,
        BoolArgumentType.bool()
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
        true,
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
        TimeArgument.time(),
        "The number of ticks it takes for the player to load after teleporting\n" +
                "Default 5 seconds",
        textValue = ::formatTime
    )

    val enableDebug = setting<Boolean, Unit>(
        "enable_debug",
        QuiltLoader.isDevelopmentEnvironment(),
        null
    )

    val gameMode = setting<DeathSwapGameMode, String>(
        "game_mode",
        DeathSwapGameMode.NORMAL,
        StringArgumentType.word(),
        brigadierDeserializer = {
            (it as? String)?.let(DeathSwapGameMode::byName) ?: throw INVALID_ENUM_EXCEPTION.create(it)
        },
        brigadierSuggestor = { _, builder ->
            SharedSuggestionProvider.suggest(Arrays.stream(DeathSwapGameMode.values()).map(DeathSwapGameMode::getSerializedName), builder)
        },
        serializer = { it.serializedName },
        deserializer = { (it as? String)?.let(DeathSwapGameMode::byName) ?: DeathSwapGameMode.NORMAL }
    )

    val craftingCountsTowardsItemCount = setting(
        "crafting_counts_towards_item_count",
        true,
        BoolArgumentType.bool(),
    )

    val destroyItemsDuringSwap = setting(
        "destroy_items_during_swap",
        false,
        BoolArgumentType.bool(),
    )

    val swapLimit = setting(
        "swap_limit",
        10,
        IntegerArgumentType.integer(1)
    )

    val mainThreadWeight = setting(
        "main_thread_weight",
        10,
        IntegerArgumentType.integer(1),
        "The number of iterations per player per tick to run the start location finder\nDefault 10"
    )

    val fantasyGroup = group(
        "fantasy",
        "Options for working with Fantasy, the mod that allows temporary worlds to be created for each deathswap"
    )

    val fantasyEnabled = fantasyGroup.setting(
        "enabled",
        false,
        BoolArgumentType.bool(),
        "Whether to enable creating new worlds for Deathswap"
    )

    val fantasyDifficulty = fantasyGroup.setting<Difficulty, String>(
        "difficulty",
        Difficulty.NORMAL,
        StringArgumentType.word(),
        "The difficulty to use for Fantasy worlds made for Deathswap",
        brigadierDeserializer = {
            (it as? String)?.let(Difficulty::byName) ?: throw INVALID_ENUM_EXCEPTION.create(it)
        },
        brigadierSuggestor = { _, builder ->
            SharedSuggestionProvider.suggest(Arrays.stream(Difficulty.values()).map(Difficulty::getSerializedName), builder)
        },
        serializer = { it.serializedName },
        deserializer = { (it as? String)?.let(Difficulty::byName) ?: Difficulty.NORMAL }
    )

    var defaultKit: Inventory = loadKit()

    private fun loadKit() = Inventory(null).apply {
        if (DeathSwapMod.defaultKitStoreLocation.exists()) {
            load(
                NbtIo.readCompressed(DeathSwapMod.defaultKitStoreLocation)
                    .getList("Inventory", Tag.TAG_COMPOUND.toInt())
            )
        }
    }

    fun writeDefaultKit() {
        NbtIo.writeCompressed(CompoundTag().apply {
            put("Inventory", ListTag().apply(defaultKit::save))
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
