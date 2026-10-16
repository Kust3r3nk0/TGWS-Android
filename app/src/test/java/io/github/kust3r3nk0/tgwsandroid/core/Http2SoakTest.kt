package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.io.IOException
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.ConnectionPool
import okhttp3.Dispatcher as HttpDispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

/** Complete native MTProto -> core -> real TLS/HTTP2 /api echo under concurrent media load. */
class Http2SoakTest {
    private val secret=ByteArray(16){it.toByte()}
    private fun ctr(key:ByteArray,iv:ByteArray)=Cipher.getInstance("AES/CTR/NoPadding").apply{init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),IvParameterSpec(iv))}
    private fun sha(data:ByteArray)=MessageDigest.getInstance("SHA-256").digest(data)
    private fun close(client:OkHttpClient){client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdownNow()}
    @Test fun concurrentNativeMediaSessionsUseH2ThroughNetworkResetsWithoutCipherReplay(){
        val seconds=requireNotNull(System.getProperty("tgws.soak.seconds","2")).toInt().also{require(it in 1..3600)}
        val tls=TestTls.contexts()
        MockWebServer().use{peer->
            peer.useHttps(tls.first.socketFactory,false);peer.protocols=listOf(Protocol.HTTP_2,Protocol.HTTP_1_1)
            peer.dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest)=
                if(request.method=="HEAD")MockResponse().setResponseCode(200) else MockResponse().setBody(request.body.clone())}
            peer.start()
            fun client()=OkHttpClient.Builder().sslSocketFactory(tls.second.socketFactory,TestTls.trustManager()).proxy(Proxy.NO_PROXY)
                .retryOnConnectionFailure(false).followRedirects(false).connectionPool(ConnectionPool(2,1,TimeUnit.SECONDS))
                .dispatcher(HttpDispatcher().apply{maxRequests=8;maxRequestsPerHost=8}).readTimeout(5,TimeUnit.SECONDS).build()
            val activeClient=AtomicReference(client())
            val networkLock=Any();val generation=AtomicLong()
            val errors=AtomicReference<Throwable?>()
            val packets=AtomicInteger();val sessions=AtomicInteger();val disconnects=AtomicInteger()
            val maxSessions=AtomicInteger();val maxH2=AtomicInteger();var maxCalls=0;var heap=0L
            val started=CountDownLatch(1)
            val factory=object:TransportFactory{
                override fun openHttpMedia(domain:String,tag:ByteArray,own:(Closeable)->Unit):RemoteTransport {
                    assertTrue(domain in setOf("kws2.proxy.example","kws4.proxy.example"))
                    return Http2MediaTransport.open(activeClient.get(),peer.url("/api").newBuilder().host("localhost").build(),tag,own)
                }
                override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport=throw IOException("No fallback endpoint in test")
                override fun openTcp(address:String,own:(Closeable)->Unit):RemoteTransport=throw IOException("No fallback endpoint in test")
            }
            val port=ServerSocket(0).use{it.localPort}
            val workers=Executors.newFixedThreadPool(4)
            MtprotoServer(port,secret.joinToString(""){"%02x".format(it)},onEvent={event->
                if(event==ServerEvent.State(ServerPhase.LISTENING))started.countDown()
                if(event is ServerEvent.Sessions){maxSessions.accumulateAndGet(event.established+event.pending,::maxOf);maxH2.accumulateAndGet(event.h2,::maxOf)}
            },transports=factory,transportSettings=TransportSettings(TransportMode.AUTO_CLOUDFLARE,cloudflareDomains=listOf("proxy.example"),http2Media=true)).use{server->
                server.start();assertTrue(started.await(3,TimeUnit.SECONDS))
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds.toLong())
                val jobs=(0..3).map{worker->workers.submit{
                    var index=worker
                    try{while(System.nanoTime()<deadline && errors.get()==null){
                        val pair=synchronized(networkLock){generation.get() to Socket("127.0.0.1",port).apply{tcpNoDelay=true;soTimeout=8000}}
                        try{pair.second.use{socket->
                            val marker=listOf(0xef,0xee,0xdd)[worker%3]
                            val plain=ByteArray(64){(it+1).toByte()};for(i in 56..59)plain[i]=marker.toByte()
                            val dc=if(worker%2==0)-2 else -4;plain[60]=dc.toByte();plain[61]=(dc shr 8).toByte()
                            val material=plain.copyOfRange(8,56);val send=ctr(sha(material.copyOfRange(0,32)+secret),material.copyOfRange(32,48))
                            val hello=plain.copyOf().apply{send.update(plain).copyInto(this,56,56,64)}
                            val reversed=material.reversedArray();val receive=ctr(sha(reversed.copyOfRange(0,32)+secret),reversed.copyOfRange(32,48))
                            socket.getOutputStream().write(hello)
                            fun read(n:Int):ByteArray {
                                val bytes=socket.getInputStream().readNBytes(n);if(bytes.size!=n)throw java.io.EOFException()
                                return receive.update(bytes)
                            }
                            repeat(if(worker==0)10 else 4){sequence->
                                val body=ByteArray(40){((it+index+sequence)%250+1).toByte()}
                                val frame=(if(marker==0xef)byteArrayOf(10)else byteArrayOf(40,0,0,0))+body
                                val encrypted=send.update(frame)
                                socket.getOutputStream().write(encrypted,0,2);socket.getOutputStream().write(encrypted,2,encrypted.size-2)
                                val size=if(marker==0xef)(read(1)[0].toInt() and 127)*4 else {
                                    val header=read(4);(0..3).fold(0){n,i->n or ((header[i].toInt() and 255) shl (i*8))}
                                }
                                assertTrue(size in 40..43);val reply=read(size)
                                assertArrayEquals(body,reply.copyOf(40));if(marker!=0xdd)assertEquals(40,size)
                                packets.incrementAndGet();if(worker==0)Thread.sleep(5)
                            }
                            sessions.incrementAndGet()
                        }}catch(error:IOException){if(generation.get()==pair.first)throw error;disconnects.incrementAndGet()}
                        index+=4;Thread.sleep(20)
                    }}catch(error:Throwable){errors.compareAndSet(null,error)}
                }}
                var resetAt=System.nanoTime()+TimeUnit.SECONDS.toNanos(1)
                try{
                    while(System.nanoTime()<deadline && errors.get()==null){
                        val dispatcher=activeClient.get().dispatcher
                        maxCalls=maxOf(maxCalls,dispatcher.runningCallsCount()+dispatcher.queuedCallsCount())
                        heap=maxOf(heap,Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())
                        if(System.nanoTime()>=resetAt){
                            synchronized(networkLock){
                                generation.incrementAndGet();val old=activeClient.getAndSet(client())
                                server.disconnectSessions();close(old)
                            }
                            resetAt=System.nanoTime()+TimeUnit.SECONDS.toNanos(1)
                        }
                        Thread.sleep(10)
                    }
                    jobs.forEach{it.get(10,TimeUnit.SECONDS)}
                }finally{server.stop();workers.shutdownNow();close(activeClient.get())}
                errors.get()?.let{throw AssertionError("Native H2 soak failed",it)}
                assertTrue(packets.get()>0 && sessions.get()>0);assertTrue(maxSessions.get() in 1..8);assertTrue(maxH2.get() in 1..8)
                assertTrue(maxCalls<=16);assertEquals(0 to 0,server.warmConnections())
                println("H2_SOAK seconds=$seconds completedPackets=${packets.get()} completedSessions=${sessions.get()} resets=${generation.get()} expectedDisconnects=${disconnects.get()} maxSessions=${maxSessions.get()} maxH2Sessions=${maxH2.get()} maxHttpCalls=$maxCalls totalHttpRequests=${peer.requestCount} peakHeapBytes=$heap")
            }
            assertTrue(workers.awaitTermination(3,TimeUnit.SECONDS))
            assertThrows(IOException::class.java){Socket("127.0.0.1",port).close()}
        }
    }
}
