package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.PushbackInputStream
import java.net.InetAddress
import java.net.ProtocolException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory

class RawWebSocketTest {
    private val limits = RawWebSocket.Limits(readMillis = 750, handshakeMillis = 750, writeMillis = 750, closeMillis = 200)

    private fun slowOutputSocket(socket: Socket, throttled: () -> Boolean): Socket {
        val output=socket.getOutputStream()
        return object:Socket(){
            override fun getInputStream()=socket.getInputStream()
            override fun getOutputStream()=object:java.io.OutputStream(){
                override fun write(value:Int)=output.write(value)
                override fun write(bytes:ByteArray,offset:Int,length:Int){
                    if(throttled() && length>=1024)Thread.sleep(35)
                    output.write(bytes,offset,length)
                }
                override fun flush()=output.flush()
            }
            override fun setSoTimeout(timeout:Int){socket.soTimeout=timeout}
            override fun isClosed()=socket.isClosed
            override fun close()=socket.close()
        }
    }

    @Test(timeout=10000) fun pongCanInterleaveAProgressingFragmentedUpload(){
        val payload=ByteArray(256*1024){it.toByte()}
        peer(server={socket->
            val received=ByteArrayOutputStream()
            var fragments=0
            var pong=false
            do {
                val next=readClientFrame(socket.getInputStream(),allowFragments=true)
                if(next.opcode==10){assertArrayEquals(byteArrayOf(77),next.body);pong=true;continue}
                assertEquals(if(fragments++==0)2 else 0,next.opcode)
                received.write(next.body)
                if(fragments==1)socket.getOutputStream().write(frame(9,byteArrayOf(77)))
                if(next.fin){assertTrue("Control frame waited for the whole upload",pong);break}
            }while(true)
            assertArrayEquals(payload,received.toByteArray())
            socket.getOutputStream().write(frame(2,byteArrayOf(42)))
        }){socket->
            var throttled=false
            val ws=RawWebSocket.upgrade(slowOutputSocket(socket){throttled},"localhost",limits=limits.copy(writeMillis=400,readMillis=3000))
            throttled=true
            val failure=AtomicReference<Throwable?>()
            val writer=Thread{try{ws.send(payload)}catch(error:Throwable){failure.set(error)}}.apply{isDaemon=true;start()}
            try {
                assertArrayEquals(byteArrayOf(42),ws.receive())
                writer.join(1000)
                assertFalse(writer.isAlive)
                failure.get()?.let{throw AssertionError("Fragmented sender failed",it)}
            }finally{ws.abort();writer.join(1000)}
        }
    }

