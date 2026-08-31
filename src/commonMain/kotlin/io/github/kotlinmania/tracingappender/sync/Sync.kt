// port-lint: source tracing-appender/src/sync.rs
package io.github.kotlinmania.tracingappender.sync

import kotlin.concurrent.Volatile

/**
 * Abstracts over synchronization primitive implementations.
 */
public class RwLock<T>(
    @Volatile
    private var value: T,
) {
    /**
     * Returns the current value held by the lock.
     */
    public fun read(): T = value

    /**
     * Executes the given action with the current value.
     */
    public fun <R> withRead(action: (T) -> R): R = action(value)

    /**
     * Updates the value held by the lock.
     */
    public fun write(newValue: T) {
        value = newValue
    }

    /**
     * Executes the given action to compute a new value and return a result.
     */
    public fun <R> withWrite(action: (T) -> Pair<T, R>): R {
        val (newValue, result) = action(value)
        value = newValue
        return result
    }

    /**
     * Gets direct mutable reference to the underlying value.
     */
    public fun getMut(): T = value

    /**
     * Sets the mutable value directly.
     */
    public fun setMut(newValue: T) {
        value = newValue
    }

    /**
     * Attempts to read the value without blocking.
     */
    public fun tryRead(): T? = value

    /**
     * Attempts to write the value without blocking.
     */
    public fun tryWrite(newValue: T): Boolean {
        value = newValue
        return true
    }

    public companion object {
        /**
         * Creates a new [RwLock] wrapping [value].
         */
        public fun <T> new(value: T): RwLock<T> = RwLock(value)
    }
}
