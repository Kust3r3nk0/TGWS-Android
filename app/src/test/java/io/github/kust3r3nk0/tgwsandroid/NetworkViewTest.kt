package io.github.kust3r3nk0.tgwsandroid

import org.junit.Assert.*
import org.junit.Test

class NetworkViewTest {
    @Test fun fourReferenceCardsHaveExactPrimaryAndAdditionalCodesWithoutInventedCredentials(){
        val cards=CarrierReference.cards;assertEquals(4,cards.size)
        assertEquals(listOf("25002","25001","25099","25020"),cards.map{it.primaryPlmn})
        assertEquals(CarrierBrand.MEGAFON,CarrierReference.hint("25014")?.brand)
        assertNull(CarrierReference.hint("250999"));assertNull(CarrierReference.hint(null))
        assertNull(cards.first().login);assertNull(cards.last().password)
        assertEquals(listOf("internet","internet.mts.ru","internet.beeline.ru","internet"),cards.map{it.apn})
    }
    @Test fun roamingAndMvnoKeepRegisteredAndSimOperatorsSeparateAndUnknownsExplicit(){
        val value=OperatorView.fromActiveDataSim(true,"Roaming carrier","25099","Home SIM","25001",13)
        assertEquals("25099",value.registeredPlmn);assertEquals("25001",value.simPlmn);assertEquals(RadioKind.LTE,value.radio)
        val missing=OperatorView.fromActiveDataSim(false,"Wrong SIM","25002","Wrong SIM","25002",20)
        assertEquals(OperatorView(),missing)
        val unknown=OperatorView.fromActiveDataSim(true,"Mega5G","","","bad",null)
        assertEquals(RadioKind.UNKNOWN,unknown.radio);assertNull(unknown.registeredPlmn);assertNull(unknown.simName)
        assertEquals("Other MVNO",OperatorView.fromActiveDataSim(true,"Other MVNO","25077",null,null,20).registeredName)
    }
    @Test fun rapidDefaultChangesLossRecoveryAndLateCallbacksAreOperatorIndependent(){
        for(card in CarrierReference.cards){
            val state=DefaultNetworkState<String>()
            assertTrue(card.brand.name,state.update("wifi","wifi","v4"))
            assertFalse(state.update("wifi","wifi","v4"))
            assertTrue(state.update("cell","cell","dual:${card.primaryPlmn}"))
            assertFalse(state.lost("wifi"));assertFalse(state.update("wifi","cell","late"))
            assertTrue(state.lost("cell"));assertNull(state.current)
            assertTrue(state.update("cell2","cell2","v6-nat64"))
            assertTrue(state.update("cell2","cell2","new-dns"))
            state.stop();assertFalse(state.update("wifi","wifi","late-after-stop"));assertNull(state.current)
        }
    }
    @Test fun radioComesOnlyFromReportedTechnologyNotOperatorBrand(){
        assertEquals(RadioKind.G2,CarrierReference.radio(1));assertEquals(RadioKind.G3,CarrierReference.radio(3))
        assertEquals(RadioKind.LTE,CarrierReference.radio(13));assertEquals(RadioKind.LTE_PLUS,CarrierReference.radio(19))
        assertEquals(RadioKind.NR,CarrierReference.radio(20));assertEquals(RadioKind.UNKNOWN,CarrierReference.radio(123))
    }
    @Test fun ipFamiliesDescribeConfiguredAddressesAndExcludeLinkLocalOnlyIpv6(){
        assertEquals(IpSupport.UNKNOWN,NetworkView().ipSupport)
        assertEquals(IpSupport.IPV4,NetworkView(ipv4=true).ipSupport)
        assertEquals(IpSupport.IPV6,NetworkView(ipv6=true).ipSupport)
        assertEquals(IpSupport.DUAL,NetworkView(ipv4=true,ipv6=true).ipSupport)
        assertFalse(internetAddress(java.net.InetAddress.getByName("fe80::1")))
        assertFalse(internetAddress(java.net.InetAddress.getByName("::1")))
        assertTrue(internetAddress(java.net.InetAddress.getByName("2001:db8::1")))
        assertTrue(internetAddress(java.net.InetAddress.getByName("192.0.2.1")))
    }
}
