package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException

enum class ServerPhase { STARTING, LISTENING, STOPPING, STOPPED, ERROR }
enum class ServerIssue { CONFIGURATION, PORT, HANDSHAKE, WSS_HANDSHAKE, TLS, TCP, RELAY, SESSION, CAPACITY, TIMEOUT }

/** Safe diagnostics never carry peer data, cryptographic material or exception messages. */
sealed interface ServerEvent {
    data class State(val phase: ServerPhase) : ServerEvent
    data class Sessions(val established: Int, val pending: Int, val wss: Int = 0, val tcp: Int = 0, val relay: Int = 0) : ServerEvent
    data class Diagnostic(val issue: ServerIssue, val rejection: HandshakeRejection? = null) : ServerEvent
}

enum class TransportKind { WSS, TCP, RELAY }

/** A future transport can implement this interface without depending on Android or Compose. */
interface RemoteTransport : Closeable {
    val kind: TransportKind
    fun send(data: ByteArray)
    fun receive(): ByteArray?
    /** Immediately release blocking IO during stop, network loss and deadlines. */
    fun abort() = close()
}

interface TransportFactory {
    /** Register every socket before connect/TLS/upgrade can block. */
    fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport
    fun openRelay(endpoint: RelayEndpoint, dc: Int, own: (Closeable) -> Unit): RemoteTransport =
        throw java.io.IOException("Relay transport unavailable")
    fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport
}

private object DirectTransportFactory : TransportFactory {
    override fun openRelay(endpoint: RelayEndpoint, dc: Int, own: (Closeable) -> Unit): RemoteTransport =
        wrap(RawWebSocket.connect(endpoint.domain, endpoint.domain, endpoint.requestPath(dc), socketOwner = { own(it) }), TransportKind.RELAY)

    override fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport {
        val websocket = RawWebSocket.connect(address, domain, socketOwner = { own(it) })
        return wrap(websocket, TransportKind.WSS)
    }

    private fun wrap(websocket: RawWebSocket, transportKind: TransportKind) = object : RemoteTransport {
            override val kind = transportKind
            override fun send(data: ByteArray) = websocket.send(data)
            override fun receive() = websocket.receive()
            override fun close() = websocket.close()
            override fun abort() = websocket.abort()
    }

    override fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport {
        val socket = Socket()
        own(socket)
        try {
            socket.connect(InetSocketAddress(address, 443), 8_000)
            socket.tcpNoDelay = true
            // The session watchdog applies a deadline to both directions, including writes.
            socket.soTimeout = 0
            return object : RemoteTransport {
                override val kind = TransportKind.TCP
                override fun send(data: ByteArray) {
                    socket.getOutputStream().write(data)
                    socket.getOutputStream().flush()
                }
                override fun receive() = socket.getInputStream().readChunk()
                override fun close() = socket.close()
            }
        } catch (error: Exception) {
            socket.close()
            throw error
        }
    }
}

/** Limits cover untrusted clients, setup, idle IO, threads and pending work. */
data class ServerLimits(
    val maxSessions: Int = 8,
    val handshakeTimeoutMillis: Int = 10_000,
    val connectTimeoutMillis: Int = 25_000,
    val idleTimeoutMillis: Int = 300_000,
    val wssAttemptTimeoutMillis: Int = 8_000,
    val writeTimeoutMillis: Int = 10_000,
) {
    init {
        require(maxSessions in 1..64)
        require(handshakeTimeoutMillis > 0 && connectTimeoutMillis > 0 && idleTimeoutMillis > 0 &&
            wssAttemptTimeoutMillis > 0 && writeTimeoutMillis > 0)
    }
}

