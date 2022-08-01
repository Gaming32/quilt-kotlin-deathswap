package xyz.wagyourtail.betterconfig

import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text

class ConfigRemoteClientGui(name: String) : Screen(Text.literal(name)) {

    override fun init() {
        super.init()

        TODO()
    }

    override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(matrices)
        super.render(matrices, mouseX, mouseY, delta)
    }

}

class ConfigRemoteServer<T : ConfigGroup>(val config: T) {
}