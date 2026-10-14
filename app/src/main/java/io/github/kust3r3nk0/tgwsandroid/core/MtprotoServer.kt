package io.github.kust3r3nk0.tgwsandroid.core

import io.github.kust3r3nk0.tgwsandroid.ProxyState
import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

/**
 * Alpha port: local obfuscated2 MTProto -> direct Telegram WSS, then direct TCP fallback.
 * No Cloudflare/HTTP2/FakeTLS support yet. Production readiness requires parity tests.
 */
class MtprotoServer(
    private val port: Int, secretHex: String,
    private val report: (String) -> Unit,
    private val sessionCount: (Int) -> Unit,
) : Closeable {
    private val secret = secretHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val capacity = Semaphore(32)
    private val active = AtomicInteger(0)
    private val workers = Executors.newCachedThreadPool { task ->
        Thread(task, "tgws-client").apply { isDaemon = true }
    }
    @Volatile private var listening = false
    @Volatile private var server: ServerSocket? = null

    fun start() {
        if (listening) return
        listening = true
        Thread({ listen() }, "tgws-listener").apply { isDaemon = true }.start()
    }
    private fun listen() {
        try {
            val ss = ServerSocket()
            server = ss
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 32)
            ProxyState.running = true
            report("Прослушивание 127.0.0.1:$port")
            while (listening) {
                val client = ss.accept()
                if (!capacity.tryAcquire()) { client.close(); continue }
                sockets += client
                sessionCount(active.incrementAndGet())
                workers.execute {
                    try { serve(client) }
                    catch (_: Exception) { /* Do not log secrets or handshake contents */ }
                    finally {
                        sockets.remove(client)
                        try { client.close() } catch (_: Exception) { }
                        capacity.release()
                        sessionCount(active.decrementAndGet())
                    }
                }
            }
        } catch (error: Exception) {
            if (listening) report("Ошибка локального порта: ${error.javaClass.simpleName}")
        } finally {
            listening = false
            ProxyState.running = false
            try { server?.close() } catch (_: Exception) { }
        }
    }
    private fun readInit(client: Socket): ByteArray {
        client.soTimeout = 10_000
        val buffer = ByteArray(64)
        var n = 0
        while (n < buffer.size) {
            val count = client.getInputStream().read(buffer, n, buffer.size - n)
            if (count < 0) throw EOFException("Client disconnected during handshake")
            n += count
        }
        client.soTimeout = 0
        return buffer
    }
    private fun serve(client: Socket) {
        client.tcpNoDelay = true
        val handshake = MtprotoCrypto.parseClientInit(readInit(client), secret) ?: return
        // Dedicated test DC support remains a future stage.
        if (handshake.dc >= 10000) return
        val relay = MtprotoCrypto.createRelayInit(handshake)
        val ctx = MtprotoCrypto.buildContext(handshake, secret, relay)
        val redirect = when (handshake.dc) {
            2, 4 -> "149.154.167.220"
            1 -> "149.154.175.50"
            3 -> "149.154.175.100"
            5 -> "149.154.171.5"
            203 -> "91.105.192.100"
            else -> return
        }
        val hostDc = if (handshake.dc == 203) 2 else handshake.dc
        val names = if (handshake.media) listOf("kws$hostDc-1.web.telegram.org", "kws$hostDc.web.telegram.org")
                    else listOf("kws$hostDc.web.telegram.org")
        var ws: RawWebSocket? = null
        for (name in names) {
            try { ws = RawWebSocket.connect(redirect, name); break }
            catch (_: Exception) { /* Try next WS domain, then TCP */ }
        }
        if (ws != null) {
            ws.use { channel ->
                channel.send(relay)
                bridge(client, ctx, handshake.tag,
                    up = { data -> channel.send(data) },
                    down = { channel.receive() },
                    remoteClose = { channel.close() }, split = true)
            }
        } else {
            Socket().use { remote ->
                remote.connect(InetSocketAddress(redirect, 443), 10_000)
                remote.tcpNoDelay = true
                remote.getOutputStream().write(relay)
                remote.getOutputStream().flush()
                bridge(client, ctx, handshake.tag,
                    up = { data -> remote.getOutputStream().write(data); remote.getOutputStream().flush() },
                    down = { remote.getInputStream().readOneChunk() },
                    remoteClose = { remote.close() }, split = false)
            }
        }
    }
    private fun InputStream.readOneChunk(): ByteArray? {
        val data = ByteArray(65536)
        val n = read(data)
        return if (n <= 0) null else data.copyOf(n)
    }
    private fun bridge(client: Socket, ctx: CryptoContext, tag: ByteArray,
        up: (ByteArray) -> Unit, down: () -> ByteArray?, remoteClose: () -> Unit, split: Boolean) {
        val splitter = PacketSplitter(tag)
        val downstream = Thread({
            try {
                while (true) {
                    val chunk = down() ?: break
                    val plain = ctx.fromTelegram.update(chunk)
                    val encrypted = ctx.toClient.update(plain)
                    client.getOutputStream().write(encrypted)
                    client.getOutputStream().flush()
                }
            } catch (_: Exception) { }
            finally { try { client.close() } catch (_: Exception) { }; remoteClose() }
        }, "tgws-downstream")
        downstream.isDaemon = true
        downstream.start()
        try {
            while (true) {
                val data = client.getInputStream().readOneChunk() ?: break
                val plain = ctx.fromClient.update(data)
                val crypt = ctx.toTelegram.update(plain)
                if (split) splitter.split(crypt, plain).forEach(up) else up(crypt)
            }
            if (split) splitter.flush().forEach(up)
        } catch (_: Exception) { }
        finally {
            remoteClose()
            try { client.close() } catch (_: Exception) { }
            downstream.join(1000)
        }
    }
    override fun close() = stop()
    fun stop() {
        listening = false
        try { server?.close() } catch (_: Exception) { }
        sockets.forEach { try { it.close() } catch (_: Exception) { } }
        workers.shutdownNow()
        ProxyState.running = false
    }
}
