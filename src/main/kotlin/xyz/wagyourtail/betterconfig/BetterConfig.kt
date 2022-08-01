package xyz.wagyourtail.betterconfig

import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.EmptyByteBuf
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.Identifier
import org.quiltmc.qkl.wrapper.qsl.client.networking.onPlayConnectionReady
import org.quiltmc.qkl.wrapper.qsl.networking.onPlayDisconnect
import org.quiltmc.qkl.wrapper.qsl.registerEvents
import org.quiltmc.qsl.networking.api.ServerPlayNetworking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

object BetterConfig {

    const val MOD_ID = "betterconfig"

    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger(BetterConfig::class.java)

    private val playersAvailable = mutableSetOf<UUID>()

    private val remoteGuis = mutableMapOf<UUID, ConfigRemoteServer<*>>()

    fun bootstrapClient() {
        registerEvents {
            onPlayConnectionReady { sender, _ ->
                sender.sendPacket(Identifier(MOD_ID, "available"), PacketByteBuf(EmptyByteBuf(ByteBufAllocator.DEFAULT)))
            }
        }
    }

    fun bootstrap() {
        ServerPlayNetworking.registerGlobalReceiver(Identifier(MOD_ID, "available")) { _, player, _, _, _ ->
            playersAvailable.add(player.uuid)
            LOGGER.info("Player {} has $MOD_ID", player.name)
        }
        registerEvents {
            onPlayDisconnect {
                playersAvailable.remove(player.uuid)
                remoteGuis.remove(player.uuid)
            }
        }
    }
}