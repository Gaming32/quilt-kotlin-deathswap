package io.github.gaming32.qkdeathswap

inline fun <reified E : Throwable, T, R> T.successOrNull(action: T.() -> R) =
    try {
        action()
    } catch (e: Throwable) {
        if (e is E) {
            null
        } else {
            throw e
        }
    }
