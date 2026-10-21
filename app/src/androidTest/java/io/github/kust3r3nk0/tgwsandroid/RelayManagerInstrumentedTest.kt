package io.github.kust3r3nk0.tgwsandroid

import android.app.LocaleManager
import android.content.Intent
import android.os.LocaleList
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
class RelayManagerInstrumentedTest {
    @get:Rule val ui=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private lateinit var locales:LocaleList
    private val secret="00112233445566778899aabbccddeeff"
    private val legacy="wss://legacy.example/old-token/apiws"
    @Before fun setup(){
        locales=context.getSystemService(LocaleManager::class.java).applicationLocales
        instrumentation.runOnMainSync{context.getSystemService(LocaleManager::class.java).applicationLocales=LocaleList.forLanguageTags("ru");ProxyState.snapshot=ProxySnapshot()}
        AppPreferences(context).theme=ThemeMode.DARK
        SettingsStore(context).recover(8443,secret,legacy)
    }
    @After fun cleanup(){
        context.stopService(Intent(context,ProxyService::class.java));SettingsStore(context).recover(8443,secret)
        AppPreferences(context).theme=ThemeMode.SYSTEM
        instrumentation.runOnMainSync{context.getSystemService(LocaleManager::class.java).applicationLocales=locales}
    }
    private fun settle(){ui.waitForIdle();ui.waitUntil(10000){ui.onAllNodesWithTag("settings.loading").fetchSemanticsNodes().isEmpty()}}
    private fun click(tag:String,scroll:Boolean=true){if(scroll)ui.onNodeWithTag(tag).performScrollTo();ui.onNodeWithTag(tag).performClick();ui.waitForIdle()}
    private fun text(tag:String,value:String){ui.onNodeWithTag(tag).performScrollTo().performTextReplacement(value);ui.waitForIdle()}
    private fun shot(){
        ui.waitForIdle()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/tgws-ui")).use{it.readBytes()}
        val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try{val pipe=instrumentation.uiAutomation.executeShellCommandRw("dd of=/sdcard/Download/tgws-ui/relay-manager-dark.png")
            android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use{assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use{it.readBytes()}
        }finally{image.recycle()}
    }
    @Test fun encryptedNodesAndPolicyPersistAcrossRecreationAndDeleteWithoutReplacingSecret(){
        val keyStore=java.security.KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        val created=keyStore.getCreationDate("tgws-secret-aes-v1")
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            settle();click("nav.settings",false);click("settings.relays")
            for((name,url) in listOf("Primary" to "wss://one.example/private/apiws","Backup" to "wss://two.example/secret/apiws")){
                text("relays.name",name);text("relays.url",url);click("relays.apply")
            }
            click("relays.enabled");click("relays.mode.AUTOMATIC");click("relays.prefer.media");click("settings.save")
            ui.waitUntil(10000){SettingsStore(context).load().transport.relays.nodes.size==2}
            val first=SettingsStore(context).load().transport.relays
            click("relays.primary.${first.nodes[1].id}");click("settings.save")
            ui.waitUntil(10000){SettingsStore(context).load().transport.relays.primary==first.nodes[1].id}
            scenario.recreate();settle();ui.onNodeWithTag("relays.enabled").assertIsOn()
            val saved=SettingsStore(context).load()
            assertEquals(legacy,saved.relayUrl);assertEquals(secret,saved.secret);assertTrue(saved.transport.relays.preferMedia)
            assertEquals(listOf(first.nodes[0].id),saved.transport.relays.backups)
            assertEquals(created,keyStore.getCreationDate("tgws-secret-aes-v1"))
            for(value in context.getSharedPreferences("settings",0).all.values.filterIsInstance<String>()){
                assertFalse(value.contains("one.example"));assertFalse(value.contains("two.example"));assertFalse(value.contains(secret))
            }
            ui.onNodeWithTag("relays.enabled").performScrollTo();shot()
            click("relays.edit.${first.nodes[0].id}");text("relays.name","Edited");click("relays.apply");click("settings.save")
            ui.waitUntil(10000){SettingsStore(context).load().transport.relays.nodes.first().name=="Edited"}
            for(node in first.nodes.reversed()){
                click("relays.delete.${node.id}");click("relays.delete.confirm",false);click("settings.save")
                ui.waitUntil(10000){SettingsStore(context).load().transport.relays.nodes.none{it.id==node.id}}
            }
            val after=SettingsStore(context).load();assertFalse(after.transport.relays.enabled);assertEquals(legacy,after.relayUrl);assertEquals(secret,after.secret)
            assertFalse(ProxyState.snapshot.running)
        }
    }
    @Test fun unsavedEndpointDraftSurvivesRotationWithoutPlaintextPersistenceOrStartingProxy(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            settle();click("nav.settings",false);click("settings.relays");text("relays.name","Draft");text("relays.url","wss://draft.example/hidden/apiws")
            scenario.recreate();settle();ui.onNodeWithTag("relays.name").assertTextEquals("Draft")
            ui.onNodeWithTag("relays.url").assertTextEquals("wss://draft.example/hidden/apiws")
            assertTrue(SettingsStore(context).load().transport.relays.nodes.isEmpty());assertEquals(secret,SettingsStore(context).load().secret)
            assertFalse(ProxyState.snapshot.running)
            ui.onNodeWithTag("relays.reveal").performScrollTo().performClick()
            scenario.onActivity{assertTrue(it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0)}
            click("nav.home",false)
            scenario.onActivity{assertEquals(0,it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE)}
        }
    }
}
