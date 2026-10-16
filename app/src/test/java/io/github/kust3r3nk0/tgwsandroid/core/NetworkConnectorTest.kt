package io.github.kust3r3nk0.tgwsandroid.core

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class NetworkConnectorTest {
    private fun access(addresses:List<InetAddress>,validated:Boolean=true)=object:NetworkAccess{
        override val validated=validated
        override fun addresses(host:String)=addresses
        override fun socket()=Socket()
    }
    @Test fun ipv4DualStackAndDns64StyleIpv6UseAllAnswersOnCapturedNetwork(){
        val v4=InetAddress.getByName("127.0.0.1");val v6=InetAddress.getByName("::1")
        for(answers in listOf(listOf(v4),listOf(v6,v4))){
            ServerSocket(0,4,v4).use{listener->
                val network=access(answers)
                NetworkConnector{network}.use{connector->
                    val owned=mutableListOf<Socket>();connector.connect("configured.example",listener.localPort,1000){owned+=it}.use{connected->
                        listener.accept().use{assertTrue(connected.isConnected)}
                        assertEquals(answers.mapNotNull{it.hostAddress},connector.takeTiming(connected)?.resolvedAddresses)}
                    assertTrue(owned.all{it.isClosed})
                }
            }
        }
        // Models an AAAA/DNS64 answer. No NAT64 address is fabricated by the connector.
        ServerSocket(0,4,v6).use{listener->val network=access(listOf(v6))
            NetworkConnector{network}.use{connector->connector.connect("configured.example",listener.localPort,1000){}.use{
                listener.accept().use{peer->assertTrue(peer.inetAddress is java.net.Inet6Address)}}}}
    }
    @Test fun noValidatedNetworkAndNoCompatibleAddressFailBeforeAnyUnboundedRetry(){
        val sockets=AtomicInteger()
        val missing=object:NetworkAccess{
            override val validated=false
            override fun addresses(host:String)=throw AssertionError("DNS without validation")
            override fun socket():Socket{sockets.incrementAndGet();return Socket()}
        }
        NetworkConnector{missing}.use{assertThrows(NetworkUnavailable::class.java){it.connect("configured.example",443,100){}}}
        NetworkConnector{null}.use{assertThrows(NetworkUnavailable::class.java){it.connect("configured.example",443,100){}}}
        assertEquals(0,sockets.get())
        val empty=access(emptyList());NetworkConnector{empty}.use{assertThrows(java.net.UnknownHostException::class.java){it.connect("configured.example",443,100){}}}
    }
    @Test fun staleDnsFromOldNetworkCannotOpenSocketOnNewNetwork(){
        val started=CountDownLatch(1);val release=CountDownLatch(1);val sockets=AtomicInteger()
        val old=object:NetworkAccess{
            override val validated=true
            override fun addresses(host:String):List<InetAddress>{started.countDown();release.await();return listOf(InetAddress.getLoopbackAddress())}
            override fun socket():Socket{sockets.incrementAndGet();return Socket()}
        }
        val current=AtomicReference<NetworkAccess?>(old);val worker=Executors.newSingleThreadExecutor()
        NetworkConnector{current.get()}.use{connector->
            val task=worker.submit<Boolean>{try{connector.connect("configured.example",443,1000){};false}catch(_:NetworkUnavailable){true}}
            assertTrue(started.await(1,TimeUnit.SECONDS));current.set(access(listOf(InetAddress.getLoopbackAddress())));release.countDown()
            assertTrue(task.get(2,TimeUnit.SECONDS));assertEquals(0,sockets.get())
        }
        worker.shutdownNow();assertTrue(worker.awaitTermination(1,TimeUnit.SECONDS))
    }
}
