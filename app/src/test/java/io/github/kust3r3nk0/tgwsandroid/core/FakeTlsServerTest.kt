package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class FakeTlsServerTest {
    private val secret=FakeTls.hex("00112233445566778899aabbccddeeff")
    private fun ctr(key:ByteArray,iv:ByteArray)=Cipher.getInstance("AES/CTR/NoPadding").apply{init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),IvParameterSpec(iv))}
    private fun sha(data:ByteArray)=MessageDigest.getInstance("SHA-256").digest(data)
    private fun hmac(data:ByteArray)=Mac.getInstance("HmacSHA256").apply{init(SecretKeySpec(secret,"HmacSHA256"))}.doFinal(data)
    @Test fun configuredFakeTlsKeepsPlainObfuscated2CompatibleIncludingTlsLookingRandomPrefixes(){
        LocalTelegramPeer().use { peer ->
            val factory=object:TransportFactory {
                override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport=throw java.io.IOException("Unavailable")
                override fun openTcp(address:String,own:(Closeable)->Unit):RemoteTransport {
                    val socket=Socket("127.0.0.1",peer.tcpPort).also(own)
                    return object:RemoteTransport {
                        override val kind=TransportKind.TCP
                        override fun send(data:ByteArray){socket.getOutputStream().write(data)}
                        override fun receive():ByteArray?{val b=ByteArray(4096);val n=socket.getInputStream().read(b);return if(n<0)null else b.copyOf(n)}
                        override fun close()=socket.close()
                    }
                }
            }
            val port=ServerSocket(0).use{it.localPort}
            val phase=java.util.concurrent.atomic.AtomicReference(ServerPhase.STOPPED)
            val rejections=AtomicInteger()
            MtprotoServer(port,"00112233445566778899aabbccddeeff",{
                if(it is ServerEvent.State)phase.set(it.phase)
                if(it is ServerEvent.Diagnostic && it.issue==ServerIssue.HANDSHAKE)rejections.incrementAndGet()
            },transports=factory,fakeTlsDomain="example.com").use { server ->
                for(tag in listOf(0xef,0xee,0xdd)) {
                    server.start()
                    val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3)
                    while(phase.get()!=ServerPhase.LISTENING){check(System.nanoTime()<end);Thread.sleep(2)}
                    for(prefix in listOf(byteArrayOf(1,2,3,4,5),byteArrayOf(0x16,3,3,0,38),byteArrayOf(0x16,3,3,2,0))) {
                        Socket("127.0.0.1",port).use { client ->
                            client.soTimeout=3000
                            val plain=ByteArray(64){(it+1).toByte()}.apply{prefix.copyInto(this)}
                            for(i in 56..59)plain[i]=tag.toByte()
                            plain[60]=(-2).toByte();plain[61]=(-1).toByte()
                            val material=plain.copyOfRange(8,56)
                            val enc=ctr(sha(material.copyOfRange(0,32)+secret),material.copyOfRange(32,48))
                            val header=plain.copyOf().apply{enc.update(plain).copyInto(this,56,56,64)}
                            val backwards=material.reversedArray()
                            val dec=ctr(sha(backwards.copyOfRange(0,32)+secret),backwards.copyOfRange(32,48))
                            val packet=(if(tag==0xef)byteArrayOf(2)else byteArrayOf(8,0,0,0))+ByteArray(8){it.toByte()}
                            client.getOutputStream().write(header+enc.update(packet))
                            assertArrayEquals(packet,dec.update(client.getInputStream().readNBytes(packet.size)))
                        }
                    }
                    server.stop()
                }
                assertEquals(0,rejections.get())
            }
        }
    }
    private fun hello():ByteArray {
        val p=Properties().apply{FakeTlsServerTest::class.java.getResourceAsStream("/fake-tls-v1.11.2.properties")!!.use{load(it)}}
        val wire=FakeTls.hex(p.getProperty("hello")).apply{fill(0,11,43)}
        val digest=hmac(wire);val now=System.currentTimeMillis()/1000
        for(i in 0..3)digest[28+i]=(digest[28+i].toInt() xor (now shr (8*i)).toInt()).toByte()
        digest.copyInto(wire,11);return wire
    }
    @Test fun realLocalFakeTlsCarriesObfuscatedSessionAndRejectsBadAuthenticationBeforeDial(){
        LocalTelegramPeer().use{peer->
            val dials=AtomicInteger()
            val factory=object:TransportFactory{
                override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport=throw java.io.IOException("Unavailable")
                override fun openTcp(address:String,own:(Closeable)->Unit):RemoteTransport{
                    dials.incrementAndGet();val socket=Socket().also{own(it);it.connect(java.net.InetSocketAddress("127.0.0.1",peer.tcpPort))}
                    return object:RemoteTransport{
                        override val kind=TransportKind.TCP
                        override fun send(data:ByteArray){socket.getOutputStream().write(data)}
                        override fun receive():ByteArray?{val b=ByteArray(4096);val n=socket.getInputStream().read(b);return if(n<0)null else b.copyOf(n)}
                        override fun close()=socket.close()
                    }
                }
            }
            val port=ServerSocket(0).use{it.localPort};val listening=CountDownLatch(1)
            val rejections=java.util.concurrent.CopyOnWriteArrayList<HandshakeRejection>()
            MtprotoServer(port,"00112233445566778899aabbccddeeff",{if(it==ServerEvent.State(ServerPhase.LISTENING))listening.countDown();if(it is ServerEvent.Diagnostic)it.rejection?.let(rejections::add)},
                transports=factory,fakeTlsDomain="example.com").use{server->
                server.start();assertTrue(listening.await(3,TimeUnit.SECONDS))
                Socket("127.0.0.1",port).use{client->
                    client.soTimeout=3000;val ch=hello();client.getOutputStream().write(ch)
                    val reader=DataInputStream(client.getInputStream());var response=ByteArray(0)
                    repeat(3){val head=ByteArray(5);reader.readFully(head);val n=((head[3].toInt() and 255) shl 8) or (head[4].toInt() and 255)
                        assertTrue(n<=2100);response+=head+ByteArray(n).also(reader::readFully)}
                    val expected=hmac(ch.copyOfRange(11,43)+response.copyOf().apply{fill(0,11,43)})
                    assertArrayEquals(expected,response.copyOfRange(11,43))
                    val plain=ByteArray(64){(it+1).toByte()};for(i in 56..59)plain[i]=0xdd.toByte();plain[60]=2;plain[61]=0
                    val material=plain.copyOfRange(8,56)
                    val encrypt=ctr(sha(material.copyOfRange(0,32)+secret),material.copyOfRange(32,48))
                    val header=plain.copyOf().apply{encrypt.update(plain).copyInto(this,56,56,64)}
                    val reversed=material.reversedArray();val decrypt=ctr(sha(reversed.copyOfRange(0,32)+secret),reversed.copyOfRange(32,48))
                    val packet=byteArrayOf(8,0,0,0)+ByteArray(8){it.toByte()}
                    val writer=FakeTls.Writer(client.getOutputStream());writer.write(header,0,17);writer.write(header,17,47);writer.write(encrypt.update(packet));writer.flush()
                    assertArrayEquals(packet,decrypt.update(FakeTls.Reader(client.getInputStream()).readNBytes(packet.size)))
                }
                val before=dials.get()
                Socket("127.0.0.1",port).use{client->client.soTimeout=3000;client.getOutputStream().write(hello().apply{this[300]=1});
                    assertEquals(-1,client.getInputStream().read())}
                assertEquals(before,dials.get())
                assertEquals(listOf(HandshakeRejection.FAKE_TLS),rejections.toList())
            }
        }
    }
}