/** Local Obfuscated2 -> direct WSS (upstream redirects), then direct TCP fallback. */
class MtprotoServer(
    private val port: Int,
    secretHex: String,
    private val onEvent: (ServerEvent) -> Unit,
    private val onFatalError: () -> Unit = {},
    private val transports: TransportFactory = DirectTransportFactory,
    private val limits: ServerLimits = ServerLimits(),
    private val relayEndpoint: RelayEndpoint? = null,
) : Closeable {
    private val secret: ByteArray
    private val lifecycle = Any()
    @Volatile private var current: Run? = null

    init {
        require(port in 1..65535) { "Invalid local port" }
        require(secretHex.matches(Regex("[0-9a-fA-F]{32}"))) { "Invalid MTProto secret format" }
        secret = secretHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun threads(name: String) = ThreadFactory { task ->
        Thread(task, name).apply { isDaemon = true }
    }

    private fun pool(name: String) = ThreadPoolExecutor(
        limits.maxSessions, limits.maxSessions, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(limits.maxSessions), threads(name), ThreadPoolExecutor.AbortPolicy(),
    )

    private inner class Run {
        val stopping = AtomicBoolean(false)
        val stopped = CountDownLatch(1)
        @Volatile var shutdownThread: Thread? = null
        val sessions = ConcurrentHashMap.newKeySet<Session>()
        val workers = pool("tgws-client")
        val downstream = pool("tgws-downstream")
        val watchdog = ScheduledThreadPoolExecutor(1, threads("tgws-deadline")).apply {
            // A canceled WSS timer must not accumulate under high client turnover.
            removeOnCancelPolicy = true
        }
        @Volatile var server: ServerSocket? = null
        lateinit var listener: Thread
    }

    private inner class Session(val run: Run, val client: Socket) : Closeable {
        private val resourcesLock = Any()
        private val resources = mutableSetOf<Closeable>(client)
        val closed = AtomicBoolean(false)
        @Volatile var established = false
        @Volatile var transportKind: TransportKind? = null
        @Volatile var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(limits.handshakeTimeoutMillis.toLong())
        @Volatile var lastActivity = System.nanoTime()
        @Volatile var upstreamWriteDeadline = 0L
        @Volatile var downstreamWriteDeadline = 0L

        fun own(resource: Closeable) {
            val alreadyClosed = synchronized(resourcesLock) {
                if (closed.get()) true else { resources += resource; false }
            }
            if (alreadyClosed) release(resource)
        }

        fun checkOpen() {
            if (closed.get() || run.stopping.get()) throw EOFException("Session closed")
        }

        fun touch() { lastActivity = System.nanoTime() }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            val owned = synchronized(resourcesLock) { resources.toList().also { resources.clear() } }
            owned.forEach(::release)
            synchronized(lifecycle) {
                run.sessions.remove(this)
                counts(run)
            }
        }
    }

    /** An individual route deadline closes only that attempt, preserving TCP fallback time. */
    private inner class Attempt : Closeable {
        private val lock = Any()
        private val resources = mutableSetOf<Closeable>()
        val closed = AtomicBoolean(false)
        private val deadlineActive = AtomicBoolean(true)
        fun expire() { if (deadlineActive.compareAndSet(true, false)) close() }
        fun retain() = deadlineActive.compareAndSet(true, false) && !closed.get()
        fun own(resource: Closeable) {
            val expired = synchronized(lock) {
                if (closed.get()) true else { resources += resource; false }
            }
            if (expired) release(resource)
        }
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            synchronized(lock) { resources.toList().also { resources.clear() } }.forEach(::release)
        }
    }

    private fun release(resource: Closeable) {
        try {
            if (resource is RemoteTransport) resource.abort() else resource.close()
        } catch (_: Exception) { /* Resource release remains best-effort and secret-free. */ }
    }

    // Events are serialized with lifecycle transitions; Android marshals them to its main thread.
    private fun emit(run: Run, event: ServerEvent) {
        synchronized(lifecycle) { if (current === run) onEvent(event) }
    }

    private fun counts(run: Run) {
        val sessions = run.sessions.toList()
        emit(run, ServerEvent.Sessions(sessions.count { it.established }, sessions.count { !it.established },
            sessions.count { it.established && it.transportKind == TransportKind.WSS },
            sessions.count { it.established && it.transportKind == TransportKind.TCP },
            sessions.count { it.established && it.transportKind == TransportKind.RELAY }))
    }

    fun start() {
        synchronized(lifecycle) {
            if (current != null) return
            val run = Run()
            current = run
            run.listener = Thread({ listen(run) }, "tgws-listener").apply { isDaemon = true }
            val tickMillis = minOf(1_000, limits.handshakeTimeoutMillis / 4,
                limits.idleTimeoutMillis / 4, limits.writeTimeoutMillis / 4).coerceAtLeast(10)
            run.watchdog.scheduleWithFixedDelay({ expire(run) }, tickMillis.toLong(), tickMillis.toLong(), TimeUnit.MILLISECONDS)
            onEvent(ServerEvent.State(ServerPhase.STARTING))
            if (current !== run) return
            run.listener.start()
        }
    }

    private fun listen(run: Run) {
        try {
            val server = ServerSocket()
            synchronized(lifecycle) {
                if (current !== run || run.stopping.get()) { server.close(); return }
                run.server = server
            }
            server.reuseAddress = true
            server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), limits.maxSessions)
            synchronized(lifecycle) {
                if (run.stopping.get()) return
                emit(run, ServerEvent.State(ServerPhase.LISTENING))
            }
            while (!run.stopping.get()) {
                val client = server.accept()
                synchronized(lifecycle) {
                    if (run.stopping.get()) { client.close(); return@synchronized }
                    if (run.sessions.size >= limits.maxSessions) {
                        client.close()
                        emit(run, ServerEvent.Diagnostic(ServerIssue.CAPACITY))
                        return@synchronized
                    }
                    val session = Session(run, client)
                    run.sessions += session
                    counts(run)
                    try {
                        run.workers.execute {
                            try { serve(session) }
                            catch (_: Exception) {
                                if (!session.closed.get()) emit(run, ServerEvent.Diagnostic(ServerIssue.SESSION))
                            } finally {
                                session.close()
                            }
                        }
                    } catch (_: java.util.concurrent.RejectedExecutionException) {
                        session.close()
                    }
                }
            }
        } catch (_: Exception) {
            if (!run.stopping.get()) {
                emit(run, ServerEvent.Diagnostic(ServerIssue.PORT))
                if (shutdown(run, ServerPhase.ERROR)) onFatalError()
            }
        } finally {
            run.server?.let(::release)
        }
    }

    private fun expire(run: Run) {
        val now = System.nanoTime()
        for (session in run.sessions) {
            val upstreamDeadline = session.upstreamWriteDeadline
            val downstreamDeadline = session.downstreamWriteDeadline
            val expired = if (session.established) {
                now - session.lastActivity >= TimeUnit.MILLISECONDS.toNanos(limits.idleTimeoutMillis.toLong()) ||
                    (upstreamDeadline != 0L && now >= upstreamDeadline) ||
                    (downstreamDeadline != 0L && now >= downstreamDeadline)
            } else now >= session.deadline
            if (expired && !session.closed.get()) {
                emit(run, ServerEvent.Diagnostic(ServerIssue.TIMEOUT))
                session.close()
            }
        }
    }

    private fun readInit(session: Session): ByteArray {
        val buffer = ByteArray(64)
        var read = 0
        while (read < buffer.size) {
            session.checkOpen()
            val remaining = session.deadline - System.nanoTime()
            if (remaining <= 0) throw java.net.SocketTimeoutException("Handshake deadline")
            session.client.soTimeout = TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val count = session.client.getInputStream().read(buffer, read, buffer.size - read)
            if (count < 0) throw EOFException("Client closed during handshake")
            read += count
        }
        // The watchdog, rather than read-only socket deadlines, measures bidirectional activity.
        session.client.soTimeout = 0
        return buffer
    }

    private fun serve(session: Session) {
        session.client.tcpNoDelay = true
        val result = MtprotoCrypto.inspectClientInit(readInit(session), secret)
        if (result is ClientInitResult.Rejected) {
            emit(session.run, ServerEvent.Diagnostic(ServerIssue.HANDSHAKE, result.reason))
            return
        }
        val handshake = (result as ClientInitResult.Accepted).handshake
        val relay = MtprotoCrypto.createRelayInit(handshake)
        val context = MtprotoCrypto.buildContext(handshake, secret, relay)
        session.deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(limits.connectTimeoutMillis.toLong())
        val remote = establish(session, handshake, relay) ?: return
        session.checkOpen()
        synchronized(lifecycle) {
            if (session.closed.get()) return
            session.transportKind = remote.kind
            session.established = true
            session.touch()
            counts(session.run)
        }
        bridge(session, remote, context, handshake.tag)
    }

    private fun establish(session: Session, handshake: Handshake, relay: ByteArray): RemoteTransport? {
        relayEndpoint?.let { endpoint ->
            val attempt = Attempt()
            session.own(attempt)
            val timeout = session.run.watchdog.schedule(
                { attempt.expire() }, limits.wssAttemptTimeoutMillis.toLong(), TimeUnit.MILLISECONDS,
            )
            try {
                session.checkOpen()
                val candidate = transports.openRelay(endpoint, handshake.dc, attempt::own)
                attempt.own(candidate)
                session.checkOpen()
                if (attempt.closed.get()) throw java.net.SocketTimeoutException("Relay route deadline")
                candidate.send(relay)
                if (!attempt.retain()) throw java.net.SocketTimeoutException("Relay route deadline")
                return candidate
            } catch (error: Exception) {
                attempt.close()
                if (!session.closed.get()) emit(session.run, ServerEvent.Diagnostic(
                    if (error is SSLException) ServerIssue.TLS else ServerIssue.RELAY))
                return null
            } finally { timeout.cancel(false) }
        }
        val wssIp = TelegramRoutes.webSocketIp(handshake.dc)
        if (wssIp != null) {
            for (domain in TelegramRoutes.webSocketDomains(handshake.dc, handshake.media)) {
                session.checkOpen()
                val attempt = Attempt()
                session.own(attempt)
                val timeout = session.run.watchdog.schedule(
                    { attempt.expire() }, limits.wssAttemptTimeoutMillis.toLong(), TimeUnit.MILLISECONDS,
                )
                var candidate: RemoteTransport? = null
                try {
                    candidate = transports.openWebSocket(wssIp, domain, attempt::own)
                    attempt.own(candidate)
                    session.checkOpen()
                    if (attempt.closed.get()) throw java.net.SocketTimeoutException("WSS route deadline")
                    candidate.send(relay)
                    if (!attempt.retain()) throw java.net.SocketTimeoutException("WSS route deadline")
                    timeout.cancel(false)
                    return candidate
                } catch (error: Exception) {
                    candidate?.let(::release)
                    attempt.close()
                    if (session.closed.get()) return null
                    emit(session.run, ServerEvent.Diagnostic(if (error is SSLException) ServerIssue.TLS else ServerIssue.WSS_HANDSHAKE))
                } finally {
                    timeout.cancel(false)
                }
            }
        }
        session.checkOpen()
        val tcpIp = TelegramRoutes.ip(handshake.dc) ?: return null
        var candidate: RemoteTransport? = null
        try {
            candidate = transports.openTcp(tcpIp, session::own)
            session.own(candidate)
            session.checkOpen()
            // No client payload has been consumed: a failed WSS relay send can safely retry here.
            candidate.send(relay)
            return candidate
        } catch (_: Exception) {
            candidate?.let(::release)
            if (!session.closed.get()) emit(session.run, ServerEvent.Diagnostic(ServerIssue.TCP))
            return null
        }
    }

    private fun bridge(session: Session, remote: RemoteTransport, context: CryptoContext, tag: ByteArray) {
        val splitter = PacketSplitter(tag)
        val downstream = session.run.downstream.submit {
            try {
                while (!session.closed.get()) {
                    val data = remote.receive() ?: break
                    // Empty WS messages are legal, but cannot keep an idle proxy session alive.
                    if (data.isEmpty()) continue
                    val plain = context.fromTelegram.update(data)
                    session.downstreamWriteDeadline = writeDeadline()
                    try {
                        session.client.getOutputStream().write(context.toClient.update(plain))
                        session.client.getOutputStream().flush()
                    } finally { session.downstreamWriteDeadline = 0L }
                    session.touch()
                }
            } catch (_: Exception) {
                if (!session.closed.get()) emit(session.run, ServerEvent.Diagnostic(ServerIssue.SESSION))
            } finally {
                session.close()
            }
        }
        try {
            while (!session.closed.get()) {
                val data = session.client.getInputStream().readChunk() ?: break
                val plain = context.fromClient.update(data)
                val encrypted = context.toTelegram.update(plain)
                // TCP carries the same framed stream: validate bounds before forwarding in both paths.
                splitter.split(encrypted, plain).forEach { packet ->
                    session.upstreamWriteDeadline = writeDeadline()
                    try { remote.send(packet) } finally { session.upstreamWriteDeadline = 0L }
                }
                session.touch()
            }
            splitter.flush().forEach(remote::send)
        } catch (error: Exception) {
            if (!session.closed.get()) emit(session.run, ServerEvent.Diagnostic(ServerIssue.SESSION))
            throw error
        } finally {
            session.close()
            downstream.cancel(true)
        }
    }

    /** Network changes abort sessions, allowing Telegram to reconnect through the same listener. */
    fun disconnectSessions() { current?.sessions?.toList()?.forEach(Session::close) }

    private fun writeDeadline() = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(limits.writeTimeoutMillis.toLong())

    private fun shutdown(run: Run, phase: ServerPhase): Boolean {
        val ownsShutdown = synchronized(lifecycle) {
            if (current !== run) return false
            if (run.stopping.compareAndSet(false, true)) {
                run.shutdownThread = Thread.currentThread()
                emit(run, ServerEvent.State(ServerPhase.STOPPING))
                true
            } else false
        }
        if (!ownsShutdown) {
            // A simultaneous stop must await the same cleanup instead of reporting completion early.
            // Never wait from the listener needed by the owner, or from a reentrant callback.
            if (Thread.currentThread() !== run.listener && Thread.currentThread() !== run.shutdownThread) {
                try { run.stopped.await(1, TimeUnit.SECONDS) }
                catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }
            return false
        }
        run.server?.let(::release)
        run.sessions.toList().forEach(Session::close)
        run.watchdog.shutdownNow()
        run.workers.shutdownNow()
        run.downstream.shutdownNow()
        // ServerSocket.close can defer native descriptor release while accept is blocked.
        // Wait outside the lifecycle lock before publishing STOPPED or permitting a restart.
        if (Thread.currentThread() !== run.listener) {
            try { run.listener.join(1_000) }
            catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        val completedPhase = if (Thread.currentThread() !== run.listener && run.listener.isAlive) {
            emit(run, ServerEvent.Diagnostic(ServerIssue.TIMEOUT))
            ServerPhase.ERROR
        } else phase
        synchronized(lifecycle) {
            if (current === run) {
                onEvent(ServerEvent.Sessions(0, 0))
                onEvent(ServerEvent.State(completedPhase))
                current = null
            }
        }
        run.stopped.countDown()
        return true
    }

    override fun close() = stop()
    fun stop() { current?.let { shutdown(it, ServerPhase.STOPPED) } }
}

private fun InputStream.readChunk(): ByteArray? {
    val data = ByteArray(65_536)
    val count = read(data)
    return if (count < 0) null else data.copyOf(count)
}
