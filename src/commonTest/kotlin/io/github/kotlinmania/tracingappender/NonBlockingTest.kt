package io.github.kotlinmania.tracingappender

import io.github.kotlinmania.tracingappender.rolling.DefaultAppenderWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NonBlockingTest {
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

    @Test
    fun testLogsDroppedIfLossy() {
        val writer = DefaultAppenderWriter()
        val (nonBlocking, guard) =
            NonBlockingBuilder
                .default()
                .lossy(true)
                .bufferedLinesLimit(1)
                .finish(writer)

        for (i in 0 until 10) {
            nonBlocking.write("drop me".encodeToByteArray())
        }
        guard.close()
    }
}
