package io.github.gaming32.qkdeathswap

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.gson.JsonParser
import net.minecraft.SharedConstants
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import org.quiltmc.loader.api.QuiltLoader
import org.quiltmc.qsl.resource.loader.api.QuiltResourcePack
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.file.*
import javax.imageio.ImageIO
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

object ResourcePackManager {
    val GAME_VERSION = SharedConstants.getCurrentVersion().id!!
    val RESOURCEPACK_PATH = DeathSwapMod.configDir / "resourcepack.zip"
    val GAME_JAR_PATH = DeathSwapMod.cacheDir / "mc-$GAME_VERSION.jar"

    private val fallbackTexture = run {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.BLACK
        g.fillRect(0, 0, 16, 16)
        g.color = Color(0xf800f8)
        g.fillRect(8, 0, 8, 8)
        g.fillRect(0, 8, 8, 8)
        g.dispose()
        image
    }
    private var resourcePack: FileSystem? = null
    private var gameJar: FileSystem? = null

    private val imageCache = CacheBuilder.newBuilder()
        .expireAfterAccess(5.minutes.toJavaDuration())
        .build<ResourceLocation, BufferedImage>(CacheLoader.from { location ->
            getInputStream(QuiltResourcePack.getResourcePath(PackType.CLIENT_RESOURCES, location))
                ?.let(ImageIO::read)
                ?: fallbackTexture
        })

    private fun getInputStream(path: String): InputStream? {
        getResourcePack()?.getPath(path)?.successOrNull<IOException, _, _> { inputStream() }?.let { return it }
        for (mod in QuiltLoader.getAllMods()) {
            mod.getPath(path)?.tryGetInputStream()?.let { return it }
        }
        return getGameJar()?.getPath(path)?.tryGetInputStream()
    }

    private fun Path.tryGetInputStream() = successOrNull<IOException, _, _> { inputStream() }

    private fun getResourcePack() = resourcePack
        ?: FileSystems.newFileSystem(RESOURCEPACK_PATH, mapOf("create" to true))
            .apply { resourcePack = this }

    private fun getGameJar() = try {
        gameJar ?: FileSystems.newFileSystem(GAME_JAR_PATH).apply { gameJar = this }
    } catch (e1: IOException) {
        try {
            DeathSwapMod.LOGGER.info("Downloading client.jar for its resourcepack")
            URL("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json")
                .openStream()
                .reader()
                .use(JsonParser::parseReader)
                .asJsonObject["versions"]
                .asJsonArray
                .first { it.asJsonObject["id"].asString == GAME_VERSION }
                .asJsonObject["url"]
                .asString
                .let(::URL)
                .openStream()
                .reader()
                .use(JsonParser::parseReader)
                .asJsonObject["downloads"]
                .asJsonObject["client"]
                .asJsonObject["url"]
                .asString
                .let(::URL)
                .openStream()
                .use { Files.copy(it, GAME_JAR_PATH, StandardCopyOption.REPLACE_EXISTING) }
            DeathSwapMod.LOGGER.info("Done downloading client.jar for its resourcepack")
            FileSystems.newFileSystem(GAME_JAR_PATH).apply { gameJar = this }
        } catch (e2: IOException) {
            e2.addSuppressed(e1)
            DeathSwapMod.LOGGER.error("Failed to download game JAR", e2)
            null
        }
    }

    fun reloadTextures() {
        resourcePack?.close()
        resourcePack = null
        gameJar?.close()
        gameJar = null
        imageCache.invalidateAll()
    }

    fun getTexture(texture: ResourceLocation): BufferedImage = imageCache[texture]
}
