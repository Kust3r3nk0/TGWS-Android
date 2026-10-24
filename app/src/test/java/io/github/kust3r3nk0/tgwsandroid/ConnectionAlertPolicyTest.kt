package io.github.kust3r3nk0.tgwsandroid

import io.github.kust3r3nk0.tgwsandroid.core.*
import org.junit.Assert.*
import org.junit.Test

class ConnectionAlertPolicyTest {
    private val failed=AlertObservation(true,health=ConnectionHealth(attempts=1,failures=1))
    private val up=AlertObservation(true,health=ConnectionHealth(attempts=1,withInboundData=1,
        active=listOf(ActiveConnection(1,2,false,TransportKind.WSS,100,true))))
    @Test fun switchingToAnotherOwnRelayIsNotLostBecauseTransportKindIsUnchanged(){
        val p=ConnectionAlertPolicy()
        val primary=up.copy(health=up.health.copy(selection=RouteSelection(ProfileId.WIFI,TransportKind.RELAY,2,false,SelectionReason.USER_PRIMARY,false,1)))
        assertTrue(p.update(true,primary,0).isEmpty())
        val backup=up.copy(health=up.health.copy(selection=RouteSelection(ProfileId.WIFI,TransportKind.RELAY,2,false,SelectionReason.FALLBACK,true,2)))
        assertEquals(listOf(ConnectionAlert.FALLBACK),p.update(true,backup,1))
        assertTrue(p.update(true,backup,2).isEmpty())
    }
    @Test fun shortReconnectDoesNotNotifyButLongOutageAndRealProgressDo(){
        val p=ConnectionAlertPolicy();assertTrue(p.update(true,failed,0).isEmpty());assertEquals(30_000L,p.deadline)
        assertTrue(p.update(true,up,20_000).isEmpty());assertNull(p.deadline)
        p.update(true,failed.copy(health=failed.health.copy(failures=2)),21_000)
        assertEquals(listOf(ConnectionAlert.OUTAGE),p.update(true,failed.copy(health=failed.health.copy(failures=2)),51_000))
        assertNull(p.deadline);assertTrue(p.update(true,failed,60_000).isEmpty())
        assertEquals(listOf(ConnectionAlert.RESTORED),p.update(true,up,61_000))
    }
    @Test fun idleAndIncompleteLocalHandshakeDoNotProduceConnectivityAlerts(){
        val p=ConnectionAlertPolicy();assertTrue(p.update(true,AlertObservation(true),0).isEmpty());assertNull(p.deadline)
        p.update(true,AlertObservation(true,pending=1),100)
        p.update(true,AlertObservation(true),1000)
        assertNull(p.deadline);assertTrue(p.update(true,AlertObservation(true),60_000).isEmpty())
    }
    @Test fun fallbackIsDeduplicatedAndConfigurationAndDisableAreIndependent(){
        val p=ConnectionAlertPolicy()
        val fallback=up.copy(health=up.health.copy(selection=RouteSelection(ProfileId.WIFI,TransportKind.TCP,2,false,SelectionReason.FALLBACK,true)))
        assertEquals(listOf(ConnectionAlert.FALLBACK),p.update(true,fallback,0))
        assertTrue(p.update(true,fallback,1).isEmpty())
        p.update(true,up.copy(health=up.health.copy(selection=RouteSelection(ProfileId.WIFI,TransportKind.WSS,2,false,SelectionReason.USER_PRIMARY,false))),2)
        assertTrue(p.update(true,fallback,3).isEmpty())
        assertEquals(listOf(ConnectionAlert.CONFIGURATION),p.update(true,AlertObservation(false,criticalConfiguration=true),4))
        assertTrue(p.update(false,failed,5).isEmpty());assertNull(p.deadline)
    }
    @Test fun losingAndroidNetworkAfterProgressSchedulesOnlyOneDeadline(){
        val p=ConnectionAlertPolicy();p.update(true,up,0)
        val lost=AlertObservation(true,networkUnavailable=true)
        p.update(true,lost,1000);assertEquals(31_000L,p.deadline)
        repeat(100){p.update(true,lost,2000+it.toLong())}
        assertEquals(31_000L,p.deadline)
        assertEquals(listOf(ConnectionAlert.OUTAGE),p.update(true,lost,31_000))
        assertTrue(p.update(true,lost,300_000).isEmpty());assertNull(p.deadline)
    }
}
