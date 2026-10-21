package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.*
import org.junit.Test

class SmartRoutingTest {
    @Test fun automaticSelectionDistinguishesOwnNodesOfTheSameTransport(){
        val routing=SmartRouting{60_000};val a=RoutingCandidate("relay:a:2",TransportKind.RELAY,2,true);val b=RoutingCandidate("relay:b:2",TransportKind.RELAY,2,true)
        routing.failed(routing.begin(a));val good=routing.begin(b);routing.connected(good,12);routing.progress(good)
        assertSame(b,routing.order(listOf(a,b),ConnectionProfile(primary=TransportKind.RELAY,backups=emptySet()),true).first().candidate)
    }
    @Test fun saverDelaysDemandDrivenExplorationWithoutOpeningSocketsOrBlacklistingRoutes(){
        var now=0L;val routing=SmartRouting{now};val profile=ConnectionProfile(power=PowerPolicy.SAVER)
        routing.failed(routing.begin(wss));val good=routing.begin(tcp);routing.connected(good,10);routing.progress(good)
        now=299_999;assertSame(tcp,routing.order(listOf(wss,tcp),profile,true).first().candidate)
        now=300_000;assertEquals(SelectionReason.CONTROLLED_PROBE,routing.order(listOf(wss,tcp),profile,true).first().reason)
        assertEquals(2,routing.retainedSamples())
        assertSame(tcp,routing.order(listOf(wss,tcp),profile,true).first().candidate)
    }
    private val wss=RoutingCandidate("private.example/secret",TransportKind.WSS,2,false)
    private val tcp=RoutingCandidate("tcp",TransportKind.TCP,2,false)
    @Test fun socketSetupAloneNeverProvesReachabilityOrOutranksRealInboundProgress(){
        var now=0L;val routing=SmartRouting{now}
        repeat(10){val t=routing.begin(wss);routing.connected(t,1)}
        assertEquals(ConnectionQuality.INSUFFICIENT,routing.health(true,ProfileId.WIFI).quality)
        val token=routing.begin(tcp);routing.connected(token,500);routing.progress(token);now=60_000
        assertSame(tcp,routing.order(listOf(wss,tcp),ConnectionProfile(),true).first().candidate)
        assertFalse(routing.health(true,ProfileId.WIFI).toString().contains("private.example"))
    }
    @Test fun oldGenerationOutcomesCannotAffectNewNetwork(){
        val routing=SmartRouting{0};val old=routing.begin(wss);routing.reset()
        routing.connected(old,50);routing.progress(old);routing.failed(old)
        assertEquals(0,routing.health(true,ProfileId.MOBILE).attempts)
        assertEquals(0,routing.retainedSamples())
    }
    @Test fun manualAndBackupPermissionsAreEnforcedWithoutBroadeningConfiguredCandidates(){
        val routing=SmartRouting{0}
        val manual=ConnectionProfile(mode=RoutingMode.MANUAL,primary=TransportKind.TCP,backups=emptySet())
        assertEquals(listOf(tcp),routing.order(listOf(wss,tcp),manual,true).map{it.candidate})
        assertTrue(routing.order(listOf(wss),manual,true).isEmpty())
        val backup=manual.copy(mode=RoutingMode.FAILOVER,backups=setOf(TransportKind.WSS))
        assertEquals(listOf(tcp,wss),routing.order(listOf(wss,tcp),backup,true).map{it.candidate})
        assertEquals(listOf(wss,tcp),routing.order(listOf(wss,tcp),manual,false).map{it.candidate})
    }
    @Test fun failedRouteReturnsThroughAtMostOneDemandDrivenProbePerMinute(){
        var now=0L;val routing=SmartRouting{now}
        val failed=routing.begin(wss);routing.failed(failed)
        val good=routing.begin(tcp);routing.connected(good,20);routing.progress(good)
        now=59_999;assertSame(tcp,routing.order(listOf(wss,tcp),ConnectionProfile(),true).first().candidate)
        now=60_000;val probe=routing.order(listOf(wss,tcp),ConnectionProfile(),true).first()
        assertSame(wss,probe.candidate);assertEquals(SelectionReason.CONTROLLED_PROBE,probe.reason)
        assertSame(tcp,routing.order(listOf(wss,tcp),ConnectionProfile(),true).first().candidate)
    }
    @Test fun historyIsBoundedExpiresAndDoesNotLeakRawKeys(){
        var now=0L;val routing=SmartRouting{now}
        repeat(1024){routing.failed(routing.begin(wss))}
        assertEquals(128,routing.retainedSamples())
        assertEquals(ConnectionQuality.POOR,routing.health(true,ProfileId.CUSTOM).quality)
        now=600_001;assertEquals(0,routing.health(true,ProfileId.CUSTOM).attempts)
        assertEquals(ConnectionQuality.INSUFFICIENT,routing.health(true,ProfileId.CUSTOM).quality)
        assertFalse(wss.toString().contains("secret"))
    }
    @Test fun cancelAndSlowLocalClientDoNotPenalizeExternalRoute(){
        val routing=SmartRouting{100}
        val canceled=routing.begin(wss);routing.cancel(canceled);routing.failed(canceled)
        val token=routing.begin(tcp);routing.connected(token,10);routing.progress(token)
        routing.end(token,SessionEndReason.DOWNSTREAM_WRITE_TIMEOUT,null)
        assertEquals(0,routing.health(false,ProfileId.AUTOMATIC).failures)
        assertEquals(1,routing.health(false,ProfileId.AUTOMATIC).withInboundData)
    }
    @Test fun qualityRequiresConclusiveObservationsAndRecordsRuntimeBreaksOnce(){
        val routing=SmartRouting{100}
        repeat(5){val t=routing.begin(tcp);routing.connected(t,42);routing.progress(t)}
        assertEquals(ConnectionQuality.EXCELLENT,routing.health(true,ProfileId.WIFI).quality)
        val t=routing.begin(wss);routing.connected(t,5)
        routing.end(t,SessionEndReason.REMOTE_IO,null);routing.end(t,SessionEndReason.REMOTE_IO,null)
        val h=routing.health(true,ProfileId.WIFI)
        assertEquals(1,h.abnormalClosures);assertEquals(1,h.failures);assertEquals(83,h.successPercent)
        assertEquals(ConnectionQuality.GOOD,h.quality)
    }
}
