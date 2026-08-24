// port-lint: source sync.rs
package io.github.kotlinmania.tracingappender.sync

import kotlin.concurrent.Volatile

/**
 * Abstracts over synchronization primitive implementations.
 */
public class RwLock<T>(
    @Volatile
    private var value: T,
) {
    public fun read(): T = value

    public fun <R> withRead(action: (T) -> R): R = action(value)

    public fun write(newValue: T) {
        value = newValue
    }

    public fun <R> withWrite(action: (T) -> Pair<T, R>): R {
        val (newValue, result) = action(value)
        value = newValue
        return result
    }
}
