package io.github.gaming32.qkdeathswap

import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.command.argument.DimensionArgumentType
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import net.minecraft.util.registry.RegistryKey
import net.minecraft.world.World
import org.quiltmc.config.api.values.TrackedValue
import org.quiltmc.loader.api.config.QuiltConfig

object DeathSwapConfig {
    private val MIN_SWAP_TIME = TrackedValue.create(20 * 60, "min")!!
    private val MAX_SWAP_TIME = TrackedValue.create(20 * 180, "max")!!
    private val MIN_SPREAD_DISTANCE = TrackedValue.create(10_000, "min")!!
    private val MAX_SPREAD_DISTANCE = TrackedValue.create(20_000, "max")!!
    private val DIMENSION = TrackedValue.create(World.OVERWORLD.value.toString(), "dimension") { option ->
        option.constraint(IdentifierConstraint)
    }!!
    private val RESISTANCE_TIME = TrackedValue.create(20 * 30, "resistance_time")!!

    val CONFIG = QuiltConfig.create("qkdeathswap", "deathswap", consumerApply {
        section("swap_time") { section ->
            section.field(MIN_SWAP_TIME)
            section.field(MAX_SWAP_TIME)
        }
        section("spread_distance") { section ->
            section.field(MIN_SPREAD_DISTANCE)
            section.field(MAX_SPREAD_DISTANCE)
        }
        field(DIMENSION)
        field(RESISTANCE_TIME)
    })!!

    private val baseTimeType = Pair(IntegerArgumentType.integer(0)) { v: Int -> v }
    val CONFIG_TYPES = mapOf(
        MIN_SWAP_TIME.key() to baseTimeType,
        MAX_SWAP_TIME.key() to baseTimeType,
        MIN_SPREAD_DISTANCE.key() to baseTimeType,
        MAX_SPREAD_DISTANCE.key() to baseTimeType,
        DIMENSION.key() to Pair(DimensionArgumentType.dimension()) { id: Identifier -> id.toString() },
        RESISTANCE_TIME.key() to baseTimeType
    )

    var minSwapTime: Int
        get() = MIN_SWAP_TIME.value()
        set(value) {
            MIN_SWAP_TIME.setValue(value, true)
        }

    var maxSwapTime: Int
        get() = MAX_SWAP_TIME.value()
        set(value) {
            MAX_SWAP_TIME.setValue(value, true)
        }

    val swapTime: IntRange
        get() = minSwapTime..maxSwapTime

    var minSpreadDistance: Int
        get() = MIN_SPREAD_DISTANCE.value()
        set(value) {
            MIN_SPREAD_DISTANCE.setValue(value, true)
        }

    var maxSpreadDistance: Int
        get() = MAX_SPREAD_DISTANCE.value()
        set(value) {
            MAX_SPREAD_DISTANCE.setValue(value, true)
        }

    val spreadDistance: IntRange
        get() = minSpreadDistance..maxSpreadDistance

    var dimension: RegistryKey<World>
        get() = RegistryKey.of(Registry.WORLD_KEY, Identifier(DIMENSION.value()))
        set(value) {
            DIMENSION.setValue(value.value.toString(), true)
        }

    var resistanceTime: Int
        get() = RESISTANCE_TIME.value()
        set(value) {
            RESISTANCE_TIME.setValue(value, true)
        }
}
