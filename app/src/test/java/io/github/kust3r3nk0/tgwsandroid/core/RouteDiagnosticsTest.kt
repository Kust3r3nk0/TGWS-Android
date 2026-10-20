package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.*
import org.junit.Test

class RouteDiagnosticsTest {
    @Test fun reconnectDoesNotExposeStalePhaseMeasurements(){
        val book=RouteDiagnostics();book.mark("route",2,false,TransportKind.WSS,RouteStatus.CONNECTING)
        book.mark("route",2,false,TransportKind.WSS,RouteStatus.AVAILABLE,timings=SetupTimings(1,2,3,4))
        assertNotNull(book.snapshot().single().setupMillis)
        book.mark("route",2,false,TransportKind.WSS,RouteStatus.CONNECTING)
        assertNull(book.snapshot().single().setupMillis);assertNull(book.snapshot().single().timings)
        assertEquals(2,book.snapshot().single().attempts)
    }
    @Test fun wrappedSetupFailuresAreClassifiedWithBoundedCycleSafeCauseTraversal(){
        val dns=java.io.IOException("private endpoint",java.net.UnknownHostException("private endpoint"))
        assertEquals(ServerIssue.DNS,setupFailure(dns,ServerIssue.WSS_HANDSHAKE))
        assertEquals(ServerIssue.TLS,setupFailure(java.util.concurrent.ExecutionException(javax.net.ssl.SSLHandshakeException("private")),ServerIssue.RELAY))
        val a=java.io.IOException();val b=java.io.IOException();a.initCause(b);b.initCause(a)
        assertEquals(ServerIssue.RELAY,setupFailure(a,ServerIssue.RELAY))
        var deep:Exception=java.net.UnknownHostException();repeat(10){deep=java.io.IOException(deep)}
        assertEquals(ServerIssue.RELAY,setupFailure(deep,ServerIssue.RELAY))
    }
    @Test fun actualSetupTimeBackoffTrafficAndNetworkResetAreNotFabricated(){
        var nano=1000L;var wall=100000L
        val book=RouteDiagnostics({nano},{wall})
        book.mark("private-key",2,true,TransportKind.RELAY,RouteStatus.CONNECTING)
        assertEquals(0,book.snapshot().single().checkedAtMillis)
        nano+=2000000;book.mark("private-key",2,true,TransportKind.RELAY,RouteStatus.AVAILABLE)
        assertEquals(2L,book.snapshot().single().setupMillis)
        book.traffic("private-key",12,16);wall+=5000
        book.mark("private-key",2,true,TransportKind.RELAY,RouteStatus.BACKOFF)
        val observed=book.snapshot().single();assertEquals(100000L,observed.checkedAtMillis);assertEquals(12L,observed.sent)
        assertFalse(observed.toString().contains("private-key"));book.clear();assertTrue(book.snapshot().isEmpty())
    }
    @Test fun nativeSetupFailuresKeepDnsTcpTlsAndDeadlineSeparateFromUpgrade(){
        val cases=listOf(java.net.UnknownHostException("private endpoint") to ServerIssue.DNS,
            java.net.ConnectException("private endpoint") to ServerIssue.TCP,
            java.net.NoRouteToHostException("private endpoint") to ServerIssue.TCP,
            javax.net.ssl.SSLHandshakeException("private endpoint") to ServerIssue.TLS,
            java.net.SocketTimeoutException("private endpoint") to ServerIssue.TIMEOUT,
            NetworkUnavailable() to ServerIssue.NETWORK,
            java.net.ProtocolException("private endpoint") to ServerIssue.WSS_HANDSHAKE)
        for((error,expected)in cases){
            assertEquals(expected,setupFailure(error,ServerIssue.WSS_HANDSHAKE))
            val book=RouteDiagnostics();book.mark("private endpoint",2,false,TransportKind.WSS,RouteStatus.UNAVAILABLE,expected)
            assertFalse(book.snapshot().toString().contains("private endpoint"))
        }
    }
    @Test fun closedRouteCannotRemainReadyAndDoesNotClaimNetworkUnreachable(){
        val book=RouteDiagnostics()
        book.mark("private-media-route",1,true,TransportKind.RELAY,RouteStatus.CONNECTING)
        book.mark("private-media-route",1,true,TransportKind.RELAY,RouteStatus.AVAILABLE)
        book.traffic("private-media-route",4096,2048)
        assertTrue(book.closed("private-media-route",1000,WebSocketCloseCategory.TCP_EOF))
        val closed=book.snapshot().single()
        assertEquals(RouteStatus.CLOSED,closed.status)
        assertEquals(RouteStage.CLOSED,closed.stage)
        assertEquals(4096L,closed.sent)
        assertEquals(2048L,closed.received)
        assertNull(closed.issue)
        assertEquals(1000,closed.closeCode)
        assertEquals(WebSocketCloseCategory.TCP_EOF,closed.closeCategory)
        assertFalse(book.closed("private-media-route"))
        book.mark("private-media-route",1,true,TransportKind.RELAY,RouteStatus.CONNECTING)
        book.mark("private-media-route",1,true,TransportKind.RELAY,RouteStatus.AVAILABLE)
        assertEquals(RouteStatus.AVAILABLE,book.snapshot().single().status)
    }
    @Test fun websocketCloseSignalsUseCategorizedPublicMetadataOnly(){
        val book=RouteDiagnostics()
        book.mark("secret-worker-url",1,true,TransportKind.RELAY,RouteStatus.AVAILABLE)
        book.failed("secret-worker-url",ServerIssue.RELAY,1008,WebSocketCloseCategory.BYTE_LIMIT)
        val sample=book.snapshot().single()
        assertEquals(1008,sample.closeCode)
        assertEquals(WebSocketCloseCategory.BYTE_LIMIT,sample.closeCategory)
        assertFalse(sample.toString().contains("secret-worker-url"))
    }
    @Test fun diagnosticsStayBoundedUnderManyRoutes(){
        val book=RouteDiagnostics();repeat(1000){book.mark("key$it",2,false,TransportKind.WSS,RouteStatus.UNAVAILABLE,ServerIssue.TLS)}
        assertEquals(32,book.snapshot().size)
    }
}
