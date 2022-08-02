package xyz.wagyourtail.betterconfig

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import kotlin.math.ceil

class InventoryBasedMenuBuilder(
    private val title: Text,
    private val size: Int,
) {

    private val sizeUp = (ceil(size / 9.toDouble()) * 9).toInt()

    private val sizeEnum = if (size > 9 * 6) throw IllegalArgumentException("Inventory size must be <= 9 * 6")
        else if (size > 9 * 5) ScreenHandlerType.GENERIC_9X6
        else if (size > 9 * 4) ScreenHandlerType.GENERIC_9X5
        else if (size > 9 * 3) ScreenHandlerType.GENERIC_9X4
        else if (size > 9 * 2) ScreenHandlerType.GENERIC_9X3
        else if (size > 9 * 1) ScreenHandlerType.GENERIC_9X2
        else ScreenHandlerType.GENERIC_3X3


    private val items = Array<MenuItem?>(size) { null }

    fun addItem(item: MenuItem) {
        if (!(item.slot in 0 until size)) {
            throw IllegalArgumentException("Slot ${item.slot} must be between 0 and ${size - 1}")
        }
        items[item.slot] = item
    }

    var onClose: ((GenericContainerScreenHandler, PlayerEntity) -> Unit)? = null

    fun build(): NamedScreenHandlerFactory {
        return object : NamedScreenHandlerFactory {

            override fun createMenu(
                syncId: Int,
                playerInventory: PlayerInventory?,
                playerEntity: PlayerEntity?
            ): ScreenHandler {
                val handler = object : GenericContainerScreenHandler(
                    sizeEnum,
                    syncId,
                    playerInventory,
                    object : SimpleInventory(sizeUp) {
                    },
                    sizeUp / 9
                ) {
                    override fun close(player: PlayerEntity) {
                        super.close(player)
                        onClose?.invoke(this, player)
                    }

                    override fun onSlotClick(
                        slotIndex: Int,
                        button: Int,
                        actionType: SlotActionType,
                        player: PlayerEntity
                    ) {
                        if (slotIndex > sizeUp) {
                            super.onSlotClick(slotIndex, button, actionType, player)
                        } else {
                            items[slotIndex]?.let { it.clickAction.invoke(it, this, slotIndex, button, actionType, player) }
                            updateToClient()
                        }
                    }

                }

                for (i in 0 until size) {
                    copyIntoInventory(i, handler.inventory)
                }

                return handler
            }

            override fun getDisplayName(): Text {
                return title
            }
        }
    }

    fun copyIntoInventory(slot: Int, inventory: Inventory) {
        inventory.setStack(slot, items[slot]?.stack ?: ItemStack.EMPTY)
    }
}

data class MenuItem(
    val slot: Int,
    val item: Item,
    val title: Text,
    val tooltip: Text?,
    val clickAction: (MenuItem, GenericContainerScreenHandler, Int, Int, SlotActionType, PlayerEntity) -> Unit, // todo: figure out the appropriate signature
    val enchanted: Boolean,
    val count: Int
) {

    fun changeSlot(handler: GenericContainerScreenHandler) {
        handler.inventory.setStack(slot, stack)
    }

    val stack: ItemStack
        get() {
            return ItemStack(item, count).apply {
                setCustomName(title)
                orCreateNbt.apply {
                    if (enchanted) {
                        put("Enchantments", NbtList())
                    }
                    if (tooltip != null) {
                        put("display", NbtCompound().apply {
                            put("Lore", NbtList().apply {
                                add(NbtString.of(Text.Serializer.toJson(tooltip)))
                            })
                        })
                    }
                }
            }
        }
}