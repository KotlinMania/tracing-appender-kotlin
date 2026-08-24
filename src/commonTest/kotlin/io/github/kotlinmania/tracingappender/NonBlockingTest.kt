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
}
