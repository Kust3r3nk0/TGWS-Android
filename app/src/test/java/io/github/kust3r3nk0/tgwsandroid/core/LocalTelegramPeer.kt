package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Real local TCP/TLS/WebSocket peer, independent Java AES-CTR echo in both relay directions. */
internal class LocalTelegramPeer : Closeable {
    private val contexts = TestTls.contexts()
    val clientTls = contexts.second
    private val wsListener = contexts.first.serverSocketFactory.createServerSocket(0, 16, InetAddress.getLoopbackAddress())
    private val tcpListener = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
    val wsPort get() = wsListener.localPort
    val tcpPort get() = tcpListener.localPort
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    val maximumSockets = AtomicInteger()
    val failure = AtomicReference<Throwable?>()
    private val workers = Executors.newFixedThreadPool(16) { task -> Thread(task, "tgws-soak-peer").apply { isDaemon = true } }
    @Volatile private var closed = false
    private val listeners = listOf(wsListener to true, tcpListener to false).map { (listener, ws) ->
        Thread({
            try {
                while (!closed) {
                    val socket = listener.accept()
                    socket.tcpNoDelay = true
                    socket.soTimeout = 10_000
                    sockets += socket
                    maximumSockets.accumulateAndGet(sockets.size, ::maxOf)
                    workers.submit {
                        try { socket.use { serve(it, ws) } }
                        catch (_: java.io.IOException) { /* A client/network reset is allowed to abort IO. */ }
                        catch (error: Throwable) { failure.compareAndSet(null, error) }
                        finally { sockets -= socket }
                    }
                }
            } catch (error: Exception) { if (!closed) failure.compareAndSet(null, error) }
        }, "tgws-soak-accept").apply { isDaemon = true; start() }
    }

    private fun serve(socket: Socket, websocket: Boolean) {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        if (websocket) {
            val bytes = StringBuilder()
            while (!bytes.endsWith("\r\n\r\n")) {
                check(bytes.length < 16_384)
                bytes.append(input.byte().toChar())
            }
            val key = bytes.lines().single { it.startsWith("Sec-WebSocket-Key:") }.substringAfter(':').trim()
            val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII)))
            output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $accept\r\n\r\n").toByteArray(Charsets.US_ASCII))
            output.flush()
        }
        var decrypt: Cipher? = null
        var encrypt: Cipher? = null
        var fragmented = false
        var fragments = java.io.ByteArrayOutputStream()
        while (!closed) {
            val data = if (websocket) {
                val first = input.byte()
                val second = input.byte()
                check(first and 0x70 == 0 && second and 0x80 != 0)
                val fin = first and 0x80 != 0
                val opcode = first and 15
                var count = (second and 127).toLong()
                if (count == 126L) count = (input.byte() shl 8 or input.byte()).toLong()
                else if (count == 127L) { count = 0; repeat(8) { count = (count shl 8) or input.byte().toLong() } }
                check(count in 0..(4 * 1024 * 1024 + 4).toLong())
                val mask = input.exact(4)
                val body = input.exact(count.toInt()).apply { for (i in indices) this[i] = (this[i].toInt() xor mask[i % 4].toInt()).toByte() }
                if (opcode >= 8) check(fin && count <= 125)
                if (opcode == 9) { output.write(frame(10, body)); output.flush(); continue }
                if (opcode == 10) continue
                if (opcode == 8) { output.write(frame(8, body)); output.flush(); break }
                check(opcode == if (fragmented) 0 else 2)
                if (!fin || fragmented) {
                    check(fragments.size() + body.size <= 4 * 1024 * 1024 + 4)
                    fragments.write(body)
                    fragmented = !fin
                    if (!fin) continue
                    fragments.toByteArray().also { fragments = java.io.ByteArrayOutputStream() }
                } else body
            } else if (decrypt == null) input.exact(64) else {
                val buffer = ByteArray(65_536)
                val size = input.read(buffer)
                if (size < 0) break
                buffer.copyOf(size)
            }
            if (decrypt == null) {
                check(data.size == 64)
                decrypt = ctr(data.copyOfRange(8, 40), data.copyOfRange(40, 56)).also { it.update(ByteArray(64)) }
                val reversed = data.copyOfRange(8, 56).reversedArray()
                encrypt = ctr(reversed.copyOfRange(0, 32), reversed.copyOfRange(32, 48))
            } else {
                val response = encrypt!!.update(decrypt.update(data))
                output.write(if (websocket) frame(2, response) else response)
                output.flush()
            }
        }
    }

    private fun ctr(key: ByteArray, iv: ByteArray) = Cipher.getInstance("AES/CTR/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    }
    private fun frame(opcode: Int, bytes: ByteArray): ByteArray {
        check(bytes.size <= 4 * 1024 * 1024 + 4)
        val output = java.io.ByteArrayOutputStream()
        output.write(0x80 or opcode)
        when {
            bytes.size < 126 -> output.write(bytes.size)
            bytes.size <= 65535 -> { output.write(126); output.write(bytes.size ushr 8); output.write(bytes.size) }
            else -> { output.write(127); for (shift in 56 downTo 0 step 8) output.write((bytes.size.toLong() ushr shift).toInt()) }
        }
        output.write(bytes)
        return output.toByteArray()
    }
    private fun InputStream.byte(): Int = read().also { if (it < 0) throw EOFException() }
    private fun InputStream.exact(size: Int): ByteArray = ByteArray(size).also { bytes ->
        var position = 0
        while (position < size) { val count = read(bytes, position, size - position); if (count < 0) throw EOFException(); position += count }
    }
    override fun close() {
        closed = true
        wsListener.close(); tcpListener.close()
        sockets.toList().forEach(Socket::close)
        workers.shutdownNow()
        listeners.forEach { it.join(1000) }
        check(workers.awaitTermination(3, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Local peer protocol failure", it) }
    }
}
