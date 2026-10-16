package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.*
import org.junit.Test

class TransportSettingsTest {
    @Test fun modesAndOrderedListsRoundTrip(){
        for(mode in TransportMode.entries){
            val config=TransportSettings(mode,"example.com",listOf("one.example","two.example"),listOf("wss://worker.example/secret/apiws"))
            val decoded=TransportSettings.decode(config.encode());assertEquals(config.encode(),decoded.encode())
            assertFalse(config.toString().contains("secret"))
        }
    }
    @Test fun invalidDomainsUrlsAndLimitsAreRejectedAsAWhole(){
        for(text in listOf("good.example\n127.0.0.1","good.example\nhttps://bad.example",(1..9).joinToString("\n"){"a$it.example"},"","same.example\nsame.example"))
            assertThrows(IllegalArgumentException::class.java){TransportSettings.trustedDomains(text)}
        assertEquals(listOf("good.example"),TransportSettings.trustedDomains("# comment\ngood.example\n"))
        assertThrows(IllegalArgumentException::class.java){TransportSettings(workerUrls=listOf("http://worker.example/apiws"))}
        assertThrows(IllegalArgumentException::class.java){TransportSettings(mode=TransportMode.AUTO_CLOUDFLARE)}
        assertThrows(IllegalArgumentException::class.java){TransportSettings(http2Media=true)}
    }
}
