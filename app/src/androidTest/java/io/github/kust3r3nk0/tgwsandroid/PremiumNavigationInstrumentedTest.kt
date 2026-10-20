package io.github.kust3r3nk0.tgwsandroid

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PremiumNavigationInstrumentedTest {
    @get:Rule val ui=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    @Before fun setup(){
        AppPreferences(context).apply{theme=ThemeMode.DARK;liquidGlass=false;transparency=false;diagnostics=true}
        SettingsStore(context).recover(8443,"00112233445566778899aabbccddeeff")
    }
    @After fun cleanup(){context.stopService(Intent(context,ProxyService::class.java));AppPreferences(context).theme=ThemeMode.SYSTEM}
    private fun settle(){ui.waitUntil(10000){ui.onAllNodesWithTag("settings.loading").fetchSemanticsNodes().isEmpty()}}
    private fun click(tag:String,scroll:Boolean=false){
        if(scroll)ui.onNodeWithTag(tag).performScrollTo()
        ui.onNodeWithTag(tag).performClick();ui.waitForIdle()
    }
    private fun shot(name:String){
        ui.waitForIdle();Thread.sleep(350)
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/tgws-ui")).use{it.readBytes()}
        val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try{
            val pipes=instrumentation.uiAutomation.executeShellCommandRw("dd of=/sdcard/Download/tgws-ui/$name.png")
            ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use{assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
            ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use{it.readBytes()}
        }finally{image.recycle()}
    }
    @Test fun threeTabsAndBackPreserveUnsavedInputAndSecretAcrossRecreation(){
        val before=context.getSharedPreferences("settings",0).all.toMap()
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            settle();click("nav.settings");click("settings.connection",true)
            ui.onNodeWithTag("settings.port").performTextReplacement("2443")
            val draft="ffeeddccbbaa99887766554433221100"
            ui.onNodeWithTag("settings.secret").performTextReplacement(draft)
            scenario.recreate();settle()
            ui.onNodeWithTag("settings.port").assertTextContains("2443")
            // Keystore records have not changed simply by editing or recreation.
            assertEquals(before,context.getSharedPreferences("settings",0).all)
            click("nav.diagnostics");ui.onNodeWithTag("diagnostics.status").assertExists()
            assertFalse(ProxyState.snapshot.running)
            click("nav.settings");click("settings.connection",true)
            ui.onNodeWithTag("settings.port").assertTextContains("2443")
            click("settings.save",true);ui.waitUntil(10000){SettingsStore(context).port==2443}
            assertEquals(draft,SettingsStore(context).load().secret)
            UiDevice.getInstance(instrumentation).pressBack();ui.waitForIdle()
            ui.onNodeWithTag("settings.connection").assertExists()
            UiDevice.getInstance(instrumentation).pressBack();ui.waitForIdle()
            ui.onNodeWithTag("home.status").assertExists()
        }
    }
    @Test fun actualListenerStaysRunningAcrossAllTabsAndNativeScreensRenderInBothThemes(){
        ActivityScenario.launch(MainActivity::class.java).use{
            settle();click("proxy.toggle");ui.waitUntil(10000){ProxyState.snapshot.localAvailable}
            shot("premium-home-dark")
            click("nav.diagnostics");assertTrue(ProxyState.snapshot.localAvailable);shot("premium-diagnostics-dark")
            click("nav.settings");assertTrue(ProxyState.snapshot.localAvailable);shot("premium-settings-dark")
            click("theme.toggle");shot("premium-settings-light")
            click("nav.diagnostics");shot("premium-diagnostics-light")
            click("nav.home");shot("premium-home-light")
            click("proxy.toggle");ui.waitUntil(10000){!ProxyState.snapshot.running}
        }
    }
}
