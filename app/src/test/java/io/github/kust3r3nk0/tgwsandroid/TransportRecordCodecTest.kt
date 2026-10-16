package io.github.kust3r3nk0.tgwsandroid

import io.github.kust3r3nk0.tgwsandroid.core.TransportMode
import io.github.kust3r3nk0.tgwsandroid.core.TransportSettings
import java.util.Base64
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class TransportRecordCodecTest {
    @Test fun defaultAndAdvancedConfigRoundTripWithFreshAuthenticatedIv(){
        val key=KeyGenerator.getInstance("AES").apply{init(256)}.generateKey()
        for(config in listOf(TransportSettings(),TransportSettings(TransportMode.AUTO_CLOUDFLARE,"example.com",
            listOf("proxy.example"),listOf("wss://worker.example/private-token/apiws"),true))){
            val first=TransportRecordCodec.encrypt(key,config);val second=TransportRecordCodec.encrypt(key,config)
            assertNotEquals(first,second);assertEquals(config,TransportRecordCodec.decrypt(key,first))
            assertFalse(String(Base64.getDecoder().decode(first),Charsets.ISO_8859_1).contains("private-token"))
            val tampered=Base64.getDecoder().decode(first).apply{this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()}
            assertThrows(Exception::class.java){TransportRecordCodec.decrypt(key,Base64.getEncoder().encodeToString(tampered))}
            assertThrows(Exception::class.java){RelayRecordCodec.decrypt(key,first)}
        }
    }
    @Test fun wrongKeyAndMalformedRecordNeverBecomeDefaultDirectConfig(){
        val keys=(0..1).map{KeyGenerator.getInstance("AES").apply{init(256)}.generateKey()}
        val record=TransportRecordCodec.encrypt(keys[0],TransportSettings())
        assertThrows(Exception::class.java){TransportRecordCodec.decrypt(keys[1],record)}
        assertThrows(Exception::class.java){TransportRecordCodec.decrypt(keys[0],"corrupted")}
    }
}
