package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.*
import org.junit.Test

class RouteDiagnosticsTest {
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
    @Test fun diagnosticsStayBoundedUnderManyRoutes(){
        val book=RouteDiagnostics();repeat(1000){book.mark("key$it",2,false,TransportKind.WSS,RouteStatus.UNAVAILABLE,ServerIssue.TLS)}
        assertEquals(32,book.snapshot().size)
    }
}
