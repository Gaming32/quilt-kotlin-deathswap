package io.github.gaming32.qkdeathswap

import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries

object Presets {

    val builtin = listOf("classic", "the_end")


    fun list(): List<String> {
        return builtin +
            DeathSwapMod.presetsDir.listDirectoryEntries().mapNotNull { if(Files.isDirectory(it)) it.fileName.toString() else null }
    }

    fun load(preset: String) : Boolean {
        val configFile = DeathSwapMod.presetsDir.resolve("$preset/deathswap.toml")
        if (configFile.exists()) {
            val kitPreset = DeathSwapMod.presetsDir.resolve("$preset/default_kit.dat")
            if (kitPreset.exists()) {
                Files.copy(kitPreset, DeathSwapMod.configDir.resolve("default_kit.dat"), StandardCopyOption.REPLACE_EXISTING)
            } else {
                DeathSwapMod.configDir.resolve("default_kit.dat").deleteIfExists()
            }
            configFile.inputStream().use {
                DeathSwapConfig.copyFrom(DeathSwapConfig(it))
            }
            return true
        } else {
            val internalPath = "/assets/${MOD_ID}/presets/${preset}"
            val internalFile = "$internalPath/deathswap.toml"
            Presets::class.java.getResourceAsStream(internalFile)?.use { configStream ->
                val kitPreset = "$internalPath/default_kit.dat"
                Presets::class.java.getResourceAsStream(kitPreset).use { kitFileInputStream ->
                    if (kitFileInputStream != null) {
                        Files.copy(
                            kitFileInputStream,
                            DeathSwapMod.configDir.resolve("default_kit.dat"),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    } else {
                        DeathSwapMod.configDir.resolve("default_kit.dat").deleteIfExists()
                    }
                }
                DeathSwapConfig.copyFrom(DeathSwapConfig(configStream))
                return true
            }
            return false
        }
    }

    private fun findPresetConfig(preset: String): InputStream? {
        val configFile = DeathSwapMod.presetsDir.resolve("$preset/deathswap.toml")
        return if (configFile.exists()) {
            Files.newInputStream(configFile)
        } else {
            Presets::class.java.getResourceAsStream("/assets/${MOD_ID}/presets/${preset}/deathswap.toml")
        }
    }

    private fun findPresetKit(preset: String): InputStream? {
        val kitPreset = DeathSwapMod.presetsDir.resolve("$preset/default_kit.dat")
        return if (kitPreset.exists()) {
            Files.newInputStream(kitPreset)
        } else {
            Presets::class.java.getResourceAsStream("/assets/${MOD_ID}/presets/${preset}/default_kit.dat")
        }
    }

    fun preview(preset: String): Component? {
        findPresetConfig(preset).use { stream ->
            return if (stream != null) {
                DeathSwapConfig(stream).toText()
            } else {
                null
            }
        }
    }

    fun previewKit(preset: String): Inventory? {
        findPresetKit(preset).use { stream ->
            return if (stream != null) {
                return Inventory(null).apply {
                    load(
                        NbtIo.readCompressed(DeathSwapMod.defaultKitStoreLocation)
                            .getList("Inventory", Tag.TAG_COMPOUND.toInt())
                    )
                }
            } else {
                null
            }
        }
    }

    fun save(preset: String) {
        DeathSwapConfig.save()
        val configFile = DeathSwapMod.configDir.resolve("deathswap.toml")
        val presetFolder = DeathSwapMod.presetsDir.resolve(preset)
        if (!presetFolder.exists()) {
            presetFolder.toFile().mkdirs()
        }
        Files.copy(configFile, presetFolder.resolve("deathswap.toml"), StandardCopyOption.REPLACE_EXISTING)
        val kitFile = DeathSwapMod.configDir.resolve("default_kit.dat")
        if (kitFile.exists()) {
            Files.copy(kitFile, presetFolder.resolve("default_kit.dat"), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun delete(preset: String) : Boolean {
        val presetDir = DeathSwapMod.presetsDir.resolve(preset)
        return if (presetDir.exists()) {
            Files.walk(presetDir).forEach {
                it.deleteIfExists()
            }
            true
        } else {
            false
        }
    }
}