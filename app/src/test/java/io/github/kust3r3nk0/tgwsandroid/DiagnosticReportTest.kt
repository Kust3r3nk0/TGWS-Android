package io.github.kust3r3nk0.tgwsandroid

import org.junit.Assert.*
import org.junit.Test

class DiagnosticReportTest {
    @Test fun exportNeverIncludesNetworkAddressesPlmnPrivateDnsOrRawOperatorNames(){
        val snapshot=ProxySnapshot(network=NetworkView(kind=AccessKind.CELLULAR,ipv6=true,nat64Prefix="64:ff9b::/96",
            dnsServers=listOf("192.0.2.123"),privateDnsName="private.example",operator=OperatorView("private-name","25002","sim-name","25001",RadioKind.NR)))
        val report=DiagnosticReport.render(snapshot)
        for(value in listOf("192.0.2.123","64:ff9b","private.example","private-name","sim-name","25002","25001"))assertFalse(value,report.contains(value))
        assertTrue(report.contains("CELLULAR"));assertTrue(report.contains("\"ipv6\": true"))
    }
}
