package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kust3r3nk0.tgwsandroid.core.TransportMode
import io.github.kust3r3nk0.tgwsandroid.core.TransportSettings
import java.security.KeyStore
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdvancedSettingsInstrumentedTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get()=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
    private val secret="00112233445566778899aabbccddeeff"
    @Before fun before()=reset()
    @After fun after()=reset()
    private fun reset(){prefs.edit().clear().commit();KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry("tgws-secret-aes-v1")}}
    @Test fun legacyDirectAndRelayOnlyUpgradePreservesPortSecretAndExplicitRoute(){
        for(url in listOf("","wss://worker.example/access-token/apiws")){
            reset();val store=SettingsStore(context);store.save(8443,secret,url)
            prefs.edit().remove("transport_encrypted").commit() // exact absence of this key in 0.2
            val before=prefs.getString("secret_encrypted",null)
            val migrated=SettingsStore(context).load()
            assertEquals(8443,migrated.port);assertEquals(secret,migrated.secret);assertEquals(url,migrated.relayUrl)
            assertEquals(if(url.isEmpty())TransportMode.DIRECT else TransportMode.RELAY_ONLY,migrated.transport.mode)
            assertEquals(before,prefs.getString("secret_encrypted",null));assertTrue(migrated.transport.workerUrls.isEmpty())
        }
    }
    @Test fun advancedRecordsAreAuthenticatedAndNeverSilentlyBecomeDirectAfterCorruption(){
        val store=SettingsStore(context)
        val config=TransportSettings(TransportMode.AUTO_CLOUDFLARE,"cloudflare.com",listOf("proxy.example"),listOf("wss://worker.example/private-token/apiws"),true)
        store.save(8443,secret,transport=config)
        assertEquals(config,store.load().transport)
        assertFalse(prefs.all.values.contains(config.encode()))
        store.save(2443,secret);assertEquals(config,store.load().transport)
        prefs.edit().putString("transport_encrypted","corrupt").commit()
        assertThrows(SettingsException::class.java){store.load()}
        assertThrows(SettingsException::class.java){store.save(2443,secret,transport=TransportSettings())}
        assertEquals("corrupt",prefs.getString("transport_encrypted",null))
        store.recover(2443,secret,transport=TransportSettings())
        assertEquals(TransportMode.DIRECT,store.load().transport.mode)
    }
}
