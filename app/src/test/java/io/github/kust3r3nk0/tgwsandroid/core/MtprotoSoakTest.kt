package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

/** Duration is controllable; no public network or Telegram account participates. */
class MtprotoSoakTest {
    private val secret = ByteArray(16) { it.toByte() }
    private fun ctr(key: ByteArray, iv: ByteArray) = Cipher.getInstance("AES/CTR/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)

    @Test fun sustainedConcurrentTcpAndTlsWssPreserveCipherStateThroughPoolAndNetworkResets() {
        val seconds = (System.getProperty("tgws.soak.seconds") ?: "2").toInt().also { require(it in 1..3600) }
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        val snapshot = AtomicReference(ServerEvent.Sessions(0, 0))
        val listening = AtomicReference(ServerPhase.STOPPED)
        val completed = AtomicInteger()
        val completedSessions = AtomicInteger()
        val wss = AtomicInteger()
        val tcp = AtomicInteger()
        val epoch = AtomicLong()
        val resetDisconnects = AtomicInteger()
        val errors = AtomicReference<Throwable?>()
        val peakSessions = AtomicInteger()
        val peakPool = AtomicInteger()
        val maxWss = AtomicInteger()
        val maxTcp = AtomicInteger()
        val poolLeases = AtomicInteger()
        var peakHeap = 0L
        val clients = Executors.newFixedThreadPool(4)
        LocalTelegramPeer().use { peer ->
            val factory = object : TransportFactory {
                override val supportsWarmWebSockets = true
                override fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport {
                    if (wss.incrementAndGet() % 11 == 0) throw IOException("Injected route failure")
                    val ws = RawWebSocket.connectTls("127.0.0.1", "localhost", "/apiws", peer.wsPort, peer.clientTls,
                        RawWebSocket.Limits(connectMillis = 2000, handshakeMillis = 3000, readMillis = 5000, closeMillis = 200),
                        socketOwner = { own(it) })
                    return object : RemoteTransport {
                        override val kind = TransportKind.WSS
                        override fun send(data: ByteArray) = ws.send(data)
                        override fun receive() = ws.receive()
                        override fun verifyIdle() { ws.probeIdle(); poolLeases.incrementAndGet() }
                        override fun abort() = ws.abort()
                        override fun close() = ws.abort()
                    }
                }
                override fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport {
                    tcp.incrementAndGet()
                    val socket = Socket().also { own(it); it.connect(java.net.InetSocketAddress("127.0.0.1", peer.tcpPort), 2000); it.tcpNoDelay = true }
                    return object : RemoteTransport {
                        override val kind = TransportKind.TCP
                        override fun send(data: ByteArray) { socket.getOutputStream().write(data) }
                        override fun receive(): ByteArray? {
                            val bytes = ByteArray(4096); val size = socket.getInputStream().read(bytes)
                            return if (size < 0) null else bytes.copyOf(size)
                        }
                        override fun close() = socket.close()
                    }
                }
            }
            MtprotoServer(port, secret.joinToString("") { "%02x".format(it) }, onEvent = { event ->
                if (event is ServerEvent.State) listening.set(event.phase)
                if (event is ServerEvent.Sessions) {
                    snapshot.set(event)
                    peakSessions.accumulateAndGet(event.established + event.pending, ::maxOf)
                    maxWss.accumulateAndGet(event.wss, ::maxOf)
                    maxTcp.accumulateAndGet(event.tcp, ::maxOf)
                }
            }, transports = factory, limits = ServerLimits(warmMaxAgeMillis = 250)).use { server ->
                server.start()
                val startup = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                while (listening.get() != ServerPhase.LISTENING) { check(System.nanoTime() < startup); Thread.sleep(2) }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds.toLong())
                val tasks = (0 until 4).map { worker -> clients.submit {
                    var index = worker
                    try {
                        while (System.nanoTime() < deadline && errors.get() == null) {
                            val generation = epoch.get()
                            try {
                                Socket("127.0.0.1", port).use { client ->
                                    client.soTimeout = 5000
                                    client.tcpNoDelay = true
                                    val tag = listOf(0xef, 0xee, 0xdd)[index % 3]
                                    val dc = if (index % 2 == 0) 2 else -4
                                    val plain = ByteArray(64) { (it + 1).toByte() }
                                    for (i in 56..59) plain[i] = tag.toByte()
                                    plain[60] = dc.toByte(); plain[61] = (dc shr 8).toByte()
                                    val material = plain.copyOfRange(8, 56)
                                    val encrypt = ctr(sha(material.copyOfRange(0, 32) + secret), material.copyOfRange(32, 48))
                                    val header = plain.copyOf().apply { encrypt.update(plain).copyInto(this, 56, 56, 64) }
                                    val reversed = material.reversedArray()
                                    val decrypt = ctr(sha(reversed.copyOfRange(0, 32) + secret), reversed.copyOfRange(32, 48))
                                    val body = java.nio.ByteBuffer.allocate(8).putInt(index).putInt(index xor 0x12345678).array()
                                    val packet = (if (tag == 0xef) byteArrayOf(2) else byteArrayOf(8, 0, 0, 0)) + body
                                    val payload = encrypt.update(packet)
                                    client.getOutputStream().write(header, 0, 17)
                                    client.getOutputStream().write(header, 17, 47)
                                    client.getOutputStream().write(payload, 0, 2)
                                    client.getOutputStream().write(payload, 2, payload.size - 2)
                                    val response = ByteArray(packet.size)
                                    var position = 0
                                    while (position < response.size) {
                                        val count = client.getInputStream().read(response, position, response.size - position)
                                        if (count < 0) throw java.io.EOFException()
                                        position += count
                                    }
                                    assertArrayEquals("Session cipher/packet isolation", packet, decrypt.update(response))
                                    completed.incrementAndGet()
                                    repeat(if (worker == 0) 19 else 2) { sequence ->
                                        val nextPacket = packet.copyOf().also { it[it.lastIndex] = sequence.toByte() }
                                        client.getOutputStream().write(encrypt.update(nextPacket))
                                        val next = ByteArray(nextPacket.size)
                                        var offset = 0
                                        while (offset < next.size) {
                                            val size = client.getInputStream().read(next, offset, next.size - offset)
                                            if (size < 0) throw java.io.EOFException()
                                            offset += size
                                        }
                                        assertArrayEquals("Continuous CTR state", nextPacket, decrypt.update(next))
                                        completed.incrementAndGet()
                                        if (worker == 0) Thread.sleep(10)
                                    }
                                    completedSessions.incrementAndGet()
                                }
                            } catch (error: IOException) {
                                if (epoch.get() == generation) throw error
                                resetDisconnects.incrementAndGet()
                            }
                            index += 4
                            Thread.sleep(25)
                        }
                    } catch (error: Throwable) { errors.compareAndSet(null, error) }
                } }
                var nextReset = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
                try {
                    while (System.nanoTime() < deadline && errors.get() == null) {
                        val size = server.warmConnections()
                        peakPool.accumulateAndGet(size.first + size.second, ::maxOf)
                        peakHeap = maxOf(peakHeap, Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                        if (System.nanoTime() >= nextReset) {
                            epoch.incrementAndGet(); server.disconnectSessions()
                            nextReset = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
                        }
                        Thread.sleep(10)
                    }
                    tasks.forEach { it.get(8, TimeUnit.SECONDS) }
                } finally { server.stop(); clients.shutdownNow() }
                errors.get()?.let { throw AssertionError("Soak failed", it) }
                peer.failure.get()?.let { throw AssertionError("Peer failed", it) }
                assertTrue(completed.get() > 0)
                assertTrue(peakSessions.get() <= 8)
                assertTrue(peakPool.get() <= 4)
                if (seconds >= 10) {
                    assertTrue(maxWss.get() > 0 && maxTcp.get() > 0)
                    assertTrue(poolLeases.get() > 0)
                }
                assertEquals(ServerEvent.Sessions(0, 0), snapshot.get())
                assertEquals(0 to 0, server.warmConnections())
                println("SOAK seconds=$seconds completedPackets=${completed.get()} completedSessions=${completedSessions.get()} wssAttempts=${wss.get()} tcpAttempts=${tcp.get()} resets=${epoch.get()} expectedDisconnects=${resetDisconnects.get()} maxSessions=${peakSessions.get()} maxPool=${peakPool.get()} maxPeerSockets=${peer.maximumSockets.get()} peakHeapBytes=$peakHeap maxWssSessions=${maxWss.get()} maxTcpSessions=${maxTcp.get()} poolLeases=${poolLeases.get()}")
            }
        }
        assertTrue(clients.awaitTermination(3, TimeUnit.SECONDS))
        assertThrows(IOException::class.java) { Socket("127.0.0.1", port).close() }
    }
}
