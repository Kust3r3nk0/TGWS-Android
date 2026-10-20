package io.github.kust3r3nk0.tgwsandroid

import io.github.kust3r3nk0.tgwsandroid.core.*
import org.junit.Assert.*
import org.junit.Test

class DiagnosticJournalTest {
    @Test fun interleavedRejectionsAndSessionIdsCannotBypassLogcatRateLimit(){
        val emitted=mutableListOf<DiagnosticEvent>();val h=DiagnosticHistory{emitted+=it}
        repeat(1000){id->
            h.record(DiagnosticEvent(1,EventCategory.CLIENT,false,kind=EventKind.CLIENT_REJECTED,rejected=id),1)
            h.record(sessionEvent(SessionClosed(id.toLong(),SessionEndReason.HANDSHAKE_REJECTED,null,false,1,0,0),1),1)
        }
        assertEquals(32,emitted.size);assertEquals(128,h.state.events.size)
    }
    @Test fun distinctSessionIdsRemainBoundedInMemoryAndRateLimitedInLogcat(){
        val emitted=mutableListOf<DiagnosticEvent>();val h=DiagnosticHistory{emitted+=it}
        repeat(4096){id->h.record(sessionEvent(SessionClosed(id.toLong(),SessionEndReason.HANDSHAKE_REJECTED,null,false,1,0,0),100),100)}
        assertEquals(128,h.state.events.size);assertEquals(8,emitted.size)
        assertEquals(4095L,h.state.events.last().session?.id)
        h.record(sessionEvent(SessionClosed(4096,SessionEndReason.CLIENT_EOF,null,false,1,0,0),1100),1100)
        assertEquals(9,emitted.size)
    }
    @Test fun sessionClosuresPreserveTypedCauseAndUpgradeWithoutDuplicatingOrLeakingEndpoints(){
        val h=DiagnosticHistory()
        val capacity=HttpUpgradeDiagnostic(503,UpgradeFailure.STATUS,RelayUpgradeReason.CAPACITY,2000)
        val closed=SessionClosed(7,SessionEndReason.SETUP_FAILED,null,false,42,0,0,upgrade=capacity)
        h.record(sessionEvent(closed,100),100)
        val e=h.state.events.single()
        assertEquals(EventLevel.WARNING,e.level);assertEquals(EventKind.SESSION_CLOSED,e.kind)
        val report=DiagnosticReport.render(ProxySnapshot(),h.state)
        for(expected in listOf("\"schema\": 3","SETUP_FAILED","\"id\": 7","\"status\": 503","CAPACITY","\"retry_after_ms\": 2000"))assertTrue(expected,report.contains(expected))
        val log=diagnosticLogLine(e)
        assertTrue(log.contains("session=7"));assertTrue(log.contains("end=SETUP_FAILED"));assertTrue(log.contains("upgrade=503/STATUS"))
        assertEquals(EventLevel.INFO,sessionEvent(closed.copy(reason=SessionEndReason.NETWORK_CHANGED),100).level)
        assertEquals(EventLevel.INFO,sessionEvent(closed.copy(reason=SessionEndReason.REMOTE_CLOSE,peerClose=WebSocketClose(1000)),100).level)
        assertEquals(EventLevel.WARNING,sessionEvent(closed.copy(reason=SessionEndReason.REMOTE_CLOSE,peerClose=WebSocketClose(1008,WebSocketCloseCategory.BYTE_LIMIT)),100).level)
        h.enabled=false;h.record(sessionEvent(closed,101),101);assertTrue(h.state.events.isEmpty())
    }
    @Test fun logcatStormIsBoundedAndFormatterNeverCopiesProviderOrExceptionStrings(){
        val emitted=mutableListOf<DiagnosticEvent>();val h=DiagnosticHistory{emitted+=it}
        repeat(4096){h.record(issueEvent(ProxyIssue.TLS,it.toLong()),it.toLong())}
        assertEquals(13,emitted.size);assertEquals(4096,emitted.last().occurrences)
        val line=diagnosticLogLine(DiagnosticEvent(1,EventCategory.WEBVIEW,false,
            provider=WebViewStatus(ProviderState.AVAILABLE,"private endpoint","private token")))
        assertFalse(line.contains("private"));assertTrue(line.contains("provider_state=AVAILABLE"))
    }
    @Test fun repeatedClientRejectionsCoalesceButRetainExactReasonCountAndTime(){
        val h=DiagnosticHistory();var old=ProxySnapshot(phase=ProxyPhase.LISTENING)
        repeat(3000){i->val next=old.withRejectedHandshake(HandshakeRejection.SECRET_OR_TRANSPORT)
            h.accept(old,next,i.toLong(),i.toLong());old=next}
        val event=h.state.events.single()
        assertEquals(3000,event.occurrences);assertEquals(3000,event.rejected)
        assertEquals(0L,event.timeMillis);assertEquals(2999L,event.lastTimeMillis)
        assertEquals(EventLevel.WARNING,event.level);assertEquals(EventCategory.CLIENT,event.category)
        assertNull(old.issue);assertEquals(ProxyPhase.LISTENING,old.phase)
    }
    @Test fun differentReasonsAndLongGapsDoNotMerge(){
        val h=DiagnosticHistory()
        val e=DiagnosticEvent(1,EventCategory.CLIENT,false,kind=EventKind.CLIENT_REJECTED,rejection=HandshakeRejection.FAKE_TLS)
        h.record(e,1);h.record(e.copy(timeMillis=2,lastTimeMillis=2,rejection=HandshakeRejection.INVALID_LENGTH),2)
        h.record(e.copy(timeMillis=3,lastTimeMillis=3),3);h.record(e.copy(timeMillis=4,lastTimeMillis=4),20_000)
        assertEquals(4,h.state.events.size)
    }
    @Test fun routesRecordFailuresClosuresTimingsAndWorkerByteLimitWithoutAddresses(){
        val h=DiagnosticHistory()
        val failed=RouteObservation(9,4,true,TransportKind.WORKER,RouteStatus.UNAVAILABLE,RouteStage.CLOSED,
            ServerIssue.RELAY,checkedAtMillis=12,setupMillis=55,timings=SetupTimings(1,2,3,4,listOf("192.168.42.1")),attempts=3,
            sent=16777216,closeCode=1008,closeCategory=WebSocketCloseCategory.BYTE_LIMIT)
        val s=ProxySnapshot(routes=listOf(failed));h.accept(ProxySnapshot(),s,12,12)
        val e=h.state.events.single();assertEquals(EventKind.ROUTE_FAILED,e.kind)
        assertEquals(EventCategory.RELAY,e.category);assertEquals(3,e.route?.attempts);assertEquals(3L,e.route?.tlsMillis)
        assertEquals(WebSocketCloseCategory.BYTE_LIMIT,e.route?.closeCategory)
        assertFalse(h.state.toString().contains("192.168.42.1"));assertTrue(h.state.samples.isEmpty())
        h.accept(s,s.copy(routes=listOf(failed.copy(sent=16777217))),13,13)
        assertEquals(1,h.state.events.size)
        val closed=s.copy(routes=listOf(failed.copy(status=RouteStatus.CLOSED,issue=null)))
        h.accept(s,closed,14,14);assertEquals(EventKind.ROUTE_CLOSED,h.state.events.last().kind)
        assertEquals(EventLevel.INFO,h.state.events.last().level)
    }
    @Test fun clearPreservesUptimeAndCountersAndOffRejectsExternalEvents(){
        val h=DiagnosticHistory();h.accept(ProxySnapshot(),ProxySnapshot(phase=ProxyPhase.STARTING),1,100)
        h.clear();assertEquals(50L,uptimeMillis(h.state,150));assertTrue(h.state.events.isEmpty())
        h.enabled=false;h.record(issueEvent(ProxyIssue.TLS,2),200);assertTrue(h.state.events.isEmpty())
        h.enabled=true;h.record(issueEvent(ProxyIssue.TLS,3),300);assertEquals(1,h.state.events.size)
    }
    @Test fun fatalLocalFailureIsErrorWhileFallbackIsWarningAndCategoriesStaySpecific(){
        for((issue,category)in listOf(ProxyIssue.TLS to EventCategory.TLS,ProxyIssue.DNS to EventCategory.DNS,
            ProxyIssue.WSS_HANDSHAKE to EventCategory.WSS,ProxyIssue.TCP to EventCategory.TCP,ProxyIssue.HTTP2 to EventCategory.HTTP2,
            ProxyIssue.SECRET_UNAVAILABLE to EventCategory.CONFIGURATION)){
            assertEquals(category,issueEvent(issue,1).category)
            assertEquals(if(issue==ProxyIssue.SECRET_UNAVAILABLE)EventLevel.ERROR else EventLevel.WARNING,issueEvent(issue,1).level)
        }
        assertEquals(EventLevel.ERROR,issueEvent(ProxyIssue.PORT,1,true).level)
    }
    @Test fun filtersCombineCategoryAndLevelAndNeverReorderBackingHistory(){
        val a=issueEvent(ProxyIssue.TLS,1);val b=issueEvent(ProxyIssue.PORT,2,true)
        val c=issueEvent(ProxyIssue.TLS,3);val all=listOf(a,b,c)
        assertEquals(listOf(c,a),filterEvents(all,EventLevel.WARNING,EventCategory.TLS))
        assertEquals(listOf(b),filterEvents(all,EventLevel.ERROR,null));assertEquals(listOf(a,b,c),all)
    }
    @Test fun ipv4Ipv6ChangeIsObservedEvenWithoutAccessKindChange(){
        val h=DiagnosticHistory();val old=ProxySnapshot(network=NetworkView(kind=AccessKind.WIFI,validated=true,ipv4=true))
        h.accept(old,old.copy(network=old.network.copy(ipv6=true)),1,1)
        assertEquals(true,h.state.events.single().ipv6)
    }
    @Test fun adviceIsSpecificAndNeverInventsConnectivityOrRequestsSecretRegeneration(){
        assertTrue(diagnosticAdvice(ProxySnapshot()).isEmpty())
        val s=ProxySnapshot(phase=ProxyPhase.LISTENING,issue=ProxyIssue.DNS,network=NetworkView(kind=AccessKind.WIFI,captivePortal=true))
        assertEquals(listOf(DiagnosticAdvice.CAPTIVE_PORTAL,DiagnosticAdvice.DNS),diagnosticAdvice(s))
        assertEquals(listOf(DiagnosticAdvice.PORT),diagnosticAdvice(ProxySnapshot(issue=ProxyIssue.PORT)))
    }
    @Test fun reportContainsJournalBuildAndProviderMetadataButNoPrivateFields(){
        val h=DiagnosticHistory();h.record(issueEvent(ProxyIssue.TLS,123),1)
        val p=WebViewStatus.available("com.google.android.webview.beta","155.0.9999.123",123)
        val report=DiagnosticReport.render(ProxySnapshot(lastHandshakeRejection=HandshakeRejection.FAKE_TLS,rejectedHandshakes=4),h.state,p,"0.4.1",18,37)
        for(expected in listOf("\"schema\": 3","\"events\": [","\"level\": \"WARNING\"","\"app_version\": \"0.4.1\"","155.0.9999.123","\"latest_verified\": false","FAKE_TLS"))assertTrue(expected,report.contains(expected))
        val unsafe=WebViewStatus(ProviderState.AVAILABLE,"https://private.example/key","secret",1)
        val sanitized=DiagnosticReport.render(ProxySnapshot(),provider=unsafe,version="secret\"\n")
        assertFalse(sanitized.contains("private.example"));assertFalse(sanitized.contains("secret"))
    }
}
