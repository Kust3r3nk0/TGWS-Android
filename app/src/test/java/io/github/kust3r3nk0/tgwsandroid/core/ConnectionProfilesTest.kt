package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.*
import org.junit.Test

class ConnectionProfilesTest {
    @Test fun legacyRecordIsByteCompatibleAndDoesNotEnableRouting(){
        val legacy="DIRECT\n\nfalse\n\n"
        val loaded=TransportSettings.decode(legacy)
        assertFalse(loaded.profiles.enabled);assertEquals(legacy,loaded.encode())
    }
    @Test fun explicitProfilesRoundTripAllFieldsAndFollowOnlyNetworkKind(){
        val profile=ConnectionProfile(RoutingMode.FAILOVER,setOf(TransportKind.TCP,TransportKind.WSS),TransportKind.TCP,
            setOf(TransportKind.WSS),false,PowerPolicy.SAVER)
        val p=ConnectionProfiles(enabled=true).withProfile(ProfileId.MOBILE,profile)
        assertEquals(profile,p.active(ConnectionNetwork.MOBILE));assertEquals(ProfileId.WIFI,p.id(ConnectionNetwork.WIFI))
        assertEquals(p,ConnectionProfiles.decode(p.encode()))
        val config=TransportSettings(profiles=p);assertEquals(config,TransportSettings.decode(config.encode()))
        assertEquals(profile,p.copy(automaticNetwork=false,selected=ProfileId.MOBILE).active(ConnectionNetwork.WIFI))
    }
    @Test fun corruptOrContradictoryPermissionsAreRejectedRatherThanSilentlyBroadened(){
        assertThrows(IllegalArgumentException::class.java){ConnectionProfile(allowed=emptySet())}
        assertThrows(IllegalArgumentException::class.java){ConnectionProfile(allowed=setOf(TransportKind.WSS),backups=setOf(TransportKind.TCP))}
        for(text in listOf("",ConnectionProfiles().encode()+"|extra",ConnectionProfiles().encode().replace("AUTOMATIC;63","AUTOMATIC;999"),ConnectionProfiles().encode().replaceFirst("1|0|1","1|unknown|1"))){
            assertThrows(IllegalArgumentException::class.java){ConnectionProfiles.decode(text)}
        }
    }
    @Test fun disablingAdaptiveExponentialRecoveryKeepsMinimumOverloadProtection(){
        var now=0L;val backoff=RouteBackoff<String>(10,100,adaptive={false}){now}
        repeat(5){val p=backoff.acquire("a")!!;backoff.failure(p)
            assertEquals(10L,backoff.remainingMillis("a"));assertNull(backoff.acquire("a"));now+=10_000_000}
        backoff.defer("a",80);assertEquals(80L,backoff.remainingMillis("a"));assertNull(backoff.acquire("a"))
    }
}
