package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.*
import org.junit.Test

class TelegramSecretTest {
    private val secret="00112233445566778899aabbccddeeff"
    @Test fun standardDdAndEeSecretsRoundTripWithoutChangingBaseKey(){
        for(value in listOf(secret,"dd$secret")){val parsed=TelegramSecret.decode(value);assertEquals(secret,parsed.secret);assertEquals("",parsed.domain)}
        val encoded=TelegramSecret.encode(secret,"cloudflare.com")
        assertEquals("ee${secret}636c6f7564666c6172652e636f6d",encoded)
        assertEquals(secret,TelegramSecret.decode(encoded).secret)
        assertEquals("cloudflare.com",TelegramSecret.decode(encoded).domain)
        assertFalse(TelegramSecret.decode(encoded).toString().contains(secret))
    }
    @Test fun malformedSecretsAndDomainsAreRejected(){
        for(value in listOf("ee$secret","ee${secret}00","ee${secret}ff","aa$secret","dd${secret}00","ee${secret}616263"))
            assertThrows(IllegalArgumentException::class.java){TelegramSecret.decode(value)}
        for(domain in listOf("127.0.0.1","example.com/evil","localhost","foo..com","-a.example.com","a.local"))
            assertFalse(domain,TelegramSecret.validDomain(domain))
    }
    @Test fun importOnlyLocalTelegramLinksAndRejectDuplicateOrRemoteConfiguration(){
        val parsed=TelegramSecret.import("tg://proxy?server=127.0.0.1&port=8443&secret=${TelegramSecret.encode(secret,"example.com")}")
        assertEquals(8443,parsed.port);assertEquals("example.com",parsed.decoded.domain)
        assertEquals(secret,TelegramSecret.import("https://t.me/proxy?server=127.0.0.1&port=1443&secret=dd$secret").decoded.secret)
        for(url in listOf("tg://proxy?server=example.com&port=1443&secret=$secret","tg://proxy?server=127.0.0.1&port=1443&port=1&secret=$secret"))
            assertThrows(IllegalArgumentException::class.java){TelegramSecret.import(url)}
    }
}
