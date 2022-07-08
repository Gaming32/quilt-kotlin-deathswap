package io.github.gaming32.qkdeathswap

import net.minecraft.entity.mob.Angerable
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting

class SwapForward(private val thisPlayer: ServerPlayerEntity, private val nextPlayer: ServerPlayerEntity) {
    private val pos = nextPlayer.location

    private val vehicle = nextPlayer.vehicle

    private val health = nextPlayer.health

    private val food = nextPlayer.hungerManager.foodLevel
    private val saturation = nextPlayer.hungerManager.saturationLevel

    private val fireTicks = nextPlayer.fireTicks
    private val frozenTicks = nextPlayer.frozenTicks

    private val statusEffects = if (DeathSwapConfig.swapPotionEffects) nextPlayer.activeStatusEffects.toMap() else null

    private val angryMobs = if (DeathSwapConfig.swapMobAggression) nextPlayer.getWorld().iterateEntities().filter { it is Angerable && it.angryAt == nextPlayer.uuid } else null

    private val air = nextPlayer.air

    private val inventory: List<ItemStack>?

    init {
        if (DeathSwapConfig.swapInventory) {
            val size = nextPlayer.inventory.size()
            inventory = mutableListOf()
            for (i in 0 until size) {
                inventory.add(nextPlayer.inventory.getStack(i))
            }
        } else {
            inventory = null
        }
    }

    fun swap() {
        thisPlayer.teleport(pos)
        if (DeathSwapConfig.swapHealth) {
            thisPlayer.health = health
        }
        if (DeathSwapConfig.swapHunger) {
            thisPlayer.hungerManager.foodLevel = food
            thisPlayer.hungerManager.saturationLevel = saturation
        }
        nextPlayer.stopRiding()
        if (DeathSwapConfig.swapMount && vehicle != null) {
            thisPlayer.startRiding(vehicle, true)
        }

        if (DeathSwapConfig.swapMobAggression) {
            swapMobAggression()
        }
        if (DeathSwapConfig.swapFire) {
            thisPlayer.fireTicks = fireTicks
        }
        if (DeathSwapConfig.swapAir) {
            thisPlayer.air = air
        }
        if (DeathSwapConfig.swapFrozen) {
            thisPlayer.frozenTicks = frozenTicks
        }
        if (DeathSwapConfig.swapPotionEffects) {
            thisPlayer.clearStatusEffects()
            statusEffects?.forEach {
                thisPlayer.addStatusEffect(it.value)
            }
        }
        if (DeathSwapConfig.swapInventory) {
            thisPlayer.inventory.clear()
            for (i in 0 until (inventory?.size ?: 0)) {
                thisPlayer.inventory.setStack(i, inventory?.get(i) ?: ItemStack.EMPTY)
            }
        }

        thisPlayer.sendMessage(
            Text.literal("You were teleported to ")
                .append(nextPlayer.displayName.copy().formatted(Formatting.GREEN)),
            false
        )
        nextPlayer.sendMessage(
            thisPlayer.displayName.copy().formatted(Formatting.GREEN)
                .append(Text.literal(" teleported to you").formatted(Formatting.WHITE)),
        false
        )
    }

    private fun swapMobAggression() {
        angryMobs?.forEach {
            println("making " + it.displayName + " angry at " + thisPlayer.displayName)
            (it as Angerable).angryAt = thisPlayer.uuid
            it.target = thisPlayer
        }
    }

}