package io.github.kust3r3nk0.tgwsandroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kust3r3nk0.tgwsandroid.core.*
import java.io.Closeable
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import javax.crypto.Mac
import io.github.kust3r3nk0.tgwsandroid.core.Handshake
import io.github.kust3r3nk0.tgwsandroid.core.Http2MediaTransport
import io.github.kust3r3nk0.tgwsandroid.core.MtprotoCrypto
import java.net.Proxy
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Http2InstrumentedTest {
    private fun ctr(key:ByteArray,iv:ByteArray)=Cipher.getInstance("AES/CTR/NoPadding").apply{init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),IvParameterSpec(iv))}
    private fun fixture(action:(MockWebServer,OkHttpClient)->Unit){
        val store=KeyStore.getInstance("PKCS12")
        InstrumentationRegistry.getInstrumentation().context.assets.open("localhost.p12").use{store.load(it,"test-only-password".toCharArray())}
        val keys=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply{init(store,"test-only-password".toCharArray())}
        val serverTls=SSLContext.getInstance("TLS").apply{init(keys.keyManagers,null,null)}
        val trusted=KeyStore.getInstance("PKCS12").apply{load(null,null);setCertificateEntry("localhost",store.getCertificate("localhost"))}
        val trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply{init(trusted)}
        val manager=trust.trustManagers.filterIsInstance<X509TrustManager>().single()
        val clientTls=SSLContext.getInstance("TLS").apply{init(null,trust.trustManagers,null)}
        MockWebServer().use{server->
            server.useHttps(serverTls.socketFactory);server.protocols=listOf(Protocol.HTTP_2,Protocol.HTTP_1_1)
            server.dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse {
                return if(request.method=="HEAD")MockResponse.Builder().code(200).build()
                else MockResponse.Builder().body(okio.Buffer().write(requireNotNull(request.body))).build()
            }}
            server.start()
            val client=OkHttpClient.Builder().sslSocketFactory(clientTls.socketFactory,manager).proxy(Proxy.NO_PROXY)
                .retryOnConnectionFailure(false).readTimeout(5,TimeUnit.SECONDS).build()
            try{action(server,client)}finally{client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdownNow()}
        }
    }
    @Test fun androidTlsAlpnH2CarriesIndependentNativeStreamsWithStrictFixtureTrust(){fixture{server,client->
            val transports=mutableListOf<Http2MediaTransport>()
            try{
                val tag=ByteArray(4){0xee.toByte()}
                repeat(2){
                    val transport=Http2MediaTransport.open(client,server.url("/api").newBuilder().host("localhost").build(),tag){}
                    transports+=transport
                    val relay=MtprotoCrypto.createRelayInit(Handshake(2,true,tag,-2,ByteArray(48){it.toByte()}))
                    val forward=ctr(relay.copyOfRange(8,40),relay.copyOfRange(40,56)).apply{update(ByteArray(64))}
                    val reversed=relay.copyOfRange(8,56).reversedArray()
                    val backward=ctr(reversed.copyOfRange(0,32),reversed.copyOfRange(32,48))
                    val frame=byteArrayOf(40,0,0,0)+ByteArray(40){(it+1).toByte()}
                    transport.send(relay);transport.send(forward.update(frame))
                    assertArrayEquals(frame,backward.update(transport.receive()!!))
                }
                assertEquals(4,server.requestCount)
            }finally{transports.forEach{it.close()}}
    }}
    @Test fun androidFakeTlsObfuscatedMediaTraversesCoreAndRealH2(){fixture{peer,http->
        val secret=ByteArray(16){it.toByte()}
        fun mac(data:ByteArray)=Mac.getInstance("HmacSHA256").apply{init(SecretKeySpec(secret,"HmacSHA256"))}.doFinal(data)
        fun sha(data:ByteArray)=MessageDigest.getInstance("SHA-256").digest(data)
        val factory=object:TransportFactory{
            override fun openHttpMedia(domain:String,tag:ByteArray,own:(Closeable)->Unit):RemoteTransport=
                Http2MediaTransport.open(http,peer.url("/api").newBuilder().host("localhost").build(),tag,own)
            override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport=throw java.io.IOException("No WSS peer")
            override fun openTcp(address:String,own:(Closeable)->Unit):RemoteTransport=throw java.io.IOException("No TCP peer")
        }
        val port=ServerSocket(0).use{it.localPort};val listening=CountDownLatch(1)
        MtprotoServer(port,secret.joinToString(""){"%02x".format(it)},onEvent={if(it==ServerEvent.State(ServerPhase.LISTENING))listening.countDown()},
            transports=factory,transportSettings=TransportSettings(TransportMode.AUTO_CLOUDFLARE,fakeTlsDomain="example.com",cloudflareDomains=listOf("proxy.example"),http2Media=true)).use{core->
            core.start();assertTrue(listening.await(3,TimeUnit.SECONDS))
            Socket("127.0.0.1",port).use{socket->
                socket.soTimeout=5000
                val hello=(FakeTls.hex("1603010200010001fc0303")+ByteArray(506)).apply{this[43]=32}
                val digest=mac(hello);val now=System.currentTimeMillis()/1000
                for(i in 0..3)digest[28+i]=(digest[28+i].toInt() xor (now shr (8*i)).toInt()).toByte()
                digest.copyInto(hello,11);socket.getOutputStream().write(hello)
                val input=DataInputStream(socket.getInputStream());var reply=ByteArray(0)
                repeat(3){val head=ByteArray(5).also(input::readFully);val n=((head[3].toInt() and 255) shl 8) or (head[4].toInt() and 255)
                    assertTrue(n in 1..2100);reply+=head+ByteArray(n).also(input::readFully)}
                assertArrayEquals(mac(hello.copyOfRange(11,43)+reply.copyOf().apply{fill(0,11,43)}),reply.copyOfRange(11,43))
                val plain=ByteArray(64){(it+1).toByte()};for(i in 56..59)plain[i]=0xdd.toByte();plain[60]=(-2).toByte();plain[61]=(-1).toByte()
                val material=plain.copyOfRange(8,56);val forward=ctr(sha(material.copyOfRange(0,32)+secret),material.copyOfRange(32,48))
                val init=plain.copyOf().apply{forward.update(plain).copyInto(this,56,56,64)}
                val reversed=material.reversedArray();val backward=ctr(sha(reversed.copyOfRange(0,32)+secret),reversed.copyOfRange(32,48))
                val body=ByteArray(40){(it+1).toByte()}
                val output=FakeTls.Writer(socket.getOutputStream());output.write(init);output.write(forward.update(byteArrayOf(40,0,0,0)+body));output.flush()
                val records=DataInputStream(FakeTls.Reader(socket.getInputStream()))
                val header=backward.update(ByteArray(4).also(records::readFully))
                val size=(0..3).fold(0){n,i->n or ((header[i].toInt() and 255) shl (8*i))}
                assertTrue(size in 40..43)
                assertArrayEquals(body,backward.update(ByteArray(size).also(records::readFully)).copyOf(40))
                assertEquals(2,peer.requestCount)
            }
        }
        assertThrows(java.io.IOException::class.java){Socket("127.0.0.1",port).close()}
    }}
}
