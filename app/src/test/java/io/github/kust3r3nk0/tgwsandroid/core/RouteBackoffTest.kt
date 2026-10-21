package io.github.kust3r3nk0.tgwsandroid.core

import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class RouteBackoffTest {
    @Test fun earlySessionFailuresKeepExponentialBudgetUntilRealInboundProgress() {
        var now=0L
        val retries=RouteBackoff<String>(10,40){now}
        val first=retries.acquire("route")!!;retries.connected(first);retries.reject(first)
        assertEquals(10L,retries.remainingMillis("route"));assertNull(retries.acquire("route"))
        now+=TimeUnit.MILLISECONDS.toNanos(10)
        val second=retries.acquire("route")!!;retries.connected(second);retries.reject(second)
        retries.recovered(first);retries.recovered(second)
        assertEquals(20L,retries.remainingMillis("route"))
        now+=TimeUnit.MILLISECONDS.toNanos(20)
        val third=retries.acquire("route")!!;retries.connected(third);retries.recovered(third)
        assertNotNull(retries.acquire("route"));assertEquals(0L,retries.remainingMillis("route"))
        val fourth=retries.acquire("route")!!;retries.connected(fourth);retries.reject(fourth)
        assertEquals(10L,retries.remainingMillis("route"))
        retries.recovered(third);assertNull(retries.acquire("route"))
    }
    @Test fun aNewerWarmSetupCannotEraseFailuresOrHideRealProgressOnAnOlderLiveSession(){
        var now=0L;val retries=RouteBackoff<String>(10,40){now}
        val first=retries.acquire("route")!!;retries.connected(first);retries.reject(first)
        now=TimeUnit.MILLISECONDS.toNanos(10)
        val active=retries.acquire("route")!!;retries.connected(active)
        val warm=retries.acquire("route")!!;retries.connected(warm)
        retries.recovered(active)
        val later=retries.acquire("route")!!;retries.connected(later);retries.reject(later)
        assertEquals(10L,retries.remainingMillis("route"))
    }
    @Test fun lateRuntimeEofCannotPenalizeNewNetworkOrCountSameSessionTwice(){
        val retries=RouteBackoff<String>()
        val old=retries.acquire("route")!!;retries.connected(old)
        retries.reset()
        val fresh=retries.acquire("route")!!;retries.connected(fresh)
        retries.reject(old);assertNotNull(retries.acquire("route"))
        retries.reject(fresh);val delay=retries.remainingMillis("route")
        retries.reject(fresh);assertTrue(retries.remainingMillis("route")<=delay)
        assertNull(retries.acquire("route"))
    }
    @Test fun permitsHaveOneTerminalOutcomeEvenWhenCancellationRacesFailure() {
        var now=0L
        val retries=RouteBackoff<String>(10,40){now}
        val initial=retries.acquire("route")!!
        retries.failure(initial);retries.failure(initial)
        now=TimeUnit.MILLISECONDS.toNanos(10)
        val probe=retries.acquire("route")!!
        retries.cancel(probe);retries.failure(probe)
        assertNotNull(retries.acquire("route"))
        val other=RouteBackoff<String>()
        val success=other.acquire("route")!!
        other.success(success);other.failure(success)
        assertNotNull(other.acquire("route"))
    }

    @Test fun endpointPressureSurvivesInFlightSuccessAndAdmitsOneRecoveryProbe() {
        var now=0L
        val retries=RouteBackoff<String>(10,100){now}
        val old=retries.acquire("endpoint")!!
        retries.defer("endpoint",50)
        retries.success(old)
        assertNull(retries.acquire("endpoint"))
        assertEquals(50L,retries.remainingMillis("endpoint"))
        assertNotNull(retries.acquire("another-endpoint"))
        now=TimeUnit.MILLISECONDS.toNanos(50)
        val probe=retries.acquire("endpoint")!!
        assertNull(retries.acquire("endpoint"))
        retries.cancel(probe)
        val recovered=retries.acquire("endpoint")!!
        retries.success(recovered)
        assertNotNull(retries.acquire("endpoint"));assertNotNull(retries.acquire("endpoint"))
        retries.defer("endpoint",Int.MAX_VALUE)
        assertEquals(100L,retries.remainingMillis("endpoint"))
        retries.reset();assertNotNull(retries.acquire("endpoint"))
    }
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
