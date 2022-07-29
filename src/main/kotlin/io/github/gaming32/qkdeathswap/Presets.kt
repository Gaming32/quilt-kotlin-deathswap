package io.github.gaming32.qkdeathswap

import org.quiltmc.config.api.values.TrackedValue
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

object Presets {

    fun listPresets(): List<String> {
        return listOf("classic", "the_end") +
            DeathSwapMod.presetsDir.listDirectoryEntries().mapNotNull { if(Files.isDirectory(it)) null else it.fileName.toString() }
    }

    fun loadPreset(preset: String, configname: String = "deathswap", kitName: String = "default_kit") : Boolean {
        val configFile = DeathSwapMod.presetsDir.resolve("$preset/deathswap.toml")
        if (configFile.exists()) {
            Files.copy(configFile, DeathSwapMod.configDir.resolve("$configname.toml"), StandardCopyOption.REPLACE_EXISTING)
            val kitPreset = DeathSwapMod.presetsDir.resolve("$preset/default_kit.toml")
            if (kitPreset.exists()) {
                Files.copy(kitPreset, DeathSwapMod.configDir.resolve("$kitName.dat"), StandardCopyOption.REPLACE_EXISTING)
            } else {
                DeathSwapMod.configDir.resolve("default_kit.dat").deleteIfExists()
            }
            return true
        } else {
            val internalPath = "/assets/${MOD_ID}/presets/${preset}"
            val internalFile = "$internalPath/deathswap.toml"
            val fileInputStream = Presets::class.java.getResourceAsStream(internalFile)
            if (fileInputStream != null) {
                Files.copy(fileInputStream, DeathSwapMod.configDir.resolve("$configname.toml"), StandardCopyOption.REPLACE_EXISTING)
                val kitPreset = "$internalPath/default_kit.toml"
                val kitFileInputStream = Presets::class.java.getResourceAsStream(kitPreset)
                if (kitFileInputStream != null) {
                    Files.copy(kitFileInputStream, DeathSwapMod.configDir.resolve("default_kit.dat"), StandardCopyOption.REPLACE_EXISTING)
                } else {
                    DeathSwapMod.configDir.resolve("$kitName.dat").deleteIfExists()
                }
                return true
            } else {
                return false
            }
        }
    }

    fun previewPreset(preset: String): Iterable<TrackedValue<*>> {
        loadPreset(preset, "tmp", "tmp")
        val config = DeathSwapConfig.loadConfig("tmp")
        return config.values()
    }

    fun previewPresetKit(preset: String): Iterable<TrackedValue<*>> {
        loadPreset(preset, "tmp", "tmp")
        TODO("not implemented")
    }

    fun savePreset(preset: String) {
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

    fun deletePreset(preset: String) : Boolean {
        val configDir = DeathSwapMod.configDir
        val presetDir = DeathSwapMod.presetsDir.resolve(preset)
        if (presetDir.exists()) {
            Files.walk(presetDir).forEach {
                it.deleteIfExists()
            }
            return true
        } else {
            return false
        }
    }
}