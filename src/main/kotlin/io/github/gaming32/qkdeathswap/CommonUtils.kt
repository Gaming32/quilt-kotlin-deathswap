package io.github.gaming32.qkdeathswap

inline fun <T> consumerApply(crossinline action: T.() -> Unit): (T) -> Unit {
    return { action(it) }
}

inline fun <T, U> funtionApply(crossinline action: T.() -> U): (T) -> U {
    return { action(it) }
}
