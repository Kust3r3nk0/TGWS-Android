package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDpiInstrumentedTest {
    @get:Rule val ui=createEmptyComposeRule()
    @Test fun corruptedProxyPreferenceDoesNotSilentlyEnableDirectRouting(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=AppPreferences(context);val previous=prefs.localSocksPort
        try{context.getSharedPreferences("app_preferences",Context.MODE_PRIVATE).edit().putString("local_socks_port","invalid").commit()
            Assert.assertEquals(-1,prefs.localSocksPort)
            Assert.assertThrows(IllegalArgumentException::class.java){
                io.github.kust3r3nk0.tgwsandroid.core.NetworkTransportFactory(prefs.localSocksPort){null}
            }
        }finally{prefs.localSocksPort=previous}
    }
    @Test fun proxySettingPersistsAcrossRecreationWithoutReplacingSecret(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=AppPreferences(context);val previous=prefs.localSocksPort
        val secret="00112233445566778899aabbccddeeff"
        SettingsStore(context).recover(8443,secret)
        try{prefs.localSocksPort=0
            ActivityScenario.launch(MainActivity::class.java).use{scenario->
                ui.waitForIdle();ui.waitUntil(10000){ui.onAllNodesWithTag("settings.loading").fetchSemanticsNodes().isEmpty()}
                ui.onNodeWithTag("nav.settings").performClick();ui.waitForIdle()
                ui.onNodeWithTag("settings.transport").performScrollTo().performClick();ui.waitForIdle()
                ui.onNodeWithTag("dpi.enabled").performScrollTo().performClick();ui.waitForIdle()
                Assert.assertEquals(1080,prefs.localSocksPort)
                scenario.recreate();ui.waitForIdle()
                ui.onNodeWithTag("dpi.enabled").performScrollTo().assertIsOn().performClick();ui.waitForIdle()
                Assert.assertEquals(0,prefs.localSocksPort)
                Assert.assertEquals(secret,SettingsStore(context).load().secret)
                Assert.assertFalse(ProxyState.snapshot.running)
            }
        }finally{prefs.localSocksPort=previous}
    }
}
