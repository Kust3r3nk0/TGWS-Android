package io.github.kust3r3nk0.tgwsandroid.core

import java.io.ByteArrayOutputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ProtocolException
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/** Only validated close codes and an allowlisted category cross the transport boundary. */
internal class WebSocketPeerCloseException(val statusCode:Int, val category:WebSocketCloseCategory?) :
    IOException("WebSocket peer closed")

/** RFC 6455 binary transport. One reader, serialized masked writes, bounded I/O and memory. */
internal class RawWebSocket private constructor(
    private val socket: Socket,
    private val limits: Limits,
) : Closeable {
    internal data class Limits(
        // One maximum 4 MiB MTProto packet plus its four-byte transport prefix.
        val messageBytes: Int = 4 * 1024 * 1024 + 4,
        val connectMillis: Int = 8_000,
        val handshakeMillis: Int = 10_000,
        val readMillis: Int = 60_000,
        val pongMillis: Int = 10_000,
        val writeMillis: Int = 10_000,
        val closeMillis: Int = 1_000,
    ) {
        init {
            require(messageBytes > 0)
            require(listOf(connectMillis, handshakeMillis, readMillis, pongMillis, writeMillis, closeMillis).all { it > 0 })
        }
    }

    private val input = socket.getInputStream()
    // Assemble small headers/masks with payload before TLS writes. Otherwise each
    // one-byte write can become a separate TLS record and trigger delayed ACKs.
    private val output = BufferedOutputStream(socket.getOutputStream(), 16 * 1024)
    private val rng = SecureRandom()
    private val sendLock = ReentrantLock(true)
    private val messageLock = ReentrantLock()
    private val readLock = ReentrantLock()
    private val closing = AtomicBoolean()
    private val closed = AtomicBoolean()
    private val peerClosed = CountDownLatch(1)
    private var fragmentBuffer = ByteArray(0)
    private var fragmentBytes = 0
    private var fragmented = false
    var setupTimings=SetupTimings();private set
    @Volatile var peerClose: WebSocketClose? = null; private set

    companion object {
        private const val HEADER_LIMIT = 16 * 1024
        private const val MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private val token = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
        // A shared deadline task closes blocked sockets; no executor/thread is allocated per session.
        private val deadlines = ScheduledThreadPoolExecutor(1) { task ->
            Thread(task, "tgws-websocket-deadlines").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }

        fun connect(
            address: String,
            domain: String,
            path: String = "/apiws",
            connector:SocketConnector=SystemSocketConnector,
            socketOwner: (Socket) -> Unit = {},
        ): RawWebSocket = connectTls(address, domain, path, 443, SSLContext.getDefault(), Limits(),connector,socketOwner)

        /** Test endpoint injection preserves the same TLS trust and hostname checks as production. */
        internal fun connectTls(
            address: String,
            domain: String,
            path: String,
            port: Int,
            context: SSLContext,
            limits: Limits = Limits(),
            connector:SocketConnector=SystemSocketConnector,
            socketOwner: (Socket) -> Unit = {},
        ): RawWebSocket {
            validateRequest(domain, path)
            var tcp:Socket?=null
            var tls: SSLSocket? = null
            try {
                val connected=connector.connect(address,port,limits.connectMillis,socketOwner)
                tcp=connected
                val timing=connector.takeTiming(connected)?:SetupTimings()
                connected.soTimeout = limits.handshakeMillis
                val ssl = context.socketFactory.createSocket(connected, domain, port, true) as SSLSocket
                tls = ssl
                socketOwner(ssl)
                ssl.sslParameters = ssl.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                // soTimeout alone permits a peer to drip handshake records forever.
                val deadline = deadlines.schedule({ quietlyClose(ssl) }, limits.handshakeMillis.toLong(), TimeUnit.MILLISECONDS)
                val tlsStart=System.nanoTime()
                try { ssl.startHandshake() } finally { deadline.cancel(false) }
                val tlsMillis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-tlsStart)
                val httpStart=System.nanoTime()
                return upgrade(ssl, domain, path, limits).also{it.setupTimings=timing.copy(tlsMillis=tlsMillis,
                    httpMillis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-httpStart))}
            } catch (e: Exception) {
                (tls ?: tcp)?.let(::quietlyClose)
                throw e
            }
        }

        /** A connected local socket is injected only by protocol tests; production always uses TLS. */
        internal fun upgrade(socket: Socket, domain: String, path: String = "/apiws", limits: Limits = Limits()): RawWebSocket {
            validateRequest(domain, path)
            val ws = RawWebSocket(socket, limits)
            var status: Int? = null
            var phase = UpgradeFailure.HEADERS
            try {
                val key = Base64.getEncoder().encodeToString(ByteArray(16).also(SecureRandom()::nextBytes))
                val request = "GET $path HTTP/1.1\r\nHost: $domain\r\nUpgrade: websocket\r\n" +
                    "Connection: Upgrade\r\nSec-WebSocket-Key: $key\r\n" +
                    "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: binary\r\n\r\n"
                ws.writeBounded(limits.handshakeMillis) { ws.output.write(request.toByteArray(Charsets.US_ASCII)); ws.output.flush() }
                val deadline = deadlineAfter(limits.handshakeMillis)
                var total = 0
                fun line(): String {
                    val bytes = ByteArrayOutputStream()
                    while (true) {
                        val ch = ws.readByte(deadline)
                        if (++total > HEADER_LIMIT) { phase = UpgradeFailure.HEADER_LIMIT; throw ProtocolException("HTTP Upgrade headers exceed limit") }
                        if (ch == 13) {
                            if (ws.readByte(deadline) != 10) throw ProtocolException("HTTP response requires CRLF")
                            if (++total > HEADER_LIMIT) { phase = UpgradeFailure.HEADER_LIMIT; throw ProtocolException("HTTP Upgrade headers exceed limit") }
                            return bytes.toString(Charsets.US_ASCII.name())
                        }
                        if (ch < 32 && ch != 9 || ch > 126) throw ProtocolException("Invalid HTTP response character")
                        bytes.write(ch)
                    }
                }
                phase = UpgradeFailure.STATUS_LINE
                val statusLine = line()
                val match = Regex("HTTP/1\\.[01] ([1-5][0-9]{2}) [\\x20-\\x7e]*").matchEntire(statusLine)
                    ?: throw ProtocolException("Malformed HTTP status")
                status = match.groupValues[1].toInt()
                phase = UpgradeFailure.HEADERS
                val headers = mutableMapOf<String, MutableList<String>>()
                while (true) {
                    val header = line()
                    if (header.isEmpty()) break
                    val separator = header.indexOf(':')
                    if (separator <= 0 || !token.matches(header.substring(0, separator))) throw ProtocolException("Malformed HTTP Upgrade header")
                    val name = header.substring(0, separator).lowercase(Locale.ROOT)
                    headers.getOrPut(name) { mutableListOf() }.add(header.substring(separator + 1).trim(' ', '\t'))
                }
                fun single(name: String): String? {
                    val values = headers[name] ?: return null
                    if (values.size != 1) throw ProtocolException("Duplicate WebSocket $name header")
                    return values.single()
                }
                fun tokens(name: String): List<String> = headers[name].orEmpty().flatMap { value ->
                    value.split(',').map {
                        val part = it.trim(' ', '\t')
                        if (!token.matches(part)) throw ProtocolException("Invalid $name token")
                        part.lowercase(Locale.ROOT)
                    }
                }
                if (status != 101) {
                    val retry = single("retry-after")?.takeIf { it.length <= 6 && it.all(Char::isDigit) }
                        ?.toIntOrNull()?.coerceIn(1, 60)?.times(1000)
                    val reason = single("x-tgws-error")?.let { raw -> RelayUpgradeReason.entries.firstOrNull { it.name == raw } }
                    throw WebSocketUpgradeException(HttpUpgradeDiagnostic(status, UpgradeFailure.STATUS, reason, retry))
                }
                if (!statusLine.startsWith("HTTP/1.1 ")) { phase = UpgradeFailure.STATUS_LINE; throw ProtocolException("HTTP/1.1 required") }
                if (tokens("upgrade") != listOf("websocket") || "upgrade" !in tokens("connection")) throw ProtocolException("Invalid WebSocket Upgrade tokens")
                val expected = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((key + MAGIC).toByteArray(Charsets.US_ASCII)))
                phase = UpgradeFailure.ACCEPT
                if (single("sec-websocket-accept") != expected) throw ProtocolException("Invalid WebSocket accept key")
                phase = UpgradeFailure.SUBPROTOCOL
                single("sec-websocket-protocol")?.let {
                    if (it != "binary") throw ProtocolException("Unsupported WebSocket subprotocol")
                }
                phase = UpgradeFailure.EXTENSIONS
                if (headers.containsKey("sec-websocket-extensions")) throw ProtocolException("Unrequested WebSocket extension")
                socket.soTimeout = limits.readMillis
                return ws
            } catch (e: Exception) {
                ws.abort()
                if (e is WebSocketUpgradeException) throw e
                val failure = when(e) {
                    is SocketTimeoutException -> UpgradeFailure.TIMEOUT
                    is EOFException -> UpgradeFailure.EOF
                    is ProtocolException -> phase
                    else -> UpgradeFailure.IO
                }
                val detail = WebSocketUpgradeException(HttpUpgradeDiagnostic(status, failure))
                // Retain native timeout/EOF types for callers, without exporting their text.
                if (e is SocketTimeoutException || e is EOFException) {
                    if (e.cause == null) e.initCause(detail)
                    throw e
                }
                if (e is ProtocolException) throw detail
                throw IOException("WebSocket Upgrade IO failed", detail)
            }
        }

        private fun validateRequest(domain: String, path: String) {
            require(domain.isNotEmpty() && domain.all { it.isLetterOrDigit() && it.code < 128 || it == '.' || it == '-' }) { "Invalid WSS hostname" }
            require(path.startsWith('/') && path.all { it.code in 33..126 && it != '#' }) { "Invalid WSS request path" }
        }

        private fun deadlineAfter(millis: Int) = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis.toLong())
        private fun remaining(deadline: Long): Int {
            val nanos = deadline - System.nanoTime()
            if (nanos <= 0) throw SocketTimeoutException("WebSocket I/O deadline exceeded")
            return TimeUnit.NANOSECONDS.toMillis(nanos).coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        private fun quietlyClose(socket: Socket) { try { socket.close() } catch (_: IOException) { } }
    }

    private fun readByte(deadline: Long): Int {
        socket.soTimeout = remaining(deadline)
        return input.read().also { if (it < 0) throw EOFException("Unexpected WebSocket EOF") }
    }

    private fun exactly(count: Int, deadline: Long) = exactly(count, { deadline }) {}

    private fun exactly(count: Int, deadline: () -> Long, progress: (Int) -> Unit): ByteArray {
        val bytes = ByteArray(count)
        var position = 0
        while (position < count) {
            socket.soTimeout = remaining(deadline())
            val n = input.read(bytes, position, count - position)
            if (n < 0) throw EOFException("Unexpected WebSocket EOF")
            if (n == 0) continue
            position += n
            progress(n)
        }
        return bytes
    }

    private fun writeBounded(timeoutMillis: Int = limits.writeMillis, action: () -> Unit) {
        val end = deadlineAfter(timeoutMillis)
        if (!sendLock.tryLock(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)) throw SocketTimeoutException("WebSocket writer deadline exceeded")
        var timer: java.util.concurrent.ScheduledFuture<*>? = null
        val expired = AtomicBoolean()
        try {
            if (closed.get()) throw EOFException("WebSocket closed")
            timer = deadlines.schedule({ expired.set(true); abort() }, remaining(end).toLong(), TimeUnit.MILLISECONDS)
            try { action() } catch (failure: IOException) {
                if (expired.get()) throw SocketTimeoutException("WebSocket writer deadline exceeded").apply { initCause(failure) }
                throw failure
            }
            if (expired.get()) throw SocketTimeoutException("WebSocket writer deadline exceeded")
        } finally {
            timer?.cancel(false)
            sendLock.unlock()
        }
    }

    fun send(data: ByteArray) = send(data) {}

    /** One MTProto packet stays one WS message; control frames may interleave fragments. */
    fun send(data: ByteArray, progress: () -> Unit) {
        require(data.size <= limits.messageBytes) { "WebSocket message exceeds limit" }
        if (!messageLock.tryLock(limits.writeMillis.toLong(), TimeUnit.MILLISECONDS)) {
            abort()
            throw SocketTimeoutException("WebSocket message writer deadline exceeded")
        }
        try {
            var offset = 0
            do {
                val count = minOf(TRANSFER_CHUNK_BYTES, data.size - offset)
                writeFrame(if (offset == 0) 2 else 0, data,
                    fin = offset + count == data.size, offset = offset, count = count)
                offset += count
                progress()
            } while (offset < data.size)
        } catch (e: Exception) { abort(); throw e }
        finally { messageLock.unlock() }
    }

    private fun writeFrame(opcode: Int, body: ByteArray, allowClosing: Boolean = false, timeoutMillis: Int = limits.writeMillis,
        fin: Boolean = true, offset: Int = 0, count: Int = body.size) {
        writeBounded(timeoutMillis) {
            if (closing.get() && !allowClosing) throw EOFException("WebSocket closing")
            output.write((if (fin) 0x80 else 0) or opcode)
            when {
                count < 126 -> output.write(0x80 or count)
                count <= 65535 -> { output.write(0xfe); output.write(count ushr 8); output.write(count) }
                else -> {
                    output.write(0xff)
                    for (shift in 56 downTo 0 step 8) output.write((count.toLong() ushr shift).toInt())
                }
            }
            val mask = ByteArray(4).also(rng::nextBytes)
            output.write(mask)
            val chunk = ByteArray(minOf(4096, count))
            var position = 0
            while (position < count) {
                val length = minOf(chunk.size, count - position)
                for (i in 0 until length) chunk[i] = (body[offset + position + i].toInt() xor mask[(position + i) % 4].toInt()).toByte()
                output.write(chunk, 0, length)
                position += length
            }
            output.flush()
        }
    }

    fun receive(): ByteArray? {
        if (closed.get()) return null
        check(readLock.tryLock()) { "WebSocket supports one reader" }
        try { return readMessage(deadlineAfter(limits.readMillis)) }
        catch (e: Exception) { abort(); throw e }
        finally { readLock.unlock() }
    }

    /** Validate a never-used pooled connection before consuming any MTProto initialization. */
    fun probeIdle(timeoutMillis: Int = 1_500) {
        check(readLock.tryLock()) { "WebSocket supports one reader" }
        try {
            if (closed.get() || closing.get() || fragmented) throw EOFException("Unused WebSocket closed")
            val nonce = ByteArray(8).also(rng::nextBytes)
            val deadline = deadlineAfter(timeoutMillis)
            writeFrame(9, nonce, timeoutMillis = timeoutMillis)
            if (readMessage(deadline, probeNonce = nonce) == null) throw EOFException("Unused WebSocket closed")
        } catch (error: Exception) { abort(); throw error }
        finally { readLock.unlock() }
    }

    private fun readMessage(deadline: Long, discardMessages: Boolean = false, probeNonce: ByteArray? = null): ByteArray? {
        var nextFrameDeadline = deadline
        var messageDeadline: Long? = null
        var messageHardDeadline = 0L
        var dataSinceRenewal = 0
        var pendingPing: ByteArray? = null
        var pongDeadline = 0L
        while (!closed.get()) {
            // Healthy control traffic must not consume one absolute receive()
            // budget. Only a partial data message keeps its original deadline.
            val first = try {
                readByte(if (discardMessages || probeNonce != null) deadline else messageDeadline ?: nextFrameDeadline)
            } catch (error: SocketTimeoutException) {
                if (discardMessages || probeNonce != null || fragmented || pendingPing != null || closing.get()) throw error
                val ping = ByteArray(8).also(rng::nextBytes)
                writeFrame(9, ping)
                pendingPing = ping
                pongDeadline = deadlineAfter(minOf(limits.pongMillis, limits.readMillis))
                nextFrameDeadline = pongDeadline
                continue
            }
            val frameDeadline = if (discardMessages || probeNonce != null) deadline else messageDeadline ?: deadlineAfter(limits.readMillis)
            val second = readByte(frameDeadline)
            val fin = first and 0x80 != 0
            val opcode = first and 15
            if (first and 0x70 != 0) protocolError("Unnegotiated WebSocket RSV bits")
            if (second and 0x80 != 0) protocolError("Masked server WebSocket frame")
            if (opcode !in listOf(0, 2, 8, 9, 10)) protocolError("Unsupported WebSocket opcode", if (opcode == 1) 1003 else 1002)
            if (probeNonce != null && opcode < 8) protocolError("Data on unused WebSocket")
            if (opcode == 0 && !fragmented || opcode == 2 && fragmented) protocolError("Invalid WebSocket continuation sequence")
            var size = (second and 127).toLong()
            if (opcode >= 8 && (!fin || size > 125)) protocolError("Invalid WebSocket control frame")
            if (size == 126L) {
                val bytes = exactly(2, frameDeadline)
                size = ((bytes[0].toInt() and 255) shl 8 or (bytes[1].toInt() and 255)).toLong()
                if (size < 126) protocolError("Noncanonical WebSocket length")
            } else if (size == 127L) {
                val bytes = exactly(8, frameDeadline)
                if (bytes[0].toInt() and 0x80 != 0) protocolError("Invalid 64-bit WebSocket length")
                size = 0
                for (b in bytes) size = (size shl 8) or (b.toLong() and 255)
                if (size < 65536) protocolError("Noncanonical WebSocket length")
            }
            if (size > limits.messageBytes || opcode < 8 && size > limits.messageBytes - fragmentBytes) protocolError("WebSocket message exceeds limit", 1009)
            val body = if (opcode < 8 && !discardMessages && probeNonce == null) {
                if (messageDeadline == null) {
                    messageDeadline = frameDeadline
                    messageHardDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(limits.readMillis.toLong() * 5)
                }
                exactly(size.toInt(), { requireNotNull(messageDeadline) }) { bytes ->
                    dataSinceRenewal += bytes
                    // Tiny drips and control traffic never renew a partial message.
                    // A useful data quantum can renew the window, within a hard cap.
                    if (dataSinceRenewal >= TRANSFER_CHUNK_BYTES) {
                        dataSinceRenewal %= TRANSFER_CHUNK_BYTES
                        messageDeadline = minOf(messageHardDeadline, deadlineAfter(limits.readMillis))
                    }
                }
            } else exactly(size.toInt(), frameDeadline)
            when (opcode) {
                8 -> {
                    validateClose(body)
                    val statusCode = if(body.size>=2)((body[0].toInt() and 255) shl 8) or (body[1].toInt() and 255) else null
                    val category = if(body.size>2)body.copyOfRange(2,body.size).toString(Charsets.UTF_8)
                        .let{raw->WebSocketCloseCategory.entries.firstOrNull{it.name==raw}} else null
                    peerClose = WebSocketClose(statusCode, category)
                    // The received Close is authoritative even if the peer drops TCP
                    // before our echo is written. Never replace it with an echo error.
                    try {
                        if (closing.compareAndSet(false, true)) writeFrame(8, body, allowClosing = true, timeoutMillis = limits.closeMillis)
                    } catch (_: IOException) { /* Close metadata is already captured. */ }
                    finally { peerClosed.countDown(); abort() }
                    // 1000 with an unknown reason retains the existing graceful EOF behavior.
                    // Worker close categories are fixed constants; never export arbitrary peer text.
                    if(statusCode!=null && (statusCode!=1000 || category!=null))
                        throw WebSocketPeerCloseException(statusCode,category)
                    return null
                }
                9 -> writeFrame(10, body, allowClosing = true,
                    timeoutMillis = if (probeNonce != null) remaining(deadline) else limits.writeMillis)
                10 -> {
                    if (probeNonce?.contentEquals(body) == true) return ByteArray(0)
                    if (pendingPing?.contentEquals(body) == true) pendingPing = null
                }
                2, 0 -> {
                    if (opcode == 2 && fin) {
                        if (!discardMessages) return body
                    } else {
                        if (messageDeadline == null) messageDeadline = frameDeadline
                        val needed = fragmentBytes + body.size
                        if (needed > fragmentBuffer.size) {
                            fragmentBuffer = fragmentBuffer.copyOf(minOf(limits.messageBytes, maxOf(needed, maxOf(256, fragmentBuffer.size * 2))))
                        }
                        body.copyInto(fragmentBuffer, fragmentBytes)
                        fragmentBytes += body.size
                        fragmented = !fin
                        if (fin) {
                            val message = if (!discardMessages) fragmentBuffer.copyOf(fragmentBytes) else null
                            fragmentBuffer = ByteArray(0)
                            fragmentBytes = 0
                            if (message != null) return message
                        }
                    }
                }
            }
            nextFrameDeadline = if (pendingPing != null) pongDeadline else deadlineAfter(limits.readMillis)
        }
        return null
    }

    private fun validateClose(body: ByteArray) {
        if (body.size == 1) protocolError("Invalid WebSocket Close length")
        if (body.size < 2) return
        val code = (body[0].toInt() and 255) shl 8 or (body[1].toInt() and 255)
        if (!(code in 1000..1014 && code !in 1004..1006 || code in 3000..4999)) protocolError("Invalid WebSocket Close code")
        try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body, 2, body.size - 2))
        } catch (_: java.nio.charset.CharacterCodingException) { protocolError("Invalid UTF-8 WebSocket Close reason", 1007) }
    }

    private fun protocolError(message: String, code: Int = 1002): Nothing {
        if (closing.compareAndSet(false, true)) {
            try { writeFrame(8, byteArrayOf((code ushr 8).toByte(), code.toByte()), allowClosing = true, timeoutMillis = limits.closeMillis) }
            catch (_: Exception) { }
        }
        abort()
        throw ProtocolException(message)
    }

    /** Immediate cancellation for service/session shutdown, independent of the reader/writer locks. */
    fun abort() {
        closing.set(true)
        if (closed.compareAndSet(false, true)) quietlyClose(socket)
    }

    override fun close() {
        if (!closing.compareAndSet(false, true)) return
        val deadline = deadlineAfter(limits.closeMillis)
        try {
            writeFrame(8, byteArrayOf(3, -24), allowClosing = true, timeoutMillis = remaining(deadline))
            if (readLock.tryLock()) {
                try { readMessage(deadline, discardMessages = true) } finally { readLock.unlock() }
            } else {
                peerClosed.await(remaining(deadline).toLong(), TimeUnit.MILLISECONDS)
            }
        } catch (_: Exception) {
            // Closing is best effort, but always has a deadline and releases the underlying socket.
        } finally { abort() }
    }
}
