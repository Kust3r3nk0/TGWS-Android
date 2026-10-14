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
}
