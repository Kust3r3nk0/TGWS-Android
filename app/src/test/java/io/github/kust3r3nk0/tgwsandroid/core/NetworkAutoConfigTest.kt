package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class NetworkAutoConfigTest {
    @Test fun probeRequiresAnEncryptedMtprotoReplyWithMatchingNonceAcrossFragments(){
        fun ctr(material:ByteArray)=Cipher.getInstance("AES/CTR/NoPadding").apply{
            init(Cipher.ENCRYPT_MODE,SecretKeySpec(material.copyOfRange(0,32),"AES"),IvParameterSpec(material.copyOfRange(32,48)))
        }
        var decrypt:Cipher?=null;var encrypt:Cipher?=null;var response=byteArrayOf();var sends=0
        val remote=object:RemoteTransport {
            override val kind=TransportKind.TCP
            override fun send(data:ByteArray){
                sends++
                if(sends==1){assertEquals(64,data.size);val material=data.copyOfRange(8,56)
                    decrypt=ctr(material).also{it.update(ByteArray(64))};encrypt=ctr(material.reversedArray())
                }else{
                    val plain=decrypt!!.update(data);val b=ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN)
                    assertEquals(40,b.int);assertEquals(0L,b.long);assertEquals(0L,b.long and 3L)
                    assertEquals(20,b.int);assertEquals(0xbe7e8ef1.toInt(),b.int)
                    val nonce=ByteArray(16).also(b::get)
                    val reply=ByteBuffer.allocate(88).order(ByteOrder.LITTLE_ENDIAN).putInt(84).putLong(0).putLong(1).putInt(64).putInt(0x05162463).put(nonce)
                        .put(ByteArray(16)).put(8.toByte()).put(ByteArray(8){1}).put(ByteArray(3)).putInt(0x1cb5c415).putInt(1).putLong(42).array()
                    response=encrypt!!.update(reply)
                }
            }
            override fun receive():ByteArray? {if(response.isEmpty())return null
                return response.take(3).toByteArray().also{response=response.drop(it.size).toByteArray()}}
            override fun close(){}
        }
        MtprotoAvailabilityProbe.check(remote);assertEquals(2,sends)
    }
    @Test fun forgedNonceOrEmptyReplyIsNotAvailability(){
        assertFalse(MtprotoAvailabilityProbe.validResponse(ByteArray(40),ByteArray(16)))
        assertFalse(MtprotoAvailabilityProbe.validResponse(ByteArray(3),ByteArray(16)))
        val body=ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN).putLong(0).putLong(1).putInt(20).putInt(0x05162463).put(ByteArray(16){1}).array()
        assertFalse(MtprotoAvailabilityProbe.validResponse(body,ByteArray(16)))
    }
    @Test fun failureDoesNotProduceAReplacementAndSuccessPreservesOtherNetworksAndPrivateSettings(){
        val old=TransportSettings(mode=TransportMode.AUTO_CLOUDFLARE,fakeTlsDomain="example.org",workerUrls=listOf("wss://one.example/private","wss://two.example/private"))
        assertNull(NetworkAutoConfig.apply(old,ConnectionNetwork.MOBILE,emptyList()))
        val target=NetworkAutoConfig.targets(old,"",ConnectionNetwork.MOBILE).first{it.worker==old.workerUrls[1]}
        val next=NetworkAutoConfig.apply(old,ConnectionNetwork.MOBILE,listOf(AutoConfigObservation(target,30,null)))!!
        assertEquals(old.profiles.wifi,next.profiles.wifi);assertEquals(old.fakeTlsDomain,next.fakeTlsDomain)
        assertEquals(old.workerUrls.reversed(),next.workerUrls);assertEquals(TransportKind.WORKER,next.profiles.mobile.primary)
        assertEquals(RoutingMode.FAILOVER,next.profiles.mobile.mode);assertTrue(next.profiles.automaticNetwork)
        assertFalse(target.toString().contains("private"))
    }
    @Test fun relayOnlyAndExplicitTransportExclusionsAreRespected(){
        val profile=ConnectionProfile(allowed=setOf(TransportKind.TCP),primary=TransportKind.TCP,backups=emptySet())
        val s=TransportSettings(profiles=ConnectionProfiles(enabled=true,mobile=profile))
        assertEquals(listOf(TransportKind.TCP),NetworkAutoConfig.targets(s,"",ConnectionNetwork.MOBILE).map{it.kind})
        assertEquals(listOf(TransportKind.RELAY),NetworkAutoConfig.targets(TransportSettings(mode=TransportMode.RELAY_ONLY),"wss://relay.example/private",ConnectionNetwork.WIFI).map{it.kind})
    }
}
