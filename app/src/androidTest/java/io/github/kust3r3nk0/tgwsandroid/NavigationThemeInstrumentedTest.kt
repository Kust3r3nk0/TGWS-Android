package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import io.github.kust3r3nk0.tgwsandroid.core.DnsMode
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Native accessibility/input driver; no Espresso hidden InputManager calls. */
@RunWith(AndroidJUnit4::class)
class NavigationThemeInstrumentedTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val device get()=UiDevice.getInstance(instrumentation)
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .bufferedReader().use{it.readText()}
    @Before fun setup(){
        // Only the unrelated emulator launcher is stopped. TGWS errors/ANRs remain visible.
        shell("am force-stop com.google.android.apps.nexuslauncher")
        AppPreferences(context).apply{theme=ThemeMode.SYSTEM;dns=DnsMode.SYSTEM}
        SettingsStore(context).recover(8443,"00112233445566778899aabbccddeeff")
    }
    @After fun cleanup(){AppPreferences(context).apply{dns=DnsMode.SYSTEM;theme=ThemeMode.SYSTEM}}
    private fun selector(tag:String)=By.pkg(context.packageName).res(tag)
    private fun node(tag:String,direction:Direction?=null):UiObject2 {
        device.wait(Until.findObject(selector(tag)),1000)?.let{return it}
        if(direction!=null)repeat(12){
            // Settings also contain independently scrollable text fields. Target the page,
            // not the first accessibility node advertising scrollable=true.
            val area=device.findObject(selector("page.scroll"))?:error("No page scroller for $tag")
            val more=area.scroll(direction,0.65f)
            device.wait(Until.findObject(selector(tag)),1000)?.let{return it}
            if(!more)error("Control not found: $tag")
        }
        error("Control not found: $tag")
    }
    private fun settled(){
        assertTrue(device.wait(Until.hasObject(selector("nav.home")),10_000))
        assertTrue(device.wait(Until.gone(selector("settings.loading")),10_000))
    }
    private fun eventually(check:()->Boolean){
        val deadline=SystemClock.elapsedRealtime()+10_000
        while(!check()){if(SystemClock.elapsedRealtime()>=deadline)error("UI state did not settle");SystemClock.sleep(20)}
    }
    private fun selected(tag:String,direction:Direction=Direction.DOWN){
        val item=node(tag,direction);assertTrue("Expected selected control: $tag",item.isChecked || item.isSelected)
    }
    private fun screenshot(name:String){
        require(name.matches(Regex("[a-z-]+")))
        device.waitForIdle(1000)
        val automation=instrumentation.uiAutomation
        val image=requireNotNull(automation.takeScreenshot())
        try{
            shell("mkdir -p /sdcard/Download/tgws-ui")
            val pipes=automation.executeShellCommandRw("dd of=/sdcard/Download/tgws-ui/$name.png")
            ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use{check(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
            ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use{it.readBytes()}
            check((shell("stat -c %s /sdcard/Download/tgws-ui/$name.png").trim().toLongOrNull()?:0)>0)
        }finally{image.recycle()}
    }
    @Test fun navigationKeepsDraftAndSaveSurvivesActivityRecreation(){
        ActivityScenario.launch(MainActivity::class.java).use{activity->
            settled();assertFalse(device.hasObject(selector("settings.secret")))
            assertNotNull(node("home.network",Direction.DOWN))
            node("nav.settings").click()
            node("settings.port",Direction.DOWN).text="2443"
            node("nav.home").click();assertFalse(device.hasObject(selector("settings.port")))
            node("nav.settings").click();assertEquals("2443",node("settings.port",Direction.DOWN).text)
            node("settings.save",Direction.DOWN).click();eventually{SettingsStore(context).port==2443}
            activity.recreate();settled()
            assertEquals("2443",node("settings.port",Direction.DOWN).text)
            assertFalse(ProxyState.snapshot.running)
        }
    }
    @Test fun themeAndExplicitCloudflareChoicePersistWithoutChangingProtectedConfiguration(){
        val prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
        val before=prefs.getString("secret_encrypted",null)
        ActivityScenario.launch(MainActivity::class.java).use{activity->
            settled();node("nav.settings").click();selected("dns.SYSTEM")
            node("dns.CLOUDFLARE",Direction.DOWN).click()
            node("theme.DARK",Direction.UP).click()
            eventually{AppPreferences(context).dns==DnsMode.CLOUDFLARE && AppPreferences(context).theme==ThemeMode.DARK}
            activity.recreate();settled()
            selected("theme.DARK");selected("dns.CLOUDFLARE")
            node("theme.DARK",Direction.UP);screenshot("settings-dark")
            node("theme.toggle").click();eventually{AppPreferences(context).theme==ThemeMode.LIGHT}
            node("nav.home").click();screenshot("home-light")
            node("theme.toggle").click();eventually{AppPreferences(context).theme==ThemeMode.DARK};screenshot("home-dark")
            assertEquals(before,prefs.getString("secret_encrypted",null))
        }
    }
}
