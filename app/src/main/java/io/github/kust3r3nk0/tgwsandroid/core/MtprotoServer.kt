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
enum class ServerIssue { CONFIGURATION, PORT, HANDSHAKE, WSS_HANDSHAKE, TLS, TCP, RELAY, RETRY_LATER, SESSION, CAPACITY, TIMEOUT, HTTP2, DNS, NETWORK }

/** Safe diagnostics never carry peer data, cryptographic material or exception messages. */
sealed interface ServerEvent {
    data class State(val phase: ServerPhase) : ServerEvent
    data class Sessions(val established: Int, val pending: Int, val wss: Int = 0, val tcp: Int = 0, val relay: Int = 0,val cf:Int=0,val h2:Int=0) : ServerEvent
    data class Diagnostic(val issue: ServerIssue, val rejection: HandshakeRejection? = null) : ServerEvent
    data class BackgroundIssue(val issue: ServerIssue) : ServerEvent
    data class Routes(val routes: List<RouteObservation>) : ServerEvent
    data class Traffic(val sent:Long,val received:Long) : ServerEvent
}

enum class TransportKind { WSS, TCP, RELAY, WORKER, CLOUDFLARE, HTTP2 }

/** A future transport can implement this interface without depending on Android or Compose. */
interface RemoteTransport : Closeable {
    val kind: TransportKind
    val setupTimings:SetupTimings? get()=null
    fun send(data: ByteArray)
    fun send(data: ByteArray, progress: () -> Unit) { send(data); progress() }
    fun receive(): ByteArray?
    /** Only transports advertising warm support must implement a bounded idle probe. */
    fun verifyIdle() { throw java.io.IOException("Idle verification unsupported") }
    /** Immediately release blocking IO during stop, network loss and deadlines. */
    fun abort() = close()
}

interface TransportFactory {
    val supportsWarmWebSockets: Boolean get() = false
    /** Register every socket before connect/TLS/upgrade can block. */
    fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport
    fun openRelay(endpoint: RelayEndpoint, dc: Int, own: (Closeable) -> Unit): RemoteTransport =
        throw java.io.IOException("Relay transport unavailable")
    fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport
    fun openHttpMedia(domain:String,tag:ByteArray,own:(Closeable)->Unit):RemoteTransport =
        throw java.io.IOException("HTTP2 transport unavailable")
}

private object DirectTransportFactory : TransportFactory {
    override val supportsWarmWebSockets = true
    override fun openRelay(endpoint: RelayEndpoint, dc: Int, own: (Closeable) -> Unit): RemoteTransport =
        wrap(RawWebSocket.connect(endpoint.domain, endpoint.domain, endpoint.requestPath(dc), socketOwner = { own(it) }), TransportKind.RELAY)

    override fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport {
        val websocket = RawWebSocket.connect(address, domain, socketOwner = { own(it) })
        return wrap(websocket, TransportKind.WSS)
    }

