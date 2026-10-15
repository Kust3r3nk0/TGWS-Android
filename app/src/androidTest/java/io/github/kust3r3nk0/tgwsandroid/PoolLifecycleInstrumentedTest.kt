package io.github.kust3r3nk0.tgwsandroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kust3r3nk0.tgwsandroid.core.MtprotoServer
import io.github.kust3r3nk0.tgwsandroid.core.RemoteTransport
import io.github.kust3r3nk0.tgwsandroid.core.ServerEvent
import io.github.kust3r3nk0.tgwsandroid.core.ServerPhase
import io.github.kust3r3nk0.tgwsandroid.core.TransportFactory
import io.github.kust3r3nk0.tgwsandroid.core.TransportKind
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoolLifecycleInstrumentedTest {
    private class Channel : RemoteTransport {
        override val kind = TransportKind.WSS
        val closed = CountDownLatch(1)
        val probes = AtomicInteger()
        override fun verifyIdle() { probes.incrementAndGet() }
        override fun send(data: ByteArray) { check(closed.count > 0) }
        override fun receive(): ByteArray? { check(closed.await(10, TimeUnit.SECONDS)); return null }
        override fun close() { closed.countDown() }
    }
    private fun await(message: String, condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) { assertTrue(message, System.nanoTime() < end); Thread.sleep(5) }
    }
    private fun workers() = Thread.getAllStackTraces().keys.count {
        it.isAlive && it.name in setOf("tgws-preconnect", "tgws-client", "tgws-downstream", "tgws-listener", "tgws-deadline")
    }
    private fun assertClosed(socket: Socket) {
        try { assertEquals(-1, socket.getInputStream().read()) }
        catch (_: java.net.SocketException) { /* EOF and reset both release the connection; timeouts still fail. */ }
    }
    private fun hello(): ByteArray {
        val plain = ByteArray(64) { (it + 1).toByte() }
        for (i in 56..59) plain[i] = 0xee.toByte()
        plain[60] = 2; plain[61] = 0
        val key = MessageDigest.getInstance("SHA-256").digest(plain.copyOfRange(8, 40) + ByteArray(16) { it.toByte() })
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(plain.copyOfRange(40, 56)))
        }
        return plain.copyOf().apply { cipher.update(plain).copyInto(this, 56, 56, 64) }
    }

    @Test fun repeatedCorePoolLeasesAndStopReleaseSocketsAndWorkerThreads() {
        val before = workers()
        repeat(5) {
            val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val channels = CopyOnWriteArrayList<Channel>()
            val phase = AtomicReference(ServerPhase.STOPPED)
            val factory = object : TransportFactory {
                override val supportsWarmWebSockets = true
                override fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport =
                    Channel().also { channels += it; own(it) }
                override fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport = throw AssertionError("Unexpected TCP")
            }
            MtprotoServer(port, "000102030405060708090a0b0c0d0e0f", onEvent = { if (it is ServerEvent.State) phase.set(it.phase) },
                transports = factory).use { server ->
                server.start()
                await("Listener not started") { phase.get() == ServerPhase.LISTENING }
                Socket("127.0.0.1", port).use { first ->
                    first.soTimeout = 2000; first.getOutputStream().write(hello())
                    await("Pool did not refill") { server.warmConnections() == (1 to 0) }
                    Socket("127.0.0.1", port).use { second ->
                        second.soTimeout = 2000; second.getOutputStream().write(hello())
                        await("Prepared connection not leased") { channels[1].probes.get() == 1 && server.warmConnections() == (1 to 0) }
                        server.stop()
                        assertClosed(first)
                        assertClosed(second)
                        assertEquals(0 to 0, server.warmConnections())
                        assertTrue(channels.all { it.closed.count == 0L })
                    }
                }
            }
            await("Core workers survived stop") { workers() <= before }
        }
    }
}
