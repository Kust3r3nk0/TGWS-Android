package io.github.kust3r3nk0.tgwsandroid

import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LocalListenerCheckTest {
    @Test fun realLoopbackCheckSendsNoBytesAndClosesPeer()=runBlocking<Unit>{
        val executor=Executors.newSingleThreadExecutor()
        try{ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use{server->
            server.soTimeout=3000
            val peer=async(Dispatchers.IO){server.accept().use{it.soTimeout=3000;it.getInputStream().read()}}
            val result=LocalListenerCheck(executor).run(server.localPort)
            assertEquals(LocalCheckOutcome.AVAILABLE,result.outcome);assertNotNull(result.millis)
            assertEquals(-1,peer.await())
        }}finally{executor.shutdownNow()}
    }
    @Test fun closedPortAndInvalidPortHaveDistinctOutcomes()=runBlocking<Unit>{
        val executor=Executors.newSingleThreadExecutor()
        try{val port=ServerSocket(0).use{it.localPort}
            val checker=LocalListenerCheck(executor)
            assertEquals(LocalCheckOutcome.REFUSED,checker.run(port).outcome)
            assertEquals(LocalCheckOutcome.INVALID_PORT,checker.run(0).outcome)
            assertEquals(LocalCheckOutcome.INVALID_PORT,checker.run(65536).outcome)
        }finally{executor.shutdownNow()}
    }
    @Test fun cancellationImmediatelyClosesInFlightSocket()=runBlocking<Unit>{
        val executor=Executors.newSingleThreadExecutor();val entered=CountDownLatch(1);val closed=CountDownLatch(1)
        val fake=object:Socket(){
            override fun connect(endpoint:java.net.SocketAddress?,timeout:Int){entered.countDown();check(closed.await(2,TimeUnit.SECONDS))}
            override fun close(){closed.countDown()}
        }
        try{val job=launch{LocalListenerCheck(executor){fake}.run(1443)}
            withContext(Dispatchers.IO){assertTrue(entered.await(2,TimeUnit.SECONDS))}
            job.cancelAndJoin();assertEquals(0L,closed.count)
        }finally{executor.shutdownNow()}
    }
    @Test fun executorShutdownFailsPredictablyAndClosesSocket()=runBlocking<Unit>{
        val executor=Executors.newSingleThreadExecutor();executor.shutdownNow()
        val socket=Socket();val result=LocalListenerCheck(executor){socket}.run(1443)
        assertEquals(LocalCheckOutcome.IO_ERROR,result.outcome);assertTrue(socket.isClosed)
    }
}
