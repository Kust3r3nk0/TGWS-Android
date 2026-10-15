package io.github.kust3r3nk0.tgwsandroid.core

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class WarmConnectionPoolTest {
    private class Channel : RemoteTransport {
        override val kind = TransportKind.WSS
        val closed = AtomicInteger()
        override fun send(data: ByteArray) { fail("Warmup must never send MTProto initialization or payload") }
        override fun receive(): ByteArray? = throw AssertionError("Warmup must not consume client data")
        override fun close() { closed.incrementAndGet() }
    }
    private fun await(predicate: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!predicate()) { if (System.nanoTime() > until) fail("Pool condition timed out"); Thread.sleep(2) }
    }
    private fun timer() = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }

    @Test fun leasesAreSingleUseCapacityIsGlobalAndRotationDoesNotReconnectWithoutDemand() {
        var now = 0L
        val opened = CopyOnWriteArrayList<Channel>()
        val byRoute = java.util.concurrent.ConcurrentHashMap<String, Channel>()
        val watchdog = timer()
        try {
            WarmConnectionPool<String>(2, 100, 1000, watchdog, opener = { route, own ->
                Channel().also { opened += it; byRoute[route] = it; own(it) }
            }, now = { now }).use { pool ->
                pool.refill("a"); pool.refill("a"); pool.refill("b")
                await { pool.size() == (2 to 0) }
                pool.refill("c")
                assertEquals(2, opened.size)
                val lease = pool.take("a")!!
                assertNull(pool.take("a"))
                now = TimeUnit.MILLISECONDS.toNanos(100)
                pool.rotate()
                assertEquals(1, opened.count { it.closed.get() == 1 })
                assertEquals(0, byRoute["a"]!!.closed.get())
                assertEquals(0 to 0, pool.size())
                assertEquals(2, opened.size)
                lease.abort()
                assertEquals(2, opened.count { it.closed.get() == 1 })
            }
        } finally { watchdog.shutdownNow() }
    }

    @Test fun resetCancelsOwnedConnectAndRejectsLateResourcesFromOldNetwork() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val resource = Channel()
        val late = Channel()
        val watchdog = timer()
        try {
            WarmConnectionPool<String>(1, 1000, 1000, watchdog, opener = { _, own ->
                own(resource); entered.countDown()
                while (true) {
                    try { if (release.await(10, TimeUnit.MILLISECONDS)) break } catch (_: InterruptedException) { }
                }
                own(late)
                late
            }).use { pool ->
                pool.refill("route")
                assertTrue(entered.await(1, TimeUnit.SECONDS))
                pool.invalidate()
                assertEquals(1, resource.closed.get())
                assertEquals(0 to 0, pool.size())
                release.countDown()
                await { late.closed.get() >= 1 }
                assertNull(pool.take("route"))
            }
        } finally { release.countDown(); watchdog.shutdownNow() }
    }

    @Test fun connectDeadlineClosesSocketAndNoExpiredResultEntersPool() {
        val watchdog = timer()
        val resource = Channel()
        val stopped = CountDownLatch(1)
        try {
            WarmConnectionPool<String>(1, 1000, 50, watchdog, opener = { _, own ->
                own(java.io.Closeable { resource.close(); stopped.countDown() })
                assertTrue(stopped.await(2, TimeUnit.SECONDS))
                resource
            }).use { pool ->
                pool.refill("route")
                await { resource.closed.get() >= 1 && pool.size() == (0 to 0) }
                assertNull(pool.take("route"))
            }
        } finally { watchdog.shutdownNow() }
    }

    @Test fun repeatedRefillTakeAndNetworkResetDoesNotAccumulatePendingTimersOrSockets() {
        val watchdog = timer()
        val opened = CopyOnWriteArrayList<Channel>()
        try {
            WarmConnectionPool<Int>(4, 1000, 1000, watchdog, opener = { _, own ->
                Channel().also { opened += it; own(it) }
            }).use { pool ->
                repeat(100) { index ->
                    pool.refill(index % 4)
                    await { pool.size().second == 0 }
                    pool.take(index % 4)?.abort()
                    if (index % 7 == 0) pool.invalidate()
                    assertTrue(pool.size().first + pool.size().second <= 4)
                }
            }
            assertTrue(opened.all { it.closed.get() == 1 })
            assertEquals(0, watchdog.queue.size)
        } finally { watchdog.shutdownNow() }
    }
}
