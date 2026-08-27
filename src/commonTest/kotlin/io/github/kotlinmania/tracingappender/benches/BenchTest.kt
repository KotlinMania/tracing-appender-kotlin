// port-lint: tests tracing-appender/benches/bench.rs
package io.github.kotlinmania.tracingappender.benches

import io.github.kotlinmania.tracingappender.Writer
import io.github.kotlinmania.tracingappender.nonBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class NoOpWriter : Writer {
    override fun write(buf: ByteArray, offset: Int, length: Int): Int = length

    override fun flush() {}

    fun makeWriter(): NoOpWriter = this

    companion object {
        fun new(): NoOpWriter = NoOpWriter()
    }
}

class BenchTest {
    fun synchronousBenchmark(): Long {
        val writer = NoOpWriter.new()
        val data = "event".encodeToByteArray()
        for (i in 0 until 1000) {
            writer.write(data, 0, data.size)
        }
        return 1000L
    }

    fun nonBlockingBenchmark(): Long {
        val (nonBlocking, guard) = nonBlocking(NoOpWriter.new())
        val data = "event".encodeToByteArray()
        for (i in 0 until 1000) {
            nonBlocking.write(data, 0, data.size)
        }
        guard.close()
        return 1000L
    }

    @Test
    fun testSynchronousBenchmark() {
        val count = synchronousBenchmark()
        assertEquals(1000L, count)
    }

    @Test
    fun testNonBlockingBenchmark() {
        val count = nonBlockingBenchmark()
        assertEquals(1000L, count)
    }
}
