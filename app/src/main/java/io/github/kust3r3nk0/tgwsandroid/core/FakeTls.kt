package io.github.kust3r3nk0.tgwsandroid.core

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Flowseal v1.11.2 fake_tls.py. Record camouflage, not authenticated TLS. */
internal object FakeTls {
    private val random = SecureRandom()
    data class Hello(val random: ByteArray, val session: ByteArray, val timestamp: Long)
    private fun mac(secret: ByteArray, data: ByteArray) = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(secret, "HmacSHA256"))
    }.doFinal(data)
    private fun bytes(n: Int) = ByteArray(n).also(random::nextBytes)
    fun verify(data: ByteArray, secret: ByteArray, now: Long = System.currentTimeMillis() / 1000): Hello? {
        if (secret.size != 16 || data.size !in 43..4101 || data[0] != 0x16.toByte() || data[5] != 1.toByte()) return null
        if (((data[3].toInt() and 255) shl 8 or (data[4].toInt() and 255)) != data.size - 5) return null
        val clientRandom = data.copyOfRange(11, 43)
        val zeroed = data.copyOf().apply { fill(0, 11, 43) }
        val expected = mac(secret, zeroed)
        if (!MessageDigest.isEqual(expected.copyOf(28), clientRandom.copyOf(28))) return null
        var timestamp = 0L
        for (i in 0..3) timestamp = timestamp or (((clientRandom[28+i].toInt() xor expected[28+i].toInt()) and 255).toLong() shl (8*i))
        if (kotlin.math.abs(now - timestamp) > 120) return null
        return Hello(clientRandom, if (data.size >= 76 && data[43] == 32.toByte()) data.copyOfRange(44,76) else ByteArray(32), timestamp)
    }
    fun response(secret: ByteArray, hello: Hello, randomBytes: (Int) -> ByteArray = ::bytes,
        padding: Int = 1900 + random.nextInt(201)): ByteArray {
        require(secret.size == 16 && hello.random.size == 32 && hello.session.size == 32 && padding in 1900..2100)
        val sh = (hex("160303007a020000760303") + ByteArray(32) + byteArrayOf(32) + hello.session +
            hex("130100002e00330024001d0020") + randomBytes(32).also { require(it.size == 32) } + hex("002b00020304"))
        val result = sh + hex("140303000101170303") + byteArrayOf((padding shr 8).toByte(), padding.toByte()) +
            randomBytes(padding).also { require(it.size == padding) }
        mac(secret, hello.random + result).copyInto(result, 11)
        return result
    }
    fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    fun wrap(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var position = 0
        while (position < data.size) {
            val count = minOf(16384, data.size - position)
            out.write(byteArrayOf(0x17,3,3,(count shr 8).toByte(),count.toByte()))
            out.write(data,position,count); position += count
        }
        return out.toByteArray()
    }
    class Reader(private val source: InputStream) : InputStream() {
        private val input = DataInputStream(source)
        private var left = 0
        override fun read(): Int { val one=ByteArray(1);return if(read(one,0,1)<0)-1 else one[0].toInt() and 255 }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            java.util.Objects.checkFromIndexSize(offset,length,buffer.size)
            if(length==0)return 0
            var controls=0
            while(left==0){
                val type=input.read();if(type<0)return -1
                val major=input.readUnsignedByte();val minor=input.readUnsignedByte();val size=input.readUnsignedShort()
                if(major!=3 || minor!=3)throw java.net.ProtocolException("Invalid Fake TLS record")
                if(type==0x14){
                    if(size!=1 || input.readUnsignedByte()!=1 || ++controls>8)throw java.net.ProtocolException("Invalid Fake TLS CCS")
                    continue
                }
                if(type!=0x17 || size !in 1..16384)throw java.net.ProtocolException("Invalid Fake TLS data")
                left=size
            }
            val count=input.read(buffer,offset,minOf(length,left));if(count<0)throw EOFException("Truncated Fake TLS")
            left-=count;return count
        }
    }
    class Writer(private val target: OutputStream) : OutputStream() {
        override fun write(value: Int)=write(byteArrayOf(value.toByte()))
        override fun write(data: ByteArray, offset: Int, length: Int) {
            java.util.Objects.checkFromIndexSize(offset,length,data.size)
            var position=offset
            while(position<offset+length){val n=minOf(16384,offset+length-position)
                target.write(byteArrayOf(0x17,3,3,(n shr 8).toByte(),n.toByte()));target.write(data,position,n);position+=n}
        }
        override fun flush()=target.flush()
    }
}
