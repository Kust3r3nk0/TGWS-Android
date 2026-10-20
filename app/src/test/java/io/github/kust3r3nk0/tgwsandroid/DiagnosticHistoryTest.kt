package io.github.kust3r3nk0.tgwsandroid

import io.github.kust3r3nk0.tgwsandroid.core.*
import org.junit.Assert.*
import org.junit.Test

class DiagnosticHistoryTest {
    private fun route(time:Long,status:RouteStatus=RouteStatus.AVAILABLE)=RouteObservation(1,2,false,TransportKind.WSS,status,RouteStage.READY,checkedAtMillis=time,setupMillis=42)
    @Test fun boundedMeasurementsOnlyRecordCompletedSetupNotTrafficOrClose(){
        val history=DiagnosticHistory();var previous=ProxySnapshot()
        repeat(100){index->
            val current=previous.copy(routes=listOf(route(index+1L)))
            history.accept(previous,current,100,100);previous=current
            val traffic=current.copy(routes=current.routes.map{it.copy(sent=777)})
            history.accept(current,traffic,100,100);previous=traffic
        }
        assertEquals(32,history.state.samples.size);assertEquals(100,history.state.events.size)
        val closed=previous.copy(routes=listOf(route(200,RouteStatus.CLOSED)))
        history.accept(previous,closed,200,200)
        assertEquals(32,history.state.samples.size);assertEquals(100L,history.state.samples.last().timeMillis)
    }
    @Test fun eventsHaveHardBoundAndTurningOffClearsWithoutDisablingUptime(){
        val history=DiagnosticHistory();var old=ProxySnapshot()
        repeat(300){val next=old.copy(issue=if(it%2==0)ProxyIssue.TLS else ProxyIssue.TCP);history.accept(old,next,it.toLong(),it.toLong());old=next}
        assertEquals(128,history.state.events.size)
        history.enabled=false
        val starting=old.copy(phase=ProxyPhase.STARTING)
        history.accept(old,starting,1000,500)
        assertTrue(history.state.events.isEmpty());assertTrue(history.state.samples.isEmpty());assertEquals(300L,uptimeMillis(history.state,800))
    }
    @Test fun uptimeUsesMonotonicClockStopsAndResetsOnNewRun(){
        val history=DiagnosticHistory();val stopped=ProxySnapshot();val start=stopped.copy(phase=ProxyPhase.STARTING)
        history.accept(stopped,start,10_000,100)
        val listening=start.copy(phase=ProxyPhase.LISTENING);history.accept(start,listening,1,200)
        assertEquals(400L,uptimeMillis(history.state,500))
        history.accept(listening,stopped,1,600);assertEquals(500L,uptimeMillis(history.state,99_000))
        history.accept(stopped,start,2,1000);assertEquals(100L,uptimeMillis(history.state,1100))
    }
    @Test fun unchangedSnapshotsCannotCreateEventsOrSyntheticLatency(){
        val history=DiagnosticHistory();val current=ProxySnapshot()
        repeat(1000){history.accept(current,current,100,100)}
        assertTrue(history.state.events.isEmpty());assertTrue(history.state.samples.isEmpty());assertNull(uptimeMillis(history.state,100))
    }
    @Test fun networkAddressesAndOperatorMetadataNeverEnterEventHistory(){
        val history=DiagnosticHistory()
        val current=ProxySnapshot(network=NetworkView(kind=AccessKind.WIFI,validated=true,
            dnsServers=listOf("10.20.30.40"),privateDnsName="private.example",operator=OperatorView(simName="private-carrier")),
            routes=listOf(route(1).copy(timings=SetupTimings(resolvedAddresses=listOf("192.168.1.22")))))
        history.accept(ProxySnapshot(),current,1,1)
        val report=history.state.toString()
        for(value in listOf("10.20.30.40","private.example","private-carrier","192.168.1.22"))assertFalse(report.contains(value))
        assertEquals(1,history.state.samples.size)
    }
    @Test fun failedRoutesAreNotSuccessfulLatencyMeasurements(){
        val history=DiagnosticHistory();history.accept(ProxySnapshot(),ProxySnapshot(routes=listOf(route(1,RouteStatus.UNAVAILABLE))),1,1)
        assertTrue(history.state.samples.isEmpty())
    }
}
