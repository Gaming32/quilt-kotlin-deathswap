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
open class BetterConfig<T : BetterConfig<T>>(
    private val configToml: CommentedConfig,
    private val saveStreamer: () -> OutputStream?
) {
    @ApiStatus.Internal
    val configItems = mutableMapOf<String, ConfigItem<Any, Any>>()

    @ApiStatus.Internal
    val configGroups = mutableListOf<ConfigGroup>()

    @ApiStatus.Internal
    @Suppress("LeakingThis")
    val emptyGroup = ConfigGroup(this, null, null, null)

    inline fun <reified T : Any, reified U : Any> setting(
        name: String,
        default: T?,
        brigadierType: ArgumentType<U>?,
        comment: String? = null,
        noinline textValue: (T?) -> Text = { Text.literal(it.toString()) as Text },
        noinline serializer: (T?) -> Any? = { it },
        noinline deserializer: (Any?) -> T? = { if (it is T) it else null },
        noinline brigadierDeserializer: (U) -> T? = { if (it is T) it else null },
        noinline brigadierFilter: (CommandSource, U) -> Boolean = { _, _ -> true },
        noinline brigadierSuggestor: BrigadierSuggestor<CommandSource>? = null,
    ): ConfigItem<T, U> {
        val configItem = ConfigItem(
            this,
            name,
            emptyGroup,
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
        val configGroup = ConfigGroup(this, group, emptyGroup, comment)
        configGroups.add(configGroup)
        return configGroup
    }

    fun save(item: ConfigItem<Any, Any>? = null) {
        if (item != null) {
            configToml.set<Any>(item.group.key + item.name, item.value?.let { item.serializer(it) })
            configToml.setComment(item.group.key + item.name, item.comment)
        } else {
            configItems.forEach(consumerApply {
                configToml.set<Any>(key, value.value?.let { value.serializer(it) })
                configToml.setComment(key, value.comment)
            })
        }
        saveStreamer()?.use {
            TomlWriter().write(configToml.unmodifiable(), it)
        }
    }

    private fun <S> RequiredArgumentBuilder<S, *>.setSuggestor(suggestor: BrigadierSuggestor<CommandSource>) {
        suggests(suggestor as BrigadierSuggestor<S>)
    }

    fun buildArguments(parent: ArgumentBuilder<ServerCommandSource, *>) {
        configItems.forEach { entry ->
            val configItem = entry.value
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
        configItems.forEach(consumerApply {
            text.append("\n").append(value.toText())
        })
        return text
    }

    open fun copyFrom(other: T) {
        other.configItems.forEach(consumerApply {
            configItems[key]?.value = value.value
        })
    }

    data class ConfigGroup(
        val config: BetterConfig<*>,
        val name: String?,
        val parent: ConfigGroup?,
        val comment: String?
    ) {

        inline fun <reified T : Any, reified U : Any> setting(
            name: String,
            default: T,
            brigadierType: ArgumentType<U>?,
            comment: String? = null,
            noinline brigadierDeserializer: (U) -> T? = { if (it is T) it else null },
            noinline textValue: (T?) -> Text = { Text.literal(it.toString()) as Text },
            noinline serializer: (T?) -> Any? = { it },
            noinline deserializer: (Any?) -> T? = { if (it is T) it else null },
            noinline brigadierFilter: (CommandSource, U) -> Boolean = { _, _ -> true },
            noinline brigadierSuggestor: BrigadierSuggestor<CommandSource>? = null,
        ): ConfigItem<T, U> {
            val configItem = ConfigItem(
                config,
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
            config.configItems[configItem.key] = configItem as ConfigItem<Any, Any>
            return configItem
        }

        fun group(group: String, comment: String? = null): ConfigGroup {
            val configGroup = ConfigGroup(config, group, parent, comment)
            config.configGroups.add(configGroup)
            return configGroup
        }

        fun write() {
            if (name != null) {
                config.configToml.setComment(key, comment)
            }
            if (parent != null && parent != config.emptyGroup) {
                parent.write()
            }
        }

        val key: List<String>
            get() = if (name == null) listOf() else (parent?.key ?: listOf()) + name
    }

    data class ConfigItem<T : Any, U : Any>(
        private val config: BetterConfig<*>,

        val name: String,
        val group: ConfigGroup,
        var comment: String?,
        val textValue: (T?) -> Text,


        @Suppress("MemberVisibilityCanBePrivate")
        val default: T?,

        val serializer: (T) -> Any?,
        val deserializer: (Any?) -> T?,

        val brigadierType: ArgumentType<U>?,
        val brigadierDesierializer: (U) -> T?,
        val brigadierFilter: (CommandSource, U) -> Boolean,
        val suggestor: BrigadierSuggestor<CommandSource>?
    ) {

        private fun loadValue(): T? {
            return deserializer(config.configToml.get(group.key + name))
        }

        private fun write(): T? {
            config.configToml.set<Any>(group.key + name, default?.let { serializer(it) })
            config.configToml.setComment(group.key + name, comment)
            group.write()
            return default
        }

        var value: T? = loadValue() ?: write()
            set(value) {
                field = value
                config.save(this as ConfigItem<Any, Any>)
            }

        fun reset() {
            value = default
        }

        val key: String
            get() = (group.key + name).joinToString(".")

        fun toText(): Text {
            return Text.literal(key).append(" -> ").append(textValue(value))
        }

    }
}


