package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class NetworkTransportFactoryTest {
    private fun access(port:Int)=object:NetworkAccess {
        override val validated=true
        override fun addresses(host:String)=listOf(InetAddress.getLoopbackAddress())
        override fun socket()=object:Socket(){
            override fun connect(endpoint:SocketAddress,timeout:Int)=super.connect(InetSocketAddress("127.0.0.1",port),timeout)
        }
    }

    @Test(timeout=5000) fun invalidateClosesAlreadyConnectedTcpAndItsBlockedReader() {
        ServerSocket(0,4,InetAddress.getLoopbackAddress()).use{listener->
            val network=access(listener.localPort)
            NetworkTransportFactory{network}.use{factory->
                val owned=mutableListOf<Closeable>()
                val first=factory.openTcp("relay.example",owned::add)
                listener.accept().use{peer->
                    val reader=Executors.newSingleThreadExecutor()
                    try {
                        val reading=reader.submit<Boolean>{try{first.receive();false}catch(_:IOException){true}}
                        factory.invalidate()
                        assertTrue(reading.get(1,TimeUnit.SECONDS))
                        assertEquals(-1,peer.getInputStream().read())
                        first.abort();owned.forEach{it.close()}
                        val second=factory.openTcp("relay.example"){}
                        listener.accept().use{next->
                            second.send(byteArrayOf(42));assertEquals(42,next.getInputStream().read())
                            factory.close();assertEquals(-1,next.getInputStream().read())
                        }
                    }finally{reader.shutdownNow()}
                }
            }
        }
    }

    @Test(timeout=5000) fun invalidateDuringTlsImmediatelyClosesOldEpochWithoutReturningATransport() {
        ServerSocket(0,4,InetAddress.getLoopbackAddress()).use{listener->
            val network=access(listener.localPort)
            NetworkTransportFactory{network}.use{factory->
                val worker=Executors.newSingleThreadExecutor()
                try {
                    val opening=worker.submit<Boolean>{try{
                        factory.openRelay(RelayEndpoint.parse("wss://relay.example/private-token/apiws")!!,2){}
                        false
                    }catch(_:IOException){true}}
                    listener.accept().use{peer->
                        peer.soTimeout=1000
                        // The TLS ClientHello proves TCP has left connector.dialing.
                        assertTrue(peer.getInputStream().read()>=0)
                        factory.invalidate()
                        assertTrue(opening.get(1,TimeUnit.SECONDS))
                    }
                }finally{factory.close();worker.shutdownNow();assertTrue(worker.awaitTermination(1,TimeUnit.SECONDS))}
            }
        }
    }

    @Test(timeout=5000) fun parentCancellationBeforeConnectCannotLeakSocketOrReviveScope() {
        ServerSocket(0,4,InetAddress.getLoopbackAddress()).use{listener->
            val network=access(listener.localPort)
            NetworkTransportFactory{network}.use{factory->
                assertThrows(NetworkUnavailable::class.java){factory.openTcp("relay.example"){it.close()}}
                factory.close()
                assertThrows(NetworkUnavailable::class.java){factory.openTcp("relay.example") {}}
            }
        }
    }
}
