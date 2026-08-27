// port-lint: tests tracing-appender/src/lib.rs
package io.github.kotlinmania.tracingappender

import io.github.kotlinmania.tracingappender.rolling.DefaultAppenderWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibTest {
    @Test
    fun testNonBlockingConvenience() {
        val writer = DefaultAppenderWriter()
        val (nonBlocking, guard) = nonBlocking(writer)
        assertTrue(nonBlocking.isLossy)
        assertEquals(0, nonBlocking.errorCounter().droppedLines())

        val written = nonBlocking.write("hello from lib".encodeToByteArray())
        assertEquals(14, written)

        guard.close()
    }
}
