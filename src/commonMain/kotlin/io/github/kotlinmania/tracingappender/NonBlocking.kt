// port-lint: source non_blocking.rs
package io.github.kotlinmania.tracingappender

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlin.concurrent.Volatile

/**
 * The default maximum number of buffered log lines.
 */
public const val DEFAULT_BUFFERED_LINES_LIMIT: Int = 128_000

/**
 * A guard that flushes spans and events associated to a [NonBlocking] writer upon close.
 */
public class WorkerGuard internal constructor(
    private val scope: CoroutineScope?,
    private val sender: Channel<Msg>,
    private val shutdown: Channel<Unit>,
    private val job: Job?,
) : AutoCloseable {
    override fun close() {
        sender.trySend(Msg.Shutdown)
        shutdown.trySend(Unit)
        job?.cancel()
        scope?.cancel()
    }
}

/**
 * Tracks the number of times a log line was dropped by the background worker.
 */
public class ErrorCounter(
    @Volatile
    private var count: Int = 0,
) {
    /**
     * Returns the count of dropped log lines.
     */
    public fun droppedLines(): Int = count

    /**
     * Increments the dropped lines counter saturating at Int.MAX_VALUE.
     */
    public fun incrSaturating() {
        if (count < Int.MAX_VALUE) {
            count += 1
        }
    }
}

/**
 * A non-blocking writer that enqueues log messages to a dedicated worker.
 */
public class NonBlocking internal constructor(
    private val channel: Channel<Msg>,
    private val errorCounter: ErrorCounter,
    public val isLossy: Boolean,
) : Writer {
    /**
     * Returns the error counter tracking dropped logs.
     */
    public fun errorCounter(): ErrorCounter = errorCounter

    override fun write(buf: ByteArray, offset: Int, length: Int): Int {
        val slice = if (offset == 0 && length == buf.size) buf.copyOf() else buf.copyOfRange(offset, offset + length)
        val res = channel.trySend(Msg.Line(slice))
        if (res.isFailure && isLossy) {
            errorCounter.incrSaturating()
        }
        return length
    }

    override fun flush() {
        // Channel-buffered messages are periodically flushed by the worker
    }

    /**
     * Returns a writer for emitting log messages.
     */
    public fun makeWriter(): NonBlocking = this

    public companion object {
        /**
         * Returns a new [NonBlocking] writer wrapping the provided [writer] with default configuration.
         */
        public fun <T : Writer> new(writer: T): Pair<NonBlocking, WorkerGuard> = NonBlockingBuilder.default().finish(writer)

        /**
         * Creates a new [NonBlocking] writer with explicit parameters.
         */
        public fun <T : Writer> create(
            writer: T,
            bufferedLinesLimit: Int,
            isLossy: Boolean,
            threadName: String,
        ): Pair<NonBlocking, WorkerGuard> {
            val channel =
                Channel<Msg>(
                    capacity = bufferedLinesLimit,
                    onBufferOverflow = if (isLossy) BufferOverflow.DROP_OLDEST else BufferOverflow.SUSPEND,
                )
            val shutdown = Channel<Unit>(Channel.RENDEZVOUS)
            val errorCounter = ErrorCounter()

            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName(threadName))
            val worker = Worker.new(channel, writer, shutdown)
            val job = worker.workerThread(threadName, scope)

            val guard = WorkerGuard(scope, channel, shutdown, job)
            val nonBlocking = NonBlocking(channel, errorCounter, isLossy)
            return Pair(nonBlocking, guard)
        }
    }
}

/**
 * A builder for [NonBlocking] writers.
 */
public class NonBlockingBuilder(
    private var bufferedLinesLimit: Int = DEFAULT_BUFFERED_LINES_LIMIT,
    private var isLossy: Boolean = true,
    private var threadName: String = "tracing-appender",
) {
    /**
     * Sets the maximum number of lines to buffer before dropping or applying backpressure.
     */
    public fun bufferedLinesLimit(bufferedLinesLimit: Int): NonBlockingBuilder =
        apply {
            this.bufferedLinesLimit = bufferedLinesLimit
        }

    /**
     * Sets whether [NonBlocking] should drop logs on overflow (lossy) or suspend senders (lossless).
     */
    public fun lossy(isLossy: Boolean): NonBlockingBuilder =
        apply {
            this.isLossy = isLossy
        }

    /**
     * Sets the worker coroutine thread name.
     */
    public fun threadName(name: String): NonBlockingBuilder =
        apply {
            this.threadName = name
        }

    /**
     * Completes the builder, returning the configured [NonBlocking] writer and its [WorkerGuard].
     */
    public fun <T : Writer> finish(writer: T): Pair<NonBlocking, WorkerGuard> =
        NonBlocking.create(
            writer = writer,
            bufferedLinesLimit = bufferedLinesLimit,
            isLossy = isLossy,
            threadName = threadName,
        )

    public companion object {
        /**
         * Returns a builder initialized with default configuration values.
         */
        public fun default(): NonBlockingBuilder = NonBlockingBuilder()
    }
}
