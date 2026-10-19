package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
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
        AppPreferences(context).apply{theme=ThemeMode.SYSTEM;liquidGlass=false;transparency=false}
        SettingsStore(context).recover(8443,"00112233445566778899aabbccddeeff")
    }
    @After fun cleanup(){AppPreferences(context).apply{theme=ThemeMode.SYSTEM;liquidGlass=false;transparency=false}}
    private fun selector(tag:String)=By.pkg(context.packageName).res(tag)
    private fun node(tag:String,direction:Direction?=null):UiObject2 {
        device.wait(Until.findObject(selector(tag)),1000)?.let{return it}
        if(direction!=null)repeat(12){
            // Use the page's public Android accessibility scroll action. Gesture-driver
            // scroll completion events are unreliable for Compose's virtual scroll node.
            val area=device.findObject(selector("page.scroll"))?:error("No page scroller for $tag")
            val action=if(direction==Direction.DOWN)AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            val more=area.accessibilityNodeInfo.performAction(action)
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
    private fun screenshot(name:String,dark:Boolean){
        require(name.matches(Regex("[a-z-]+")))
        device.waitForIdle(1000)
        val automation=instrumentation.uiAutomation
        // Preference writes and accessibility idle can precede the compositor frame.
        // Verify the page and both system-bar margins before retaining a PNG.
        val deadline=SystemClock.elapsedRealtime()+5000
        var image=requireNotNull(automation.takeScreenshot())
        fun matchesTheme()=listOf(0,image.height/2,image.height-1).all{y->
            (android.graphics.Color.luminance(image.getPixel(0,y))<0.5f)==dark
        }
        var stableSince:Long?=null
        try{while(true){
            if(matchesTheme()){
                val now=SystemClock.elapsedRealtime()
                if(stableSince==null)stableSince=now
                // Allow Material component colour transitions to finish as well.
                if(now-requireNotNull(stableSince)>=300)break
            }else stableSince=null
            if(SystemClock.elapsedRealtime()>=deadline){
                writeScreenshot("failure-$name",image)
                error("Expected theme was not rendered: $name; pixels="+
                    listOf(0,image.height/2,image.height-1).joinToString{y->Integer.toHexString(image.getPixel(0,y))})
            }
            image.recycle()
            SystemClock.sleep(16)
            image=requireNotNull(automation.takeScreenshot())
        }
            writeScreenshot(name,image)
        }finally{image.recycle()}
    }
    private fun writeScreenshot(name:String,image:android.graphics.Bitmap){
        require(name.matches(Regex("[a-z-]+")))
        shell("mkdir -p /sdcard/Download/tgws-ui")
        val pipes=instrumentation.uiAutomation.executeShellCommandRw("dd of=/sdcard/Download/tgws-ui/$name.png")
        ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use{check(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use{it.readBytes()}
        check((shell("stat -c %s /sdcard/Download/tgws-ui/$name.png").trim().toLongOrNull()?:0)>0)
    }
    private fun readableText(resource:Int,dark:Boolean){
        val text=context.getString(resource)
        val bounds=requireNotNull(device.wait(Until.findObject(By.pkg(context.packageName).text(text)),5000)).visibleBounds
        val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try{
            var glyphPixels=0
            for(y in bounds.top until bounds.bottom)for(x in bounds.left until bounds.right){
                val luminance=android.graphics.Color.luminance(image.getPixel(x,y))
                if(if(dark)luminance>0.55f else luminance<0.20f)glyphPixels++
            }
            // The text rectangle must contain visible foreground glyphs, not just
            // a correctly coloured panel. Catches black inherited text on dark glass.
            assertTrue("Text is not visible in the rendered theme: $text",glyphPixels>bounds.width()*bounds.height()/50)
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
            // rememberScrollState restores the bottom viewport after recreation.
            assertEquals("2443",node("settings.port",Direction.UP).text)
            assertFalse(ProxyState.snapshot.running)
        }
    }
    @Test fun retiredDnsMigrationHandlesEveryLegacyValueAndPreservesAllOtherPreferences(){
        val legacy=context.getSharedPreferences("app_preferences",Context.MODE_PRIVATE)
        val protected=context.getSharedPreferences("settings",Context.MODE_PRIVATE).all.toMap()
        for(value in listOf<Any>("CLOUDFLARE","SYSTEM","unknown",42)){
            val update=legacy.edit().putString("theme","DARK").putBoolean("liquid_glass",true).putBoolean("transparency",true)
            if(value is Int)update.putInt("dns",value)else update.putString("dns",value as String)
            assertTrue(update.commit())
            AppPreferences(context).let{assertEquals(ThemeMode.DARK,it.theme);assertTrue(it.liquidGlass);assertTrue(it.transparency)}
            assertFalse(legacy.contains("dns"));assertEquals(protected,context.getSharedPreferences("settings",Context.MODE_PRIVATE).all)
        }
    }
    @Test fun themeAndRetiredCloudflareChoiceMigrateWithoutChangingProtectedConfiguration(){
        val prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
        val before=prefs.getString("secret_encrypted",null)
        val legacy=context.getSharedPreferences("app_preferences",Context.MODE_PRIVATE)
        assertTrue(legacy.edit().putString("dns","CLOUDFLARE").commit())
        ActivityScenario.launch(MainActivity::class.java).use{activity->
            settled();node("nav.settings").click()
            assertNotNull(node("dns.system",Direction.DOWN))
            assertNull(device.findObject(selector("dns.CLOUDFLARE")))
            node("theme.DARK",Direction.UP).click()
            eventually{!legacy.contains("dns") && AppPreferences(context).theme==ThemeMode.DARK}
            activity.recreate();settled()
            selected("theme.DARK");assertFalse(legacy.contains("dns"))
            node("theme.DARK",Direction.UP);screenshot("settings-dark",dark=true)
            node("theme.toggle").click();eventually{AppPreferences(context).theme==ThemeMode.LIGHT}
            node("nav.home").click();screenshot("home-light",dark=false)
            node("theme.toggle").click();eventually{AppPreferences(context).theme==ThemeMode.DARK};screenshot("home-dark",dark=true)
            node("nav.settings").click()
            val version=context.packageManager.getPackageInfo(context.packageName,0).versionName
            assertEquals("Android 16+ · $version",node("about.version",Direction.DOWN).text)
            screenshot("about-dark",dark=true)
            assertEquals(before,prefs.getString("secret_encrypted",null))
        }
    }
    @Test fun liquidGlassDefaultsOffPersistsAndActuallyChangesBothThemesWithoutChangingProxySettings(){
        val publicPrefs=context.getSharedPreferences("app_preferences",Context.MODE_PRIVATE)
        publicPrefs.edit().putString("liquid_glass","invalid").commit()
        assertFalse(AppPreferences(context).liquidGlass)
        publicPrefs.edit().remove("liquid_glass").commit()
        assertFalse(AppPreferences(context).liquidGlass)
        AppPreferences(context).theme=ThemeMode.LIGHT
        val protectedPrefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
        val before=protectedPrefs.all.toMap()
        fun margin():Int {
            val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            return try{image.getPixel(0,image.height/2)}finally{image.recycle()}
        }
        ActivityScenario.launch(MainActivity::class.java).use{activity->
            settled();screenshot("glass-disabled",dark=false)
            val normal=margin()
            node("nav.settings").click()
            assertFalse(node("appearance.glass",Direction.UP).isChecked)
            node("appearance.glass").click();eventually{AppPreferences(context).liquidGlass}
            activity.recreate();settled()
            assertTrue(node("appearance.glass",Direction.UP).isChecked)
            screenshot("glass-settings",dark=false)
            node("nav.home").click();eventually{margin()!=normal}
            screenshot("glass-light",dark=false)
            readableText(R.string.app_name,dark=false);readableText(R.string.status_stopped,dark=false)
            node("theme.toggle").click();eventually{AppPreferences(context).theme==ThemeMode.DARK}
            screenshot("glass-dark",dark=true)
            readableText(R.string.app_name,dark=true);readableText(R.string.status_stopped,dark=true)
            node("nav.settings").click();node("appearance.glass",Direction.UP).click()
            eventually{!AppPreferences(context).liquidGlass}
            activity.recreate();settled()
            assertFalse(node("appearance.glass",Direction.UP).isChecked)
            node("theme.toggle").click();eventually{AppPreferences(context).theme==ThemeMode.LIGHT}
            node("nav.home").click();eventually{margin()==normal}
            assertEquals(before,protectedPrefs.all)
            assertFalse(context.getSharedPreferences("app_preferences",Context.MODE_PRIVATE).contains("dns"))
            assertFalse(ProxyState.snapshot.running)
        }
    }
    @Test fun transparencyPersistsIndependentlyWithReadableThemesAndUnchangedProxyConfiguration(){
        val publicPrefs=context.getSharedPreferences("app_preferences",Context.MODE_PRIVATE)
        publicPrefs.edit().putString("transparency","invalid").commit()
        assertFalse(AppPreferences(context).transparency)
        publicPrefs.edit().remove("transparency").commit()
        assertFalse(AppPreferences(context).transparency)
        AppPreferences(context).theme=ThemeMode.LIGHT
        val protectedPrefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
        val before=protectedPrefs.all.toMap()
        ActivityScenario.launch(MainActivity::class.java).use{activity->
            settled();node("nav.settings").click()
            assertFalse(node("appearance.transparency",Direction.DOWN).isChecked)
            node("appearance.transparency").click();eventually{AppPreferences(context).transparency}
            assertFalse(AppPreferences(context).liquidGlass)
            activity.recreate();settled()
            assertTrue(node("appearance.transparency",Direction.UP).isChecked)
            screenshot("transparency-settings",dark=false)
            node("nav.home").click();screenshot("transparency-light",dark=false)
            readableText(R.string.app_name,dark=false);readableText(R.string.status_stopped,dark=false)
            node("theme.toggle").click();eventually{AppPreferences(context).theme==ThemeMode.DARK}
            screenshot("transparency-dark",dark=true)
            readableText(R.string.app_name,dark=true);readableText(R.string.status_stopped,dark=true)
            node("nav.settings").click();node("appearance.glass",Direction.UP).click()
            eventually{AppPreferences(context).liquidGlass && AppPreferences(context).transparency}
            node("nav.home").click();screenshot("transparency-combined",dark=true)
            readableText(R.string.app_name,dark=true);readableText(R.string.status_stopped,dark=true)
            node("nav.settings").click();node("appearance.transparency",Direction.DOWN).click()
            eventually{!AppPreferences(context).transparency}
            assertTrue(AppPreferences(context).liquidGlass)
            node("appearance.glass",Direction.UP).click()
            activity.recreate();settled()
            assertFalse(node("appearance.transparency",Direction.UP).isChecked)
            assertFalse(AppPreferences(context).liquidGlass)
            assertEquals(before,protectedPrefs.all)
            assertFalse(context.getSharedPreferences("app_preferences",Context.MODE_PRIVATE).contains("dns"))
        }
    }
    @Test fun transparentPanelsActuallyCompositeTheParentBackdropInBothThemesAndGlassModes(){
        ActivityScenario.launch(MainActivity::class.java).use{activity->
            settled()
            for(mode in listOf(ThemeMode.LIGHT,ThemeMode.DARK))for(glass in listOf(false,true)){
                val backdrop=mutableStateOf(Color.Red)
                activity.onActivity{screen->screen.setContent{
                    TgwsTheme(mode,liquidGlass=glass,transparency=true){
                        Box(Modifier.fillMaxSize().background(backdrop.value).semantics{testTagsAsResourceId=true}){
                            SectionCard(context.getString(R.string.app_name),Modifier.width(280.dp).height(180.dp)
                                .align(Alignment.Center).testTag("proof.panel")){
                                Text(context.getString(R.string.status_stopped))
                            }
                        }
                    }
                }}
                // setContent returns before the first accessibility/layout frame on a busy software GPU.
                val panel=requireNotNull(device.wait(Until.findObject(selector("proof.panel")),10_000)) {
                    "Proof panel was not composed"
                }.visibleBounds
                fun sample():Int {
                    val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                    return try{image.getPixel(panel.centerX(),panel.bottom-24)}finally{image.recycle()}
                }
                // Change only the real parent, not the shader's procedural backdrop.
                // Opaque paint or a simulated transparency gradient cannot pass this.
                eventually{sample().let{android.graphics.Color.red(it)-android.graphics.Color.green(it)>60}}
                val red=sample()
                activity.onActivity{backdrop.value=Color.Green}
                eventually{sample().let{android.graphics.Color.green(it)-android.graphics.Color.red(it)>60}}
                val green=sample()
                assertTrue("Parent must show through: $mode glass=$glass",android.graphics.Color.red(red)-android.graphics.Color.red(green)>60)
                assertTrue(android.graphics.Color.green(green)-android.graphics.Color.green(red)>60)
            }
        }
    }
}
