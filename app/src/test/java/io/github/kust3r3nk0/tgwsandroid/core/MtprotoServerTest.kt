package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MtprotoServerTest {
    @Test fun slowAutomaticWssRoutesLeaveSetupBudgetForTcpInsteadOfExtendingDeadline() {
        val port=freePort();val events=Events();val workerCalls=AtomicInteger();val cfCalls=AtomicInteger()
        val tcp=Channel(TransportKind.TCP)
        val factory=object:TransportFactory {
            override fun openRelay(endpoint:RelayEndpoint,dc:Int,own:(Closeable)->Unit):RemoteTransport {
                workerCalls.incrementAndGet()
                val expired=CountDownLatch(1);own(Closeable{expired.countDown()})
                assertTrue(expired.await(2,TimeUnit.SECONDS))
                throw IOException("Controlled stalled Worker")
            }
            override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport {
                cfCalls.incrementAndGet();throw IOException("No budget for further WSS probes")
            }
            override fun openTcp(address:String,own:(Closeable)->Unit):RemoteTransport=tcp.also(own)
        }
        val settings=TransportSettings(mode=TransportMode.AUTO_CLOUDFLARE,
            workerUrls=listOf("wss://first.example/apiws","wss://second.example/apiws"),
            cloudflareDomains=listOf("first-cf.example","second-cf.example"))
        MtprotoServer(port,secretHex,events.all::add,transports=factory,
            limits=limits.copy(connectTimeoutMillis=1200,wssAttemptTimeoutMillis=500),transportSettings=settings).use{server->
            server.start();await("Listener not ready"){events.phase(ServerPhase.LISTENING)}
            connect(port).use{client->
                client.getOutputStream().write(init().bytes)
                await("Slow automatic routes starved TCP fallback"){events.sessions(1,0)}
                assertEquals(2,workerCalls.get());assertEquals(0,cfCalls.get())
                assertEquals(1,tcp.sent.size)
                assertTrue(events.issue(ServerIssue.TIMEOUT))
                assertFalse(events.all.filterIsInstance<ServerEvent.Closed>().any{it.session.reason==SessionEndReason.CONNECT_TIMEOUT})
                server.stop();assertClosed(client)
            }
        }
    }

    @Test fun overloadedWorkersAndTheirCooldownCannotBlockIndependentTcpFallback() {
        val port=freePort();val events=Events();val workerCalls=AtomicInteger()
        val tcp=CopyOnWriteArrayList<Channel>()
        val factory=object:TransportFactory {
            override fun openRelay(endpoint:RelayEndpoint,dc:Int,own:(Closeable)->Unit):RemoteTransport {
                workerCalls.incrementAndGet()
                throw WebSocketUpgradeException(HttpUpgradeDiagnostic(503,UpgradeFailure.STATUS,RelayUpgradeReason.CAPACITY,60_000))
            }
            override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport=error("Unexpected direct WSS")
            override fun openTcp(address:String,own:(Closeable)->Unit):RemoteTransport=Channel(TransportKind.TCP).also{tcp+=it;own(it)}
        }
        val settings=TransportSettings(mode=TransportMode.AUTO_CLOUDFLARE,
            workerUrls=listOf("wss://first.example/apiws","wss://second.example/apiws"))
        MtprotoServer(port,secretHex,events.all::add,transports=factory,limits=limits,transportSettings=settings).use{server->
            server.start();await("Listener not ready"){events.phase(ServerPhase.LISTENING)}
            connect(port).use{first->
                first.getOutputStream().write(init(2).bytes)
                await("Both Worker failures prevented TCP fallback"){events.sessions(1,0)}
                connect(port).use{second->
                    second.getOutputStream().write(init(4).bytes)
                    // A 60-second endpoint cooldown is skipped, never slept through.
                    await("Worker BACKOFF blocked an independent DC/TCP route"){events.sessions(2,0)}
                    assertEquals(2,workerCalls.get());assertEquals(2,tcp.size)
                    assertTrue(tcp.all{it.sent.size==1})
                    val routes=events.all.filterIsInstance<ServerEvent.Routes>().last().routes
                    assertEquals(2,routes.count{it.kind==TransportKind.TCP && it.status==RouteStatus.AVAILABLE})
                    assertEquals(2,routes.count{it.kind==TransportKind.WORKER && it.status==RouteStatus.BACKOFF})
                    assertFalse(events.issue(ServerIssue.RETRY_LATER))
                    server.stop();assertClosed(first);assertClosed(second)
                }
            }
            assertTrue(tcp.all{it.closed.count==0L})
        }
    }

    @Test fun busyNewSessionCannotMarkExistingRelaySessionUnavailable() {
        val port=freePort();val events=Events();val calls=AtomicInteger();val healthy=Channel(TransportKind.RELAY)
        val factory=object:TransportFactory {
            override fun openRelay(endpoint:RelayEndpoint,dc:Int,own:(Closeable)->Unit):RemoteTransport {
                if(calls.incrementAndGet()==1)return healthy.also(own)
                throw WebSocketUpgradeException(HttpUpgradeDiagnostic(503,UpgradeFailure.STATUS,RelayUpgradeReason.CAPACITY,2000))
            }
            override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport=error("Unexpected WSS")
            override fun openTcp(address:String,own:(Closeable)->Unit):RemoteTransport=error("Unexpected TCP")
        }
        MtprotoServer(port,secretHex,events.all::add,transports=factory,limits=limits,
            relayEndpoint=RelayEndpoint.parse("wss://relay.example/apiws")).use{server->
            server.start();await("Listener not ready"){events.phase(ServerPhase.LISTENING)}
            connect(port).use{first->
                first.getOutputStream().write(init().bytes);await("First session not ready"){events.sessions(1,0)}
                val available=events.all.filterIsInstance<ServerEvent.Routes>().last().routes.single()
                connect(port).use{second->second.getOutputStream().write(init().bytes);assertClosed(second)}
                await("Pending failure leaked"){events.sessions(1,0) && events.all.any{it is ServerEvent.Closed}}
                val route=events.all.filterIsInstance<ServerEvent.Routes>().last().routes.single()
                assertEquals(available,route)
                assertEquals(RelayUpgradeReason.CAPACITY,events.all.filterIsInstance<ServerEvent.Closed>().single().session.upgrade?.relayReason)
            }
        }
    }
    @Test fun earlyWorkerTcpFailuresCannotRedialUntilRouteCooldown() {
        val port=freePort();val events=Events();val calls=AtomicInteger()
        val factory=object:TransportFactory {
            override fun openRelay(endpoint:RelayEndpoint,dc:Int,own:(Closeable)->Unit):RemoteTransport {
                calls.incrementAndGet()
                return object:RemoteTransport {
                    override val kind=TransportKind.RELAY
                    override fun send(data:ByteArray){}
                    override fun receive():ByteArray?=throw WebSocketPeerCloseException(1011,WebSocketCloseCategory.TCP_ERROR)
                    override fun close(){}
                }.also(own)
            }
            override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport=error("Unexpected WSS")
            override fun openTcp(address:String,own:(Closeable)->Unit):RemoteTransport=error("Unexpected TCP")
        }
        MtprotoServer(port,secretHex,events.all::add,transports=factory,limits=limits,
            relayEndpoint=RelayEndpoint.parse("wss://relay.example/apiws")).use{server->
            server.start();await("Listener not ready"){events.phase(ServerPhase.LISTENING)}
            repeat(3){i->
                connect(port).use{client->client.getOutputStream().write(init().bytes);assertClosed(client)}
                await("Close not reported"){events.all.filterIsInstance<ServerEvent.Closed>().size==i+1}
            }
            assertEquals(1,calls.get())
            val endings=events.all.filterIsInstance<ServerEvent.Closed>().map{it.session}
            assertEquals(SessionEndReason.REMOTE_CLOSE,endings.first().reason)
            assertEquals(WebSocketCloseCategory.TCP_ERROR,endings.first().peerClose?.category)
            assertTrue(endings.drop(1).all{it.reason==SessionEndReason.RETRY_BACKOFF})
        }
    }
    @Test fun workerOverloadCooldownIsSharedByDcAndRetainsUpgradeCause() {
        val port=freePort();val events=Events();val calls=AtomicInteger()
        val factory=object:TransportFactory {
            override fun openRelay(endpoint:RelayEndpoint,dc:Int,own:(Closeable)->Unit):RemoteTransport {
                calls.incrementAndGet()
                throw WebSocketUpgradeException(HttpUpgradeDiagnostic(503,UpgradeFailure.STATUS,RelayUpgradeReason.CAPACITY,2000))
            }
            override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport=error("Unexpected direct WSS")
            override fun openTcp(address:String,own:(Closeable)->Unit):RemoteTransport=error("Unexpected direct TCP")
        }
        MtprotoServer(port,secretHex,events.all::add,transports=factory,limits=limits,
            relayEndpoint=RelayEndpoint.parse("wss://relay.example/private-token/apiws")).use{server->
            server.start();await("Listener not ready"){events.phase(ServerPhase.LISTENING)}
            for(dc in listOf(2,4,5)){
                connect(port).use{client->client.getOutputStream().write(init(dc).bytes);assertClosed(client)}
                await("Pending session leaked"){events.sessions(0,0)}
            }
            assertEquals(1,calls.get())
            val closed=events.all.filterIsInstance<ServerEvent.Closed>().map{it.session}
            assertEquals(3,closed.size);assertEquals(3,closed.map{it.id}.distinct().size)
            assertEquals(SessionEndReason.SETUP_FAILED,closed.first().reason)
            assertEquals(503,closed.first().upgrade?.status)
            assertEquals(RelayUpgradeReason.CAPACITY,closed.first().upgrade?.relayReason)
            assertEquals(listOf(SessionEndReason.RETRY_BACKOFF,SessionEndReason.RETRY_BACKOFF),closed.drop(1).map{it.reason})
            val routes=events.all.filterIsInstance<ServerEvent.Routes>().last().routes
            assertEquals(listOf(RouteStatus.UNAVAILABLE,RouteStatus.BACKOFF,RouteStatus.BACKOFF),routes.map{it.status})
            assertTrue(routes.all{it.retryInMillis?.let{delay->delay in 1L..2000L}==true})
            assertFalse(events.all.toString().contains("private-token"))
            server.disconnectSessions()
            connect(port).use{client->client.getOutputStream().write(init().bytes);assertClosed(client)}
            assertEquals(2,calls.get())
        }
    }

    @Test fun ordinaryClientEofUsesGracefulTransportCloseBeforeRawSocketAbort() {
        val port=freePort();val events=Events();val order=CopyOnWriteArrayList<String>()
        val done=CountDownLatch(1)
        val factory=object:TransportFactory {
            override fun openRelay(endpoint:RelayEndpoint,dc:Int,own:(Closeable)->Unit):RemoteTransport {
                own(Closeable{order+="raw"})
                return object:RemoteTransport {
                    override val kind=TransportKind.RELAY
                    override fun send(data:ByteArray){}
                    override fun receive():ByteArray?{done.await();return null}
                    override fun close(){order+="close";done.countDown()}
                    override fun abort(){order+="abort";done.countDown()}
                }.also(own)
            }
            override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport=error("Unexpected WSS")
            override fun openTcp(address:String,own:(Closeable)->Unit):RemoteTransport=error("Unexpected TCP")
        }
        MtprotoServer(port,secretHex,events.all::add,transports=factory,limits=limits,
            relayEndpoint=RelayEndpoint.parse("wss://relay.example/apiws")).use{server->
            server.start();await("Listener not ready"){events.phase(ServerPhase.LISTENING)}
            connect(port).use{client->
                client.getOutputStream().write(init().bytes);await("Session not established"){events.sessions(1,0)}
                client.shutdownOutput();assertClosed(client)
            }
            await("Session close not reported"){events.all.filterIsInstance<ServerEvent.Closed>().size==1}
            // Closed records the first cause before cleanup. Counts are emitted
            // after every resource has been released; EOF alone can precede it.
            await("Session resources not released"){events.sessions(0,0)}
            assertEquals("close",order.first());assertTrue(order.indexOf("close")<order.indexOf("raw"))
            assertEquals(SessionEndReason.CLIENT_EOF,events.all.filterIsInstance<ServerEvent.Closed>().single().session.reason)
        }
    }

    @Test fun closingOneRelaySessionDoesNotHideAnotherLiveSessionOrDuplicateCloseEvents() {
        val port=freePort();val events=Events();val channels=CopyOnWriteArrayList<CountDownLatch>()
        val factory=object:TransportFactory {
            override fun openRelay(endpoint:RelayEndpoint,dc:Int,own:(Closeable)->Unit):RemoteTransport {
                val stop=CountDownLatch(1);channels+=stop
                return object:RemoteTransport {
                    override val kind=TransportKind.RELAY
                    override fun send(data:ByteArray){}
                    override fun receive():ByteArray?{stop.await();throw WebSocketPeerCloseException(1008,WebSocketCloseCategory.BYTE_LIMIT)}
                    override fun close(){stop.countDown()}
                }.also(own)
            }
            override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport=error("Unexpected WSS")
            override fun openTcp(address:String,own:(Closeable)->Unit):RemoteTransport=error("Unexpected TCP")
        }
        MtprotoServer(port,secretHex,events.all::add,transports=factory,limits=limits,
            relayEndpoint=RelayEndpoint.parse("wss://relay.example/apiws")).use{server->
            server.start();await("Listener not ready"){events.phase(ServerPhase.LISTENING)}
            connect(port).use{first->
                first.getOutputStream().write(init().bytes);await("First session not ready"){events.sessions(1,0)}
                connect(port).use{second->
                second.getOutputStream().write(init().bytes);await("Second session not ready"){events.sessions(2,0)}
                channels.first().countDown();assertClosed(first)
                await("Session close not reported"){events.all.filterIsInstance<ServerEvent.Closed>().size==1}
                val route=events.all.filterIsInstance<ServerEvent.Routes>().last().routes.single()
                assertEquals(RouteStatus.AVAILABLE,route.status)
                val closed=events.all.filterIsInstance<ServerEvent.Closed>().single().session
                assertEquals(WebSocketClose(1008,WebSocketCloseCategory.BYTE_LIMIT),closed.peerClose)
                assertEquals(SessionEndReason.REMOTE_CLOSE,closed.reason)
                server.disconnectSessions();assertClosed(second)
                await("Network close missing"){events.all.filterIsInstance<ServerEvent.Closed>().size==2}
                assertEquals(SessionEndReason.NETWORK_CHANGED,events.all.filterIsInstance<ServerEvent.Closed>().last().session.reason)
            }}
        }
    }
    private val secretHex = "00112233445566778899aabbccddeeff"
    private val secret = secretHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val limits = ServerLimits(handshakeTimeoutMillis = 5_000, connectTimeoutMillis = 5_000, idleTimeoutMillis = 5_000)

    private class Events {
        val all = CopyOnWriteArrayList<ServerEvent>()
        fun phase(phase: ServerPhase) = all.any { it == ServerEvent.State(phase) }
        fun sessions(established: Int, pending: Int): Boolean {
            val event = all.lastOrNull { it is ServerEvent.Sessions } as? ServerEvent.Sessions
            return event?.established == established && event.pending == pending
        }
        fun issue(issue: ServerIssue) = all.filterIsInstance<ServerEvent.Diagnostic>().any { it.issue == issue }
    }

    private class Channel(override val kind: TransportKind, private val failSend: Boolean = false, private val failProbe: Boolean = false) : RemoteTransport {
        val sent = CopyOnWriteArrayList<ByteArray>()
        val closed = CountDownLatch(1)
        val probes = AtomicInteger()
        override fun verifyIdle() {
            probes.incrementAndGet()
            if (failProbe) throw EOFException("Deliberate stale pool entry")
        }
        override fun send(data: ByteArray) {
            sent += data.copyOf()
            if (failSend) throw IOException("Deliberate relay failure")
        }
        override fun receive(): ByteArray? {
            if (!closed.await(3, TimeUnit.SECONDS)) throw IOException("Test receive deadline")
            return null
        }
        override fun close() { closed.countDown() }
    }

    private class FakeFactory(
        private val failWss: Boolean = true,
        private val failTcp: Boolean = false,
        private val failWssRelay: Boolean = false,
    ) : TransportFactory {
        val wss = CopyOnWriteArrayList<Pair<String, String>>()
        val tcp = CopyOnWriteArrayList<String>()
        val channels = CopyOnWriteArrayList<Channel>()
        override fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport {
            wss += address to domain
            if (failWss) throw IOException("Deliberate WSS failure")
            return Channel(TransportKind.WSS, failWssRelay).also { channels += it; own(it) }
        }
        override fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport {
            tcp += address
            if (failTcp) throw IOException("Deliberate TCP failure")
            return Channel(TransportKind.TCP).also { channels += it; own(it) }
        }
    }

    private data class Init(val bytes: ByteArray, val encrypt: Cipher)
    private fun init(dc: Int = 2): Init {
        val plain = ByteArray(64) { (it + 1).toByte() }
        for (i in 56..59) plain[i] = 0xee.toByte()
        plain[60] = dc.toByte()
        plain[61] = (dc shr 8).toByte()
        val key = MessageDigest.getInstance("SHA-256").digest(plain.copyOfRange(8, 40) + secret)
        val cipher = ctr(key, plain.copyOfRange(40, 56))
        val encrypted = cipher.update(plain)
        val header = plain.copyOf().apply { encrypted.copyInto(this, 56, 56, 64) }
        return Init(header, cipher)
    }

    private fun ctr(key: ByteArray, iv: ByteArray) = Cipher.getInstance("AES/CTR/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    }

    private fun freePort() = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    private fun connect(port: Int) = Socket().apply {
        connect(InetSocketAddress("127.0.0.1", port), 1_000)
        soTimeout = 2_000
    }
    private fun await(message: String, condition: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition()) {
            if (System.nanoTime() >= until) fail(message)
            Thread.sleep(5)
        }
    }
    private fun assertClosed(socket: Socket) {
        try { assertEquals(-1, socket.getInputStream().read()) }
        catch (_: java.net.SocketException) { /* Reset also terminates the session predictably. */ }
    }
    private fun assertPortReleased(port: Int) {
        assertThrows(IOException::class.java) { connect(port).use { } }
        ServerSocket().use { probe ->
            // Accepted sockets can leave TIME_WAIT after an active close. Match production's
            // reuse policy; a leaked listening socket still makes this bind fail.
            probe.reuseAddress = true
            probe.bind(InetSocketAddress("127.0.0.1", port))
        }
    }
    private fun server(port: Int, events: Events, factory: TransportFactory = FakeFactory(), customLimits: ServerLimits = limits) =
        MtprotoServer(port, secretHex, events.all::add, transports = factory, limits = customLimits)

    @Test fun warmupFailureIsDiagnosedWithoutDeclaringHealthyClientSessionFailed() {
        val events = Events()
        val calls = AtomicInteger()
        val channel = Channel(TransportKind.WSS)
        val factory = object : TransportFactory {
            override val supportsWarmWebSockets = true
            override fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport {
                if (calls.incrementAndGet() > 1) throw IOException("Deliberate background setup failure")
                own(channel)
                return channel
            }
            override fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport = throw AssertionError("Unexpected TCP")
        }
        val port = freePort()
        server(port, events, factory).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                client.getOutputStream().write(init().bytes)
                await("Missing background diagnostic") { events.all.any { it is ServerEvent.BackgroundIssue } }
                assertTrue(events.sessions(1, 0))
                assertFalse(events.issue(ServerIssue.WSS_HANDSHAKE))
                assertEquals(1L, channel.closed.count)
                server.stop()
                assertClosed(client)
            }
        }
    }

    @Test fun freshPooledSocketGetsOneClientsInitAndCipherStateAndStaleProbeRetriesFresh() {
        for (stale in listOf(false, true)) {
            val channels = CopyOnWriteArrayList<Channel>()
            val factory = object : TransportFactory {
                override val supportsWarmWebSockets = true
                override fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport =
                    Channel(TransportKind.WSS, failProbe = stale && channels.size == 1).also { channels += it; own(it) }
                override fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport = throw AssertionError("Unexpected TCP")
            }
            val port = freePort()
            val events = Events()
            server(port, events, factory).use { server ->
                server.start()
                await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
                connect(port).use { first ->
                    val hello = init()
                    val packet = byteArrayOf(4, 0, 0, 0, 11, 12, 13, 14)
                    first.getOutputStream().write(hello.bytes + hello.encrypt.update(packet))
                    await("No warm connection prepared") { server.warmConnections() == (1 to 0) && channels.first().sent.size == 2 }
                    assertTrue(channels[1].sent.isEmpty())
                    connect(port).use { second ->
                        val next = init()
                        second.getOutputStream().write(next.bytes + next.encrypt.update(packet))
                        val used = if (stale) 2 else 1
                        await("Warm connection did not forward") { channels.size > used && channels[used].sent.size == 2 }
                        assertEquals(1, channels[1].probes.get())
                        for (index in listOf(0, used)) {
                            val sent = channels[index].sent
                            val expected = MtprotoCrypto.buildContext(MtprotoCrypto.parseClientInit(next.bytes, secret)!!, secret, sent[0]).toTelegram.update(packet)
                            assertArrayEquals(expected, sent[1])
                        }
                        if (stale) {
                            assertTrue(channels[1].sent.isEmpty())
                            assertEquals(0L, channels[1].closed.count)
                        }
                        server.stop()
                        assertClosed(first); assertClosed(second)
                        assertTrue(channels.all { it.closed.count == 0L })
                    }
                }
            }
        }
    }

    @Test fun repeatedFailedClientsDoNotRedialCoolingRoutesAndNetworkResetAllowsRetry() {
        val port = freePort()
        val events = Events()
        val factory = FakeFactory(failTcp = true)
        server(port, events, factory).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            repeat(3) {
                connect(port).use { client -> client.getOutputStream().write(init().bytes); assertClosed(client) }
                await("Pending session leaked") { events.sessions(0, 0) }
            }
            assertEquals(1, factory.wss.size)
            assertEquals(1, factory.tcp.size)
            assertTrue(events.issue(ServerIssue.RETRY_LATER))
            server.disconnectSessions()
            connect(port).use { client -> client.getOutputStream().write(init().bytes); assertClosed(client) }
            assertEquals(2, factory.wss.size)
            assertEquals(2, factory.tcp.size)
        }
    }

    @Test fun relayOnlyCarriesIdenticalCryptoForAllDcAndMediaWithoutDirectAttempts() {
        for (dc in listOf(1, 2, 3, 4, 5, 203, -2, -4)) {
            val port = freePort()
            val events = Events()
            val channel = Channel(TransportKind.RELAY)
            val calls = CopyOnWriteArrayList<String>()
            val factory = object : TransportFactory {
                override fun openRelay(endpoint: RelayEndpoint, dc: Int, own: (Closeable) -> Unit): RemoteTransport {
                    calls += endpoint.requestPath(dc)
                    own(channel)
                    return channel
                }
                override fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport =
                    throw AssertionError("Direct WSS must not run in relay mode")
                override fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport =
                    throw AssertionError("Direct TCP must not run in relay mode")
            }
            MtprotoServer(port, secretHex, events.all::add, transports = factory, limits = limits,
                relayEndpoint = RelayEndpoint.parse("wss://relay.example/test-key/apiws")).use { server ->
                server.start()
                await("Relay listener not ready") { events.phase(ServerPhase.LISTENING) }
                connect(port).use { client ->
                    val hello = init(dc)
                    val packet = byteArrayOf(4, 0, 0, 0, 21, 22, 23, 24)
                    client.getOutputStream().write(hello.bytes + hello.encrypt.update(packet))
                    await("Relay packet missing") { channel.sent.size == 2 }
                    assertEquals(listOf("/test-key/apiws?dst=${TelegramRoutes.ip(kotlin.math.abs(dc))}&dc=${kotlin.math.abs(dc)}"), calls.toList())
                    val handshake = MtprotoCrypto.parseClientInit(hello.bytes, secret)!!
                    val expected = MtprotoCrypto.buildContext(handshake, secret, channel.sent[0]).toTelegram.update(packet)
                    assertArrayEquals(expected, channel.sent[1])
                    assertEquals(ServerEvent.Sessions(1, 0, relay = 1), events.all.last { it is ServerEvent.Sessions })
                    server.stop()
                    assertClosed(client)
                    assertEquals(0L, channel.closed.count)
                }
            }
        }
    }

    @Test fun stalledRelayDeadlineClosesResourcesWithoutDirectFallback() {
        val port = freePort()
        val events = Events()
        val channel = Channel(TransportKind.RELAY)
        val factory = object : TransportFactory {
            override fun openRelay(endpoint: RelayEndpoint, dc: Int, own: (Closeable) -> Unit): RemoteTransport {
                own(channel)
                assertTrue(channel.closed.await(2, TimeUnit.SECONDS))
                return channel
            }
            override fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport =
                throw AssertionError("Unexpected direct WSS")
            override fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport =
                throw AssertionError("Unexpected direct TCP")
        }
        MtprotoServer(port, secretHex, events.all::add, transports = factory,
            limits = limits.copy(wssAttemptTimeoutMillis = 100),
            relayEndpoint = RelayEndpoint.parse("wss://relay.example")).use { server ->
            server.start()
            await("Relay listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                client.getOutputStream().write(init().bytes)
                assertClosed(client)
                await("Missing relay deadline") { events.issue(ServerIssue.TIMEOUT) && events.sessions(0, 0) }
                assertTrue(events.all.filterIsInstance<ServerEvent.Routes>().any{event->
                    event.routes.any{it.kind==TransportKind.RELAY && it.issue==ServerIssue.TIMEOUT}})
                assertTrue(channel.sent.isEmpty())
            }
        }
    }

    @Test fun repeatedStartStopAndRestartReleaseListener() {
        val port = freePort()
        val events = Events()
        val server = server(port, events)
        server.use {
            server.start()
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            assertEquals(1, events.all.count { it == ServerEvent.State(ServerPhase.LISTENING) })
            connect(port).use { client ->
                await("Accepted client missing") { events.sessions(0, 1) }
                server.stop()
                assertClosed(client)
            }
            assertPortReleased(port)
            assertTrue(events.phase(ServerPhase.STOPPED))
            server.start()
            await("Restart listener not ready") { events.all.count { it == ServerEvent.State(ServerPhase.LISTENING) } == 2 }
            server.stop()
        }
        assertPortReleased(port)
    }

    @Test fun occupiedPortIsFatalAndNeverReportsListening() {
        val events = Events()
        val fatal = AtomicInteger()
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { occupied ->
            MtprotoServer(occupied.localPort, secretHex, events.all::add, { fatal.incrementAndGet() }).use { server ->
                server.start()
                await("Missing bind failure") { events.phase(ServerPhase.ERROR) && fatal.get() == 1 }
                assertTrue(events.issue(ServerIssue.PORT))
                assertFalse(events.phase(ServerPhase.LISTENING))
                server.stop()
            }
        }
    }

    @Test fun invalidConfigurationFailsBeforeCreatingListener() {
        for (port in listOf(0, -1, 65_536)) {
            assertThrows(IllegalArgumentException::class.java) { MtprotoServer(port, secretHex, {}) }
        }
        for (invalid in listOf("", "0".repeat(31), "g".repeat(32), "0".repeat(34))) {
            assertThrows(IllegalArgumentException::class.java) { MtprotoServer(1443, invalid, {}) }
        }
    }

    @Test fun handshakeDeadlineReleasesSlowClient() {
        val port = freePort()
        val events = Events()
        server(port, events, customLimits = limits.copy(handshakeTimeoutMillis = 100)).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                client.getOutputStream().write(byteArrayOf(1))
                assertClosed(client)
                await("Handshake capacity leaked") { events.sessions(0, 0) }
                assertTrue(events.issue(ServerIssue.TIMEOUT))
                assertFalse(events.issue(ServerIssue.HANDSHAKE))
                assertFalse(events.issue(ServerIssue.SESSION))
            }
        }
    }

    @Test fun relayCloseRetainsSafeMetadataAndNormalCloseDoesNotBecomeSessionError() {
        for (code in listOf(1000, 1008, 1011)) {
            val port = freePort()
            val events = Events()
            val closePeer = CountDownLatch(1)
            val factory = object : TransportFactory {
                override fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport = error("Unexpected direct route")
                override fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport = error("Unexpected TCP fallback")
                override fun openRelay(endpoint: RelayEndpoint, dc: Int, own: (Closeable) -> Unit): RemoteTransport =
                    object : RemoteTransport {
                        override val kind = TransportKind.RELAY
                        override fun send(data: ByteArray) { assertEquals(64, data.size) }
                        override fun receive(): ByteArray? {
                            check(closePeer.await(3, TimeUnit.SECONDS))
                            throw WebSocketPeerCloseException(code, when(code){1000->WebSocketCloseCategory.TCP_EOF
                                1008->WebSocketCloseCategory.BYTE_LIMIT;else->WebSocketCloseCategory.TCP_ERROR})
                        }
                        override fun close() { closePeer.countDown() }
                    }.also(own)
            }
            MtprotoServer(port, secretHex, events.all::add, transports = factory, limits = limits,
                relayEndpoint = RelayEndpoint.parse("wss://relay.example/apiws")).use { server ->
                server.start()
                await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
                connect(port).use { client ->
                    client.getOutputStream().write(init().bytes)
                    await("Relay not established") { events.sessions(1, 0) }
                    closePeer.countDown()
                    assertClosed(client)
                    await("Closed relay retained session") { events.sessions(0, 0) }
                }
                val route = events.all.filterIsInstance<ServerEvent.Routes>().last().routes.single()
                assertEquals(code, route.closeCode)
                assertEquals(if (code == 1011) RouteStatus.UNAVAILABLE else RouteStatus.CLOSED, route.status)
                assertEquals(code == 1011, events.issue(ServerIssue.RELAY))
                assertFalse(events.issue(ServerIssue.SESSION))
            }
        }
    }

    @Test fun localClientCloseClearsLastReadyRouteWithoutInventingRemoteFailure() {
        val port = freePort()
        val events = Events()
        MtprotoServer(port, secretHex, events.all::add, transports = FakeFactory(), limits = limits).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                client.getOutputStream().write(init().bytes)
                await("Transport not established") { events.sessions(1, 0) }
            }
            await("Client close retained session") { events.sessions(0, 0) }
            val tcp = events.all.filterIsInstance<ServerEvent.Routes>().last().routes.single { it.kind == TransportKind.TCP }
            assertEquals(RouteStatus.CLOSED, tcp.status)
            assertNull(tcp.issue)
            assertFalse(events.issue(ServerIssue.SESSION))
        }
    }

    @Test fun emptyLocalProbesDoNotBecomeSessionErrorsOrWrongSecretRejections() {
        for (domain in listOf("", "example.com")) {
            val port = freePort()
            val events = Events()
            val factory = FakeFactory()
            MtprotoServer(port, secretHex, events.all::add, transports = factory,
                limits = limits, fakeTlsDomain = domain).use { server ->
                server.start()
                await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
                repeat(16) {
                    connect(port).use { client ->
                        await("Probe was not accepted") { events.sessions(0, 1) }
                        client.shutdownOutput()
                        assertClosed(client)
                        await("Probe retained capacity") { events.sessions(0, 0) }
                    }
                }
                assertTrue(events.all.filterIsInstance<ServerEvent.Diagnostic>().isEmpty())
                assertTrue(factory.wss.isEmpty())
                assertTrue(factory.tcp.isEmpty())
                assertFalse(events.phase(ServerPhase.ERROR))
                assertFalse(events.phase(ServerPhase.STOPPED))
            }
            assertPortReleased(port)
        }
    }

    @Test fun truncatedPlainAndFakeTlsHandshakesAreRejectedAsIncompleteWithoutRemoteDial() {
        for (domain in listOf("", "example.com")) {
            val port = freePort()
            val events = Events()
            val factory = FakeFactory()
            MtprotoServer(port, secretHex, events.all::add, transports = factory,
                limits = limits, fakeTlsDomain = domain).use { server ->
                server.start()
                await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
                connect(port).use { client ->
                    client.getOutputStream().write(byteArrayOf(0x16, 3))
                    client.shutdownOutput()
                    assertClosed(client)
                    await("Truncated handshake retained capacity") { events.sessions(0, 0) }
                }
                val failures = events.all.filterIsInstance<ServerEvent.Diagnostic>()
                assertEquals(listOf(ServerEvent.Diagnostic(ServerIssue.HANDSHAKE, HandshakeRejection.INVALID_LENGTH)), failures)
                assertTrue(factory.wss.isEmpty())
                assertTrue(factory.tcp.isEmpty())
                assertFalse(events.phase(ServerPhase.ERROR))
            }
        }
    }

    @Test fun fakeTlsHelloDeadlineIsTimeoutInsteadOfWrongSecret() {
        val port = freePort()
        val events = Events()
        val factory = FakeFactory()
        MtprotoServer(port, secretHex, events.all::add, transports = factory,
            limits = limits.copy(handshakeTimeoutMillis = 100), fakeTlsDomain = "example.com").use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                // A valid-sized but incomplete record must wait only for the original handshake budget.
                client.getOutputStream().write(byteArrayOf(0x16, 3, 3, 0, 38, 1))
                assertClosed(client)
                await("Fake TLS deadline retained capacity") { events.sessions(0, 0) }
            }
            assertTrue(events.issue(ServerIssue.TIMEOUT))
            assertFalse(events.issue(ServerIssue.HANDSHAKE))
            assertFalse(events.issue(ServerIssue.SESSION))
            assertTrue(factory.wss.isEmpty())
            assertTrue(factory.tcp.isEmpty())
        }
    }

    @Test fun malformedHandshakeIsRejectedWithoutRemoteConnection() {
        val port = freePort()
        val events = Events()
        val factory = FakeFactory()
        server(port, events, factory).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                client.getOutputStream().write(ByteArray(64))
                assertClosed(client)
                await("Missing handshake diagnostic") { events.issue(ServerIssue.HANDSHAKE) }
            }
            assertTrue(factory.wss.isEmpty())
            assertTrue(factory.tcp.isEmpty())
        }
    }

    @Test fun capacityIncludesPendingHandshakesAndRejectsExcessClients() {
        val port = freePort()
        val events = Events()
        server(port, events, customLimits = limits.copy(maxSessions = 2)).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { first ->
                connect(port).use { second ->
                    await("Pending capacity missing") { events.sessions(0, 2) }
                    connect(port).use(::assertClosed)
                    // EOF becomes visible before the listener publishes its asynchronous diagnostic.
                    await("Capacity rejection not diagnosed") { events.issue(ServerIssue.CAPACITY) }
                    server.stop()
                    assertClosed(first)
                    assertClosed(second)
                }
            }
            assertTrue(events.all.filterIsInstance<ServerEvent.Sessions>().all { it.pending + it.established <= 2 })
        }
    }

    @Test fun networkLossClosesSessionsButPreservesListener() {
        val port = freePort()
        val events = Events()
        server(port, events).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                await("Missing accepted client") { events.sessions(0, 1) }
                server.disconnectSessions()
                assertClosed(client)
                await("Sessions not reset") { events.sessions(0, 0) }
            }
            connect(port).use { client ->
                await("Listener did not survive network loss") { events.sessions(0, 1) }
                server.stop()
                assertClosed(client)
            }
        }
    }

    @Test fun unavailableWssFallsBackToTcpBeforeAdvancingPayloadCipher() {
        val port = freePort()
        val events = Events()
        val factory = FakeFactory()
        val hello = init()
        val packet = byteArrayOf(4, 0, 0, 0, 11, 12, 13, 14)
        server(port, events, factory).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                client.getOutputStream().write(hello.bytes + hello.encrypt.update(packet))
                await("Fallback did not forward packet") { factory.channels.firstOrNull()?.sent?.size == 2 }
                val sent = factory.channels.single().sent
                val handshake = MtprotoCrypto.parseClientInit(hello.bytes, secret)!!
                val expected = MtprotoCrypto.buildContext(handshake, secret, sent[0]).toTelegram.update(packet)
                assertArrayEquals(expected, sent[1])
                assertEquals(listOf("149.154.167.220" to "kws2.web.telegram.org"), factory.wss.toList())
                assertEquals(listOf("149.154.167.51"), factory.tcp.toList())
                assertTrue(events.sessions(1, 0))
                assertEquals(ServerEvent.Sessions(1, 0, tcp = 1), events.all.last { it is ServerEvent.Sessions })
                server.stop()
                assertClosed(client)
                assertEquals(0L, factory.channels.single().closed.count)
            }
        }
    }

    @Test fun failedMediaWssRelayRetriesSameRelayThenTcpWithoutConsumingClientBytes() {
        val port = freePort()
        val events = Events()
        val factory = FakeFactory(failWss = false, failWssRelay = true)
        val hello = init(-4)
        val packet = byteArrayOf(4, 0, 0, 0, 21, 22, 23, 24)
        server(port, events, factory).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                client.getOutputStream().write(hello.bytes + hello.encrypt.update(packet))
                await("TCP did not forward media packet") { factory.channels.lastOrNull()?.kind == TransportKind.TCP && factory.channels.last().sent.size == 2 }
                assertEquals(listOf("kws4-1.web.telegram.org", "kws4.web.telegram.org"), factory.wss.map { it.second })
                val relay = factory.channels.last().sent[0]
                factory.channels.take(2).forEach { channel ->
                    assertArrayEquals(relay, channel.sent.single())
                    assertEquals(0L, channel.closed.count)
                }
                val expected = MtprotoCrypto.buildContext(MtprotoCrypto.parseClientInit(hello.bytes, secret)!!, secret, relay).toTelegram.update(packet)
                assertArrayEquals(expected, factory.channels.last().sent[1])
            }
        }
    }

    @Test fun bothTransportsFailWithoutRetryLoopOrSessionLeak() {
        val port = freePort()
        val events = Events()
        val factory = FakeFactory(failTcp = true)
        server(port, events, factory).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                client.getOutputStream().write(init().bytes)
                assertClosed(client)
                await("Failed session leaked") { events.sessions(0, 0) }
            }
            assertEquals(1, factory.wss.size)
            assertEquals(1, factory.tcp.size)
            assertTrue(events.issue(ServerIssue.WSS_HANDSHAKE))
            assertTrue(events.issue(ServerIssue.TCP))
        }
    }

    @Test fun malformedPacketIsRejectedOnTcpFallbackBeforeForwardingPayload() {
        val port = freePort()
        val events = Events()
        val factory = FakeFactory()
        val hello = init()
        server(port, events, factory).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                client.getOutputStream().write(hello.bytes + hello.encrypt.update(byteArrayOf(0, 0, 0, 0)))
                assertClosed(client)
                await("Missing invalid packet diagnostic") { events.issue(ServerIssue.SESSION) }
            }
            assertEquals(1, factory.channels.single().sent.size)
            // Local EOF/diagnostic can reach the peer before the session's finally block closes remote.
            assertTrue("Malformed session did not release its remote",factory.channels.single().closed.await(1,TimeUnit.SECONDS))
            assertEquals(0L, factory.channels.single().closed.count)
        }
    }

    @Test fun incompletePacketOnClientEofIsRejectedAndNotForwarded() {
        val port = freePort()
        val events = Events()
        val factory = FakeFactory()
        val hello = init()
        server(port, events, factory).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                client.getOutputStream().write(hello.bytes + hello.encrypt.update(byteArrayOf(4, 0, 0, 0, 1)))
                client.shutdownOutput()
                assertClosed(client)
                await("Truncated packet not diagnosed") { events.issue(ServerIssue.SESSION) }
            }
            assertEquals(1, factory.channels.single().sent.size)
        }
    }

    @Test fun establishedIdleSessionTimesOutAndReleasesBothDirections() {
        val port = freePort()
        val events = Events()
        val factory = FakeFactory()
        server(port, events, factory, limits.copy(idleTimeoutMillis = 100)).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                client.getOutputStream().write(init().bytes)
                assertClosed(client)
                await("Idle session leaked") { events.sessions(0, 0) }
            }
            assertTrue(events.issue(ServerIssue.TIMEOUT))
            assertEquals(0L, factory.channels.single().closed.count)
        }
    }

    @Test fun stopCancelsRemoteConnectBeforeItReturnsAndLateResourceOwnershipClosesImmediately() {
        val port = freePort()
        val events = Events()
        val started = CountDownLatch(1)
        val released = CountDownLatch(1)
        val lateClosed = CountDownLatch(1)
        val factory = object : TransportFactory {
            override fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport {
                own(Closeable { released.countDown() })
                val lateResource = Closeable { lateClosed.countDown() }
                started.countDown()
                try { released.await(2, TimeUnit.SECONDS) }
                finally { own(lateResource) }
                throw EOFException("Connect cancelled")
            }
            override fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport = throw AssertionError("Fallback after stop")
        }
        server(port, events, factory).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                client.getOutputStream().write(init().bytes)
                assertTrue(started.await(2, TimeUnit.SECONDS))
                server.stop()
                assertClosed(client)
                assertTrue(released.await(1, TimeUnit.SECONDS))
                assertTrue(lateClosed.await(1, TimeUnit.SECONDS))
            }
            assertPortReleased(port)
        }
    }

    @Test fun fallbackStreamsThroughLocalTcpPeerAndClosesRemoteSocketOnStop() {
        val port = freePort()
        val events = Events()
        val peerAccepted = CountDownLatch(1)
        val peerClosed = CountDownLatch(1)
        val received = CopyOnWriteArrayList<ByteArray>()
        val failures = CopyOnWriteArrayList<Throwable>()
        val hello = init()
        val request = byteArrayOf(4, 0, 0, 0, 31, 32, 33, 34)
        val response = byteArrayOf(4, 0, 0, 0, 41, 42, 43, 44)
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { peer ->
            val remoteThread = Thread {
                try {
                    peer.accept().use { remote ->
                        remote.soTimeout = 2_000
                        peerAccepted.countDown()
                        val relay = remote.getInputStream().readNBytes(64)
                        val encrypted = remote.getInputStream().readNBytes(request.size)
                        received += relay
                        received += encrypted
                        val backwards = relay.copyOfRange(8, 56).reversedArray()
                        remote.getOutputStream().write(ctr(backwards.copyOfRange(0, 32), backwards.copyOfRange(32, 48)).update(response))
                        remote.getOutputStream().flush()
                        assertEquals(-1, remote.getInputStream().read())
                        peerClosed.countDown()
                    }
                } catch (failure: Throwable) { failures += failure }
            }.apply { isDaemon = true; start() }
            val factory = object : TransportFactory {
                override fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport = throw IOException("Unavailable WSS")
                override fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport {
                    assertEquals("149.154.167.51", address)
                    val socket = Socket()
                    own(socket)
                    socket.connect(InetSocketAddress("127.0.0.1", peer.localPort), 1_000)
                    return object : RemoteTransport {
                        override val kind = TransportKind.TCP
                        override fun send(data: ByteArray) { socket.getOutputStream().write(data); socket.getOutputStream().flush() }
                        override fun receive(): ByteArray? {
                            val bytes = ByteArray(64)
                            val count = socket.getInputStream().read(bytes)
                            return if (count < 0) null else bytes.copyOf(count)
                        }
                        override fun close() = socket.close()
                    }
                }
            }
            server(port, events, factory).use { server ->
                server.start()
                await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
                connect(port).use { client ->
                    client.getOutputStream().write(hello.bytes + hello.encrypt.update(request))
                    assertTrue(peerAccepted.await(2, TimeUnit.SECONDS))
                    val encryptedResponse = client.getInputStream().readNBytes(response.size)
                    val material = hello.bytes.copyOfRange(8, 56).reversedArray()
                    val key = MessageDigest.getInstance("SHA-256").digest(material.copyOfRange(0, 32) + secret)
                    assertArrayEquals(response, ctr(key, material.copyOfRange(32, 48)).update(encryptedResponse))
                    assertEquals(2, received.size)
                    val expected = MtprotoCrypto.buildContext(MtprotoCrypto.parseClientInit(hello.bytes, secret)!!, secret, received[0]).toTelegram.update(request)
                    assertArrayEquals(expected, received[1])
                    server.stop()
                    assertTrue(peerClosed.await(2, TimeUnit.SECONDS))
                }
            }
            remoteThread.join(2_000)
            assertTrue("Local peer errors: $failures", failures.isEmpty())
        }
    }
    @Test fun successfulWssSplitsCoalescedAndFragmentedClientPacketsIntoSeparateMessages() {
        val port = freePort()
        val events = Events()
        val factory = FakeFactory(failWss = false)
        val hello = init()
        val first = byteArrayOf(4, 0, 0, 0, 1, 2, 3, 4)
        val second = byteArrayOf(4, 0, 0, 0, 5, 6, 7, 8)
        server(port, events, factory).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                client.getOutputStream().write(hello.bytes)
                await("WSS did not establish") { events.sessions(1, 0) }
                assertEquals(ServerEvent.Sessions(1, 0, wss = 1), events.all.last { it is ServerEvent.Sessions })
                val encrypted = hello.encrypt.update(first + second)
                client.getOutputStream().write(encrypted, 0, 3)
                // An incomplete length must not become a WS message.
                Thread.sleep(30)
                assertEquals(1, factory.channels.single().sent.size)
                client.getOutputStream().write(encrypted, 3, encrypted.size - 3)
                await("WSS did not split client packets") { factory.channels.single().sent.size == 3 }
                val sent = factory.channels.single().sent
                val context = MtprotoCrypto.buildContext(MtprotoCrypto.parseClientInit(hello.bytes, secret)!!, secret, sent[0])
                assertArrayEquals(context.toTelegram.update(first), sent[1])
                assertArrayEquals(context.toTelegram.update(second), sent[2])
                assertTrue(factory.tcp.isEmpty())
                factory.channels.single().close()
                assertClosed(client)
                await("Remote EOF leaked session") { events.sessions(0, 0) }
            }
        }
    }

    @Test fun slowWssAttemptReleasesResourcesAndStillReachesTcpFallback() {
        val port = freePort()
        val events = Events()
        val timedOut = CountDownLatch(1)
        val tcp = Channel(TransportKind.TCP)
        val factory = object : TransportFactory {
            override fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport {
                own(Closeable { timedOut.countDown() })
                assertTrue(timedOut.await(2, TimeUnit.SECONDS))
                throw IOException("Simulated slow WSS")
            }
            override fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport = tcp.also(own)
        }
        server(port, events, factory, limits.copy(wssAttemptTimeoutMillis = 80)).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                client.getOutputStream().write(init().bytes)
                await("Slow WSS prevented TCP fallback") { events.sessions(1, 0) }
                assertEquals(0L, timedOut.count)
                assertTrue(events.issue(ServerIssue.TIMEOUT))
                assertFalse(events.issue(ServerIssue.WSS_HANDSHAKE))
                assertEquals(1, tcp.sent.size)
                server.stop()
                assertClosed(client)
            }
        }
    }

    @Test fun stopAbortsBlockedTransportWriteWithoutWaitingForWriteLock() {
        val port = freePort()
        val events = Events()
        val writeStarted = CountDownLatch(1)
        val aborted = CountDownLatch(1)
        val writeExited = CountDownLatch(1)
        val transport = object : RemoteTransport {
            override val kind = TransportKind.TCP
            private var sends = 0
            override fun send(data: ByteArray) {
                if (++sends == 1) return
                writeStarted.countDown()
                try {
                    aborted.await(2, TimeUnit.SECONDS)
                    throw EOFException("Write aborted")
                } finally { writeExited.countDown() }
            }
            override fun receive(): ByteArray? { aborted.await(2, TimeUnit.SECONDS); return null }
            override fun close() { aborted.countDown() }
        }
        val factory = object : TransportFactory {
            override fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport = throw IOException("No WSS")
            override fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport = transport.also(own)
        }
        server(port, events, factory).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                val hello = init()
                client.getOutputStream().write(hello.bytes + hello.encrypt.update(byteArrayOf(4, 0, 0, 0, 1, 2, 3, 4)))
                assertTrue(writeStarted.await(2, TimeUnit.SECONDS))
                server.stop()
                assertTrue(writeExited.await(1, TimeUnit.SECONDS))
                assertClosed(client)
            }
            assertPortReleased(port)
        }
    }

    @Test fun blockedUpstreamWriteExpiresEvenWhileDownstreamKeepsSessionActive() {
        val port = freePort()
        val events = Events()
        val blocked = CountDownLatch(1)
        val aborted = CountDownLatch(1)
        val receivedChunks = AtomicInteger()
        val channel = object : RemoteTransport {
            override val kind = TransportKind.TCP
            var sends = 0
            override fun send(data: ByteArray) {
                if (++sends == 1) return
                blocked.countDown()
                aborted.await(2, TimeUnit.SECONDS)
                throw EOFException("Write deadline")
            }
            override fun receive(): ByteArray? {
                if (aborted.await(10, TimeUnit.MILLISECONDS)) return null
                receivedChunks.incrementAndGet()
                return byteArrayOf(1)
            }
            override fun close() { aborted.countDown() }
        }
        val factory = object : TransportFactory {
            override fun openWebSocket(address: String, domain: String, own: (Closeable) -> Unit): RemoteTransport = throw IOException("No WSS")
            override fun openTcp(address: String, own: (Closeable) -> Unit): RemoteTransport = channel.also(own)
        }
        server(port, events, factory, limits.copy(writeTimeoutMillis = 100)).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                val hello = init()
                client.getOutputStream().write(hello.bytes + hello.encrypt.update(byteArrayOf(4, 0, 0, 0, 1, 2, 3, 4)))
                assertTrue(blocked.await(2, TimeUnit.SECONDS))
                while (client.getInputStream().read() != -1) { /* Drain reverse traffic until write watchdog aborts. */ }
                assertTrue("No reverse activity exercised", receivedChunks.get() >= 2)
                assertTrue(events.issue(ServerIssue.TIMEOUT))
                await("Write deadline leaked session") { events.sessions(0, 0) }
            }
        }
    }

    @Test fun progressingMediaWritesRenewTheWriteDeadlineWithoutDisablingStallDetection() {
        val port=freePort()
        val events=Events()
        val closed=CountDownLatch(1)
        val delivered=CountDownLatch(1)
        val channel=object:RemoteTransport {
            override val kind=TransportKind.TCP
            override fun send(data:ByteArray) { send(data){} }
            override fun send(data:ByteArray,progress:()->Unit) {
                if(data.size==64)return
                var offset=0
                while(offset<data.size){
                    if(closed.await(35,TimeUnit.MILLISECONDS))throw EOFException("Canceled")
                    offset+=minOf(65536,data.size-offset)
                    progress()
                }
                delivered.countDown()
            }
            override fun receive():ByteArray?{closed.await(3,TimeUnit.SECONDS);return null}
            override fun close(){closed.countDown()}
        }
        val factory=object:TransportFactory {
            override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport=throw IOException("No WSS")
            override fun openTcp(address:String,own:(Closeable)->Unit)=channel.also(own)
        }
        server(port,events,factory,limits.copy(writeTimeoutMillis=100)).use { server ->
            server.start();await("Listener not ready"){events.phase(ServerPhase.LISTENING)}
            connect(port).use { client ->
                val hello=init()
                val length=512*1024
                val packet=byteArrayOf(length.toByte(),(length shr 8).toByte(),(length shr 16).toByte(),0)+ByteArray(length)
                client.getOutputStream().write(hello.bytes+hello.encrypt.update(packet))
                assertTrue("Active transfer was aborted",delivered.await(3,TimeUnit.SECONDS))
                assertFalse(events.issue(ServerIssue.TIMEOUT))
                assertTrue(events.sessions(1,0))
                client.soTimeout=50
                assertThrows(java.net.SocketTimeoutException::class.java){client.getInputStream().read()}
                server.stop();assertClosed(client)
            }
        }
    }

    @Test fun rapidStartStopCyclesDoNotLeaveListenersOrClientWorkers() {
        val port = freePort()
        val events = Events()
        server(port, events).use { server ->
            repeat(25) {
                server.start()
                server.start()
                server.stop()
            }
            val previousStarts = events.all.count { it == ServerEvent.State(ServerPhase.LISTENING) }
            server.start()
            await("Listener not ready after rapid cycles") {
                events.all.count { it == ServerEvent.State(ServerPhase.LISTENING) } == previousStarts + 1
            }
            connect(port).use { client ->
                await("Client not accepted after rapid cycles") { events.sessions(0, 1) }
                server.stop()
                assertClosed(client)
            }
        }
        assertPortReleased(port)
    }

    @Test fun dc203UsesExistingTcpBaselineAndNeverUsesTestDcOrUnconfiguredWss() {
        val port = freePort()
        val events = Events()
        val factory = FakeFactory()
        server(port, events, factory).use { server ->
            server.start()
            await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
            connect(port).use { client ->
                client.getOutputStream().write(init(203).bytes)
                await("DC203 did not establish") { events.sessions(1, 0) }
                assertTrue(factory.wss.isEmpty())
                assertEquals(listOf("91.105.192.100"), factory.tcp.toList())
                server.stop()
                assertClosed(client)
            }
        }
    }

    @Test fun stopWaitsForBlockedAcceptAndSimultaneousStopCallersReleaseSameListener() {
        repeat(20) {
            val port = freePort()
            val events = Events()
            server(port, events).use { server ->
                server.start()
                await("Listener not ready") { events.phase(ServerPhase.LISTENING) }
                val startStops = CountDownLatch(1)
                val failures = CopyOnWriteArrayList<Throwable>()
                val stops = List(2) {
                    Thread {
                        try {
                            startStops.await()
                            server.stop()
                            assertThrows(IOException::class.java) { connect(port).use { } }
                        } catch (failure: Throwable) { failures += failure }
                    }.apply { isDaemon = true; start() }
                }
                startStops.countDown()
                stops.forEach { it.join(2_000) }
                assertTrue("A stop caller did not finish", stops.none { it.isAlive })
                assertTrue("Concurrent stop errors: $failures", failures.isEmpty())
                assertTrue(events.phase(ServerPhase.STOPPED))
                assertPortReleased(port)
            }
        }
    }
}