    private fun wrap(websocket: RawWebSocket, transportKind: TransportKind) = object : RemoteTransport {
            override val kind = transportKind
            override val setupTimings get()=websocket.setupTimings
            override fun send(data: ByteArray) = websocket.send(data)
            override fun send(data: ByteArray, progress: () -> Unit) = websocket.send(data, progress)
            override fun receive() = websocket.receive()
            override fun verifyIdle() = websocket.probeIdle()
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
                override fun send(data: ByteArray, progress: () -> Unit) = writeWithProgress(socket.getOutputStream(), data, progress)
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
    val warmPoolCapacity: Int = 4,
    val warmMaxAgeMillis: Int = 30_000,
) {
    init {
        require(maxSessions in 1..64)
        require(warmPoolCapacity in 0..8 && warmMaxAgeMillis > 0)
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
    private val transportSettings:TransportSettings = TransportSettings(),
    private val fakeTlsDomain: String = transportSettings.fakeTlsDomain,
) : Closeable {
    private val secret: ByteArray
    private val lifecycle = Any()
    @Volatile private var current: Run? = null

    init {
        require(fakeTlsDomain.isEmpty() || TelegramSecret.validDomain(fakeTlsDomain))
        require(transportSettings.mode!=TransportMode.RELAY_ONLY || relayEndpoint!=null)
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
        val retries = RouteBackoff<String>()
        val diagnostics=RouteDiagnostics()
        val rotation=ConcurrentHashMap<String,Int>()
        val sent=java.util.concurrent.atomic.AtomicLong()
        val received=java.util.concurrent.atomic.AtomicLong()
        var reportedTraffic=0L to 0L
        val probes=ConcurrentHashMap.newKeySet<Attempt>()
        val probing=AtomicBoolean()
        var lastProbe=Long.MIN_VALUE
        var networkGeneration=0L
        val warm = WarmConnectionPool<WssRoute>(
            if (transports.supportsWarmWebSockets && relayEndpoint == null && transportSettings.mode==TransportMode.DIRECT) limits.warmPoolCapacity else 0,
            limits.warmMaxAgeMillis, limits.wssAttemptTimeoutMillis, watchdog,
            opener = { route, own ->
                val permit = retries.acquire(routeKey(route))
                if (permit == null) null else try {
                    transports.openWebSocket(route.address, route.domain, own).also { retries.success(permit) }
                } catch (error: Exception) {
                    retries.failure(permit)
                    emit(this, ServerEvent.BackgroundIssue(if (error is SSLException) ServerIssue.TLS else ServerIssue.WSS_HANDSHAKE))
                    throw error
                }
            })
        @Volatile var server: ServerSocket? = null
        lateinit var listener: Thread
    }

    private inner class Session(val run: Run, val client: Socket) : Closeable {
        var input: InputStream = client.getInputStream()
        var output: java.io.OutputStream = client.getOutputStream()
        private val resourcesLock = Any()
        private val resources = mutableSetOf<Closeable>(client)
        val closed = AtomicBoolean(false)
        @Volatile var established = false
        @Volatile var transportKind: TransportKind? = null
        @Volatile var routeKey:String?=null
        @Volatile var remoteFailureReported=false
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
                if (established && run.sessions.none { it.established && it.routeKey == routeKey } &&
                    run.diagnostics.closed(routeKey)) {
                    emit(run, ServerEvent.Routes(run.diagnostics.snapshot()))
                }
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
        @Volatile var timedOut=false;private set
        fun expire() { if (deadlineActive.compareAndSet(true, false)){timedOut=true;close()} }
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
        val sessions = run.sessions.snapshotEntries()
        emit(run, ServerEvent.Sessions(sessions.count { it.established }, sessions.count { !it.established },
            sessions.count { it.established && it.transportKind == TransportKind.WSS },
            sessions.count { it.established && it.transportKind == TransportKind.TCP },
            sessions.count { it.established && it.transportKind in setOf(TransportKind.RELAY,TransportKind.WORKER) },
            sessions.count { it.established && it.transportKind == TransportKind.CLOUDFLARE },
            sessions.count { it.established && it.transportKind == TransportKind.HTTP2 }))
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
        run.warm.rotate()
        val traffic=run.sent.get() to run.received.get()
        if(traffic!=run.reportedTraffic){run.reportedTraffic=traffic;emit(run,ServerEvent.Traffic(traffic.first,traffic.second));emit(run,ServerEvent.Routes(run.diagnostics.snapshot()))}
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

    /** An empty local TCP probe is not an authenticated MTProto attempt. */
    private fun readHandshakeBytes(session: Session, size: Int, allowEmpty: Boolean = false): ByteArray? {
        val buffer = ByteArray(size)
        var read = 0
        while (read < buffer.size) {
            session.checkOpen()
            val remaining = session.deadline - System.nanoTime()
            if (remaining <= 0) throw java.net.SocketTimeoutException("Handshake deadline")
            session.client.soTimeout = TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val count = session.input.read(buffer, read, buffer.size - read)
            if (count < 0) {
                if (read == 0 && allowEmpty) return null
                throw EOFException("Truncated client handshake")
            }
            read += count
        }
        return buffer
    }

    private fun readInit(session: Session): ByteArray? {
        val prefix = readHandshakeBytes(session, 5, allowEmpty = true) ?: return null
        val size = ((prefix[3].toInt() and 255) shl 8) or (prefix[4].toInt() and 255)
        val tlsCandidate = fakeTlsDomain.isNotEmpty() && prefix[0] == 0x16.toByte() &&
            prefix[1] == 3.toByte() && prefix[2].toInt() in 1..3 && size in 38..4096
        val buffer = if (!tlsCandidate) {
            prefix + requireNotNull(readHandshakeBytes(session, 59))
        } else {
            // Obfuscated2 randomness may resemble a TLS record. Authenticate a full
            // 64-byte plain init before committing to the longer TLS interpretation.
            val start = prefix + requireNotNull(readHandshakeBytes(session, minOf(size, 59)))
            if (start.size == 64 && MtprotoCrypto.parseClientInit(start, secret) != null) start
            else {
                val helloBytes = if (size > 59) start + requireNotNull(readHandshakeBytes(session, size - 59)) else start
                val hello = FakeTls.verify(helloBytes, secret)
                if (hello == null && helloBytes.size < 64) {
                    // A short TLS-looking prefix can still belong to an authenticated
                    // plain init. The common handshake deadline bounds this read.
                    helloBytes + requireNotNull(readHandshakeBytes(session, 64 - helloBytes.size))
                } else {
                    if (hello == null) throw java.net.ProtocolException("Fake TLS verification failed")
                    session.output.write(FakeTls.response(secret, hello))
                    session.output.flush()
                    session.input = FakeTls.Reader(session.input)
                    session.output = FakeTls.Writer(session.output)
                    requireNotNull(readHandshakeBytes(session, 64))
                }
            }
        }
        // The watchdog, rather than read-only socket deadlines, measures bidirectional activity.
        session.client.soTimeout = 0
        return buffer
    }

    private fun serve(session: Session) {
        session.client.tcpNoDelay = true
        val initial = try { readInit(session) ?: return } catch (_: java.net.SocketTimeoutException) {
            if (!session.closed.get()) emit(session.run, ServerEvent.Diagnostic(ServerIssue.TIMEOUT))
            return
        } catch (_: EOFException) {
            if (!session.closed.get()) emit(session.run, ServerEvent.Diagnostic(ServerIssue.HANDSHAKE, HandshakeRejection.INVALID_LENGTH))
            return
        } catch (_: java.net.ProtocolException) {
            if (!session.closed.get()) emit(session.run, ServerEvent.Diagnostic(ServerIssue.HANDSHAKE,
                if (fakeTlsDomain.isNotEmpty()) HandshakeRejection.FAKE_TLS else HandshakeRejection.SECRET_OR_TRANSPORT))
            return
        }
        val result = MtprotoCrypto.inspectClientInit(initial, secret)
        if (result is ClientInitResult.Rejected) {
            emit(session.run, ServerEvent.Diagnostic(ServerIssue.HANDSHAKE, result.reason))
            return
        }
        val handshake = (result as ClientInitResult.Accepted).handshake
        val relay = MtprotoCrypto.createRelayInit(handshake)
        val context = MtprotoCrypto.buildContext(handshake, secret, relay)
        val budget=if(transportSettings.mode==TransportMode.AUTO_CLOUDFLARE)maxOf(limits.connectTimeoutMillis.toLong(),limits.wssAttemptTimeoutMillis.toLong()*5)else limits.connectTimeoutMillis.toLong()
        session.deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budget)
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

    private fun routeKey(route: WssRoute) = "wss:${route.address}:${route.domain}"

    private fun establish(session: Session, handshake: Handshake, relay: ByteArray): RemoteTransport? {
        var attempted = false
        var attempts=0
        fun kind(issue:ServerIssue)=when(issue){ServerIssue.TCP->TransportKind.TCP;ServerIssue.RELAY->TransportKind.RELAY;ServerIssue.HTTP2->TransportKind.HTTP2;else->TransportKind.WSS}
        fun mark(key:String,issue:ServerIssue,status:RouteStatus,failure:ServerIssue?=null){
            synchronized(lifecycle){if(!session.closed.get()){
                val transport=when{key.startsWith("cf:")->TransportKind.CLOUDFLARE;key.startsWith("worker:")->TransportKind.WORKER;else->kind(issue)}
                session.run.diagnostics.mark(key,handshake.dc,handshake.media,transport,status,failure)
                emit(session.run,ServerEvent.Routes(session.run.diagnostics.snapshot()))}}
        }
        fun connect(key: String, issue: ServerIssue, open: ((Closeable) -> Unit) -> RemoteTransport): RemoteTransport? {
            session.checkOpen()
            if(transportSettings.mode==TransportMode.AUTO_CLOUDFLARE && attempts>=4 && issue!=ServerIssue.TCP)return null
            val permit = session.run.retries.acquire(key) ?: run{mark(key,issue,RouteStatus.BACKOFF);return null}
            attempts++
            attempted = true
            mark(key,issue,RouteStatus.CONNECTING)
            val attempt = Attempt()
            session.own(attempt)
            val timeout = session.run.watchdog.schedule(
                { attempt.expire() }, limits.wssAttemptTimeoutMillis.toLong(), TimeUnit.MILLISECONDS,
            )
            try {
                val candidate = open(attempt::own)
                attempt.own(candidate)
                session.checkOpen()
                if (attempt.closed.get()) throw java.net.SocketTimeoutException("Route deadline")
                // Client payload is untouched: route retries never replay consumed AES-CTR state.
                candidate.send(relay)
                if (!attempt.retain()) throw java.net.SocketTimeoutException("Route deadline")
                synchronized(lifecycle){
                    session.checkOpen()
                    session.run.retries.success(permit)
                    session.routeKey=key
                    session.run.diagnostics.mark(key,handshake.dc,handshake.media,candidate.kind,RouteStatus.AVAILABLE,timings=candidate.setupTimings)
                    emit(session.run,ServerEvent.Routes(session.run.diagnostics.snapshot()))
                }
                return candidate
            } catch (error: Exception) {
                attempt.close()
                if (session.closed.get()) session.run.retries.cancel(permit)
                else {
                    session.run.retries.failure(permit)
                    val failure=if(attempt.timedOut)ServerIssue.TIMEOUT else setupFailure(error,issue)
                    synchronized(lifecycle){if(!session.closed.get())mark(key,issue,RouteStatus.UNAVAILABLE,failure)}
                    emit(session.run, ServerEvent.Diagnostic(failure))
                }
                return null
            } finally { timeout.cancel(false) }
        }
        val endpoint = relayEndpoint
        if (endpoint != null) {
            val result = connect("relay:${handshake.dc}", ServerIssue.RELAY) { own ->
                transports.openRelay(endpoint, handshake.dc, own)
            }
            if (result == null && !attempted && !session.closed.get()) emit(session.run, ServerEvent.Diagnostic(ServerIssue.RETRY_LATER))
            return result
        }
        if(transportSettings.mode==TransportMode.RELAY_ONLY){emit(session.run,ServerEvent.Diagnostic(ServerIssue.CONFIGURATION));return null}
        if(transportSettings.mode==TransportMode.AUTO_CLOUDFLARE){
            val workers=transportSettings.workerUrls
            val workerGroup="workers:${handshake.dc}"
            val start=session.run.rotation[workerGroup]?:0
            for(offset in 0 until minOf(2,workers.size)){
                val index=(start+offset)%workers.size
                val worker=requireNotNull(RelayEndpoint.parse(workers[index]))
                val result=connect("worker:$index:${handshake.dc}",ServerIssue.RELAY){own->
                    val base=transports.openRelay(worker,handshake.dc,own)
                    object:RemoteTransport by base{override val kind=TransportKind.WORKER}
                }
                if(result!=null){session.run.rotation[workerGroup]=index;return result}
                session.run.rotation[workerGroup]=(index+1)%workers.size
            }
            val domains=transportSettings.cloudflareDomains
            val cfGroup="cf:${handshake.dc}"
            val first=session.run.rotation[cfGroup]?:0
            for(offset in 0 until minOf(2,domains.size)){
                val index=(first+offset)%domains.size
                val domain="kws${handshake.dc}.${domains[index]}"
                if(handshake.media && transportSettings.http2Media){
                    val h2=connect("h2:$index:${handshake.dc}",ServerIssue.HTTP2){own->transports.openHttpMedia(domain,handshake.tag,own)}
                    if(h2!=null){session.run.rotation[cfGroup]=index;return h2}
                }
                val cf=connect("cf:$index:${handshake.dc}",ServerIssue.WSS_HANDSHAKE){own->
                    val base=transports.openWebSocket(domain,domain,own)
                    object:RemoteTransport by base{override val kind=TransportKind.CLOUDFLARE}
                }
                if(cf!=null){session.run.rotation[cfGroup]=index;return cf}
                session.run.rotation[cfGroup]=(index+1)%domains.size
            }
        }
        if(transportSettings.mode==TransportMode.DIRECT)TelegramRoutes.webSocketIp(handshake.dc)?.let { ip ->
            for (domain in TelegramRoutes.webSocketDomains(handshake.dc, handshake.media)) {
                val route = WssRoute(ip, domain)
                val remote = connect(routeKey(route), ServerIssue.WSS_HANDSHAKE) { own ->
                    val cached = session.run.warm.take(route)
                    if (cached != null) {
                        own(cached)
                        try {
                            cached.verifyIdle()
                            session.checkOpen()
                            return@connect cached
                        } catch (_: Exception) { cached.abort(); session.checkOpen() }
                    }
                    transports.openWebSocket(ip, domain, own)
                }
                if (remote != null) {
                    synchronized(lifecycle) {
                        if (!session.closed.get() && !session.run.stopping.get()) session.run.warm.refill(route)
                    }
                    return remote
                }
            }
        }
        val ip = TelegramRoutes.ip(handshake.dc) ?: return null
        val remote = connect("tcp:${handshake.dc}", ServerIssue.TCP) { own -> transports.openTcp(ip, own) }
        if (remote == null && !attempted && !session.closed.get()) emit(session.run, ServerEvent.Diagnostic(ServerIssue.RETRY_LATER))
        return remote
    }

    private fun bridge(session: Session, remote: RemoteTransport, context: CryptoContext, tag: ByteArray) {
        val splitter = PacketSplitter(tag)
        fun remoteFailure(error:Exception){
            synchronized(lifecycle){if(!session.closed.get()){
                session.remoteFailureReported=true
                val peerClose=error as? WebSocketPeerCloseException
                if(peerClose?.statusCode==1000){
                    if(session.run.sessions.none{it!==session && !it.closed.get() && it.established && it.routeKey==session.routeKey}){
                        session.run.diagnostics.closed(session.routeKey,peerClose.statusCode,peerClose.category)
                        emit(session.run,ServerEvent.Routes(session.run.diagnostics.snapshot()))
                    }
                    return
                }
                val issue=when{
                    error is SSLException->ServerIssue.TLS
                    remote.kind==TransportKind.HTTP2->ServerIssue.HTTP2
                    remote.kind==TransportKind.RELAY || remote.kind==TransportKind.WORKER->ServerIssue.RELAY
                    else->ServerIssue.SESSION
                }
                session.run.diagnostics.failed(session.routeKey,issue,peerClose?.statusCode,peerClose?.category)
                emit(session.run,ServerEvent.Routes(session.run.diagnostics.snapshot()))
                emit(session.run,ServerEvent.Diagnostic(issue))
            }}
        }
        fun send(data:ByteArray){try{
            remote.send(data){
                session.checkOpen()
                session.upstreamWriteDeadline = writeDeadline()
                session.touch()
            }
        }catch(error:Exception){remoteFailure(error);throw error}}
        val downstream = session.run.downstream.submit {
            try {
                while (!session.closed.get()) {
                    val data = try{remote.receive()}catch(error:Exception){remoteFailure(error);throw error} ?: break
                    // Empty WS messages are legal, but cannot keep an idle proxy session alive.
                    if (data.isEmpty()) continue
                    val plain = context.fromTelegram.update(data)
                    session.downstreamWriteDeadline = writeDeadline()
                    try {
                        writeWithProgress(session.output,context.toClient.update(plain)){
                            session.checkOpen()
                            session.downstreamWriteDeadline = writeDeadline()
                            session.touch()
                        }
                        session.run.received.addAndGet(data.size.toLong());session.run.diagnostics.traffic(session.routeKey,received=data.size)
                    } finally { session.downstreamWriteDeadline = 0L }
                    session.touch()
                }
            } catch (_: Exception) {
                if (!session.closed.get() && !session.remoteFailureReported) emit(session.run, ServerEvent.Diagnostic(ServerIssue.SESSION))
            } finally {
                session.close()
            }
        }
        try {
            while (!session.closed.get()) {
                val data = session.input.readChunk() ?: break
                session.run.sent.addAndGet(data.size.toLong());session.run.diagnostics.traffic(session.routeKey,sent=data.size)
                val plain = context.fromClient.update(data)
                val encrypted = context.toTelegram.update(plain)
                // TCP carries the same framed stream: validate bounds before forwarding in both paths.
                splitter.split(encrypted, plain).forEach { packet ->
                    session.upstreamWriteDeadline = writeDeadline()
                    try { send(packet) } finally { session.upstreamWriteDeadline = 0L }
                }
                session.touch()
            }
            splitter.flush().forEach(::send)
        } catch (error: Exception) {
            if (!session.closed.get() && !session.remoteFailureReported) emit(session.run, ServerEvent.Diagnostic(ServerIssue.SESSION))
            throw error
        } finally {
            session.close()
            downstream.cancel(true)
        }
    }

    /** Network changes abort sessions, allowing Telegram to reconnect through the same listener. */
    fun disconnectSessions() {
        synchronized(lifecycle) {
            current?.let { run ->
                run.networkGeneration++
                run.warm.invalidate()
                run.probes.snapshotEntries().forEach(Attempt::close)
                run.retries.reset()
                run.rotation.clear()
                run.diagnostics.clear()
                emit(run,ServerEvent.Routes(emptyList()))
                run.sessions.snapshotEntries().forEach(Session::close)
            }
        }
    }

    internal fun warmConnections(): Pair<Int, Int> = current?.warm?.size() ?: (0 to 0)

    /** Explicit, rate-limited transport handshake probe. No MTProto payload or claimed Telegram RTT. */
    fun probeRoutes():Boolean {
        val run=current?:return false
        val generation:Long
        synchronized(lifecycle){
            val now=System.nanoTime()
            if(current!==run || run.stopping.get() || run.probing.get() ||
                (run.lastProbe!=Long.MIN_VALUE && now-run.lastProbe<TimeUnit.SECONDS.toNanos(30)))return false
            run.lastProbe=now;run.probing.set(true)
            generation=run.networkGeneration
        }
        try{run.workers.execute{
            try{
                data class Probe(val key:String,val kind:TransportKind,val open:((Closeable)->Unit)->RemoteTransport)
                val candidates=mutableListOf<Probe>()
                when{
                    relayEndpoint!=null->candidates+=Probe("relay:2",TransportKind.RELAY){own->transports.openRelay(relayEndpoint,2,own)}
                    transportSettings.mode==TransportMode.AUTO_CLOUDFLARE->{
                        transportSettings.workerUrls.take(2).forEachIndexed{index,url->candidates+=Probe("worker:$index:2",TransportKind.WORKER){own->transports.openRelay(requireNotNull(RelayEndpoint.parse(url)),2,own)}}
                        transportSettings.cloudflareDomains.take(1).forEachIndexed{index,domain->
                            if(transportSettings.http2Media)candidates+=Probe("h2:$index:2",TransportKind.HTTP2){own->transports.openHttpMedia("kws2.$domain",ByteArray(4){0xee.toByte()},own)}
                            candidates+=Probe("cf:$index:2",TransportKind.CLOUDFLARE){own->transports.openWebSocket("kws2.$domain","kws2.$domain",own)}
                        }
                    }
                    else->candidates+=Probe("wss:149.154.167.220:kws2.web.telegram.org",TransportKind.WSS){own->transports.openWebSocket("149.154.167.220","kws2.web.telegram.org",own)}
                }
                for(candidate in candidates.take(3)){
                    val attempt=Attempt()
                    synchronized(lifecycle){if(current!==run || run.stopping.get() || run.networkGeneration!=generation)return@execute;run.probes+=attempt}
                    val timeout=run.watchdog.schedule({attempt.expire()},limits.wssAttemptTimeoutMillis.toLong(),TimeUnit.MILLISECONDS)
                    try{
                        synchronized(lifecycle){if(attempt.closed.get())return@execute;run.diagnostics.mark(candidate.key,2,candidate.kind==TransportKind.HTTP2,candidate.kind,RouteStatus.CONNECTING)}
                        emit(run,ServerEvent.Routes(run.diagnostics.snapshot()))
                        val remote=candidate.open(attempt::own);attempt.own(remote)
                        if(attempt.closed.get())throw java.net.SocketTimeoutException("Probe deadline")
                        synchronized(lifecycle){if(!attempt.closed.get() && current===run && !run.stopping.get() && run.networkGeneration==generation){
                            run.diagnostics.mark(candidate.key,2,candidate.kind==TransportKind.HTTP2,candidate.kind,RouteStatus.AVAILABLE,timings=remote.setupTimings)
                            emit(run,ServerEvent.Routes(run.diagnostics.snapshot()))}}
                    }catch(error:Exception){synchronized(lifecycle){if(current===run && !run.stopping.get() && run.networkGeneration==generation){
                        val issue=if(attempt.timedOut)ServerIssue.TIMEOUT else setupFailure(error,if(candidate.kind==TransportKind.HTTP2)ServerIssue.HTTP2 else ServerIssue.WSS_HANDSHAKE)
                        run.diagnostics.mark(candidate.key,2,candidate.kind==TransportKind.HTTP2,candidate.kind,RouteStatus.UNAVAILABLE,issue)
                        emit(run,ServerEvent.Routes(run.diagnostics.snapshot()))}}}
                    finally{timeout.cancel(false);attempt.close();run.probes.remove(attempt)}
                }
            }finally{run.probing.set(false)}
        }}catch(_:java.util.concurrent.RejectedExecutionException){run.probing.set(false);return false}
        return true
    }

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
        run.warm.close()
        run.probes.snapshotEntries().forEach(Attempt::close)
        run.sessions.snapshotEntries().forEach(Session::close)
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
