package io.github.gaming32.qkdeathswap

import io.github.gaming32.qkdeathswap.mixin.EntityAccessor
import net.minecraft.entity.Entity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.mob.Angerable
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.Vec3d

open class SwapForward(protected val thisPlayer: ServerPlayerEntity, private val nextPlayer: ServerPlayerEntity) {
    protected val pos = nextPlayer.location

    private val vehicle = nextPlayer.vehicle

    private val health = nextPlayer.health

    private val food = nextPlayer.hungerManager.foodLevel
    private val saturation = nextPlayer.hungerManager.saturationLevel

    private val fireTicks = nextPlayer.fireTicks
    private val frozenTicks = nextPlayer.frozenTicks

    private val spawnPoint = nextPlayer.spawnLocation

    private val statusEffects = if (DeathSwapConfig.swapPotionEffects.value!!) nextPlayer.activeStatusEffects else null

    private val angryMobs = if (DeathSwapConfig.swapMobAggression.value!!) nextPlayer.getWorld().iterateEntities().filter { it is Angerable && it.angryAt == nextPlayer.uuid } else null

    private val air = nextPlayer.air

    private val inventory: List<ItemStack>?

    private var tempEntity: Entity? = null
    private val nextStartLocation = DeathSwapStateManager.livingPlayers[nextPlayer.uuid]!!.startLocation

    init {
        if (DeathSwapConfig.swapInventory.value!!) {
            val size = nextPlayer.inventory.size()
            inventory = mutableListOf()
            for (i in 0 until size) {
                inventory.add(nextPlayer.inventory.getStack(i))
            }
        } else {
            inventory = null
        }
    }

    open fun preSwap() {
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

    open fun swap(moreThanTwoPlayers: Boolean) {
        if (thisPlayer.server.playerManager.getPlayer(thisPlayer.uuid) != thisPlayer) {
            DeathSwapStateManager.removePlayer(thisPlayer.uuid, Text.literal(" timed out during swap").formatted(Formatting.RED))
        }
        DeathSwapStateManager.livingPlayers[thisPlayer.uuid]?.startLocation = nextStartLocation
        thisPlayer.velocity = Vec3d.ZERO
        thisPlayer.fallDistance = 0f

        val targetPos = if (nextPlayer.isDead && spawnPoint != null) spawnPoint else pos

        thisPlayer.teleport(targetPos)
        thisPlayer.spawnLocation = spawnPoint
        tempEntity?.kill()

        if (DeathSwapConfig.swapHealth.value!!) {
            thisPlayer.health = health
        }
        if (DeathSwapConfig.swapHunger.value!!) {
            thisPlayer.hungerManager.foodLevel = food
            thisPlayer.hungerManager.saturationLevel = saturation
        }
        nextPlayer.stopRiding()
        if (DeathSwapConfig.swapMount.value!! && vehicle != null) {
            thisPlayer.startRiding(vehicle, true)
        }

        if (DeathSwapConfig.swapMobAggression.value!!) {
            swapMobAggression()
        }
        if (DeathSwapConfig.swapFire.value!!) {
            thisPlayer.fireTicks = fireTicks
        }
        if (DeathSwapConfig.swapAir.value!!) {
            thisPlayer.air = air
        }
        if (DeathSwapConfig.swapFrozen.value!!) {
            thisPlayer.frozenTicks = frozenTicks
        }
        if (DeathSwapConfig.swapPotionEffects.value!!) {
            thisPlayer.clearStatusEffects()
            statusEffects?.forEach {
                thisPlayer.addStatusEffect(it.value)
            }
        }
        if (DeathSwapConfig.swapInventory.value!!) {
            thisPlayer.inventory.clear()
            for (i in 0 until (inventory?.size ?: 0)) {
                thisPlayer.inventory.setStack(i, inventory?.get(i) ?: ItemStack.EMPTY)
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
        angryMobs?.forEach {
            if (DeathSwapMod.LOGGER.isDebugEnabled) {
                DeathSwapMod.LOGGER.debug("Making ${it.displayName.string} angry at ${thisPlayer.displayName.string}")
            }
            (it as Angerable).angryAt = thisPlayer.uuid
            it.target = thisPlayer
        }
    }

}
