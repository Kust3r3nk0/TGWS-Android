package io.github.kust3r3nk0.tgwsandroid.core
import org.junit.Assert.*
import org.junit.Test
class RelayConfigurationTest {
    private val a=RelayNode("a","Основной 🔒","wss://one.example/private/apiws")
    private val b=RelayNode("b","Backup","wss://two.example/secret/apiws",false)
    @Test fun legacyFiveAndSixLineRecordsRemainByteIdentical(){
        for(value in listOf(TransportSettings(),TransportSettings(profiles=ConnectionProfiles(enabled=true)))){
            val text=value.encode();assertEquals(text,TransportSettings.decode(text).encode());assertFalse(TransportSettings.decode(text).relays.enabled)
        }
    }
    @Test fun boundedUnicodeConfigurationRoundTripsWithoutLeakingLabelsOrCredentials(){
        val c=RelayConfiguration(true,listOf(a,b),"a",listOf("b"),RelayStrategy.AUTOMATIC,true)
        assertEquals(c,RelayConfiguration.decode(c.encode()))
        val settings=TransportSettings(TransportMode.AUTO_CLOUDFLARE,relays=c)
        assertEquals(settings,TransportSettings.decode(settings.encode()))
        assertFalse(c.toString().contains("Основной"));assertFalse(c.toString().contains("secret"));assertFalse(a.toString().contains("private"))
    }
    @Test fun manualAndMediaFilterNeverBroadenExplicitBackupPermissions(){
        val c=RelayConfiguration(true,listOf(a,b),"b",listOf("a"))
        assertEquals(listOf(b,a),c.candidates(false));assertEquals(listOf(a),c.candidates(true))
        assertTrue(c.copy(strategy=RelayStrategy.MANUAL).candidates(true).isEmpty())
        assertEquals(listOf(b),c.copy(backups=emptyList()).candidates(false))
    }
    @Test fun deleteAndEditAreExplicitAndCannotEnableAnEmptyManager(){
        val c=RelayConfiguration(true,listOf(a,b),"a",listOf("b"))
        val next=c.remove("a");assertEquals("b",next.primary);assertTrue(next.backups.isEmpty())
        assertFalse(next.remove("b").enabled);assertEquals(a.copy(name="New"),c.put(a.copy(name="New")).nodes.first())
    }
    @Test fun corruptDuplicateAndOversizedListsAreRejected(){
        assertThrows(IllegalArgumentException::class.java){RelayConfiguration(enabled=true)}
        assertThrows(IllegalArgumentException::class.java){RelayConfiguration(nodes=listOf(a,a),primary="a")}
        assertThrows(IllegalArgumentException::class.java){RelayConfiguration(nodes=listOf(a,b.copy(url=a.url)),primary="a")}
        assertThrows(IllegalArgumentException::class.java){RelayConfiguration(nodes=listOf(a),primary="a",backups=listOf("missing"))}
        for(text in listOf("",RelayConfiguration().encode()+"|extra","1|0|FAILOVER|||0|a;_w;eA;1")){
            assertThrows(Exception::class.java){RelayConfiguration.decode(text)}
        }
    }
}
