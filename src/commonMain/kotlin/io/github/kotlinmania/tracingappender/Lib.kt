// port-lint: source tracing-appender/src/lib.rs
package io.github.kotlinmania.tracingappender

/**
 * Convenience function for creating a non-blocking, off-thread writer.
 */
public fun <T : Writer> nonBlocking(writer: T): Pair<NonBlocking, WorkerGuard> = NonBlocking.new(writer)
