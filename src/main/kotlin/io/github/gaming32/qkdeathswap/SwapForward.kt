package io.github.gaming32.qkdeathswap

import io.github.gaming32.qkdeathswap.mixin.EntityAccessor
import net.minecraft.entity.Entity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.mob.Angerable
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.Vec3d


data class SwapForwardData(
    val thisPlayer: ServerPlayerEntity,
    val nextPlayer: ServerPlayerEntity,

    val vehicle: Entity? = nextPlayer.vehicle,

    val health: Float = nextPlayer.health,

    val food: Int = nextPlayer.hungerManager.foodLevel,
    val saturation: Float = nextPlayer.hungerManager.saturationLevel,

    val fireTicks: Int = nextPlayer.fireTicks,
    val frozenTicks: Int = nextPlayer.frozenTicks,

    val spawnPoint: Location? = nextPlayer.spawnLocation,

    val statusEffects: Map<StatusEffect, StatusEffectInstance>? = if (DeathSwapConfig.swapPotionEffects.value!!) nextPlayer.activeStatusEffects else null,

    val angryMobs: List<Entity>? = if (DeathSwapConfig.swapMobAggression.value!!) nextPlayer.getWorld().iterateEntities().filter { it is Angerable && it.angryAt == nextPlayer.uuid } else null,

    val air: Int = nextPlayer.air,

    var inventory: MutableList<ItemStack>? = null,

    val nextStartLocation: PlayerStartLocation = DeathSwapStateManager.livingPlayers[nextPlayer.uuid]!!.startLocation,

) {
    init {
        if (DeathSwapConfig.swapInventory.value!!) {
            val size = nextPlayer.inventory.size()
            inventory = mutableListOf()
            for (i in 0 until size) {
                inventory!!.add(nextPlayer.inventory.getStack(i))
            }
        } else {
            inventory = null
        }
    }
}

open class SwapForward(protected val thisPlayer: ServerPlayerEntity, protected val nextPlayer: ServerPlayerEntity) {
    protected lateinit var pos: Location
    protected lateinit var swapData: SwapForwardData
    private var tempEntity: Entity? = null


    open fun preLoad() {
        pos = nextPlayer.location
        swapData = SwapForwardData(thisPlayer, nextPlayer)

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
            tempEntity = ArmorStandEntity(world, pos.x, this.pos.y, pos.z).apply {
                isInvulnerable = true
                isInvisible = true
                setNoGravity(true)
                (this as EntityAccessor).setDimensions(nextPlayer.getDimensions(pos.pose))
            }
            world.spawnEntity(tempEntity)
        }
        thisPlayer.teleport(pos)
        thisPlayer.spawnLocation = pos
    }

    open fun preSwap() = Unit

    open fun swap(moreThanTwoPlayers: Boolean) {
        if (thisPlayer.server.playerManager.getPlayer(thisPlayer.uuid) != thisPlayer) {
            DeathSwapStateManager.removePlayer(thisPlayer.uuid, Text.literal(" timed out during swap").formatted(Formatting.RED))
        }
        DeathSwapStateManager.livingPlayers[thisPlayer.uuid]?.startLocation = swapData.nextStartLocation
        thisPlayer.velocity = Vec3d.ZERO
        thisPlayer.fallDistance = 0f

        val targetPos = if (!nextPlayer.isDead) pos else swapData.spawnPoint ?: pos

        thisPlayer.teleport(targetPos)
        thisPlayer.spawnLocation = swapData.spawnPoint
        tempEntity?.kill()

        if (DeathSwapConfig.swapHealth.value!!) {
            thisPlayer.health = swapData.health
        }
        if (DeathSwapConfig.swapHunger.value!!) {
            thisPlayer.hungerManager.foodLevel = swapData.food
            thisPlayer.hungerManager.saturationLevel = swapData.saturation
        }
        nextPlayer.stopRiding()
        if (DeathSwapConfig.swapMount.value!! && swapData.vehicle != null) {
            thisPlayer.startRiding(swapData.vehicle, true)
        }

        if (DeathSwapConfig.swapMobAggression.value!!) {
            swapMobAggression()
        }
        if (DeathSwapConfig.swapFire.value!!) {
            thisPlayer.fireTicks = swapData.fireTicks
        }
        if (DeathSwapConfig.swapAir.value!!) {
            thisPlayer.air = swapData.air
        }
        if (DeathSwapConfig.swapFrozen.value!!) {
            thisPlayer.frozenTicks = swapData.frozenTicks
        }
        if (DeathSwapConfig.swapPotionEffects.value!!) {
            thisPlayer.clearStatusEffects()
            swapData.statusEffects?.forEach {
                thisPlayer.addStatusEffect(it.value)
            }
        }
        if (DeathSwapConfig.swapInventory.value!!) {
            thisPlayer.inventory.clear()
            for (i in 0 until (swapData.inventory?.size ?: 0)) {
                thisPlayer.inventory.setStack(i, swapData.inventory?.get(i) ?: ItemStack.EMPTY)
            }
        }

        thisPlayer.sendMessage(
            Text.literal("You were teleported to ").formatted(Formatting.GRAY)
                .append(nextPlayer.displayName.copy().formatted(Formatting.GREEN)),
            false
        )
        if (moreThanTwoPlayers) {
            nextPlayer.sendMessage(
                thisPlayer.displayName.copy().formatted(Formatting.GREEN)
                    .append(Text.literal(" teleported to you").formatted(Formatting.GRAY)),
                false
            )
        }
    }

    private fun swapMobAggression() {
        swapData.angryMobs?.forEach {
            if (DeathSwapMod.LOGGER.isDebugEnabled) {
                DeathSwapMod.LOGGER.debug("Making ${it.displayName.string} angry at ${thisPlayer.displayName.string}")
            }
            (it as Angerable).angryAt = thisPlayer.uuid
            it.target = thisPlayer
        }
    }

}
