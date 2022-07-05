package io.github.gaming32.qkdeathswap

inline fun <T> consumerApply(crossinline action: T.() -> Unit): (T) -> Unit {
    return { action(it) }
}
