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
    private val pending=java.util.concurrent.ConcurrentHashMap.newKeySet<java.util.concurrent.Future<*>>()

    fun resolve(host: String, timeoutMillis: Int): InetAddress = await(timeoutMillis){lookup(host)}
    fun resolveAll(host:String,timeoutMillis:Int,lookupAll:(String)->List<InetAddress>):List<InetAddress> =
        await(timeoutMillis){lookupAll(host).distinct().also{if(it.isEmpty())throw java.net.UnknownHostException("Empty DNS result")}}

    private fun <T> await(timeoutMillis: Int, task:()->T):T {
        require(timeoutMillis > 0)
        val future = try { executor.submit<T> { task() } }
        catch (_: RejectedExecutionException) { throw IOException("DNS capacity exhausted") }
        pending+=future
        try {
            return future.get(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            throw SocketTimeoutException("DNS deadline")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("DNS interrupted")
        } catch (_: ExecutionException) {
            throw java.net.UnknownHostException("DNS resolution failed")
        } catch (_: java.util.concurrent.CancellationException) {
            throw IOException("DNS canceled")
        } finally {
            pending-=future
            future.cancel(true)
            executor.remove(future as Runnable)
        }
    }

    fun invalidate(){pending.forEach{it.cancel(true)};executor.purge()}
    override fun close() { invalidate();executor.shutdownNow() }
}
