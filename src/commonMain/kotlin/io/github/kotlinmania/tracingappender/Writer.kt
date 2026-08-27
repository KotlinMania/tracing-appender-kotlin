// port-lint: source tracing-appender/src/non_blocking.rs
package io.github.kotlinmania.tracingappender

/**
 * Interface representing a destination for byte sequences and logs.
 */
public interface Writer {
    /**
     * Writes a portion of a byte array to the destination.
     */
    public fun write(buf: ByteArray, offset: Int = 0, length: Int = buf.size): Int

    /**
     * Flushes any buffered output.
     */
    public fun flush()

    /**
     * Writes an entire byte array to the destination.
     */
    public fun writeAll(buf: ByteArray) {
        write(buf, 0, buf.size)
    }
}
