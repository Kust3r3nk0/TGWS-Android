package io.github.kust3r3nk0.tgwsandroid.core

import java.io.IOException
import java.net.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class Http2MediaTransportTest {
    private val tag=ByteArray(4){0xee.toByte()}
    private fun ctr(key:ByteArray,iv:ByteArray)=Cipher.getInstance("AES/CTR/NoPadding").apply{init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),IvParameterSpec(iv))}
    private class Crypto(val init:ByteArray,val send:Cipher,val receive:Cipher)
    private fun crypto():Crypto {
        val handshake=Handshake(2,true,tag,-2,ByteArray(48){(it+2).toByte()})
        val init=MtprotoCrypto.createRelayInit(handshake)
        val forward=ctr(init.copyOfRange(8,40),init.copyOfRange(40,56)).apply{update(ByteArray(64))}
        val reversed=init.copyOfRange(8,56).reversedArray()
        return Crypto(init,forward,ctr(reversed.copyOfRange(0,32),reversed.copyOfRange(32,48)))
    }
    private fun fixture(h2:Boolean=true,action:(MockWebServer,OkHttpClient)->Unit){
        val contexts=TestTls.contexts()
        MockWebServer().use{server->
            server.useHttps(contexts.first.socketFactory,false)
            server.protocols=if(h2)listOf(Protocol.HTTP_2,Protocol.HTTP_1_1)else listOf(Protocol.HTTP_1_1)
            server.start()
            val client=OkHttpClient.Builder().sslSocketFactory(contexts.second.socketFactory,TestTls.trustManager())
                .proxy(Proxy.NO_PROXY).retryOnConnectionFailure(false).followRedirects(false)
                .connectTimeout(2,TimeUnit.SECONDS).readTimeout(3,TimeUnit.SECONDS).callTimeout(4,TimeUnit.SECONDS).build()
            try{action(server,client)}finally{client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdownNow()}
        }
    }
    private fun endpoint(server:MockWebServer)=server.url("/api").newBuilder().host("localhost").build()
    @Test fun realTlsH2MultiplexedStreamsPreserveBinaryBodiesAndContinuousCtr(){fixture{server,client->
        val posted=CountDownLatch(2)
        server.dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse{
            if(request.method=="HEAD")return MockResponse().setResponseCode(200)
            posted.countDown();assertTrue("H2 streams were not concurrent",posted.await(2,TimeUnit.SECONDS))
            return MockResponse().setBody(request.body.clone()).throttleBody(8,2,TimeUnit.MILLISECONDS)
        }}
        val transports=(0..1).map{Http2MediaTransport.open(client,endpoint(server),tag){}}
        assertNotNull(transports[0].setupTimings?.tlsMillis)
        assertTrue(transports[0].setupTimings!!.resolvedAddresses.isNotEmpty())
        assertNull(transports[1].setupTimings?.tlsMillis)
        assertTrue(transports[1].setupTimings!!.resolvedAddresses.isEmpty())
        try{
            val cryptos=(0..1).map{crypto()}
            val payloads=(0..1).map{worker->ByteArray(24){(it+worker+1).toByte()}}
            for(i in 0..1){val c=cryptos[i];transports[i].send(c.init);val frame=byteArrayOf(24,0,0,0)+payloads[i]
                val encrypted=c.send.update(frame);transports[i].send(encrypted.copyOf(2));transports[i].send(encrypted.copyOfRange(2,encrypted.size))}
            assertTrue(posted.await(3,TimeUnit.SECONDS))
            for(i in 0..1)assertArrayEquals(byteArrayOf(24,0,0,0)+payloads[i],cryptos[i].receive.update(transports[i].receive()!!))
            val requests=(0..3).map{server.takeRequest(2,TimeUnit.SECONDS)!!}
            assertTrue(requests.all{it.path=="/api"});assertEquals(setOf(0,1,2,3),requests.map{it.sequenceNumber}.toSet())
            assertTrue(requests.filter{it.method=="POST"}.all{it.bodySize==24L})
        }finally{transports.forEach{it.close()}}
    }}
    @Test fun refusedAlpnAndHttpPreflightCannotConsumeNativePayload(){
        fixture(false){server,client->server.enqueue(MockResponse().setResponseCode(200));assertThrows(IOException::class.java){Http2MediaTransport.open(client,endpoint(server),tag){}}
            assertEquals("HEAD",server.takeRequest().method);assertEquals(1,server.requestCount)}
        fixture{server,client->server.enqueue(MockResponse().setResponseCode(403));assertThrows(IOException::class.java){Http2MediaTransport.open(client,endpoint(server),tag){}}}
    }
    @Test fun resetStreamAndDisconnectTerminateWithoutRequestReplay(){
        for(policy in listOf(SocketPolicy.RESET_STREAM_AT_START,SocketPolicy.DISCONNECT_AFTER_REQUEST))fixture{server,client->
            server.enqueue(MockResponse().setResponseCode(200));server.enqueue(MockResponse().setSocketPolicy(policy).setHttp2ErrorCode(8))
            Http2MediaTransport.open(client,endpoint(server),tag){}.use{transport->val c=crypto();transport.send(c.init)
                transport.send(c.send.update(byteArrayOf(24,0,0,0)+ByteArray(24){1}))
                assertThrows(IOException::class.java){transport.receive()}
                // RESET_STREAM is sent before MockWebServer enqueues its bookkeeping record.
                assertNotNull(server.takeRequest(2,TimeUnit.SECONDS))
                assertNotNull(server.takeRequest(2,TimeUnit.SECONDS))
                assertEquals(2,server.requestCount)}
        }
    }
    @Test fun realGoAwayBeforeHeadersClosesChannelWithoutReplayingUserData(){
        val contexts=TestTls.contexts()
        mockwebserver3.MockWebServer().use{server->
            server.useHttps(contexts.first.socketFactory)
            server.protocols=listOf(Protocol.HTTP_2,Protocol.HTTP_1_1)
            server.enqueue(mockwebserver3.MockResponse.Builder().code(200).build())
            // A real HTTP/2 GOAWAY, rather than a TCP close or a fake route exception.
            server.enqueue(mockwebserver3.MockResponse.Builder().onResponseStart(mockwebserver3.SocketEffect.ShutdownConnection).build())
            server.start()
            val client=OkHttpClient.Builder().sslSocketFactory(contexts.second.socketFactory,TestTls.trustManager())
                .proxy(Proxy.NO_PROXY).retryOnConnectionFailure(false).readTimeout(1,TimeUnit.SECONDS).build()
            try{
                val url=server.url("/api").newBuilder().host("localhost").build()
                Http2MediaTransport.open(client,url,tag){}.use{transport->
                    val c=crypto();transport.send(c.init)
                    transport.send(c.send.update(byteArrayOf(24,0,0,0)+ByteArray(24){1}))
                    assertThrows(IOException::class.java){transport.receive()}
                    assertEquals(2,server.requestCount)
                }
            }finally{client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdownNow()}
        }
    }
    @Test fun oversizeOrHtmlResponseFailsAndCancellationUnblocksReader(){
        for(response in listOf(MockResponse().setHeader("Content-Length",4194305),MockResponse().setHeader("Content-Type","text/html").setBody("fake")))fixture{server,client->
            server.enqueue(MockResponse().setResponseCode(200));server.enqueue(response)
            Http2MediaTransport.open(client,endpoint(server),tag){}.use{transport->val c=crypto();transport.send(c.init);transport.send(c.send.update(byteArrayOf(24,0,0,0)+ByteArray(24){1}))
                assertThrows(IOException::class.java){transport.receive()}}
        }
        fixture{server,client->server.enqueue(MockResponse().setResponseCode(200))
            val transport=Http2MediaTransport.open(client,endpoint(server),tag){};val executor=Executors.newSingleThreadExecutor()
            try{val result=executor.submit<ByteArray?>{transport.receive()};transport.close();assertNull(result.get(1,TimeUnit.SECONDS))}
            finally{transport.close();executor.shutdownNow();assertTrue(executor.awaitTermination(1,TimeUnit.SECONDS))}
        }
    }
    @Test fun largeBodyCrossesH2FlowControlWindowsWithoutChangingBytes(){fixture{server,client->
        val body=ByteArray(256*1024){(it+1).toByte()}
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setBody(Buffer().write(body)).throttleBody(8192,1,TimeUnit.MILLISECONDS))
        Http2MediaTransport.open(client,endpoint(server),tag){}.use{transport->
            val c=crypto();transport.send(c.init);val size=body.size
            val frame=byteArrayOf(size.toByte(),(size shr 8).toByte(),(size shr 16).toByte(),(size shr 24).toByte())+body
            transport.send(c.send.update(frame));assertArrayEquals(frame,c.receive.update(transport.receive()!!))
            assertEquals("HEAD",server.takeRequest().method);assertArrayEquals(body,server.takeRequest().body.readByteArray())
        }
    }}
    @Test fun pendingRequestBackpressureIsReleasedByCancellation(){fixture{server,client->
        server.enqueue(MockResponse().setResponseCode(200))
        repeat(2){server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))}
        val transport=Http2MediaTransport.open(client,endpoint(server),tag){}
        val executor=Executors.newSingleThreadExecutor()
        try{
            val c=crypto();transport.send(c.init);val packet=byteArrayOf(24,0,0,0)+ByteArray(24){1}
            repeat(2){transport.send(c.send.update(packet))}
            val started=CountDownLatch(1)
            val third=executor.submit<Boolean>{started.countDown();try{transport.send(c.send.update(packet));false}catch(_:IOException){true}}
            assertTrue(started.await(1,TimeUnit.SECONDS))
            assertThrows(java.util.concurrent.TimeoutException::class.java){third.get(50,TimeUnit.MILLISECONDS)}
            transport.close();assertTrue(third.get(1,TimeUnit.SECONDS))
        }finally{transport.close();executor.shutdownNow();assertTrue(executor.awaitTermination(1,TimeUnit.SECONDS))}
    }}
}
