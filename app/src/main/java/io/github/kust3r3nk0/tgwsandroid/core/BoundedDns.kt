package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Platform DNS may ignore interruption. Bound resolver threads, pending work and caller wait separately. */
internal class BoundedDns(
    workers: Int = 2,
    queueSize: Int = 8,
    private val lookup: (String) -> InetAddress = InetAddress::getByName,
) : Closeable {
    private val executor = ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(queueSize), { task -> Thread(task, "tgws-dns").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy())

    fun resolve(host: String, timeoutMillis: Int): InetAddress {
        require(timeoutMillis > 0)
        val future = try { executor.submit<InetAddress> { lookup(host) } }
        catch (_: RejectedExecutionException) { throw IOException("DNS capacity exhausted") }
        try {
            return future.get(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            throw SocketTimeoutException("DNS deadline")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("DNS interrupted")
        } catch (_: ExecutionException) {
            throw IOException("DNS resolution failed")
        } finally {
            future.cancel(true)
            executor.remove(future as Runnable)
        }
    }

    override fun close() { executor.shutdownNow() }
}
