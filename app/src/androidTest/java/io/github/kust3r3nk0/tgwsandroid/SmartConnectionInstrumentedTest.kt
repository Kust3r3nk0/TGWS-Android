package io.github.kust3r3nk0.tgwsandroid

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kust3r3nk0.tgwsandroid.core.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartConnectionInstrumentedTest {
    @get:Rule val ui=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private lateinit var locales:android.os.LocaleList
    private val secret="00112233445566778899aabbccddeeff"
    @Before fun setup(){
        locales=context.getSystemService(android.app.LocaleManager::class.java).applicationLocales
        instrumentation.runOnMainSync{
            context.getSystemService(android.app.LocaleManager::class.java).applicationLocales=android.os.LocaleList.forLanguageTags("ru")
            ProxyState.snapshot=ProxySnapshot();ProxyState.clearDiagnostics()
        }
        AppPreferences(context).apply{theme=ThemeMode.DARK;connectionAlerts=false}
        ConnectionNotifications.clear(context)
        SettingsStore(context).recover(8443,secret)
    }
    @After fun cleanup(){
        context.stopService(Intent(context,ProxyService::class.java))
        AppPreferences(context).apply{theme=ThemeMode.SYSTEM;connectionAlerts=false}
        ConnectionNotifications.clear(context)
        SettingsStore(context).recover(8443,secret)
        instrumentation.runOnMainSync{context.getSystemService(android.app.LocaleManager::class.java).applicationLocales=locales}
        instrumentation.waitForIdleSync()
    }
    private fun settle(){ui.waitForIdle();ui.waitUntil(10_000){ui.onAllNodesWithTag("settings.loading").fetchSemanticsNodes().isEmpty()}}
    private fun click(tag:String,scroll:Boolean=true){
        if(scroll)ui.onNodeWithTag(tag).performScrollTo()
        ui.onNodeWithTag(tag).performClick();ui.waitForIdle()
    }
    private fun shot(name:String){
        ui.waitForIdle()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/tgws-ui")).use{it.readBytes()}
        val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try{
            val pipes=instrumentation.uiAutomation.executeShellCommandRw("dd of=/sdcard/Download/tgws-ui/$name.png")
            android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use{assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use{it.readBytes()}
        }finally{image.recycle()}
    }
    @Test fun profilesPersistAcrossActivityAndStoreRecreationWithoutReplacingTheKey(){
        val keystore=java.security.KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        val alias="tgws-secret-aes-v1";val created=keystore.getCreationDate(alias)
        val oldRecord=context.getSharedPreferences("settings",0).getString("secret_encrypted",null)!!
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            settle();click("nav.settings",false);click("settings.smart")
            click("smart.enabled");click("smart.network.auto");click("smart.selected.CUSTOM")
            click("smart.profile.CUSTOM");ui.onNodeWithTag("smart.profile.CUSTOM").assertIsSelected()
            click("smart.mode.MANUAL");ui.onNodeWithTag("smart.mode.MANUAL").assertIsSelected()
            click("smart.primary.TCP");click("smart.power")
            ui.onNodeWithTag("smart.mode.MANUAL").performScrollTo().assertIsSelected()
            click("settings.save")
            ui.waitUntil(10_000){SettingsStore(context).load().transport.profiles.enabled}
            val saved=SettingsStore(context).load()
            assertEquals(secret,saved.secret);assertEquals(8443,saved.port)
            assertEquals(ProfileId.CUSTOM,saved.transport.profiles.selected)
            assertEquals(RoutingMode.MANUAL,saved.transport.profiles.custom.mode)
            assertEquals(TransportKind.TCP,saved.transport.profiles.custom.primary)
            assertEquals(PowerPolicy.SAVER,saved.transport.profiles.custom.power)
            assertEquals(created,keystore.getCreationDate(alias))
            assertEquals(secret,SecretRecordCodec.decrypt(keystore.getKey(alias,null) as javax.crypto.SecretKey,oldRecord))
            assertFalse(context.getSharedPreferences("settings",0).all.values.any{it is String && it==secret})
            scenario.recreate();settle();ui.onNodeWithTag("smart.enabled").assertIsOn()
            ui.onNodeWithTag("smart.selected.CUSTOM").assertIsSelected()
            assertEquals(saved.transport,SettingsStore(context).load().transport)
            ui.onNodeWithTag("smart.enabled").performScrollTo();shot("smart-profiles-dark")
        }
    }
    @Test fun enablingProfilesDoesNotStartProxyAndEmptyHealthDoesNotInventMeasurements(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            settle();click("nav.settings",false);click("settings.smart");click("smart.enabled");click("settings.save")
            ui.waitUntil(10_000){SettingsStore(context).load().transport.profiles.enabled}
            assertFalse(ProxyState.snapshot.running)
            click("nav.diagnostics",false)
            ui.onNodeWithTag("diagnostics.health").performScrollTo()
            ui.onNodeWithTag("health.quality").assertTextEquals(context.getString(R.string.smart_quality_insufficient))
            ui.onNodeWithText(context.getString(R.string.smart_success_unknown)).assertExists()
            ui.onNodeWithText(context.getString(R.string.smart_setup_average_unknown)).assertExists()
            ui.onNodeWithTag("traffic.chart").assertDoesNotExist()
            scenario.recreate();settle();assertFalse(ProxyState.snapshot.running)
            ui.onNodeWithTag("health.quality").assertTextEquals(context.getString(R.string.smart_quality_insufficient))
            ui.onNodeWithTag("diagnostics.health").performScrollTo();shot("smart-health-dark")
        }
    }
    @Test fun optionalNotificationsAreOffUntilExplicitlyEnabledAndCanBeCleared(){
        val manager=context.getSystemService(NotificationManager::class.java)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        var controller:ConnectionNotifications?=null
        instrumentation.runOnMainSync{
            controller=ConnectionNotifications(context,Handler(Looper.getMainLooper()))
            controller.update(ProxySnapshot(phase=ProxyPhase.ERROR,issue=ProxyIssue.CONFIGURATION))
        }
        try{
            assertFalse(AppPreferences(context).connectionAlerts)
            assertTrue(manager.activeNotifications.none{it.id in 1450..1453})
            instrumentation.runOnMainSync{AppPreferences(context).connectionAlerts=true}
            val until=android.os.SystemClock.elapsedRealtime()+5000
            while(manager.activeNotifications.none{it.id==1453}){
                assertTrue(android.os.SystemClock.elapsedRealtime()<until);android.os.SystemClock.sleep(20)
            }
            val notification=manager.activeNotifications.single{it.id==1453}.notification
            assertEquals(context.getString(R.string.smart_alert_configuration),notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
            instrumentation.runOnMainSync{AppPreferences(context).connectionAlerts=false}
            // NotificationManager.cancel is an asynchronous Binder request on API 36.
            ui.waitUntil(5000){manager.activeNotifications.none{it.id in 1450..1453}}
            assertTrue(manager.activeNotifications.none{it.id in 1450..1453})
            assertEquals(secret,SettingsStore(context).load().secret)
        }finally{instrumentation.runOnMainSync{controller?.close()}}
    }
}
