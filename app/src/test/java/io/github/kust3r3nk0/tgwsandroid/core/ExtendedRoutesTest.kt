package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class ExtendedRoutesTest {
    private val secret="00112233445566778899aabbccddeeff"
    private val worker="wss://worker.example/credential/apiws"
    private fun hello(dc:Int):Pair<ByteArray,Cipher>{
        val p=ByteArray(64){(it+1).toByte()};for(i in 56..59)p[i]=0xee.toByte();p[60]=dc.toByte();p[61]=(dc shr 8).toByte()
        val cipher=Cipher.getInstance("AES/CTR/NoPadding").apply{init(Cipher.ENCRYPT_MODE,
            SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(p.copyOfRange(8,40)+FakeTls.hex(secret)),"AES"),IvParameterSpec(p.copyOfRange(40,56)))}
        return p.copyOf().apply{cipher.update(p).copyInto(this,56,56,64)} to cipher
    }
    private fun await(condition:()->Boolean){val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!condition()){assertTrue(System.nanoTime()<end);Thread.sleep(2)}}
    private fun exercise(config:TransportSettings,selected:TransportKind,dc:Int=2,relayOnly:Boolean=false,failData:Boolean=false):List<String>{
        val calls=CopyOnWriteArrayList<String>();val observed=CopyOnWriteArrayList<ServerEvent>();val close=CountDownLatch(1)
        val factory=object:TransportFactory{
            fun open(label:String,kind:TransportKind,own:(Closeable)->Unit):RemoteTransport{
                calls+=label
                if(relayOnly || kind!=selected)throw IOException("Injected route failure")
                return object:RemoteTransport{
                    override val kind=kind
                    private var initialized=false
                    override fun send(data:ByteArray){if(initialized && failData)throw IOException("Partial data write");initialized=true}
                    override fun receive():ByteArray?{close.await(3,TimeUnit.SECONDS);return null}
                    override fun close(){close.countDown()}
                }.also(own)
            }
            override fun openRelay(endpoint:RelayEndpoint,dc:Int,own:(Closeable)->Unit)=open("worker:$dc",TransportKind.RELAY,own)
            override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport{
                assertEquals(address,domain);assertFalse(domain.endsWith("telegram.org"))
                return open("cf:$domain",TransportKind.CLOUDFLARE,own)
            }
            override fun openTcp(address:String,own:(Closeable)->Unit)=open("tcp:$address",TransportKind.TCP,own)
            override fun openHttpMedia(domain:String,tag:ByteArray,own:(Closeable)->Unit)=open("h2:$domain",TransportKind.HTTP2,own)
        }
        val port=ServerSocket(0).use{it.localPort}
        MtprotoServer(port,secret,{observed+=it},transports=factory,transportSettings=config,
            relayEndpoint=if(relayOnly)RelayEndpoint.parse(worker)else null).use{server->
            server.start();await{observed.contains(ServerEvent.State(ServerPhase.LISTENING))}
            Socket("127.0.0.1",port).use{client->
                client.soTimeout=3000;val init=hello(dc);client.getOutputStream().write(init.first)
                if(relayOnly){assertEquals(-1,client.getInputStream().read())}else{
                    await{observed.filterIsInstance<ServerEvent.Sessions>().any{it.established==1}}
                    val routes=observed.filterIsInstance<ServerEvent.Routes>().last().routes
                    assertTrue(routes.any{it.status==RouteStatus.AVAILABLE && it.dc==kotlin.math.abs(dc) && it.media==(dc<0)})
                    assertFalse(routes.toString().contains("credential"))
                    if(failData){client.getOutputStream().write(init.second.update(byteArrayOf(8,0,0,0)+ByteArray(8)));assertEquals(-1,client.getInputStream().read())}
                }
            }
        }
        return calls.toList()
    }
    @Test fun autoModeUsesWorkerThenCloudflareThenTcpWithoutDirectWss(){
        val calls=exercise(TransportSettings(TransportMode.AUTO_CLOUDFLARE,cloudflareDomains=listOf("proxy.example"),workerUrls=listOf(worker)),TransportKind.TCP)
        assertEquals(listOf("worker:2","cf:kws2.proxy.example","tcp:149.154.167.51"),calls)
    }
    @Test fun mediaH2PreflightFailureFallsBackToCloudflareWssBeforePayload(){
        val calls=exercise(TransportSettings(TransportMode.AUTO_CLOUDFLARE,cloudflareDomains=listOf("proxy.example"),http2Media=true),TransportKind.CLOUDFLARE,-4)
        assertEquals(listOf("h2:kws4.proxy.example","cf:kws4.proxy.example"),calls)
    }
    @Test fun relayOnlyNeverLeaksIntoCloudflareOrDirectTcp(){
        assertEquals(listOf("worker:2"),exercise(TransportSettings(TransportMode.RELAY_ONLY),TransportKind.TCP,relayOnly=true))
    }
    @Test fun setupSuccessIsNotRetriedAfterPartialUserDataWrite(){
        val calls=exercise(TransportSettings(TransportMode.AUTO_CLOUDFLARE,workerUrls=listOf(worker)),TransportKind.RELAY,failData=true)
        assertEquals(listOf("worker:2"),calls)
    }
}
