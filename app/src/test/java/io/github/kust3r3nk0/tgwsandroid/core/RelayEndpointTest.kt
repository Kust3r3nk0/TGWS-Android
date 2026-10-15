package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.*
import org.junit.Test

class RelayEndpointTest {
    @Test fun workerRequestMatchesFlowsealDestinationAndDcForAllRoutes() {
        val endpoint = RelayEndpoint.parse("wss://relay.example")!!
        for (dc in listOf(1, 2, 3, 4, 5, 203)) {
            assertEquals("/apiws?dst=${TelegramRoutes.ip(dc)}&dc=$dc", endpoint.requestPath(dc))
        }
        assertThrows(IllegalArgumentException::class.java) { endpoint.requestPath(10002) }
        assertNull(RelayEndpoint.parse(""))
    }

    @Test fun preservesProtectedPathAndUsesSameDnsAndTlsHost() {
        val endpoint = RelayEndpoint.parse("wss://Relay.Example:443/private-key/apiws")!!
        assertEquals("relay.example", endpoint.domain)
        assertEquals("/private-key/apiws?dst=149.154.167.51&dc=2", endpoint.requestPath(2))
        assertEquals("RelayEndpoint(redacted)", endpoint.toString())
    }

    @Test fun rejectsInsecureAmbiguousInjectedAndUnsupportedEndpointForms() {
        for (url in listOf("http://relay.example", "ws://relay.example", "wss://user:pass@relay.example",
            "wss://127.0.0.1", "wss://[::1]", "wss://localhost", "wss://host.local",
            "wss://relay.example:8443", "wss://relay.example/?dst=x", "wss://relay.example/#x",
            "wss://relay.example/a%0d%0a", "wss://relay.example/a\r\nHeader:x", "wss://relay.example/../apiws",
            "wss://relay.example/" + "a".repeat(1024), " wss://relay.example", "wss://relay..example")) {
            val error = assertThrows(IllegalArgumentException::class.java) { RelayEndpoint.parse(url) }
            assertFalse(error.message.orEmpty().contains(url))
        }
    }
}
