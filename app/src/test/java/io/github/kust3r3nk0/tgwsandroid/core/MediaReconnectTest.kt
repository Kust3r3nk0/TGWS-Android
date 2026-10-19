package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

/** Large deterministic streams, independent peer crypto; no account or external endpoint. */
class MediaReconnectTest {
    private val secret=ByteArray(16){it.toByte()}
    private fun sha(value:ByteArray)=MessageDigest.getInstance("SHA-256").digest(value)
    private fun ctr(key:ByteArray,iv:ByteArray)=Cipher.getInstance("AES/CTR/NoPadding").apply{
        init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),IvParameterSpec(iv))
    }
    private class Client(val socket:Socket,val encrypt:Cipher,val decrypt:Cipher):Closeable {
        fun exchange(packet:ByteArray){
            socket.getOutputStream().write(encrypt.update(packet))
            val response=ByteArray(packet.size)
            var offset=0
            while(offset<response.size){
                val count=socket.getInputStream().read(response,offset,response.size-offset)
                if(count<0)throw EOFException("Media stream truncated")
                offset+=count
            }
            assertTrue("Media stream or CTR state changed",MessageDigest.isEqual(packet,decrypt.update(response)))
        }
        override fun close()=socket.close()
    }
    private fun client(port:Int,tag:Int,seed:Int,key:ByteArray=secret):Client {
        val plain=ByteArray(64){(it+seed).toByte()}
        for(i in 56..59)plain[i]=tag.toByte()
        plain[60]=(-2).toByte();plain[61]=(-1).toByte()
        val material=plain.copyOfRange(8,56)
        val encrypt=ctr(sha(material.copyOfRange(0,32)+key),material.copyOfRange(32,48))
        val header=plain.copyOf().apply{encrypt.update(plain).copyInto(this,56,56,64)}
        val reversed=material.reversedArray()
        val socket=Socket("127.0.0.1",port).apply{soTimeout=5000;tcpNoDelay=true}
        socket.getOutputStream().write(header,0,17)
        socket.getOutputStream().write(header,17,47)
        return Client(socket,encrypt,ctr(sha(reversed.copyOfRange(0,32)+key),reversed.copyOfRange(32,48)))
    }
    private fun packet(tag:Int):ByteArray {
        val length=1024*1024+if(tag==0xdd)3 else 0
        val body=ByteArray(length){(it*13).toByte()}
        val header=if(tag==0xef){val words=length/4;byteArrayOf(127,words.toByte(),(words shr 8).toByte(),(words shr 16).toByte())}
            else byteArrayOf(length.toByte(),(length shr 8).toByte(),(length shr 16).toByte(),0)
        return header+body
    }
    private fun await(condition:()->Boolean){
        val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3)
        while(!condition()){check(System.nanoTime()<end){"Local media lifecycle deadline"};Thread.sleep(2)}
    }
    @Test(timeout=60000) fun largeMediaSurvivesRejectedClientsNetworkResetsAndRestartsAcrossAllNativeTransports(){
        LocalTelegramPeer().use { peer ->
            for(kind in listOf(TransportKind.WSS,TransportKind.TCP,TransportKind.RELAY)) {
                val factory=object:TransportFactory {
                    private fun websocket(own:(Closeable)->Unit):RemoteTransport {
                        val ws=RawWebSocket.connectTls("127.0.0.1","localhost","/apiws",peer.wsPort,peer.clientTls,
                            RawWebSocket.Limits(readMillis=5000),socketOwner={own(it)})
                        return object:RemoteTransport {
                            override val kind=kind
                            override fun send(data:ByteArray)=ws.send(data)
                            override fun send(data:ByteArray,progress:()->Unit)=ws.send(data,progress)
                            override fun receive()=ws.receive()
                            override fun close()=ws.abort()
                        }
                    }
                    override fun openRelay(endpoint:RelayEndpoint,dc:Int,own:(Closeable)->Unit)=websocket(own)
                    override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport {
                        if(kind==TransportKind.TCP)throw IOException("Force TCP path")
                        return websocket(own)
                    }
                    override fun openTcp(address:String,own:(Closeable)->Unit):RemoteTransport {
                        val socket=Socket("127.0.0.1",peer.tcpPort).also(own)
                        return object:RemoteTransport {
                            override val kind=TransportKind.TCP
                            override fun send(data:ByteArray){socket.getOutputStream().write(data)}
                            override fun send(data:ByteArray,progress:()->Unit)=writeWithProgress(socket.getOutputStream(),data,progress)
                            override fun receive():ByteArray?{val b=ByteArray(65536);val n=socket.getInputStream().read(b);return if(n<0)null else b.copyOf(n)}
                            override fun close()=socket.close()
                        }
                    }
                }
                for(tag in listOf(0xef,0xee,0xdd)) {
                    val port=ServerSocket(0).use{it.localPort}
                    val events=CopyOnWriteArrayList<ServerEvent>()
                    val phase=AtomicReference(ServerPhase.STOPPED)
                    MtprotoServer(port,secret.joinToString(""){"%02x".format(it)},{events+=it;if(it is ServerEvent.State)phase.set(it.phase)},
                        transports=factory,relayEndpoint=if(kind==TransportKind.RELAY)RelayEndpoint.parse("wss://relay.example/key/apiws")else null).use{server->
                        server.start();await{phase.get()==ServerPhase.LISTENING}
                        val packet=packet(tag)
                        client(port,tag,1).use { active ->
                            active.exchange(packet)
                            val wrong=secret.copyOf().apply{this[0]=(this[0].toInt() xor 1).toByte()}
                            client(port,tag,3,wrong).use { bad ->assertEquals(-1,bad.socket.getInputStream().read())}
                            await{events.any{it is ServerEvent.Diagnostic && it.rejection==HandshakeRejection.SECRET_OR_TRANSPORT}}
                            active.exchange(packet)
                            server.disconnectSessions()
                            assertEquals(-1,active.socket.getInputStream().read())
                        }
                        client(port,tag,5).use{it.exchange(packet)}
                        server.stop();server.start();await{phase.get()==ServerPhase.LISTENING}
                        client(port,tag,7).use{it.exchange(packet)}
                        assertFalse(events.contains(ServerEvent.State(ServerPhase.ERROR)))
                    }
                }
            }
        }
    }
}
