// port-lint: tests tracing-appender/src/worker.rs
package io.github.kotlinmania.tracingappender

import io.github.kotlinmania.tracingappender.rolling.DefaultAppenderWriter
import kotlinx.coroutines.channels.Channel
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkerTest {
    @Test
    fun testWorkerHandleRecv() {
        val channel = Channel<Msg>(10)
        val shutdown = Channel<Unit>(1)
        val writer = DefaultAppenderWriter()
        val worker = Worker(channel, writer, shutdown)

        val state1 = worker.handleRecv(Result.success(Msg.Line("Hello".encodeToByteArray())))
        assertEquals(WorkerState.Continue, state1)
        assertEquals("Hello", writer.asString())

        val state2 = worker.handleRecv(Result.success(Msg.Shutdown))
        assertEquals(WorkerState.Shutdown, state2)

        val state3 = worker.handleRecv(Result.failure(Exception("Closed")))
        assertEquals(WorkerState.Disconnected, state3)
    }

    @Test
    fun testWorkerHandleTryRecv() {
        val channel = Channel<Msg>(10)
        val shutdown = Channel<Unit>(1)
        val writer = DefaultAppenderWriter()
        val worker = Worker(channel, writer, shutdown)

        val state1 = worker.handleTryRecv(Result.success(Msg.Line("World".encodeToByteArray())))
        assertEquals(WorkerState.Continue, state1)
        assertEquals("World", writer.asString())

        val state2 = worker.handleTryRecv(Result.success(null))
        assertEquals(WorkerState.Empty, state2)

        val state3 = worker.handleTryRecv(Result.success(Msg.Shutdown))
        assertEquals(WorkerState.Shutdown, state3)

        val state4 = worker.handleTryRecv(Result.failure(Exception("Closed")))
        assertEquals(WorkerState.Disconnected, state4)
    }
}
