package io.github.gaming32.qkdeathswap

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.command.argument.DimensionArgumentType
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import net.minecraft.util.registry.RegistryKey
import net.minecraft.world.World
import org.quiltmc.config.api.annotations.Comment
import org.quiltmc.config.api.values.TrackedValue
import org.quiltmc.loader.api.config.QuiltConfig

object DeathSwapConfig {
    private val MIN_SWAP_TIME = TrackedValue.create(20 * 60, "min")!!
    private val MAX_SWAP_TIME = TrackedValue.create(20 * 180, "max")!!
    private val MIN_SPREAD_DISTANCE = TrackedValue.create(10_000, "min")!!
    private val MAX_SPREAD_DISTANCE = TrackedValue.create(20_000, "max")!!
    private val RIDE_OPPONENT_ENTITY_ON_TELEPORT = TrackedValue.create(true, "ride_opponent_entity_on_teleport") { option ->
        option.metadata(Comment.TYPE) { comments -> comments.add(
            "The entity that the opponent is riding will stay ridden by the player on swap"
        ) }
    }!!
    private val DIMENSION = TrackedValue.create(World.OVERWORLD.value.toString(), "dimension") { option ->
        option.constraint(IdentifierConstraint)
        option.metadata(Comment.TYPE) { comments -> comments.add(
            "The dimension in which deathswaps will take place, as a dimension identifier"
        ) }
    }!!
    private val RESISTANCE_TIME = TrackedValue.create(20 * 30, "resistance_time") { option ->
        option.metadata(Comment.TYPE) { comments -> comments.add(
            "The number of ticks of resistance players will get at the beginning of the deathswap",
            "Default 15 seconds"
        ) }
    }!!
    private val TELEPORT_LOAD_TIME = TrackedValue.create(20 * 5, "teleport_load_time") { option ->
        option.metadata(Comment.TYPE) { comments -> comments.add(
            "The number of ticks it takes for the player to load after teleporting",
            "Default 5 seconds"
        ) }
    }

    val CONFIG = QuiltConfig.create("qkdeathswap", "deathswap", consumerApply {
        section("swap_time") { section ->
            section.metadata(Comment.TYPE) { comments -> comments.add(
                "The amount of time between swaps, in ticks",
                "Default 1-3 minutes"
            ) }
            section.field(MIN_SWAP_TIME)
            section.field(MAX_SWAP_TIME)
        }
        section("spread_distance") { section ->
            section.metadata(Comment.TYPE) { comments -> comments.add(
                "The distance from 0,0 players are teleported to"
            ) }
            section.field(MIN_SPREAD_DISTANCE)
            section.field(MAX_SPREAD_DISTANCE)
        }
        field(RIDE_OPPONENT_ENTITY_ON_TELEPORT)
        field(DIMENSION)
        field(RESISTANCE_TIME)
        field(TELEPORT_LOAD_TIME)
    })!!

    private val baseTimeType = Pair(IntegerArgumentType.integer(0)) { v: Int -> v }
    val CONFIG_TYPES = mapOf(
        MIN_SWAP_TIME.key() to baseTimeType,
        MAX_SWAP_TIME.key() to baseTimeType,
        MIN_SPREAD_DISTANCE.key() to baseTimeType,
        MAX_SPREAD_DISTANCE.key() to baseTimeType,
        RIDE_OPPONENT_ENTITY_ON_TELEPORT.key() to Pair(BoolArgumentType.bool()) { v: Boolean -> v },
        DIMENSION.key() to Pair(DimensionArgumentType.dimension()) { id: Identifier -> id.toString() },
        RESISTANCE_TIME.key() to baseTimeType,
        TELEPORT_LOAD_TIME.key() to baseTimeType
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

    var rideOpponentEntityOnTeleport: Boolean
        get() = RIDE_OPPONENT_ENTITY_ON_TELEPORT.value()
        set(value) {
            RIDE_OPPONENT_ENTITY_ON_TELEPORT.setValue(value, true)
        }

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

    var teleportLoadTime: Int
        get() = TELEPORT_LOAD_TIME.value()
        set(value) {
            TELEPORT_LOAD_TIME.setValue(value, true)
        }
}
