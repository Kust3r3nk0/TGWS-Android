package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

/** Actual loopback TCP, strict fixture TLS/WSS and independent client CTR state; no Telegram account. */
class ManagedRelayNetworkTest {
    private val secret=ByteArray(16){it.toByte()}
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun ctr(key:ByteArray,iv:ByteArray)=Cipher.getInstance("AES/CTR/NoPadding").apply{
        init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),IvParameterSpec(iv))
    }
    private fun await(message:String,check:()->Boolean){val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4)
        while(!check()){assertTrue(message,System.nanoTime()<end);Thread.sleep(2)}}
    @Test fun largeEncryptedMediaUsesOwnWssRelayWithoutReplayOrWorkerLimit(){
        val events=CopyOnWriteArrayList<ServerEvent>();val tcp=AtomicInteger();val wss=AtomicInteger()
        val port=ServerSocket(0).use{it.localPort}
        LocalTelegramPeer().use{peer->
            val factory=object:TransportFactory {
                override fun openRelay(endpoint:RelayEndpoint,dc:Int,own:(Closeable)->Unit):RemoteTransport {
                    wss.incrementAndGet()
                    val ws=RawWebSocket.connectTls("127.0.0.1","localhost","/apiws",peer.wsPort,peer.clientTls,
                        RawWebSocket.Limits(connectMillis=1000,handshakeMillis=2000,readMillis=5000,closeMillis=200),socketOwner={own(it)})
                    return object:RemoteTransport {
                        override val kind=TransportKind.RELAY
                        override fun send(data:ByteArray)=ws.send(data)
                        override fun receive()=ws.receive()
                        override fun close()=ws.close()
                        override fun abort()=ws.abort()
                    }
                }
                override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport=error("Unpermitted direct WSS")
                override fun openTcp(address:String,own:(Closeable)->Unit):RemoteTransport {
                    tcp.incrementAndGet();val socket=Socket().also{own(it);it.connect(java.net.InetSocketAddress("127.0.0.1",peer.tcpPort),1000)}
                    return object:RemoteTransport {
                        override val kind=TransportKind.TCP
                        override fun send(data:ByteArray){socket.getOutputStream().write(data);socket.getOutputStream().flush()}
                        override fun receive():ByteArray?{val bytes=ByteArray(4096);val n=socket.getInputStream().read(bytes);return if(n<0)null else bytes.copyOf(n)}
                        override fun close()=socket.close()
                    }
                }
            }
            val node=RelayNode("origin","Private test label","wss://relay.example/private/apiws")
            val manager=RelayConfiguration(true,listOf(node),node.id,preferMedia=true)
            MtprotoServer(port,secret.joinToString(""){ "%02x".format(it)},events::add,transports=factory,
                transportSettings=TransportSettings(mode=TransportMode.RELAY_ONLY,relays=manager)).use{server->
                server.start();await("Listener not ready"){events.contains(ServerEvent.State(ServerPhase.LISTENING))}
                fun exchange(index:Int,hold:(Socket)->Unit={}){
                    Socket("127.0.0.1",port).use{client->
                        client.soTimeout=4000
                        val tag=0xee;val dc=-4
                        val header=ByteArray(64){(it+1+index).toByte()};for(i in 56..59)header[i]=tag.toByte()
                        header[60]=dc.toByte();header[61]=(dc shr 8).toByte()
                        val material=header.copyOfRange(8,56);val out=ctr(sha(material.copyOfRange(0,32)+secret),material.copyOfRange(32,48))
                        val init=header.copyOf().apply{out.update(header).copyInto(this,56,56,64)}
                        val reversed=material.reversedArray();val incoming=ctr(sha(reversed.copyOfRange(0,32)+secret),reversed.copyOfRange(32,48))
                        client.getOutputStream().write(init,0,17);client.getOutputStream().write(init,17,47)
                        repeat(24){sequence->
                            val body=ByteArray(1024*1024){(it+sequence).toByte()}
                            val packet=byteArrayOf(0,0,16,0)+body
                            val encrypted=out.update(packet)
                            client.getOutputStream().write(encrypted,0,2);client.getOutputStream().write(encrypted,2,encrypted.size-2)
                            assertArrayEquals("No replay, corruption or CTR reuse",packet,incoming.update(client.getInputStream().readNBytes(packet.size)))
                        }
                        hold(client)
                    }
                }
                exchange(0){
                    await("No real relay progress"){events.filterIsInstance<ServerEvent.Health>().lastOrNull()?.health?.active?.any{it.inboundProgress && it.relaySlot==1}==true}
                    assertEquals(0,tcp.get());assertEquals(1,wss.get())
                }
                server.stop()
                assertTrue(server.trafficTotals().first>=24L*1024*1024)
                assertTrue(server.trafficTotals().second>=24L*1024*1024)
                assertNull(peer.failure.get())
            }
        }
    }
}
