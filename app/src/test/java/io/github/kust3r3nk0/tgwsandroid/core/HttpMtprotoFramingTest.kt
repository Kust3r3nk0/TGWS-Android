package io.github.kust3r3nk0.tgwsandroid.core

import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

class HttpMtprotoFramingTest {
    @Test fun framingMatchesPinnedPythonForAbridgedIntermediateAndBothPaddedCases(){
        val vectors=Properties().apply{HttpMtprotoFramingTest::class.java.getResourceAsStream("/http-media-v1.11.2.properties")!!.use{load(it)}}
        for(index in 0..3){
            fun bytes(name:String)=FakeTls.hex(vectors.getProperty("$index.$name"))
            val tag=bytes("tag");val body=bytes("body")
            assertArrayEquals(body,HttpMtprotoFraming.body(bytes("wire"),tag))
            assertArrayEquals(bytes("reply"),HttpMtprotoFraming.reply(body,tag,3){ByteArray(it){3}})
        }
    }
    @Test fun malformedPaddingLengthAlignmentAndOversizeAreRejected(){
        val tag=ByteArray(4){0xdd.toByte()}
        for(body in listOf(ByteArray(24),ByteArray(32).apply{this[16]=100},ByteArray(23){1})){
            val frame=byteArrayOf(body.size.toByte(),0,0,0)+body
            assertThrows(IllegalArgumentException::class.java){HttpMtprotoFraming.body(frame,tag)}
        }
        assertThrows(IllegalArgumentException::class.java){HttpMtprotoFraming.reply(ByteArray(3),tag)}
    }
}
