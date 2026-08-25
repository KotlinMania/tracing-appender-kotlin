// port-lint: tests non_blocking.rs
package io.github.kotlinmania.tracingappender

import io.github.kotlinmania.tracingappender.rolling.DefaultAppenderWriter
import kotlinx.coroutines.channels.Channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockWriter(public val capacity: Int) : Writer {
    val channel = Channel<String>(capacity)

    override fun write(buf: ByteArray, offset: Int, length: Int): Int {
        val str = buf.decodeToString(offset, offset + length)
        channel.trySend(str)
        return length
    }

    override fun flush() {}

    companion object {
        fun new(capacity: Int): Pair<MockWriter, Channel<String>> {
            val writer = MockWriter(capacity)
            return Pair(writer, writer.channel)
        }
    }
}

class NonBlockingTest {
    fun writeNonBlocking(nonBlocking: NonBlocking, msg: ByteArray) {
        nonBlocking.writeAll(msg)
    }

    @Test
    fun backpressureExerted() {
        val (mockWriter, rx) = MockWriter.new(1)
        val (nonBlocking, guard) =
            NonBlockingBuilder
                .default()
                .lossy(false)
                .bufferedLinesLimit(1)
                .finish(mockWriter)

        val errorCount = nonBlocking.errorCounter()
        nonBlocking.writeAll("Hello".encodeToByteArray())
        assertEquals(0, errorCount.droppedLines())

        nonBlocking.writeAll(", World".encodeToByteArray())
        assertEquals(0, errorCount.droppedLines())

        guard.close()
    }

    @Test
    fun logsDroppedIfLossy() {
        val (mockWriter, rx) = MockWriter.new(1)
        val (nonBlocking, guard) =
            NonBlockingBuilder
                .default()
                .lossy(true)
                .bufferedLinesLimit(1)
                .finish(mockWriter)

        val errorCount = nonBlocking.errorCounter()
        writeNonBlocking(nonBlocking, "Hello".encodeToByteArray())
        writeNonBlocking(nonBlocking, ", World".encodeToByteArray())
        writeNonBlocking(nonBlocking, "Test".encodeToByteArray())
        writeNonBlocking(nonBlocking, "Universe".encodeToByteArray())

        guard.close()
    }

    @Test
    fun multiThreadedWrites() {
        val (mockWriter, rx) = MockWriter.new(DEFAULT_BUFFERED_LINES_LIMIT)
        val (nonBlocking, guard) =
            NonBlockingBuilder
                .default()
                .lossy(true)
                .finish(mockWriter)

        for (i in 0 until 10) {
            val cloned = nonBlocking.makeWriter()
            cloned.writeAll("Hello".encodeToByteArray())
        }
        guard.close()
    }

    @Test
    fun testDefaultBuilder() {
        val writer = DefaultAppenderWriter()
        val (nonBlocking, guard) = NonBlockingBuilder.default().finish(writer)
        assertTrue(nonBlocking.isLossy)
        assertEquals(0, nonBlocking.errorCounter().droppedLines())
        guard.close()
    }

    @Test
    fun testLossyWrites() {
        val writer = DefaultAppenderWriter()
        val (nonBlocking, guard) =
            NonBlockingBuilder
                .default()
                .lossy(true)
                .bufferedLinesLimit(100)
                .finish(writer)

        val written = nonBlocking.write("Hello World".encodeToByteArray())
        assertEquals(11, written)
        assertEquals(0, nonBlocking.errorCounter().droppedLines())

        guard.close()
    }

    @Test
    fun testErrorCounter() {
        val counter = ErrorCounter()
        assertEquals(0, counter.droppedLines())
        counter.incrSaturating()
        assertEquals(1, counter.droppedLines())
        counter.incrSaturating()
        assertEquals(2, counter.droppedLines())
    }

    @Test
    fun testNonBlockingCompanionNew() {
        val writer = DefaultAppenderWriter()
        val (nonBlocking, guard) = NonBlocking.new(writer)
        val written = nonBlocking.write("test".encodeToByteArray())
        assertEquals(4, written)
        guard.close()
    }

    @Test
    fun testNonBlockingMakeWriter() {
        val writer = DefaultAppenderWriter()
        val (nonBlocking, guard) = NonBlocking.new(writer)
        val writerRef = nonBlocking.makeWriter()
        assertEquals(nonBlocking, writerRef)
        guard.close()
    }
}
