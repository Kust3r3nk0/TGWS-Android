package io.github.kust3r3nk0.tgwsandroid.core

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class HappyEyeballsTest {
    private val v6=InetAddress.getByName("2001:db8::1")
    private val v4=InetAddress.getByName("192.0.2.1")
    private class Peer(private val dial:(InetSocketAddress,Int,Peer)->Unit):Socket(){
        val released=CountDownLatch(1)
        override fun connect(endpoint:SocketAddress,timeout:Int)=dial(endpoint as InetSocketAddress,timeout,this)
        override fun close(){super.close();released.countDown()}
    }
    private fun network(answers:List<InetAddress>,dial:(InetSocketAddress,Int,Peer)->Unit)=object:NetworkAccess{
        override val validated=true
        override fun addresses(host:String)=answers
        override fun socket()=Peer(dial)
    }
    @Test fun otherFamilyStartsBeforeBlackholedPreferredAddressTimesOutAndLoserCloses(){
        for(answers in listOf(listOf(v6,v4),listOf(v4,v6))){
            val preferred=CountDownLatch(1);val fallback=CountDownLatch(1)
            val owned=Collections.synchronizedList(mutableListOf<Socket>())
            val access=network(answers){target,timeout,peer->
                if(target.address==answers.first()){
                    preferred.countDown()
                    assertTrue("Losing socket was not closed",peer.released.await(timeout.toLong(),TimeUnit.MILLISECONDS))
                    throw SocketException("Closed")
                }else{assertEquals(0L,preferred.count);fallback.countDown()}
            }
            NetworkConnector{access}.use{connector->
                connector.connect("relay.example",443,2000){owned+=it}.use{winner->
                    assertEquals(0L,fallback.count);assertEquals(2,owned.size)
                    assertTrue(owned.first().isClosed);assertFalse(winner.isClosed)
                    assertEquals(answers.mapNotNull{it.hostAddress},connector.takeTiming(winner)?.resolvedAddresses)
                }
                assertTrue(owned.all{it.isClosed})
            }
        }
    }
    @Test fun alternateFamilyIsNotBuriedBehindMultipleAaaaAnswers(){
        val access=network(listOf(v6,InetAddress.getByName("2001:db8::2"),v4)){_,_,_->}
        NetworkConnector{access}.use{connector->
            assertEquals(listOf(v6,v4,InetAddress.getByName("2001:db8::2")),
                connector.interleave(access.addresses("relay.example")))
        }
    }
    @Test fun eightAnswerCapRetainsAlternateFamilyEvenAfterManyAaaaRecords(){
        val answers=(1..10).map{InetAddress.getByName("2001:db8::$it")}+v4
        val count=AtomicInteger()
        val access=network(answers){target,_,_->count.incrementAndGet();if(target.address!=v4)throw java.net.ConnectException("Refused")}
        NetworkConnector{access}.use{connector->
            connector.connect("relay.example",443,2000){}.use{winner->
                assertEquals(2,count.get());val timing=requireNotNull(connector.takeTiming(winner))
                assertEquals(8,timing.resolvedAddresses.size);assertTrue(timing.resolvedAddresses.contains(requireNotNull(v4.hostAddress)))
            }
        }
    }
    @Test fun bothFamiliesBlackholedRespectDeadlineAndCloseEveryAttempt(){
        val owned=Collections.synchronizedList(mutableListOf<Socket>())
        val access=network(listOf(v6,v4)){_,timeout,peer->
            peer.released.await(timeout.toLong(),TimeUnit.MILLISECONDS)
            throw java.net.SocketTimeoutException("Timed out")
        }
        NetworkConnector{access}.use{connector->
            assertThrows(java.net.SocketTimeoutException::class.java){connector.connect("relay.example",443,600){owned+=it}}
            assertEquals(2,owned.size);assertTrue(owned.all{it.isClosed})
        }
    }
    @Test fun invalidateCancelsInFlightDialAndNeverReturnsOldNetworkSocket(){
        val started=CountDownLatch(1);val workers=Executors.newSingleThreadExecutor()
        val access=network(listOf(v6,v4)){_,_,peer->started.countDown();peer.released.await();throw SocketException("Closed")}
        val owned=Collections.synchronizedList(mutableListOf<Socket>())
        try{NetworkConnector{access}.use{connector->
            val future=workers.submit<Boolean>{try{connector.connect("relay.example",443,3000){owned+=it};false}catch(_:NetworkUnavailable){true}}
            assertTrue(started.await(1,TimeUnit.SECONDS));connector.invalidate()
            assertTrue(future.get(1,TimeUnit.SECONDS));assertTrue(owned.all{it.isClosed})
        }}finally{workers.shutdownNow();assertTrue(workers.awaitTermination(1,TimeUnit.SECONDS))}
    }
    @Test fun immediateRefusalTriesEveryAnswerOnceAndReleasesSockets(){
        val count=AtomicInteger();val owned=mutableListOf<Socket>()
        val answers=(1..10).map{InetAddress.getByName("192.0.2.$it")}
        val access=network(answers){_,_,_->count.incrementAndGet();throw java.net.ConnectException("Refused")}
        NetworkConnector{access}.use{connector->
            assertThrows(java.net.ConnectException::class.java){connector.connect("relay.example",443,2000){owned+=it}}
            assertEquals(8,count.get());assertTrue(owned.all{it.isClosed})
        }
    }
    @Test fun canceledOwnerCannotLaunchOrLeakAnotherSocket(){
        val count=AtomicInteger();val owned=mutableListOf<Socket>()
        val access=network(listOf(v6,v4)){_,_,_->count.incrementAndGet()}
        NetworkConnector{access}.use{connector->
            assertThrows(java.io.IOException::class.java){connector.connect("relay.example",443,1000){owned+=it;it.close()}}
            assertEquals(0,count.get());assertEquals(1,owned.size);assertTrue(owned.all{it.isClosed})
        }
    }
}
