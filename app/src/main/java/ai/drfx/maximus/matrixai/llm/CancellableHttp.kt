package ai.drfx.maximus.matrixai.llm

import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cancellation closes the socket as well as interrupting the worker; no request survives its UI job. */
internal object CancellableHttp {
    private val executor = Executors.newFixedThreadPool(4) { task ->
        Thread(task, "matrix-provider-http").apply { isDaemon = true }
    }
    private val active = ThreadLocal<Session>()

    internal class Session {
        private val cancelled = AtomicBoolean(false)
        private val connection = AtomicReference<HttpURLConnection?>()
        fun register(value: HttpURLConnection) {
            connection.getAndSet(value)?.disconnect()
            if (cancelled.get()) {
                value.disconnect()
                throw CancellationException("Provider request cancelled")
            }
        }
        fun cancel() { cancelled.set(true); connection.getAndSet(null)?.disconnect() }
        fun close() { connection.getAndSet(null)?.disconnect() }
    }

    fun register(connection: HttpURLConnection): HttpURLConnection {
        active.get()?.register(connection)
        return connection
    }

    suspend fun <T> execute(block: () -> T): T = suspendCancellableCoroutine { continuation ->
        val session = Session()
        val future = executor.submit {
            active.set(session)
            try {
                if (continuation.isActive) {
                    val result = block()
                    if (continuation.isActive) continuation.resume(result)
                }
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } finally {
                session.close()
                active.remove()
            }
        }
        continuation.invokeOnCancellation { session.cancel(); future.cancel(true) }
    }
}
