package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramRoutesTest {
    @Test fun defaultsMatchFlowsealBaseline() {
        val baseline = mapOf(
            1 to "149.154.175.50",
            2 to "149.154.167.51",
            3 to "149.154.175.100",
            4 to "149.154.167.91",
            5 to "149.154.171.5",
            203 to "91.105.192.100",
        )
        baseline.forEach { (dc, expected) -> assertEquals("DC $dc", expected, TelegramRoutes.ip(dc)) }
        assertNull(TelegramRoutes.ip(999))
    }

    @Test fun domainsMatchFlowsealBaseline() {
        assertEquals(listOf("kws4.web.telegram.org"), TelegramRoutes.webSocketDomains(4, false))
        assertEquals(
            listOf("kws2-1.web.telegram.org", "kws2.web.telegram.org"),
            TelegramRoutes.webSocketDomains(203, true),
        )
        assertEquals(emptyList<String>(), TelegramRoutes.webSocketDomains(999, false))
    }

    @Test fun wssRedirectsRemainSeparateFromTcpFallbackEndpoints() {
        assertEquals("149.154.167.220", TelegramRoutes.webSocketIp(2))
        assertEquals("149.154.167.220", TelegramRoutes.webSocketIp(4))
        for (dc in listOf(1, 3, 5, 203, 0, -2, 999, 10001)) {
            assertNull("DC $dc", TelegramRoutes.webSocketIp(dc))
        }
        assertEquals("149.154.167.51", TelegramRoutes.ip(2))
        assertEquals("149.154.167.91", TelegramRoutes.ip(4))
    }

    @Test fun allProductionAndMediaDomainsRemainDeterministic() {
        for (dc in 1..5) {
            assertEquals(listOf("kws$dc.web.telegram.org"), TelegramRoutes.webSocketDomains(dc, false))
            assertEquals(listOf("kws$dc-1.web.telegram.org", "kws$dc.web.telegram.org"), TelegramRoutes.webSocketDomains(dc, true))
        }
        for (dc in listOf(0, -1, 10001, Int.MAX_VALUE)) {
            assertNull(TelegramRoutes.ip(dc))
            assertEquals(emptyList<String>(), TelegramRoutes.webSocketDomains(dc, true))
        }
    }

    @Test fun routingMatchesGeneratedPinnedUpstreamVectors() {
        repeat(FlowsealVectors.value("routing.count").toInt()) { index ->
            val prefix = "routing.$index"
            val dc = FlowsealVectors.value("$prefix.dc").toInt()
            assertEquals(FlowsealVectors.value("$prefix.ip"), TelegramRoutes.ip(dc))
            assertEquals(FlowsealVectors.value("$prefix.ordinary").split(','), TelegramRoutes.webSocketDomains(dc, false))
            assertEquals(FlowsealVectors.value("$prefix.media").split(','), TelegramRoutes.webSocketDomains(dc, true))
        }
    }
}
