package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class RouteProbeTest {
    private fun await(condition:()->Boolean){val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!condition()){assertTrue(System.nanoTime()<end);Thread.sleep(2)}}
    @Test fun explicitProbeIsBoundedRateLimitedAndSendsNoMtprotoInitialization(){
        val opens=AtomicInteger();val sends=AtomicInteger();val closes=AtomicInteger();val events=CopyOnWriteArrayList<ServerEvent>()
        val factory=object:TransportFactory{
            override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport{
                opens.incrementAndGet();return object:RemoteTransport{
                    override val kind=TransportKind.WSS
                    override fun send(data:ByteArray){sends.incrementAndGet()}
                    override fun receive():ByteArray?=throw AssertionError("Probe reads MTProto")
                    override fun close(){closes.incrementAndGet()}
                }.also(own)
            }
            override fun openTcp(address:String,own:(Closeable)->Unit):RemoteTransport=throw AssertionError("Unconfigured TCP probe")
        }
        val port=ServerSocket(0).use{it.localPort}
        MtprotoServer(port,"00112233445566778899aabbccddeeff",{events+=it},transports=factory).use{server->
            server.start();await{events.contains(ServerEvent.State(ServerPhase.LISTENING))}
            assertTrue(server.probeRoutes());assertFalse(server.probeRoutes())
            await{events.filterIsInstance<ServerEvent.Routes>().any{it.routes.any{it.status==RouteStatus.AVAILABLE}}}
            await{closes.get()>0};assertEquals(1,opens.get());assertEquals(0,sends.get());assertFalse(server.probeRoutes())
            server.disconnectSessions();assertTrue(events.filterIsInstance<ServerEvent.Routes>().last().routes.isEmpty())
        }
    }
}
