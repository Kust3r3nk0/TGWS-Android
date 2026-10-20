package io.github.kust3r3nk0.tgwsandroid

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket

@RunWith(AndroidJUnit4::class)
class DiagnosticToolsInstrumentedTest {
    @get:Rule val ui=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private lateinit var locales:android.os.LocaleList
    @Before fun setup(){
        locales=context.getSystemService(android.app.LocaleManager::class.java).applicationLocales
        instrumentation.runOnMainSync{
            context.getSystemService(android.app.LocaleManager::class.java).applicationLocales=android.os.LocaleList.forLanguageTags("ru")
            ProxyState.clearDiagnostics();ProxyState.recordDiagnostics=true
        }
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        AppPreferences(context).apply{theme=ThemeMode.DARK;diagnostics=true;liquidGlass=false;transparency=false}
        SettingsStore(context).recover(8443,"00112233445566778899aabbccddeeff")
    }
    @After fun cleanup(){
        context.stopService(Intent(context,ProxyService::class.java))
        instrumentation.runOnMainSync{
            ProxyState.clearDiagnostics();ProxyState.recordDiagnostics=true
            context.getSystemService(android.app.LocaleManager::class.java).applicationLocales=locales
        }
        AppPreferences(context).apply{theme=ThemeMode.SYSTEM;diagnostics=true}
        instrumentation.waitForIdleSync()
    }
    private fun settle(){ui.waitUntil(10000){ui.onAllNodesWithTag("settings.loading").fetchSemanticsNodes().isEmpty()}}
    private fun click(tag:String,scroll:Boolean=false){if(scroll)ui.onNodeWithTag(tag).performScrollTo();ui.onNodeWithTag(tag).performClick();ui.waitForIdle()}
    private fun shot(name:String){
        ui.waitForIdle()
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/tgws-ui")).use{it.readBytes()}
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try{val pipes=instrumentation.uiAutomation.executeShellCommandRw("dd of=/sdcard/Download/tgws-ui/$name.png")
            ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
            ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use{it.readBytes()}
        }finally{bitmap.recycle()}
    }
    @Test fun actualProviderAndEmptyLoopbackCheckPreserveCredentialsAcrossRecreation(){
        val beforeStart=SettingsStore(context).load()
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            settle();click("proxy.toggle");ui.waitUntil(10000){ProxyState.snapshot.localAvailable}
            // Explicit Start saves the form using a fresh GCM nonce. Diagnostics must not write it again.
            assertEquals(beforeStart,SettingsStore(context).load())
            val saved=context.getSharedPreferences("settings",0).all.toMap()
            click("nav.diagnostics");click("diagnostics.local.check",true)
            ui.waitUntil(5000){ui.onAllNodesWithTag("diagnostics.local.result").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("diagnostics.local.result").assertTextEquals(context.getString(R.string.tools_local_available))
            assertEquals(0,ProxyState.snapshot.rejectedHandshakes)
            assertEquals(saved,context.getSharedPreferences("settings",0).all)
            click("webview.refresh",true)
            val systemProvider=android.webkit.WebView.getCurrentWebViewPackage()
            val provider=WebViewProviderReader.read()
            assertEquals(if(systemProvider==null)ProviderState.MISSING else ProviderState.AVAILABLE,provider.state)
            assertEquals(systemProvider?.packageName,provider.packageName)
            if(systemProvider!=null)assertEquals(systemProvider.longVersionCode,provider.versionCode)
            provider.packageName?.let{ui.onNodeWithTag("webview.package").assertTextEquals(it)}
            provider.version?.let{ui.onNodeWithTag("webview.version").assertTextContains(it)}
            shot("diagnostics-webview-dark")
            scenario.recreate();settle();assertTrue(ProxyState.snapshot.localAvailable)
            ui.onNodeWithTag("webview.state").performScrollTo().assertTextEquals(context.getString(providerStateResource(provider.state)))
            assertEquals(saved,context.getSharedPreferences("settings",0).all)
        }
    }
    @Test fun realPortFailureCanBeFilteredClearedAndReportedAfterActivityRecreation(){
        ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use{occupied->
            SettingsStore(context).recover(occupied.localPort,"00112233445566778899aabbccddeeff")
            ActivityScenario.launch(MainActivity::class.java).use{scenario->
                settle();click("proxy.toggle");ui.waitUntil(10000){ProxyState.snapshot.phase==ProxyPhase.ERROR}
                click("nav.diagnostics");click("diagnostics.all",true)
                click("journal.level.ERROR",true)
                assertTrue(ProxyState.diagnostics.events.any{it.level==EventLevel.ERROR && it.issue==ProxyIssue.PORT})
                click("journal.category");click("journal.category.SERVICE")
                scenario.recreate();settle();ui.onNodeWithTag("journal.dialog").assertExists()
                ui.onNodeWithTag("journal.level.ERROR").assertIsSelected()
                shot("diagnostics-error-journal-dark")
                click("journal.clear");click("journal.clear.confirm")
                assertTrue(ProxyState.diagnostics.events.isEmpty())
                assertFalse(ProxyState.snapshot.running)
                assertEquals(occupied.localPort,SettingsStore(context).load().port)
            }
        }
    }
    @Test fun exportedJsonIsParseableRedactedReadOnlyAndCacheScopeIsNarrow(){
        val value=ProxySnapshot(network=NetworkView(kind=AccessKind.WIFI,privateDnsName="private.invalid",dnsServers=listOf("192.0.2.1")))
        val report=DiagnosticReport.render(value,provider=WebViewProviderReader.read(),version="0.4.1",versionCode=18,api=android.os.Build.VERSION.SDK_INT)
        val intent=DiagnosticExport.prepare(context,report)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        val uri=requireNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM,android.net.Uri::class.java))
        assertEquals(context.packageName+".diagnostics",uri.authority)
        val text=context.contentResolver.openInputStream(uri)!!.bufferedReader().use{it.readText()}
        assertEquals(2,JSONObject(text).getInt("schema"));assertFalse(text.contains("private.invalid"));assertFalse(text.contains("192.0.2.1"))
        repeat(8){DiagnosticExport.prepare(context,report)}
        assertTrue(File(context.cacheDir,"diagnostic_reports").listFiles()!!.size<=4)
        val forbidden=File(context.filesDir,"secret-fixture").apply{writeText("private")}
        try{
            try{androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.diagnostics",forbidden);fail("Private files must not be shareable")}
            catch(_:IllegalArgumentException){}
        }finally{forbidden.delete()}
        val reportFile=File(context.cacheDir,"diagnostic_reports/not-an-update.apk").apply{writeText("fixture")}
        try{assertThrows(IllegalArgumentException::class.java){UpdateInstaller(context).installIntent(reportFile)}}finally{reportFile.delete()}
        File(context.cacheDir,"diagnostic_reports").deleteRecursively()
    }
    @Test fun disabledRecordingStillAllowsProviderInspectionWithoutRecreatingHistory(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            settle();click("nav.settings");click("settings.diagnostics",true);click("diagnostics.record")
            assertFalse(AppPreferences(context).diagnostics)
            click("nav.diagnostics");click("webview.refresh",true)
            assertTrue(ProxyState.diagnostics.events.isEmpty())
            scenario.recreate();settle();assertFalse(AppPreferences(context).diagnostics)
            assertTrue(ProxyState.diagnostics.events.isEmpty())
            ui.onNodeWithTag("webview.state").assertExists()
        }
    }
    @Test fun changingPortInvalidatesEarlierCheckWithoutSavingOrChangingSecret(){
        val beforeStart=SettingsStore(context).load()
        ActivityScenario.launch(MainActivity::class.java).use{
            settle();click("proxy.toggle");ui.waitUntil(10000){ProxyState.snapshot.localAvailable}
            assertEquals(beforeStart,SettingsStore(context).load())
            val saved=context.getSharedPreferences("settings",0).all.toMap()
            click("nav.diagnostics");click("diagnostics.local.check",true)
            ui.waitUntil(5000){ui.onAllNodesWithTag("diagnostics.local.result").fetchSemanticsNodes().isNotEmpty()}
            click("nav.home");click("proxy.toggle");ui.waitUntil(10000){!ProxyState.snapshot.running}
            click("nav.settings");click("settings.connection",true)
            ui.onNodeWithTag("settings.port").performTextReplacement("2443");ui.waitForIdle()
            click("nav.diagnostics");ui.onNodeWithTag("diagnostics.local.result").assertDoesNotExist()
            assertEquals(saved,context.getSharedPreferences("settings",0).all)
        }
    }
}
