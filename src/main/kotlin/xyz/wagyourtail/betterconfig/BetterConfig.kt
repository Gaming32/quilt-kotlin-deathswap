package xyz.wagyourtail.betterconfig

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.github.gaming32.qkdeathswap.consumerApply
import net.minecraft.command.CommandSource
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.jetbrains.annotations.ApiStatus
import org.quiltmc.loader.impl.lib.electronwill.nightconfig.core.CommentedConfig
import org.quiltmc.loader.impl.lib.electronwill.nightconfig.toml.TomlWriter
import org.quiltmc.qkl.wrapper.minecraft.brigadier.argument
import org.quiltmc.qkl.wrapper.minecraft.brigadier.argument.literal
import org.quiltmc.qkl.wrapper.minecraft.brigadier.execute
import org.quiltmc.qkl.wrapper.minecraft.brigadier.optional
import org.quiltmc.qkl.wrapper.minecraft.brigadier.required
import java.io.OutputStream
import java.util.concurrent.CompletableFuture

private typealias BrigadierSuggestor<S> = (CommandContext<S>, SuggestionsBuilder) -> CompletableFuture<Suggestions>

/**
 * @author Wagyourtail
 */
@Suppress("UNCHECKED_CAST", "UNUSED")
abstract class BetterConfig<T : BetterConfig<T>>(
    displayName: String,

    @ApiStatus.Internal
    val configToml: CommentedConfig,

    private val saveStreamer: () -> OutputStream?
) : ConfigGroup(displayName, null, null) {

    override fun save() {
        save(null)
    }

    fun save(item: ConfigItem<*, *>? = null) {
        if (item != null) {
            item.write(false)
        } else {
            writeAll()
        }
        saveStreamer()?.use {
            TomlWriter().write(configToml.unmodifiable(), it)
        }
    }

    private fun <S> RequiredArgumentBuilder<S, *>.setSuggestor(suggestor: BrigadierSuggestor<CommandSource>) {
        suggests(suggestor as BrigadierSuggestor<S>)
    }

    fun buildArguments(parent: ArgumentBuilder<ServerCommandSource, *>) {
        flatItems.forEach { entry ->
            val configItem = entry.value as ConfigItem<Any?, Any?>
            if (configItem.brigadierType != null) {
                parent.required(literal(configItem.key)) {
                    optional(argument("value", configItem.brigadierType)) { valueAccess ->
                        if (this is RequiredArgumentBuilder<*, *> && configItem.suggestor != null) {
                            setSuggestor(configItem.suggestor)
                        }
                        execute {
                            if (valueAccess != null) {
                                val newValue = (configItem.brigadierDesierializer as (Any) -> Any)(
                                    getArgument(
                                        "value",
                                        Any::class.java
                                    )
                                )
                                if (!configItem.brigadierFilter(source, newValue)) {
                                    source.sendFeedback(
                                        Text.literal("")
                                            .append(Text.literal(newValue.toString()).formatted(Formatting.RED))
                                            .append(" is not a valid value for")
                                            .append(configItem.key),
                                        false
                                    )
                                } else {
                                    configItem.value = newValue
                                    save(configItem)
                                    source.sendFeedback(
                                        configItem.toText(),
                                        false
                                    )
                                }
                            } else {
                                source.sendFeedback(
                                    configItem.toText(),
                                    false
                                )
                            }
                        }
                    }
                }
            }
        }
        parent.execute {
            source.sendFeedback(toText(), false)
        }
    }

    fun toText(): Text {
        val text = Text.literal("Config: ")
        flatItems.toSortedMap().forEach(consumerApply {
            text.append("\n").append(value.toText())
        })
        return text
    }

    open fun copyFrom(other: T) {
        other.flatItems.forEach(consumerApply {
            val item = flatItems[key] as ConfigItem<Any?, Any?>?
            if (item != null) {
                item.value = value.value
            }
        })
    }

    override fun write(writeParent: Boolean) {
        //no-op
    }

}