    @Test(timeout = 10000) fun progressingMediaUploadCanOutliveOneWriteWindow() {
        val payload = ByteArray(512 * 1024) { (it * 17).toByte() }
        peer(server = { socket ->
            val received = ByteArrayOutputStream()
            var fragments = 0
            do {
                val frame = readClientFrame(socket.getInputStream(), allowFragments = true)
                assertEquals(if (fragments++ == 0) 2 else 0, frame.opcode)
                assertTrue(frame.body.size <= 65536)
                received.write(frame.body)
            } while (!frame.fin)
            assertTrue(fragments > 1)
            assertArrayEquals(payload, received.toByteArray())
            socket.getOutputStream().write(frame(2, byteArrayOf(42)))
        }) { socket ->
            var throttled = false
            val slowSocket = slowOutputSocket(socket) { throttled }
            val ws = RawWebSocket.upgrade(slowSocket, "localhost", limits = limits.copy(writeMillis = 400, readMillis = 3000))
            throttled = true
            val started = System.nanoTime()
            ws.send(payload)
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) > 400)
            assertArrayEquals(byteArrayOf(42), ws.receive())
            ws.abort()
        }
    }

    @Test(timeout = 5000) fun idlePoolProbeRequiresMatchingPongAndDoesNotConsumeSubsequentPayload() {
        peer(server = { socket ->
            val ping = readClientFrame(socket.getInputStream())
            assertEquals(9, ping.opcode)
            socket.getOutputStream().write(frame(10, byteArrayOf(0)) + frame(9, byteArrayOf(5)))
            assertEquals(10, readClientFrame(socket.getInputStream()).opcode)
            socket.getOutputStream().write(frame(10, ping.body))
            val init = readClientFrame(socket.getInputStream())
            assertEquals(64, init.body.size)
            socket.getOutputStream().write(frame(2, byteArrayOf(42)))
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits)
            ws.probeIdle(500)
            ws.send(ByteArray(64))
            assertArrayEquals(byteArrayOf(42), ws.receive())
            ws.abort()
        }
    }

    @Test(timeout = 5000) fun unsolicitedBinaryDataCannotBeReusedAsAnotherClientsSession() {
        peer(server = { socket ->
            readClientFrame(socket.getInputStream())
            socket.getOutputStream().write(frame(2, byteArrayOf(42)))
            readClientFrame(socket.getInputStream())
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits)
            expect<ProtocolException> { ws.probeIdle(200) }
            assertTrue(socket.isClosed)
        }
    }

    @Test(timeout = 5000) fun idleProbeWrongPongsCannotExtendItsDeadline() {
        peer(server = { socket ->
            readClientFrame(socket.getInputStream())
            socket.getOutputStream().write(frame(10, byteArrayOf(0)))
            assertEquals(-1, socket.getInputStream().read())
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits)
            expect<SocketTimeoutException> { ws.probeIdle(100) }
            assertTrue(socket.isClosed)
        }
    }

    @Test(timeout = 5000) fun controlFramesKeepHealthyConnectionAliveBeyondOneReadWindow() {
        peer(server = { socket ->
            repeat(6) {
                Thread.sleep(40)
                socket.getOutputStream().write(frame(9, byteArrayOf(it.toByte())))
                val pong = readClientFrame(socket.getInputStream())
                assertEquals(10, pong.opcode)
            }
            socket.getOutputStream().write(frame(2, byteArrayOf(42)))
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits.copy(readMillis = 120))
            assertArrayEquals(byteArrayOf(42), ws.receive())
            ws.abort()
        }
    }

    @Test(timeout = 5000) fun idlePeerCanAnswerHeartbeatWithoutReconnecting() {
        peer(server = { socket ->
            repeat(3) {
                val ping = readClientFrame(socket.getInputStream())
                assertEquals(9, ping.opcode)
                assertEquals(8, ping.body.size)
                socket.getOutputStream().write(frame(10, ping.body))
            }
            socket.getOutputStream().write(frame(2, byteArrayOf(42)))
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits.copy(readMillis = 80))
            assertArrayEquals(byteArrayOf(42), ws.receive())
            assertFalse(socket.isClosed)
            ws.abort()
        }
    }

    @Test(timeout = 5000) fun wrongPongCannotKeepUnansweredHeartbeatAlive() {
        peer(server = { socket ->
            val ping = readClientFrame(socket.getInputStream())
            assertEquals(9, ping.opcode)
            socket.getOutputStream().write(frame(10, byteArrayOf(0)))
            assertEquals(-1, socket.getInputStream().read())
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits.copy(readMillis = 100))
            expect<SocketTimeoutException> { ws.receive() }
            assertTrue(socket.isClosed)
        }
    }

    @Test(timeout = 5000) fun controlTrafficCannotExtendFragmentedMessageDeadline() {
        peer(server = { socket ->
            socket.getOutputStream().write(frame(2, byteArrayOf(1), fin = false))
            val input = PushbackInputStream(socket.getInputStream())
            try {
                repeat(10) {
                    Thread.sleep(30)
                    socket.getOutputStream().write(frame(9, byteArrayOf(2)))
                    val first = input.read()
                    if (first < 0) return@peer
                    input.unread(first)
                    val answer = readClientFrame(input)
                    assertEquals(10, answer.opcode)
                }
                fail("Incomplete fragmented message did not expire")
            } catch (_: java.io.IOException) { /* Receiver must close before all pings. */ }
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits.copy(readMillis = 150))
            expect<SocketTimeoutException> { ws.receive() }
            assertTrue(socket.isClosed)
        }
    }

    @Test(timeout = 5000) fun acceptsUpgradeTokensAndMasksClientBinaryFrames() {
        peer(response = { key -> response(key).replace("Connection: Upgrade", "Connection: keep-alive, uPgRaDe") }, server = { socket ->
            val sent = readClientFrame(socket.getInputStream())
            assertEquals(2, sent.opcode)
            assertArrayEquals(byteArrayOf(1, 2, 3), sent.body)
            socket.getOutputStream().write(frame(2, byteArrayOf(4, 5)))
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits)
            ws.send(byteArrayOf(1, 2, 3))
            assertArrayEquals(byteArrayOf(4, 5), ws.receive())
            ws.abort()
        }
    }

    @Test(timeout = 5000) fun clientFramesUseCanonicalLengthAtAllBoundaries() {
        val sizes = listOf(0, 125, 126, 65535, 65536)
        peer(server = { socket ->
            for (size in sizes) {
                val sent = readClientFrame(socket.getInputStream())
                assertEquals(2, sent.opcode)
                assertEquals(size, sent.body.size)
                assertTrue(sent.body.all { it == 7.toByte() })
            }
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits)
            for (size in sizes) ws.send(ByteArray(size) { 7 })
            ws.abort()
        }
    }

    @Test(timeout = 5000) fun assemblesFragmentsAndHandlesInterleavedPingPong() {
        peer(server = { socket ->
            socket.getOutputStream().write(frame(2, byteArrayOf(1, 2), fin = false) + frame(9, byteArrayOf(7)) + frame(10, byteArrayOf(8)) + frame(0, byteArrayOf(3, 4)))
            val pong = readClientFrame(socket.getInputStream())
            assertEquals(10, pong.opcode)
            assertArrayEquals(byteArrayOf(7), pong.body)
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), ws.receive())
            ws.abort()
        }
    }

    @Test(timeout = 5000) fun preservesBoundariesForCoalescedAndSplitTcpFrames() {
        peer(server = { socket ->
            val bytes = frame(2, byteArrayOf(1, 2, 3)) + frame(2, byteArrayOf(4))
            for (byte in bytes) { socket.getOutputStream().write(byte.toInt()); socket.getOutputStream().flush() }
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits)
            assertArrayEquals(byteArrayOf(1, 2, 3), ws.receive())
            assertArrayEquals(byteArrayOf(4), ws.receive())
            ws.abort()
        }
    }

    @Test(timeout = 5000) fun decodesCanonicalExtendedServerLengths() {
        val sizes = listOf(126, 65536)
        peer(server = { socket ->
            for (size in sizes) socket.getOutputStream().write(frame(2, ByteArray(size) { 6 }))
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits)
            for (size in sizes) assertArrayEquals(ByteArray(size) { 6 }, ws.receive())
            ws.abort()
        }
    }

    @Test(timeout = 5000) fun acceptsMessageExactlyAtConfiguredBound() {
        peer(server = { socket -> socket.getOutputStream().write(frame(2, ByteArray(128) { 3 })) }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits.copy(messageBytes = 128))
            assertArrayEquals(ByteArray(128) { 3 }, ws.receive())
            ws.abort()
        }
    }

    @Test(timeout = 5000) fun repliesToServerCloseAndClosesSocket() {
        val body = byteArrayOf(3, -24) + "готово".toByteArray()
        peer(server = { socket ->
            socket.getOutputStream().write(frame(8, body))
            val reply = readClientFrame(socket.getInputStream())
            assertEquals(8, reply.opcode)
            assertArrayEquals(body, reply.body)
            assertEquals(-1, socket.getInputStream().read())
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits)
            assertNull(ws.receive())
            assertTrue(socket.isClosed)
            assertNull(ws.receive())
            ws.close()
        }
    }

    @Test(timeout = 5000) fun capturesOnlyAllowlistedWorkerCloseReasons(){
        val body=byteArrayOf(3,-16)+"BYTE_LIMIT".toByteArray(Charsets.UTF_8)
        peer(server={socket->
            socket.getOutputStream().write(frame(8,body))
            assertEquals(8,readClientFrame(socket.getInputStream()).opcode)
        }){socket->
            val ws=RawWebSocket.upgrade(socket,"localhost",limits=limits)
            val error=expect<WebSocketPeerCloseException>{ws.receive()}
            assertEquals(1008,error.statusCode)
            assertEquals(WebSocketCloseCategory.BYTE_LIMIT,error.category)
        }
        val untrusted=byteArrayOf(3,-16)+"private.example/token=secret".toByteArray(Charsets.UTF_8)
        peer(server={socket->
            socket.getOutputStream().write(frame(8,untrusted))
            assertEquals(8,readClientFrame(socket.getInputStream()).opcode)
        }){socket->
            val ws=RawWebSocket.upgrade(socket,"localhost",limits=limits)
            val error=expect<WebSocketPeerCloseException>{ws.receive()}
            assertNull(error.category)
            assertFalse(error.message.orEmpty().contains("private.example"))
        }
        val normal=byteArrayOf(3,-24)+"TCP_EOF".toByteArray(Charsets.UTF_8)
        peer(server={socket->
            socket.getOutputStream().write(frame(8,normal))
            assertEquals(8,readClientFrame(socket.getInputStream()).opcode)
        }){socket->
            val ws=RawWebSocket.upgrade(socket,"localhost",limits=limits)
            val closed=expect<WebSocketPeerCloseException>{ws.receive()}
            assertEquals(1000,closed.statusCode)
            assertEquals(WebSocketCloseCategory.TCP_EOF,closed.category)
        }
    }
    @Test(timeout = 5000) fun initiatesCloseAndWaitsForPeerReply() {
        peer(server = { socket ->
            val close = readClientFrame(socket.getInputStream())
            assertEquals(8, close.opcode)
            assertArrayEquals(byteArrayOf(3, -24), close.body)
            socket.getOutputStream().write(frame(8, close.body))
            assertEquals(-1, socket.getInputStream().read())
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits)
            ws.close()
            assertTrue(socket.isClosed)
        }
    }

    @Test(timeout = 5000) fun closesAfterBoundedWaitWhenPeerIgnoresClose() {
        peer(server = { socket ->
            assertEquals(8, readClientFrame(socket.getInputStream()).opcode)
            assertEquals(-1, socket.getInputStream().read())
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits)
            val start = System.nanoTime()
            ws.close()
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500)
            assertTrue(socket.isClosed)
        }
    }

    @Test(timeout = 5000) fun rejectsMaskedServerFrames() = invalidFrame(byteArrayOf(-126, -127, 0, 0, 0, 0, 1))
    @Test(timeout = 5000) fun rejectsEachRsvBitWithoutExtensions() {
        for (rsv in listOf(0x10, 0x20, 0x40)) invalidFrame(byteArrayOf((0x82 or rsv).toByte(), 0))
    }
    @Test(timeout = 5000) fun rejectsUnexpectedContinuation() = invalidFrame(frame(0, byteArrayOf(1)))
    @Test(timeout = 5000) fun rejectsSecondStartDuringFragmentation() = invalidFrame(frame(2, byteArrayOf(1), false) + frame(2, byteArrayOf(2)))
    @Test(timeout = 5000) fun rejectsTextForBinaryTransport() = invalidFrame(frame(1, byteArrayOf(1)), 1003)
    @Test(timeout = 5000) fun rejectsReservedOpcode() = invalidFrame(frame(3, byteArrayOf(1)))
    @Test(timeout = 5000) fun rejectsFragmentedControlFrames() = invalidFrame(frame(9, byteArrayOf(1), false))
    @Test(timeout = 5000) fun rejectsOversizedControlHeaderBeforePayloadRead() = invalidFrame(byteArrayOf(-119, 126))
    @Test(timeout = 5000) fun rejectsNoncanonical16BitLength() = invalidFrame(byteArrayOf(-126, 126, 0, 1, 1))
    @Test(timeout = 5000) fun rejectsNoncanonical64BitLength() = invalidFrame(byteArrayOf(-126, 127, 0, 0, 0, 0, 0, 0, 0, 126))
    @Test(timeout = 5000) fun rejectsNegative64BitLength() = invalidFrame(byteArrayOf(-126, 127, -128, 0, 0, 0, 0, 0, 0, 0))
    @Test(timeout = 5000) fun rejectsOneByteClosePayload() = invalidFrame(frame(8, byteArrayOf(1)))
    @Test(timeout = 5000) fun rejectsReservedCloseCodes() {
        for (code in listOf(999, 1004, 1005, 1006, 1015, 2000, 5000)) invalidFrame(frame(8, byteArrayOf((code ushr 8).toByte(), code.toByte())))
    }
    @Test(timeout = 5000) fun rejectsMalformedUtf8CloseReason() = invalidFrame(frame(8, byteArrayOf(3, -24, -64, -81)), 1007)
    @Test(timeout = 5000) fun boundsSingleFrameBeforeAllocatingPayload() = invalidFrame(byteArrayOf(-126, 126, 1, 0), 1009, limits.copy(messageBytes = 128))
    @Test(timeout = 5000) fun boundsFragmentedMessageBeforeAppendingPayload() = invalidFrame(frame(2, ByteArray(70), false) + frame(0, ByteArray(70)), 1009, limits.copy(messageBytes = 128))

    @Test(timeout = 5000) fun unexpectedEofInPayloadClosesSocket() {
        peer(server = { socket -> socket.getOutputStream().write(byteArrayOf(-126, 4, 1)) }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits)
            expect<EOFException> { ws.receive() }
            assertTrue(socket.isClosed)
        }
    }

    @Test(timeout = 5000) fun disconnectBetweenFragmentsIsAnError() {
        peer(server = { socket -> socket.getOutputStream().write(frame(2, byteArrayOf(1), false)) }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits)
            expect<EOFException> { ws.receive() }
            assertTrue(socket.isClosed)
        }
    }

    @Test(timeout = 5000) fun silentPeerReadTimesOutAndClosesSocket() {
        peer(server = { socket ->
            assertEquals(9, readClientFrame(socket.getInputStream()).opcode)
            assertEquals(-1, socket.getInputStream().read())
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits.copy(readMillis = 100))
            expect<SocketTimeoutException> { ws.receive() }
            assertTrue(socket.isClosed)
        }
    }

    @Test(timeout = 5000) fun slowDripFrameStillHasOverallDeadline() {
        peer(server = { socket ->
            socket.getOutputStream().write(byteArrayOf(-126, 4))
            repeat(4) {
                try { Thread.sleep(80); socket.getOutputStream().write(1) } catch (_: java.io.IOException) { return@peer }
            }
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits.copy(readMillis = 130))
            expect<SocketTimeoutException> { ws.receive() }
            assertTrue(socket.isClosed)
        }
    }

    @Test(timeout = 10000) fun progressingMediaDownloadCanOutliveOneReadWindow() {
        val payload=ByteArray(512*1024){(it*31).toByte()}
        peer(server={socket->
            val wire=frame(2,payload)
            val header=wire.size-payload.size
            socket.getOutputStream().write(wire,0,header)
            try {
                var offset=header
                while(offset<wire.size){
                    Thread.sleep(35)
                    val count=minOf(16384,wire.size-offset)
                    socket.getOutputStream().write(wire,offset,count)
                    offset+=count
                }
            }catch(_:java.io.IOException){/* The negative baseline closes part way through. */}
        }){socket->
            val ws=RawWebSocket.upgrade(socket,"localhost",limits=limits.copy(readMillis=400))
            val start=System.nanoTime()
            assertArrayEquals(payload,ws.receive())
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)>400)
            ws.abort()
        }
    }

    @Test(timeout = 5000) fun meaningfulDownloadProgressStillCannotExceedHardMessageLifetime() {
        peer(server={socket->
            val payload=ByteArray(2*1024*1024)
            val wire=frame(2,payload)
            val header=wire.size-payload.size
            socket.getOutputStream().write(wire,0,header)
            try {
                var offset=header
                while(offset<wire.size){
                    Thread.sleep(60)
                    val count=minOf(65536,wire.size-offset)
                    socket.getOutputStream().write(wire,offset,count)
                    offset+=count
                }
            }catch(_:java.io.IOException){/* The hard deadline must close the receiver. */}
        }){socket->
            val ws=RawWebSocket.upgrade(socket,"localhost",limits=limits.copy(readMillis=200))
            expect<SocketTimeoutException>{ws.receive()}
            assertTrue(socket.isClosed)
        }
    }

    @Test(timeout = 5000) fun abortUnblocksConcurrentReader() {
        peer(server = { socket -> assertEquals(-1, socket.getInputStream().read()) }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits)
            val result = AtomicReference<Throwable?>()
            val entered = CountDownLatch(1)
            val reader = Thread {
                entered.countDown()
                try { ws.receive() } catch (failure: Throwable) { result.set(failure) }
            }.apply { isDaemon = true; start() }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            awaitSocketIo(reader)
            ws.abort()
            reader.join(1000)
            assertFalse(reader.isAlive)
            assertTrue(result.get() is java.io.IOException)
            assertTrue(socket.isClosed)
        }
    }

    @Test(timeout = 5000) fun stalledWriterTimesOutAndClosesSocket() {
        val finished = CountDownLatch(1)
        peer(server = { socket ->
            socket.receiveBufferSize = 1024
            assertTrue(finished.await(2, TimeUnit.SECONDS))
        }) { socket ->
            socket.sendBufferSize = 1024
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits.copy(writeMillis = 150))
            try {
                expect<SocketTimeoutException> { ws.send(ByteArray(4 * 1024 * 1024)) }
                assertTrue(socket.isClosed)
            } finally { ws.abort(); finished.countDown() }
        }
    }

    @Test(timeout = 5000) fun abortUnblocksWriterWithoutAcquiringItsLock() {
        val finished = CountDownLatch(1)
        peer(server = { socket ->
            socket.receiveBufferSize = 1024
            assertTrue(finished.await(2, TimeUnit.SECONDS))
        }) { socket ->
            socket.sendBufferSize = 1024
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = limits.copy(writeMillis = 2000))
            val failure = AtomicReference<Throwable?>()
            val writer = Thread {
                try { ws.send(ByteArray(4 * 1024 * 1024)) } catch (problem: Throwable) { failure.set(problem) }
            }.apply { isDaemon = true; start() }
            try {
                awaitSocketIo(writer)
                ws.abort()
                writer.join(1000)
                assertFalse(writer.isAlive)
                assertTrue(failure.get() is java.io.IOException)
                assertTrue(socket.isClosed)
            } finally { ws.abort(); finished.countDown() }
        }
    }

    @Test(timeout = 5000) fun rejectsIncorrectHttpStatus() = invalidUpgrade { key -> response(key).replace("101 Switching Protocols", "200 OK") }
    @Test(timeout = 5000) fun rejectsStatusWithEmbedded101() = invalidUpgrade { key -> response(key).replace("101 Switching Protocols", "200 101 OK") }
    @Test(timeout = 5000) fun rejectsWrongAcceptKey() = invalidUpgrade { key -> response(key).replace(accept(key), "wrong") }
    @Test(timeout = 5000) fun rejectsDuplicateAcceptHeader() = invalidUpgrade { key -> response(key).replace("\r\n\r\n", "\r\nSec-WebSocket-Accept: ${accept(key)}\r\n\r\n") }
    @Test(timeout = 5000) fun rejectsSubstringUpgradeToken() = invalidUpgrade { key -> response(key).replace("Upgrade: websocket", "Upgrade: notwebsocket") }
    @Test(timeout = 5000) fun rejectsSubstringConnectionToken() = invalidUpgrade { key -> response(key).replace("Connection: Upgrade", "Connection: notupgrade") }
    @Test(timeout = 5000) fun rejectsUnexpectedSubprotocol() = invalidUpgrade { key -> response(key).replace("Protocol: binary", "Protocol: text") }
    @Test(timeout = 5000) fun rejectsDuplicatedSubprotocol() = invalidUpgrade { key -> response(key).replace("\r\n\r\n", "\r\nSec-WebSocket-Protocol: binary\r\n\r\n") }
    @Test(timeout = 5000) fun rejectsUnrequestedExtensions() = invalidUpgrade { key -> response(key).replace("\r\n\r\n", "\r\nSec-WebSocket-Extensions: permessage-deflate\r\n\r\n") }
    @Test(timeout = 5000) fun rejectsBareLfHttpResponse() = invalidUpgrade { key -> response(key).replace("\r\n", "\n") }
    @Test(timeout = 5000) fun rejectsFoldedHttpHeader() = invalidUpgrade { key -> response(key).replace("Connection:", " Connection:") }
    @Test(timeout = 5000) fun boundsHttpHeaders() = invalidUpgrade { key -> response(key).replace("\r\n\r\n", "\r\nX-Long: ${"a".repeat(17 * 1024)}\r\n\r\n") }

    @Test(timeout = 5000) fun httpUpgradeResponseHasDeadline() {
        peer(response = { "" }, server = { socket -> assertEquals(-1, socket.getInputStream().read()) }) { socket ->
            expect<SocketTimeoutException> { RawWebSocket.upgrade(socket, "localhost", limits = limits.copy(handshakeMillis = 100)) }
            assertTrue(socket.isClosed)
        }
    }

    @Test(timeout = 5000) fun rejectsHttpRequestInjectionBeforeWriting() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            Socket("127.0.0.1", listener.localPort).use { socket ->
                expect<IllegalArgumentException> { RawWebSocket.upgrade(socket, "localhost\r\nInjected: x", limits = limits) }
                expect<IllegalArgumentException> { RawWebSocket.upgrade(socket, "localhost", "/apiws\r\nInjected: x", limits) }
            }
            listener.accept().use { socket -> assertEquals(-1, socket.getInputStream().read()) }
        }
    }

    @Test(timeout = 5000) fun verifiesTrustedCertificateAndHostnameInRealTlsUpgrade() {
        tlsPeer { port, context ->
            val owned = mutableListOf<Socket>()
            val ws = RawWebSocket.connectTls("127.0.0.1", "localhost", "/apiws", port, context, limits, socketOwner = owned::add)
            assertTrue("TLS TCP socket must avoid Nagle delays", owned.first().tcpNoDelay)
            assertArrayEquals(byteArrayOf(1, 2), ws.receive())
            ws.abort()
        }
    }

    @Test(timeout = 5000) fun protectedRelayPathAndFlowsealQuerySurviveRealTlsUpgrade() {
        val path = RelayEndpoint.parse("wss://relay.example/private-key/apiws")!!.requestPath(4)
        tlsPeer(expectedPath = path) { port, context ->
            val ws = RawWebSocket.connectTls("127.0.0.1", "localhost", path, port, context, limits)
            assertArrayEquals(byteArrayOf(1, 2), ws.receive())
            ws.abort()
        }
    }

    @Test(timeout = 5000) fun rejectsWrongTlsHostname() {
        tlsPeer(expectTlsFailure = true) { port, context ->
            expect<SSLException> { RawWebSocket.connectTls("127.0.0.1", "other.invalid", "/apiws", port, context, limits) }
        }
    }

    @Test(timeout = 5000) fun rejectsUntrustedTlsCertificate() {
        tlsPeer(expectTlsFailure = true) { port, _ ->
            expect<SSLException> { RawWebSocket.connectTls("127.0.0.1", "localhost", "/apiws", port, SSLContext.getDefault(), limits) }
        }
    }

    @Test(timeout = 5000) fun registersSocketsForCancellationDuringTlsHandshake() {
        val accepted = CountDownLatch(1)
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            val serverFailure = AtomicReference<Throwable?>()
            val server = Thread {
                try {
                    listener.accept().use { socket ->
                        accepted.countDown()
                        socket.soTimeout = 1000
                        // The owner cancels the TLS socket before it writes a ClientHello.
                        assertEquals(-1, socket.getInputStream().read())
                    }
                } catch (failure: Throwable) { serverFailure.set(failure) }
            }.apply { isDaemon = true; start() }
            val sockets = mutableListOf<Socket>()
            expect<java.io.IOException> {
                RawWebSocket.connectTls("127.0.0.1", "localhost", "/apiws", listener.localPort, tlsContexts().second, limits) { owned ->
                    sockets.add(owned)
                    if (owned is SSLSocket) owned.close()
                }
            }
            assertTrue(accepted.await(1, TimeUnit.SECONDS))
            assertEquals(2, sockets.size)
            assertTrue(sockets.all { it.isClosed })
            server.join(1000)
            assertFalse(server.isAlive)
            serverFailure.get()?.let { throw AssertionError("TLS cancellation peer failed", it) }
        }
    }

    private fun invalidFrame(bytes: ByteArray, closeCode: Int = 1002, testLimits: RawWebSocket.Limits = limits) {
        peer(server = { socket ->
            socket.getOutputStream().write(bytes)
            val close = readClientFrame(socket.getInputStream())
            assertEquals(8, close.opcode)
            assertArrayEquals(byteArrayOf((closeCode ushr 8).toByte(), closeCode.toByte()), close.body)
            assertEquals(-1, socket.getInputStream().read())
        }) { socket ->
            val ws = RawWebSocket.upgrade(socket, "localhost", limits = testLimits)
            expect<ProtocolException> { ws.receive() }
            assertTrue(socket.isClosed)
        }
    }

    private fun invalidUpgrade(response: (String) -> String) {
        peer(response = response, server = { socket ->
            // Some malformed responses are rejected before all response bytes are read.
            try { assertEquals(-1, socket.getInputStream().read()) } catch (_: java.net.SocketException) { }
        }) { socket ->
            expect<ProtocolException> { RawWebSocket.upgrade(socket, "localhost", limits = limits) }
            assertTrue(socket.isClosed)
        }
    }

    private fun peer(response: (String) -> String = ::response, server: (Socket) -> Unit, client: (Socket) -> Unit) {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            listener.soTimeout = 2000
            val failure = AtomicReference<Throwable?>()
            val thread = Thread {
                try {
                    listener.accept().use { socket ->
                        socket.soTimeout = 2000
                        val key = requestKey(socket.getInputStream())
                        socket.getOutputStream().write(response(key).toByteArray(Charsets.US_ASCII))
                        socket.getOutputStream().flush()
                        server(socket)
                    }
                } catch (problem: Throwable) { failure.set(problem) }
            }.apply { isDaemon = true; start() }
            try { Socket("127.0.0.1", listener.localPort).use(client) }
            finally {
                thread.join(2500)
                assertFalse("Local WebSocket peer did not finish", thread.isAlive)
                failure.get()?.let { throw AssertionError("Local WebSocket peer failed", it) }
            }
        }
    }

    private fun tlsPeer(expectTlsFailure: Boolean = false, expectedPath: String = "/apiws", client: (Int, SSLContext) -> Unit) {
        val (serverContext, clientContext) = tlsContexts()
        serverContext.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            listener.soTimeout = 2000
            val failure = AtomicReference<Throwable?>()
            val thread = Thread {
                try {
                    listener.accept().use { socket ->
                        socket.soTimeout = 2000
                        (socket as SSLSocket).startHandshake()
                        val key = requestKey(socket.getInputStream(), expectedPath)
                        socket.getOutputStream().write(response(key).toByteArray(Charsets.US_ASCII) + frame(2, byteArrayOf(1, 2)))
                        socket.getOutputStream().flush()
                    }
                } catch (problem: Throwable) {
                    if (!(expectTlsFailure && problem is java.io.IOException)) failure.set(problem)
                }
            }.apply { isDaemon = true; start() }
            try { client(listener.localPort, clientContext) }
            finally {
                thread.join(2500)
                assertFalse("Local TLS peer did not finish", thread.isAlive)
                failure.get()?.let { throw AssertionError("Local TLS peer failed", it) }
            }
        }
    }

    private fun tlsContexts(): Pair<SSLContext, SSLContext> {
        // Public test-only private key, never included in the Android APK. Valid for localhost, 2020–2119.
        val password = "test-only-password".toCharArray()
        val store = KeyStore.getInstance("PKCS12")
        javaClass.getResourceAsStream("/websocket/localhost.p12")!!.use { store.load(it, password) }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, password) }
        val server = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
        val trustStore = KeyStore.getInstance("PKCS12").apply { load(null, null); setCertificateEntry("localhost", store.getCertificate("localhost")) }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trustStore) }
        val client = SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }
        return server to client
    }

    private data class ClientFrame(val opcode: Int, val body: ByteArray, val fin: Boolean)
    private fun readClientFrame(input: InputStream, allowFragments: Boolean = false): ClientFrame {
        val first = input.read()
        assertTrue("Client frame missing", first >= 0)
        assertEquals(0, first and 0x70)
        if (!allowFragments) assertEquals(0x80, first and 0x80)
        val second = input.read()
        assertEquals("Client frame must be masked", 0x80, second and 0x80)
        var size = (second and 127).toLong()
        if (size == 126L) {
            size = (input.read() shl 8 or input.read()).toLong()
            assertTrue("Client 16-bit length must be canonical", size >= 126)
        } else if (size == 127L) {
            size = 0
            repeat(8) { size = size shl 8 or input.read().toLong() }
            assertTrue("Client 64-bit length must be canonical", size >= 65536)
        }
        assertTrue(size in 0..(16 * 1024 * 1024))
        val mask = input.readNBytes(4)
        assertEquals(4, mask.size)
        val body = input.readNBytes(size.toInt())
        assertEquals(size.toInt(), body.size)
        for (i in body.indices) body[i] = (body[i].toInt() xor mask[i % 4].toInt()).toByte()
        return ClientFrame(first and 15, body, first and 0x80 != 0)
    }

    private fun requestKey(input: InputStream, expectedPath: String? = null): String {
        var key: String? = null
        var first = true
        while (true) {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val ch = input.read()
                if (ch < 0) throw EOFException("EOF in test HTTP request")
                if (ch == 10) break
                if (ch != 13) bytes.write(ch)
            }
            val line = bytes.toString(Charsets.US_ASCII.name())
            if (first && expectedPath != null) assertEquals("GET $expectedPath HTTP/1.1", line)
            first = false
            if (line.isEmpty()) return requireNotNull(key)
            if (line.startsWith("Sec-WebSocket-Key: ")) key = line.substringAfter(": ")
        }
    }

    private fun accept(key: String): String = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
        .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII)))
    private fun response(key: String) = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${accept(key)}\r\nSec-WebSocket-Protocol: binary\r\n\r\n"
    private fun frame(opcode: Int, body: ByteArray, fin: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        out.write((if (fin) 0x80 else 0) or opcode)
        when {
            body.size < 126 -> out.write(body.size)
            body.size <= 65535 -> { out.write(126); out.write(body.size ushr 8); out.write(body.size) }
            else -> { out.write(127); for (shift in 56 downTo 0 step 8) out.write((body.size.toLong() ushr shift).toInt()) }
        }
        out.write(body)
        return out.toByteArray()
    }

    private inline fun <reified T : Throwable> expect(block: () -> Unit): T {
        try { block() } catch (failure: Throwable) {
            if (failure is T) return failure
            throw AssertionError("Expected ${T::class.java.simpleName}, got ${failure.javaClass.simpleName}", failure)
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }

    private fun awaitSocketIo(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (thread.isAlive && System.nanoTime() < deadline) {
            if (thread.stackTrace.any {
                    (it.className.contains("SocketInputStream") || it.className.contains("SocketOutputStream") || it.className.contains("NioSocketImpl")) &&
                        (it.methodName.contains("read", ignoreCase = true) || it.methodName.contains("write", ignoreCase = true))
                }) return
            Thread.sleep(5)
        }
        throw AssertionError("Worker did not enter socket I/O")
    }
}
