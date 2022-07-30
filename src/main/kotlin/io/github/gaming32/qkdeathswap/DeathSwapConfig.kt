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
    private val WARN_TIME = TrackedValue.create(0, "warn") { option ->
        option.metadata(Comment.TYPE) { comments -> comments.add(
            "The number of ticks before a swap to notify players"
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
    private val SWAP_MOUNT = TrackedValue.create(true, "mount") { option ->
        option.metadata(Comment.TYPE) { comments -> comments.add(
            "The entity that the opponent is riding will stay ridden by the player on swap"
        ) }
    }!!
    private val SWAP_HEALTH = TrackedValue.create(false, "health") {
        option -> option.metadata(Comment.TYPE) { comments -> comments.add(
            "Whether players will swap their health"
        ) }
    }!!
    private val SWAP_MOB_AGGRESSION = TrackedValue.create(false, "mob_aggression") {
        option -> option.metadata(Comment.TYPE) { comments -> comments.add(
            "Whether players will swap their mob aggression"
        ) }
    }!!
    private val SWAP_HUNGER = TrackedValue.create(false, "hunger") {
        option -> option.metadata(Comment.TYPE) { comments -> comments.add(
            "Whether players will swap their hunger"
        ) }
    }!!
    private val SWAP_FIRE = TrackedValue.create(false, "fire") {
        option -> option.metadata(Comment.TYPE) { comments -> comments.add(
            "Whether players will swap their fire"
        ) }
    }!!
    private val SWAP_AIR = TrackedValue.create(false, "air") {
        option -> option.metadata(Comment.TYPE) { comments -> comments.add(
            "Whether players will swap their air"
        ) }
    }!!
    private val SWAP_FROZEN = TrackedValue.create(false, "frozen") {
        option -> option.metadata(Comment.TYPE) { comments -> comments.add(
            "Whether players will swap their frozen state"
        ) }
    }!!
    private val SWAP_POTION_EFFECTS = TrackedValue.create(false, "potion_effects") {
        option -> option.metadata(Comment.TYPE) { comments -> comments.add(
            "Whether players will swap their potion effects"
        ) }
    }!!
    private val SWAP_INVENTORY = TrackedValue.create(false, "inventory") {
        option -> option.metadata(Comment.TYPE) { comments -> comments.add(
            "Whether players will swap their inventory"
        ) }
    }!!
    private val ENABLE_QUICK_SWAP = TrackedValue.create(true, "enable_quick_swap") { option ->
        option.metadata(Comment.TYPE) { comments -> comments.add(
            "Whether quick swaps are enabled"
        ) }
    }!!
    private val TELEPORT_LOAD_TIME = TrackedValue.create(20 * 5, "teleport_load_time") { option ->
        option.metadata(Comment.TYPE) { comments -> comments.add(
            "The number of ticks it takes for the player to load after teleporting",
            "Default 5 seconds"
        ) }
    }!!

    val CONFIG = QuiltConfig.create("qkdeathswap", "deathswap", consumerApply {
        section("swap_time") { section ->
            section.metadata(Comment.TYPE) { comments -> comments.add(
                "The amount of time between swaps, in ticks",
                "Default 1-3 minutes"
            ) }
            section.field(MIN_SWAP_TIME)
            section.field(MAX_SWAP_TIME)
            section.field(WARN_TIME)
        }
        section("spread_distance") { section ->
            section.metadata(Comment.TYPE) { comments -> comments.add(
                "The distance from 0,0 players are teleported to"
            ) }
            section.field(MIN_SPREAD_DISTANCE)
            section.field(MAX_SPREAD_DISTANCE)
        }
        field(DIMENSION)
        field(RESISTANCE_TIME)
        section("swap_options") { section ->
            section.field(SWAP_MOUNT)
            section.field(SWAP_HEALTH)
            section.field(SWAP_HUNGER)
            section.field(SWAP_MOB_AGGRESSION)
            section.field(SWAP_FIRE)
            section.field(SWAP_AIR)
            section.field(SWAP_FROZEN)
            section.field(SWAP_POTION_EFFECTS)
            section.field(SWAP_INVENTORY)
            section.field(ENABLE_QUICK_SWAP)
        }
        field(TELEPORT_LOAD_TIME)
    })!!

    val CONFIG_TYPES = mapOf(
        MIN_SWAP_TIME.key() to Pair(IntegerArgumentType.integer(0)) { v: Int -> v },
        MAX_SWAP_TIME.key() to Pair(IntegerArgumentType.integer(0)) { v: Int -> v },
        WARN_TIME.key() to Pair(IntegerArgumentType.integer(0)) { v: Int -> v },
        MIN_SPREAD_DISTANCE.key() to Pair(IntegerArgumentType.integer(0)) { v: Int -> v },
        MAX_SPREAD_DISTANCE.key() to Pair(IntegerArgumentType.integer(0)) { v: Int -> v },
        DIMENSION.key() to Pair(DimensionArgumentType.dimension()) { id: Identifier -> id.toString() },
        RESISTANCE_TIME.key() to Pair(IntegerArgumentType.integer(0)) { v: Int -> v },
        SWAP_MOUNT.key() to Pair(BoolArgumentType.bool()) { v: Boolean -> v },
        SWAP_HEALTH.key() to Pair(BoolArgumentType.bool()) { v: Boolean -> v },
        SWAP_HUNGER.key() to Pair(BoolArgumentType.bool()) { v: Boolean -> v },
        SWAP_MOB_AGGRESSION.key() to Pair(BoolArgumentType.bool()) { v: Boolean -> v },
        SWAP_FIRE.key() to Pair(BoolArgumentType.bool()) { v: Boolean -> v },
        SWAP_AIR.key() to Pair(BoolArgumentType.bool()) { v: Boolean -> v },
        SWAP_FROZEN.key() to Pair(BoolArgumentType.bool()) { v: Boolean -> v },
        SWAP_POTION_EFFECTS.key() to Pair(BoolArgumentType.bool()) { v: Boolean -> v },
        SWAP_INVENTORY.key() to Pair(BoolArgumentType.bool()) { v: Boolean -> v },
        ENABLE_QUICK_SWAP.key() to Pair(BoolArgumentType.bool()) { v: Boolean -> v },
        TELEPORT_LOAD_TIME.key() to Pair(IntegerArgumentType.integer(0)) { v: Int -> v }
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

    var warnTime: Int
        get() = WARN_TIME.value()
        set(value) {
            WARN_TIME.setValue(value, true)
        }

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

    var swapMount: Boolean
        get() = SWAP_MOUNT.value()
        set(value) {
            SWAP_MOUNT.setValue(value, true)
        }

    var swapHealth: Boolean
        get() = SWAP_HEALTH.value()
        set(value) {
            SWAP_HEALTH.setValue(value, true)
        }

    var swapHunger: Boolean
        get() = SWAP_HUNGER.value()
        set(value) {
            SWAP_HUNGER.setValue(value, true)
        }

    var swapMobAggression: Boolean
        get() = SWAP_MOB_AGGRESSION.value()
        set(value) {
            SWAP_MOB_AGGRESSION.setValue(value, true)
        }

    var swapFire: Boolean
        get() = SWAP_FIRE.value()
        set(value) {
            SWAP_FIRE.setValue(value, true)
        }

    var swapAir: Boolean
        get() = SWAP_AIR.value()
        set(value) {
            SWAP_AIR.setValue(value, true)
        }

    var swapFrozen: Boolean
        get() = SWAP_FROZEN.value()
        set(value) {
            SWAP_FROZEN.setValue(value, true)
        }

    var swapPotionEffects: Boolean
        get() = SWAP_POTION_EFFECTS.value()
        set(value) {
            SWAP_POTION_EFFECTS.setValue(value, true)
        }

    var swapInventory: Boolean
        get() = SWAP_INVENTORY.value()
        set(value) {
            SWAP_INVENTORY.setValue(value, true)
        }

    var enableQuickSwap: Boolean
        get() = ENABLE_QUICK_SWAP.value()
        set(value) {
            ENABLE_QUICK_SWAP.setValue(value, true)
        }

    var teleportLoadTime: Int
        get() = TELEPORT_LOAD_TIME.value()
        set(value) {
            TELEPORT_LOAD_TIME.setValue(value, true)
        }
}