@Suppress("UNCHECKED_CAST", "UNUSED", "MemberVisibilityCanBePrivate")
open class ConfigGroup(
    val name: String,
    val parent: ConfigGroup?,
    val comment: String?
) {


    @ApiStatus.Internal
    val configItems = mutableMapOf<String, ConfigItem<*, *>>()

    @ApiStatus.Internal
    val configGroups = mutableListOf<ConfigGroup>()

    @get:ApiStatus.Internal
    val flatItems: Map<String, ConfigItem<*, *>>
        get() {
            val combined = mutableMapOf<String, ConfigItem<*, *>>()
            combined.putAll(configItems)
            configGroups.forEach {
                combined.putAll(it.flatItems)
            }
            return combined
        }

    val parentConfig: BetterConfig<*>
        get() {
            var current: ConfigGroup? = this
            while (current != null) {
                if (current is BetterConfig<*>) {
                    return current
                }
                current = current.parent
            }
            throw IllegalStateException("ConfigGroup has no parent config")
        }
    inline fun <reified T, reified U> setting(
        name: String,
        default: T,
        brigadierType: ArgumentType<U>?,
        comment: String? = null,
        noinline brigadierDeserializer: (U) -> T = { if (it is T) it else if (null is T) it as T else throw IllegalArgumentException("Invalid type") },
        noinline textValue: (T) -> Text = { Text.literal(it.toString()) as Text },
        noinline serializer: (T) -> Any? = { it },
        noinline deserializer: (Any?) -> T = { if (it is T) it else if (null is T) it as T else throw IllegalArgumentException("Invalid type") },
        noinline brigadierFilter: (CommandSource, U) -> Boolean = { _, _ -> true },
        noinline brigadierSuggestor: BrigadierSuggestor<CommandSource>? = null,
    ): ConfigItem<T, U> {
        val configItem = ConfigItem(
            name,
            this,
            comment,
            textValue,
            default,
            serializer,
            deserializer,
            brigadierType,
            brigadierDeserializer,
            brigadierFilter,
            brigadierSuggestor,
        )
        configItems[configItem.key] = configItem as ConfigItem<Any, Any>
        return configItem
    }

    fun group(group: String, comment: String? = null): ConfigGroup {
        val configGroup = ConfigGroup(group, this, comment)
        configGroups.add(configGroup)
        return configGroup
    }

    open fun write(writeParent: Boolean = true) {
        parentConfig.configToml.setComment(key, comment)
        if (writeParent) {
            parent?.write()
        }
    }

    fun writeAll() {
        write(false)
        configGroups.forEach { it.writeAll() }
    }

    open fun save() {
        write()
        parentConfig.save()
    }

    val key: List<String>
        get() = if (this is BetterConfig<*>) listOf() else (parent?.key ?: listOf()) + name
}

@Suppress("UNUSED")
data class ConfigItem<T, U>(
    val name: String,
    val group: ConfigGroup,
    var comment: String?,
    val textValue: (T) -> Text,


    @Suppress("MemberVisibilityCanBePrivate")
    val default: T,

    val serializer: (T) -> Any?,
    val deserializer: (Any?) -> T,

    val brigadierType: ArgumentType<U>?,
    val brigadierDesierializer: (U) -> T,
    val brigadierFilter: (CommandSource, U) -> Boolean,
    val suggestor: BrigadierSuggestor<CommandSource>?
) {

    private fun loadValue(): T {
        return deserializer(group.parentConfig.configToml.get(tomlKey))
    }

    fun write(writeParent: Boolean = true) {
        group.parentConfig.configToml.set<Any>(tomlKey, value?.let { serializer(it) })
        group.parentConfig.configToml.setComment(tomlKey, comment)
        group.write(writeParent)
    }

    var value: T = loadValue() ?: default
        set(value) {
            field = value
            group.parentConfig.save(this)
        }

    fun reset() {
        value = default
    }

    private val tomlKey: List<String>
        get() = group.key + name

    val key: String
        get() = (group.key + name).joinToString(".")

    fun toText(): Text {
        return Text.literal(key).append(" -> ").append(textValue(value))
    }

}


