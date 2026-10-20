package io.github.kust3r3nk0.tgwsandroid

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
    private lateinit var previousLocales:android.os.LocaleList
    @Before fun setup(){
        val manager=context.getSystemService(android.app.LocaleManager::class.java)
        previousLocales=manager.applicationLocales
        instrumentation.runOnMainSync{manager.applicationLocales=android.os.LocaleList.forLanguageTags("ru")}
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        AppPreferences(context).apply{theme=ThemeMode.DARK;liquidGlass=false;transparency=false;diagnostics=true}
        SettingsStore(context).recover(8443,"00112233445566778899aabbccddeeff")
    }
    @After fun cleanup(){
        context.stopService(Intent(context,ProxyService::class.java));AppPreferences(context).theme=ThemeMode.SYSTEM
        instrumentation.runOnMainSync{context.getSystemService(android.app.LocaleManager::class.java).applicationLocales=previousLocales}
        instrumentation.waitForIdleSync()
    }
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
            scenario.onActivity{activity->
                activity.currentFocus?.clearFocus()
                activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(activity.window.decorView.windowToken,0)
            }
            ui.waitForIdle()
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
    @Test fun largeFontWithGlassAndTransparencyKeepsNativeActionsAndNavigationAccessible(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            settle()
            var page by mutableIntStateOf(0)
            var mode by mutableStateOf(ThemeMode.DARK)
            var shares=0;var copies=0;var opens=0
            // Explicit pure-UI fixture with no remote connection or fabricated measurements.
            scenario.onActivity{it.setContent{TgwsTheme(mode,true,true){
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,1.6f)){
                    AppScaffold(page,{page=it},"0.4.1",mode==ThemeMode.DARK,{mode=if(mode==ThemeMode.DARK)ThemeMode.LIGHT else ThemeMode.DARK}){
                        when(page){
                            0->PremiumHome(ProxySnapshot(),NetworkView(),"8443","",TransportDraft(),true,true,{},
                                {shares++},{copies++},{opens++},{page=1},{page=4})
                            4->PremiumDiagnostics(ProxySnapshot(),DiagnosticHistoryState(),{},{})
                            else->PremiumSettings({page=it},mode,io.github.kust3r3nk0.tgwsandroid.core.TransportMode.DIRECT,true)
                        }
                    }
                }
            }}}
            click("telegram.share",true);click("telegram.copy_secret",true);click("telegram.open",true)
            assertEquals(1,shares);assertEquals(1,copies);assertEquals(1,opens)
            click("nav.diagnostics");ui.onNodeWithTag("diagnostics.status").assertIsDisplayed()
            shot("premium-large-font-dark")
            click("theme.toggle");click("nav.settings");ui.onNodeWithTag("settings.connection").assertIsDisplayed()
            shot("premium-large-font-light")
            click("nav.home");click("telegram.open",true);assertEquals(2,opens)
        }
    }

}
