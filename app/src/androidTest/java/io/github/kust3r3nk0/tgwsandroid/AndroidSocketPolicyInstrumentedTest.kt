package io.github.kust3r3nk0.tgwsandroid

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSocketPolicyInstrumentedTest {
    @Test fun livenessOptionIsAppliedBeforeConnectAndDuplicateClosePreservesSocket(){
        ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { listener ->
            Socket().use { client ->
                AndroidSocketPolicy.configure(client)
                assertTrue(client.keepAlive)
                // A second duplicate can also close without closing the live socket.
                ParcelFileDescriptor.fromSocket(client).close()
                assertFalse(client.isClosed)
                client.connect(InetSocketAddress("127.0.0.1",listener.localPort),2000)
                listener.accept().use { peer ->
                    peer.soTimeout=2000;client.soTimeout=2000
                    client.getOutputStream().write(42)
                    assertEquals(42,peer.getInputStream().read())
                    peer.getOutputStream().write(43)
                    assertEquals(43,client.getInputStream().read())
                }
            }
        }
    }
    @Test fun closedSocketCannotBeReusedOrSilentlySkipPolicy(){
        val socket=Socket().apply{close()}
        assertThrows(SocketException::class.java){AndroidSocketPolicy.configure(socket)}
        assertTrue(socket.isClosed)
    }
    @Test(timeout=12000) fun zeroWindowCannotKeepUnacknowledgedDataBlockedIndefinitely(){
        ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use { listener ->
            listener.receiveBufferSize=1024
            Socket().use { client ->
                client.sendBufferSize=1024
                AndroidSocketPolicy.configure(client,1000)
                client.connect(InetSocketAddress("127.0.0.1",listener.localPort),2000)
                listener.accept().use { peer ->
                    peer.receiveBufferSize=1024
                    val finished=java.util.concurrent.CountDownLatch(1)
                    val failure=java.util.concurrent.atomic.AtomicReference<Throwable?>()
                    val writer=Thread {
                        try{client.getOutputStream().write(ByteArray(8*1024*1024))}
                        catch(error:Throwable){failure.set(error)}
                        finally{finished.countDown()}
                    }.apply{isDaemon=true;start()}
                    try {
                        assertTrue("Kernel did not expire the blocked TCP stream",finished.await(8,java.util.concurrent.TimeUnit.SECONDS))
                        assertTrue(failure.get() is java.io.IOException)
                    } finally {client.close();writer.join(1000)}
                    assertFalse(writer.isAlive)
                }
            }
        }
    }
}
