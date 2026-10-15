package io.github.kust3r3nk0.tgwsandroid.core

import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class BoundedDnsTest {
    @Test fun successfulResolutionAndFailuresAreBoundedAndSafe() {
        BoundedDns(lookup = { InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)) }).use {
            assertEquals("127.0.0.1", it.resolve("relay.example", 1000).hostAddress)
        }
        BoundedDns(lookup = { throw IOException("private host details") }).use {
            val error = assertThrows(IOException::class.java) { it.resolve("relay.example", 1000) }
            assertEquals("DNS resolution failed", error.message)
            assertNull(error.cause)
        }
    }

    @Test fun uninterruptibleLookupCannotExhaustSessionThreadsOrAccumulateCanceledQueueEntries() {
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val calls = AtomicInteger()
        BoundedDns(workers = 1, queueSize = 1, lookup = {
            calls.incrementAndGet()
            entered.countDown()
            while (true) {
                try { if (release.await(10, TimeUnit.MILLISECONDS)) break }
                catch (_: InterruptedException) { /* Simulates a platform DNS call ignoring cancellation. */ }
            }
            InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        }).use { dns ->
            try {
                assertThrows(SocketTimeoutException::class.java) { dns.resolve("relay.example", 50) }
                assertTrue(entered.await(1, TimeUnit.SECONDS))
                repeat(4) { assertThrows(SocketTimeoutException::class.java) { dns.resolve("relay.example", 20) } }
                assertEquals(1, calls.get())
                release.countDown()
                assertEquals("127.0.0.1", dns.resolve("relay.example", 1000).hostAddress)
            } finally { release.countDown() }
        }
    }
}
