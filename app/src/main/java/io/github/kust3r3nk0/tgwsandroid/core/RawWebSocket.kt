package io.github.kust3r3nk0.tgwsandroid.core

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/** Minimal RFC 6455 binary framing; single reader and serialized writes. */
internal class RawWebSocket private constructor(private val socket: SSLSocket) : Closeable {
    private val input = socket.inputStream
    private val output = socket.outputStream
    private val rng = SecureRandom()
    private val sendLock = Any()
    private val fragments = ByteArrayOutputStream()
    private var fragmented = false
    private var closed = false

    companion object {
        private const val LIMIT = 16 * 1024 * 1024
        fun connect(address: String, domain: String, path: String = "/apiws"): RawWebSocket {
            val tcp = Socket()
            tcp.connect(InetSocketAddress(address, 443), 8_000)
            tcp.soTimeout = 10_000
            val tls = try {
                val ssl = SSLContext.getDefault().socketFactory.createSocket(tcp, domain, 443, true) as SSLSocket
                ssl.sslParameters = ssl.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                ssl.startHandshake()  // RFC 2818 hostname verification against 'domain'
                ssl
            } catch (e: Exception) { tcp.close(); throw e }
            try {
                val key = ByteArray(16).also(SecureRandom()::nextBytes)
                val textKey = Base64.getEncoder().encodeToString(key)
                val request = "GET $path HTTP/1.1\r\nHost: $domain\r\nUpgrade: websocket\r\n" +
                    "Connection: Upgrade\r\nSec-WebSocket-Key: $textKey\r\n" +
                    "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: binary\r\n\r\n"
                tls.outputStream.write(request.toByteArray(Charsets.US_ASCII))
                tls.outputStream.flush()
                var total = 0
                val status = line(tls.inputStream).also { total += it.length }
                require(status.contains(" 101 ")) { "WS upgrade failed: ${status.take(100)}" }
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val header = line(tls.inputStream)
                    total += header.length + 2
                    require(total <= 16 * 1024) { "HTTP upgrade headers exceeded maximum" }
                    if (header.isEmpty()) break
                    val idx = header.indexOf(':')
                    if (idx > 0) headers[header.substring(0, idx).trim().lowercase()] =
                        header.substring(idx + 1).trim()
                }
                require("websocket" in headers.getOrDefault("upgrade", "").lowercase())
                require("upgrade" in headers.getOrDefault("connection", "").lowercase())
                val expected = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((textKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII)))
                require(headers["sec-websocket-accept"] == expected) { "Invalid WebSocket accept key" }
                tls.soTimeout = 0
                return RawWebSocket(tls)
            } catch (e: Exception) { tls.close(); throw e }
        }
        private fun line(input: InputStream): String {
            val out = ByteArrayOutputStream()
            while (true) {
                val ch = input.read()
                if (ch < 0) throw EOFException("EOF in WS handshake")
                if (ch == 10) break
                if (ch != 13) out.write(ch)
                require(out.size() <= 8192) { "WS response line too long" }
            }
            return out.toString(Charsets.ISO_8859_1.name())
        }
        private fun exactly(input: InputStream, count: Int): ByteArray {
            val result = ByteArray(count)
            var pos = 0
            while (pos < count) {
                val n = input.read(result, pos, count - pos)
                if (n < 0) throw EOFException("Unexpected WebSocket EOF")
                pos += n
            }
            return result
        }
    }

    fun send(data: ByteArray) = writeFrame(2, data)
    private fun writeFrame(op: Int, body: ByteArray) {
        synchronized(sendLock) {
            if (closed) throw EOFException("WebSocket already closed")
            output.write(0x80 or op)
            val count = body.size
            if (count < 126) output.write(0x80 or count)
            else if (count <= 65535) {
                output.write(0x80 or 126); output.write(count shr 8); output.write(count)
            } else {
                output.write(0x80 or 127)
                for (shift in 56 downTo 0 step 8) output.write((count.toLong() shr shift).toInt())
            }
            val mask = ByteArray(4).also(rng::nextBytes)
            output.write(mask)
            val chunk = body.copyOf()
            for (i in chunk.indices) chunk[i] = (chunk[i].toInt() xor mask[i % 4].toInt()).toByte()
            output.write(chunk)
            output.flush()
        }
    }

    fun receive(): ByteArray? {
        while (true) {
            val first = input.read()
            if (first < 0) return null
            val second = input.read()
            if (second < 0) throw EOFException("EOF in WS header")
            val fin = first and 0x80 != 0
            val op = first and 15
            val masked = second and 0x80 != 0
            var size = (second and 127).toLong()
            if (size == 126L) {
                val len = exactly(input, 2)
                size = ((len[0].toInt() and 255) shl 8 or (len[1].toInt() and 255)).toLong()
            } else if (size == 127L) {
                size = 0
                for (b in exactly(input, 8)) size = (size shl 8) or (b.toLong() and 255)
            }
            require(size in 0..LIMIT.toLong()) { "WS frame too large" }
            if (op >= 8) require(fin && size <= 125) { "Invalid WS control frame" }
            val mask = if (masked) exactly(input, 4) else null
            val body = exactly(input, size.toInt())
            if (mask != null) for (i in body.indices)
                body[i] = (body[i].toInt() xor mask[i % 4].toInt()).toByte()
            when (op) {
                8 -> return null
                9 -> { writeFrame(10, body); continue }
                10 -> continue
                0, 1, 2 -> {
                    if (!fin || fragmented) {
                        fragments.write(body)
                        require(fragments.size() <= LIMIT)
                        fragmented = true
                        if (!fin) continue
                        val payload = fragments.toByteArray()
                        fragments.reset()
                        fragmented = false
                        return payload
                    }
                    return body
                }
                else -> throw IllegalArgumentException("Unsupported WS opcode $op")
            }
        }
    }
    override fun close() {
        synchronized(sendLock) {
            closed = true
            try { socket.close() } catch (_: Exception) { }
        }
    }
}
