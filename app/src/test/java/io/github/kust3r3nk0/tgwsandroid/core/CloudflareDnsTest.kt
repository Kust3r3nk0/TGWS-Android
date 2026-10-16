package io.github.kust3r3nk0.tgwsandroid.core

import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class CloudflareDnsTest {
    private fun fixture(action:(MockWebServer,OkHttpClient)->Unit){
        val tls=TestTls.contexts()
        MockWebServer().use{server->
            server.useHttps(tls.first.socketFactory,false);server.protocols=listOf(Protocol.HTTP_2,Protocol.HTTP_1_1);server.start()
            val client=OkHttpClient.Builder().sslSocketFactory(tls.second.socketFactory,TestTls.trustManager())
                .proxy(Proxy.NO_PROXY).callTimeout(2,TimeUnit.SECONDS).build()
            try{action(server,client)}finally{client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdownNow()}
        }
    }
    private fun open(server:MockWebServer,client:OkHttpClient,valid:()->Boolean={true})=
        CloudflareDns.using(client,server.url("/dns-query").newBuilder().host("localhost").build().toString(),listOf(InetAddress.getByName("127.0.0.1")),valid)
    private fun reply(request:RecordedRequest,empty:Boolean=false):MockResponse {
        assertEquals("POST",request.method);assertEquals("/dns-query",request.path)
        assertEquals("application/dns-message",request.getHeader("Content-Type"))
        val query=request.body.readByteArray();var end=12
        while(query[end]!=0.toByte())end+=(query[end].toInt() and 255)+1
        end++;val type=((query[end].toInt() and 255) shl 8) or (query[end+1].toInt() and 255);end+=4
        assertTrue(type in listOf(1,28))
        val address=if(type==1)byteArrayOf(192.toByte(),0,2,42) else InetAddress.getByName("2001:db8::42").address
        val bytes=query.copyOf(2)+byteArrayOf(0x81.toByte(),if(empty)0x83.toByte() else 0x80.toByte(),0,1,0,if(empty)0 else 1,0,0,0,0)+query.copyOfRange(12,end)+
            if(empty)byteArrayOf()else byteArrayOf(0xc0.toByte(),12,0,type.toByte(),0,1,0,0,0,1,0,address.size.toByte())+address
        return MockResponse().setHeader("Content-Type","application/dns-message").setBody(Buffer().write(bytes))
    }
    @Test fun realTlsDohReturnsBothFamiliesWithoutSystemDnsAndRedirection(){fixture{server,client->
        server.dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest)=reply(request)}
        open(server,client.newBuilder().dns{throw AssertionError("System DNS must not be used")}.build()).use{resolver->
            val results=resolver.lookup("example.com")
            assertEquals(2,results.size);assertTrue(results.any{it is java.net.Inet4Address});assertTrue(results.any{it is java.net.Inet6Address})
            assertTrue(results.any{it.address.contentEquals(byteArrayOf(192.toByte(),0,2,42))})
        }
        assertEquals(2,server.requestCount)
    }}
    @Test fun nxdomainMalformedHtmlOversizeAndRedirectFailWithoutFallback(){
        for(kind in listOf("nxdomain","bad","html","oversize","redirect"))fixture{server,client->
            server.dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest)=when(kind){
                "nxdomain"->reply(request,true)
                "bad"->MockResponse().setHeader("Content-Type","application/dns-message").setBody("bad")
                "html"->MockResponse().setHeader("Content-Type","text/html").setBody("invalid")
                "oversize"->MockResponse().setHeader("Content-Type","application/dns-message").setHeader("Content-Length",65536)
                else->MockResponse().setResponseCode(302).setHeader("Location","https://unrelated.example/")
            }}
            val system=client.newBuilder().dns{throw AssertionError("No plaintext fallback")}.build()
            open(server,system).use{assertThrows(UnknownHostException::class.java){it.lookup("example.com")}}
            assertEquals(2,server.requestCount)
        }
    }
    @Test fun tlsCertificateFailureIsNotBypassed(){fixture{server,_->
        val untrusted=OkHttpClient.Builder().proxy(Proxy.NO_PROXY).callTimeout(2,TimeUnit.SECONDS).build()
        try{open(server,untrusted).use{assertThrows(UnknownHostException::class.java){it.lookup("example.com")}}}
        finally{untrusted.dispatcher.cancelAll();untrusted.connectionPool.evictAll();untrusted.dispatcher.executorService.shutdownNow()}
    }}
    @Test fun stopCancelsPendingDohAndStaleNetworkResultsCannotBeAdopted(){fixture{server,client->
        val started=CountDownLatch(1);val release=CountDownLatch(1);val current=AtomicBoolean(true)
        server.dispatcher=object:Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse{
            started.countDown();release.await(3,TimeUnit.SECONDS);return reply(request)
        }}
        val workers=Executors.newSingleThreadExecutor();val resolver=open(server,client){current.get()}
        try{
            val result=workers.submit<Boolean>{try{resolver.lookup("example.com");false}catch(_:java.io.IOException){true}}
            assertTrue(started.await(1,TimeUnit.SECONDS));current.set(false);resolver.close();release.countDown()
            assertTrue(result.get(2,TimeUnit.SECONDS));assertThrows(NetworkUnavailable::class.java){resolver.lookup("example.com")}
        }finally{release.countDown();resolver.close();workers.shutdownNow();assertTrue(workers.awaitTermination(2,TimeUnit.SECONDS))}
    }}
}
