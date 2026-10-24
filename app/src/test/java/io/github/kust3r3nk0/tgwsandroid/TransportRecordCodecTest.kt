package io.github.kust3r3nk0.tgwsandroid

import io.github.kust3r3nk0.tgwsandroid.core.*
import io.github.kust3r3nk0.tgwsandroid.core.TransportMode
import io.github.kust3r3nk0.tgwsandroid.core.TransportSettings
import java.util.Base64
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class TransportRecordCodecTest {
    @Test fun largestRelayAndWorkerConfigurationIsBoundedEncryptedAndAuthenticated(){
        val nodes=(1..8).map{RelayNode("n$it","Private name $it","wss://r$it.example/"+"x".repeat(900)+"/apiws")}
        val config=TransportSettings(mode=TransportMode.AUTO_CLOUDFLARE,
            workerUrls=(1..8).map{"wss://w$it.example/"+"y".repeat(900)+"/apiws"},
            relays=RelayConfiguration(true,nodes,nodes.first().id,nodes.drop(1).map{it.id}))
        assertTrue(config.encode().length>12288);assertTrue(config.encode().length<=TransportSettings.MAX_ENCODED)
        val key=KeyGenerator.getInstance("AES").apply{init(256)}.generateKey()
        val encoded=TransportRecordCodec.encrypt(key,config);assertEquals(config,TransportRecordCodec.decrypt(key,encoded))
        assertFalse(String(Base64.getDecoder().decode(encoded),Charsets.ISO_8859_1).contains("Private name"))
        val broken=Base64.getDecoder().decode(encoded).apply{this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()}
        assertThrows(Exception::class.java){TransportRecordCodec.decrypt(key,Base64.getEncoder().encodeToString(broken))}
    }
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
