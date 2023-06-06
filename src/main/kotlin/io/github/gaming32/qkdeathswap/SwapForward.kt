package io.github.gaming32.qkdeathswap

import io.github.gaming32.qkdeathswap.mixin.EntityAccessor
import io.github.gaming32.qkdeathswap.mixin.WardenSpawnTrackerAccessor
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.NeutralMob
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

class SwapForward(private val thisPlayer: ServerPlayer, private val nextPlayer: ServerPlayer) {
    private val pos = nextPlayer.location

    private val vehicle = nextPlayer.vehicle

    private val health = nextPlayer.health

    private val food = nextPlayer.foodData.foodLevel
    private val saturation = nextPlayer.foodData.saturationLevel

    private val fireTicks = nextPlayer.remainingFireTicks
    private val frozenTicks = nextPlayer.ticksFrozen

    private val spawnPoint = nextPlayer.spawnLocation

    private val statusEffects = if (DeathSwapConfig.swapPotionEffects.value) nextPlayer.activeEffectsMap else null

    private val angryMobs = if (DeathSwapConfig.swapMobAggression.value) {
        nextPlayer.getLevel().allEntities.filter { it is NeutralMob && it.persistentAngerTarget == nextPlayer.uuid }
    } else {
        null
    }

    private val wardenTracker = nextPlayer.wardenSpawnTracker.get()

    private val air = nextPlayer.airSupply

    private val inventory: List<ItemStack>?

    private var tempEntity: Entity? = null
    private val nextStartLocation = DeathSwapStateManager.livingPlayers[nextPlayer.uuid]!!.startLocation

    init {
        if (DeathSwapConfig.swapInventory.value) {
            val size = nextPlayer.inventory.containerSize
            inventory = mutableListOf()
            for (i in 0 until size) {
                inventory.add(nextPlayer.inventory.getItem(i))
            }
        } else {
            inventory = null
        }
    }

    fun preSwap() {
        val pos = Location(
            pos.world,
            pos.x,
            pos.y + 10000.0,
            pos.z,
            pos.yaw,
            pos.pitch,
            pos.pose
        )
        val world = pos.getWorld(this.thisPlayer.server)
        if (world != null) {
            tempEntity = ArmorStand(world, pos.x, this.pos.y, pos.z).apply {
                isInvulnerable = true
                isInvisible = true
                isNoGravity = true
                @Suppress("KotlinConstantConditions")
                (this as EntityAccessor).setDimensions(nextPlayer.getDimensions(pos.pose))
            }
            world.addFreshEntity(tempEntity)
        }
        thisPlayer.teleport(pos)
        thisPlayer.spawnLocation = pos
    }

    fun swap(moreThanTwoPlayers: Boolean) {
        DeathSwapStateManager.livingPlayers[thisPlayer.uuid]?.startLocation = nextStartLocation
        thisPlayer.deltaMovement = Vec3.ZERO
        thisPlayer.fallDistance = 0f

        val targetPos = if (nextPlayer.isDeadOrDying && spawnPoint != null) spawnPoint else pos

        thisPlayer.teleport(targetPos)
        thisPlayer.spawnLocation = spawnPoint
        tempEntity?.kill()

        if (DeathSwapConfig.swapHealth.value) {
            thisPlayer.health = health
        }
        if (DeathSwapConfig.swapHunger.value) {
            thisPlayer.foodData.foodLevel = food
            thisPlayer.foodData.setSaturation(saturation)
        }
        nextPlayer.stopRiding()
        if (DeathSwapConfig.swapMount.value && vehicle != null) {
            thisPlayer.startRiding(vehicle, true)
        }

        if (DeathSwapConfig.swapMobAggression.value) {
            swapMobAggression()
        }
        if (DeathSwapConfig.swapFire.value) {
            thisPlayer.remainingFireTicks = fireTicks
        }
        if (DeathSwapConfig.swapAir.value) {
            thisPlayer.airSupply = air
        }
        if (DeathSwapConfig.swapFrozen.value) {
            thisPlayer.ticksFrozen = frozenTicks
        }
        if (DeathSwapConfig.swapPotionEffects.value) {
            thisPlayer.removeAllEffects()
            statusEffects?.forEach {
                thisPlayer.addEffect(it.value)
            }
        }
        if (DeathSwapConfig.swapInventory.value) {
            thisPlayer.inventory.clearContent()
            for (i in 0 until (inventory?.size ?: 0)) {
                thisPlayer.inventory.setItem(i, inventory?.get(i) ?: ItemStack.EMPTY)
            }
        }

        if (moreThanTwoPlayers) {
            thisPlayer.displayClientMessage(
                Component.literal("You were teleported to ").withStyle(ChatFormatting.GRAY)
                    .append(nextPlayer.displayName.copy().withStyle(ChatFormatting.GREEN)),
                false
            )
            nextPlayer.displayClientMessage(
                thisPlayer.displayName.copy().withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(" teleported to you").withStyle(ChatFormatting.GRAY)),
                false
            )
        } else {
            thisPlayer.displayClientMessage(
                Component.literal("You swapped with ").withStyle(ChatFormatting.GRAY)
                    .append(nextPlayer.displayName.copy().withStyle(ChatFormatting.GREEN)),
                false
            )
        }
    }

    private fun swapMobAggression() {
        angryMobs?.forEach {
            if (DeathSwapMod.LOGGER.isDebugEnabled) {
                DeathSwapMod.LOGGER.debug("Making ${it.displayName.string} angry at ${thisPlayer.displayName.string}")
            }
            (it as NeutralMob).persistentAngerTarget = thisPlayer.uuid
            it.target = thisPlayer
        }
        (thisPlayer.wardenSpawnTracker.get() as WardenSpawnTrackerAccessor).invokeCopyData(wardenTracker)
    }

}
