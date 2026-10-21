package io.github.kust3r3nk0.tgwsandroid

import org.junit.Assert.*
import org.junit.Test
import io.github.kust3r3nk0.tgwsandroid.core.*

class DiagnosticReportTest {
    @Test fun healthAndSelectionExportOnlyTypedBoundedEvidence(){
        val selection=RouteSelection(ProfileId.MOBILE,TransportKind.WORKER,4,true,SelectionReason.FALLBACK,true)
        val health=ConnectionHealth(enabled=true,attempts=7,withInboundData=5,selection=selection,
            active=List(100){ActiveConnection(it.toLong(),4,true,TransportKind.WORKER,1234,true)})
        val snapshot=ProxySnapshot(health=health)
        val report=DiagnosticReport.render(snapshot)
        assertTrue(report.contains("\"connection_health\""));assertTrue(report.contains("\"reason\": \"FALLBACK\""))
        assertEquals(8,Regex("\"duration_ms\": 1234").findAll(report).count())
        assertTrue(report.contains("\"success_percent\": null"));assertFalse(report.contains("endpoint"))
    }
    @Test fun mediaRouteIncludesTrafficAndSafeWorkerCloseCategory(){
        val snapshot=ProxySnapshot(routes=listOf(RouteObservation(1,1,true,TransportKind.RELAY,
            RouteStatus.UNAVAILABLE,RouteStage.CLOSED,ServerIssue.RELAY,sent=4096,received=512,
            closeCode=1008,closeCategory=WebSocketCloseCategory.BYTE_LIMIT)))
        val report=DiagnosticReport.render(snapshot)
        assertTrue(report.contains("\"route_sent\": 4096"))
        assertTrue(report.contains("\"route_received\": 512"))
        assertTrue(report.contains("\"close_code\": 1008"))
        assertTrue(report.contains("\"close_reason\": \"BYTE_LIMIT\""))
    }
    @Test fun exportNeverIncludesNetworkAddressesPlmnPrivateDnsOrRawOperatorNames(){
        val snapshot=ProxySnapshot(network=NetworkView(kind=AccessKind.CELLULAR,ipv6=true,nat64Prefix="64:ff9b::/96",
            dnsServers=listOf("192.0.2.123"),privateDnsName="private.example",operator=OperatorView("private-name","25002","sim-name","25001",RadioKind.NR)),
            routes=listOf(RouteObservation(1,2,true,TransportKind.HTTP2,RouteStatus.AVAILABLE,RouteStage.READY,
                timings=SetupTimings(resolvedAddresses=listOf("198.51.100.42","2001:db8::123")))))
        val report=DiagnosticReport.render(snapshot)
        for(value in listOf("192.0.2.123","64:ff9b","private.example","private-name","sim-name","25002","25001","198.51.100.42","2001:db8::123"))assertFalse(value,report.contains(value))
        assertTrue(report.contains("CELLULAR"));assertTrue(report.contains("\"ipv6\": true"))
    }
}
