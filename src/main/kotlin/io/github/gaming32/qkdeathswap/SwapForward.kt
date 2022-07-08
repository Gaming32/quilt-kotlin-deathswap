package io.github.gaming32.qkdeathswap

import net.minecraft.entity.mob.Angerable
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

    private val angryMobs = if (DeathSwapConfig.swapMobAggression) nextPlayer.getWorld().iterateEntities().filter { it is Angerable && it.angryAt == nextPlayer.uuid } else null

    private val air = nextPlayer.air

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