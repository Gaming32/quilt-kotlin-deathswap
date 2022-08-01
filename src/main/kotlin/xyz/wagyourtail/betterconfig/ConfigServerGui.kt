package xyz.wagyourtail.betterconfig

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.ScreenHandler
import net.minecraft.text.Text

class ConfigServerGui(config: ConfigGroup, page: Int = 0) : NamedScreenHandlerFactory {
    override fun createMenu(i: Int, playerInventory: PlayerInventory?, playerEntity: PlayerEntity?): ScreenHandler? {
        TODO("Not yet implemented")
    }

    override fun getDisplayName(): Text {
        TODO("Not yet implemented")
    }

}