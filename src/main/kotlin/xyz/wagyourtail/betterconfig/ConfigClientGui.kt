package xyz.wagyourtail.betterconfig

import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text

class ConfigClientGui<T : ConfigGroup>(val config: T, val group: List<String>) : Screen(Text.literal(config.name)) {

    override fun init() {
        super.init()

        for (item in config.configItems.values) {
            addItem(item)
        }
    }

    fun addItem(item: ConfigItem<*, *>) {
        if (item.default is Boolean) {
            addBoolItem(item as ConfigItem<Boolean, *>)
        } else if (item.default is Enum<*>) {
            addEnumItem(item as ConfigItem<Enum<*>, *>)
        } else {
            addOtherItem(item)
        }
    }

    fun addBoolItem(item: ConfigItem<Boolean, *>) {
        TODO()
    }

    fun addEnumItem(item: ConfigItem<Enum<*>, *>) {
        TODO()
    }

    fun addOtherItem(item: ConfigItem<*, *>) {
        TODO()
    }

    override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(matrices)
        super.render(matrices, mouseX, mouseY, delta)
    }

}