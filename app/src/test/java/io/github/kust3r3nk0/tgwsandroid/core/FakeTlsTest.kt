package io.github.kust3r3nk0.tgwsandroid.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

class FakeTlsTest {
    private val vectors=Properties().apply{FakeTlsTest::class.java.getResourceAsStream("/fake-tls-v1.11.2.properties")!!.use{load(it)}}
    private fun bytes(name:String)=FakeTls.hex(vectors.getProperty(name))
    private val secret get()=bytes("secret")
    private val now get()=vectors.getProperty("now").toLong()
    @Test fun pinnedPythonHelloAndServerResponseMatchByteForByte(){
        val hello=FakeTls.verify(bytes("hello"),secret,now)!!
        assertEquals(now,hello.timestamp);assertArrayEquals(ByteArray(32){it.toByte()},hello.session)
        assertArrayEquals(bytes("response"),FakeTls.response(secret,hello,{n->ByteArray(n){(it*13+7).toByte()}},1999))
    }
    @Test fun wrongSecretTamperingTimestampAndTruncationAreRejected(){
        val hello=bytes("hello")
        assertNull(FakeTls.verify(hello,ByteArray(16),now))
        assertNull(FakeTls.verify(hello,secret,now+121))
        assertNotNull(FakeTls.verify(hello,secret,now+120))
        assertNull(FakeTls.verify(hello.copyOf(50),secret,now))
        assertNull(FakeTls.verify(hello.copyOf().apply{this[300]=(this[300].toInt() xor 1).toByte()},secret,now))
    }
    @Test fun recordFramingMatchesPythonAndSurvivesFragmentedReads(){
        val payload=ByteArray(256*65){it.toByte()}
        assertArrayEquals(bytes("wrapped"),FakeTls.wrap(payload))
        val out=ByteArrayOutputStream();FakeTls.Writer(out).write(payload);assertArrayEquals(bytes("wrapped"),out.toByteArray())
        val input=object:ByteArrayInputStream(FakeTls.hex("140303000101")+out.toByteArray()){
            override fun read(b:ByteArray,o:Int,l:Int)=super.read(b,o,minOf(l,3))
        }
        assertArrayEquals(payload,FakeTls.Reader(input).readBytes())
    }
    @Test fun invalidRecordsAndPartialBodiesFailWithoutAccumulation(){
        for(data in listOf("1703030000","1703034001","14030300020201","170301000100","160303000100","170303000200")){
            assertThrows(java.io.IOException::class.java){FakeTls.Reader(ByteArrayInputStream(FakeTls.hex(data))).readBytes()}
        }
    }
}
