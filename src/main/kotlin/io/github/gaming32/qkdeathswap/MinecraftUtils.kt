package io.github.gaming32.qkdeathswap

import net.minecraft.network.MessageType
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text

fun MinecraftServer.broadcast(message: String) {
    broadcast(Text.literal(message))
}

fun MinecraftServer.broadcast(message: Text) {
    playerManager.broadcastSystemMessage(message, MessageType.SYSTEM)
}
