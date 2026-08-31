// port-lint: source tracing-appender/src/worker.rs
package io.github.kotlinmania.tracingappender

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Message sent across the channel to the logging worker.
 */
internal sealed class Msg {
    /**
     * A log line message containing byte payload.
     */
    class Line(
        val msg: ByteArray,
    ) : Msg()

    /**
     * Signal to shut down the worker thread.
     */
    object Shutdown : Msg()
}

/**
 * State returned after processing a batch of log items.
 */
internal enum class WorkerState {
    Empty,
    Disconnected,
    Continue,
    Shutdown,
}

/**
 * Worker responsible for receiving messages from channel and writing to destination.
 */
internal class Worker<T : Writer>(
    private val receiver: Channel<Msg>,
    private val writer: T,
    private val shutdown: Channel<Unit>,
) {
    fun handleRecv(result: Result<Msg>): WorkerState =
        result.fold(
            onSuccess = { msg ->
                when (msg) {
                    is Msg.Line -> {
                        writer.writeAll(msg.msg)
                        WorkerState.Continue
                    }
                    is Msg.Shutdown -> WorkerState.Shutdown
                }
            },
            onFailure = {
                WorkerState.Disconnected
            },
        )

    fun handleTryRecv(result: Result<Msg?>): WorkerState =
        result.fold(
            onSuccess = { msg ->
                when (msg) {
                    is Msg.Line -> {
                        writer.writeAll(msg.msg)
                        WorkerState.Continue
                    }
                    is Msg.Shutdown -> WorkerState.Shutdown
                    null -> WorkerState.Empty
                }
            },
            onFailure = {
                WorkerState.Disconnected
            },
        )

    /**
     * Receives messages from channel and writes them to the underlying writer until empty.
     */
    suspend fun work(): WorkerState {
        val firstMsg =
            try {
                Result.success(receiver.receive())
            } catch (e: ClosedReceiveChannelException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }

        var workerState = handleRecv(firstMsg)
        while (workerState == WorkerState.Continue) {
            val poll = receiver.tryReceive()
            workerState =
                if (poll.isSuccess) {
                    handleTryRecv(Result.success(poll.getOrNull()))
                } else if (poll.isClosed) {
                    WorkerState.Disconnected
                } else {
                    WorkerState.Empty
                }
        }
        try {
            writer.flush()
        } catch (e: Exception) {
            println("Failed to flush. Error: $e")
        }
        return workerState
    }

    /**
     * Creates and launches a coroutine worker processing messages.
     */
    fun workerThread(
        name: String,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName(name)),
    ): Job =
        scope.launch {
            while (isActive) {
                val state = work()
                if (state == WorkerState.Shutdown || state == WorkerState.Disconnected) {
                    try {
                        shutdown.receive()
                    } catch (_: Exception) {
                    }
                    break
                }
            }
            try {
                writer.flush()
            } catch (e: Exception) {
                println("Failed to flush. Error: $e")
            }
        }

    companion object {
        fun <T : Writer> new(
            receiver: Channel<Msg>,
            writer: T,
            shutdown: Channel<Unit>,
        ): Worker<T> = Worker(receiver, writer, shutdown)
    }
}
