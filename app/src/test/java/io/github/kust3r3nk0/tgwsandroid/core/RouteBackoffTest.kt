package io.github.kust3r3nk0.tgwsandroid.core

import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class RouteBackoffTest {
    @Test fun exponentialCooldownHasSingleHalfOpenProbeAndResetsOnSuccess() {
        var now = 0L
        val retries = RouteBackoff<String>(10, 40) { now }
        for (delay in listOf(10, 20, 40, 40)) {
            val attempt = retries.acquire("dc2")!!
            retries.failure(attempt)
            assertNull(retries.acquire("dc2"))
            assertNotNull(retries.acquire("dc4"))
            now += TimeUnit.MILLISECONDS.toNanos(delay.toLong()) - 1
            assertNull(retries.acquire("dc2"))
            now++
            val probe = retries.acquire("dc2")!!
            assertNull(retries.acquire("dc2"))
            retries.cancel(probe)
        }
        val healthy = retries.acquire("dc2")!!
        retries.success(healthy)
        assertNotNull(retries.acquire("dc2"))
        assertNotNull(retries.acquire("dc2"))
    }

    @Test fun oldConcurrentFailuresCannotUndoNewerSuccessOrNetworkReset() {
        val retries = RouteBackoff<String>()
        val old = retries.acquire("dc2")!!
        val recent = retries.acquire("dc2")!!
        retries.success(recent)
        retries.failure(old)
        assertNotNull(retries.acquire("dc2"))
        retries.reset()
        retries.failure(recent)
        assertNotNull(retries.acquire("dc2"))
        val failed = retries.acquire("dc2")!!
        retries.failure(failed)
        assertNull(retries.acquire("dc2"))
        retries.reset()
        assertNotNull(retries.acquire("dc2"))
    }
}
