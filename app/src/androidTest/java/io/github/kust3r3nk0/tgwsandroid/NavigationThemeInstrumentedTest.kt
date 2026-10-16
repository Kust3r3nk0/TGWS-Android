package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kust3r3nk0.tgwsandroid.core.DnsMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationThemeInstrumentedTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup(){
        AppPreferences(context).apply{theme=ThemeMode.SYSTEM;dns=DnsMode.SYSTEM}
        SettingsStore(context).recover(8443,"00112233445566778899aabbccddeeff")
    }
    @After fun cleanup(){AppPreferences(context).apply{dns=DnsMode.SYSTEM;theme=ThemeMode.SYSTEM}}
    private fun settled(){compose.waitUntil(10_000){compose.onAllNodesWithTag("nav.home").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("settings.loading").fetchSemanticsNodes().isEmpty()};compose.waitForIdle()}
    private fun loaded(){settled();compose.onNodeWithTag("home.status").assertExists()}
    private fun screenshot(name:String){
        compose.waitForIdle()
        require(name.matches(Regex("[a-z-]+")))
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val image=requireNotNull(automation.takeScreenshot())
        // AGP removes app-owned storage after tests. Shell-owned CI artifacts survive teardown.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("mkdir -p /sdcard/Download/tgws-ui")).use{it.readBytes()}
        val pipes=automation.executeShellCommandRw("dd of=/sdcard/Download/tgws-ui/$name.png")
        android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use{check(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use{it.readBytes()}
        val size=android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("stat -c %s /sdcard/Download/tgws-ui/$name.png"))
            .bufferedReader().use{it.readText().trim().toLongOrNull()}
        check(size!=null && size>0)
        image.recycle()
    }
    @Test fun navigationKeepsDraftAndSaveSurvivesActivityRecreation(){
        ActivityScenario.launch(MainActivity::class.java).use{activity->
            loaded();compose.onNodeWithTag("settings.secret").assertDoesNotExist()
            compose.onNodeWithTag("home.network").assertExists()
            compose.onNodeWithTag("nav.settings").performClick()
            compose.onNodeWithTag("settings.port").performScrollTo().performTextReplacement("2443")
            compose.onNodeWithTag("nav.home").performClick();compose.onNodeWithTag("settings.port").assertDoesNotExist()
            compose.onNodeWithTag("nav.settings").performClick()
            compose.onNodeWithTag("settings.port").performScrollTo().assertTextContains("2443")
            compose.onNodeWithTag("settings.save").performScrollTo().performClick()
            compose.waitUntil(10_000){SettingsStore(context).port==2443}
            activity.recreate();settled()
            compose.onNodeWithTag("settings.port").performScrollTo().assertTextContains("2443")
            assertFalse(ProxyState.snapshot.running)
        }
    }
    @Test fun themeAndExplicitCloudflareChoicePersistWithoutChangingProtectedConfiguration(){
        val prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
        val before=prefs.getString("secret_encrypted",null)
        ActivityScenario.launch(MainActivity::class.java).use{activity->
            loaded();compose.onNodeWithTag("nav.settings").performClick()
            compose.onNodeWithTag("dns.SYSTEM").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("dns.CLOUDFLARE").performScrollTo().performClick()
            compose.onNodeWithTag("theme.DARK").performScrollTo().performClick()
            assertEquals(DnsMode.CLOUDFLARE,AppPreferences(context).dns)
            assertEquals(ThemeMode.DARK,AppPreferences(context).theme)
            activity.recreate();settled()
            compose.onNodeWithTag("theme.DARK").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("dns.CLOUDFLARE").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("theme.DARK").performScrollTo();screenshot("settings-dark")
            compose.onNodeWithTag("theme.toggle").performClick()
            compose.waitForIdle();assertEquals(ThemeMode.LIGHT,AppPreferences(context).theme)
            compose.onNodeWithTag("nav.home").performClick();screenshot("home-light")
            compose.onNodeWithTag("theme.toggle").performClick();screenshot("home-dark")
            assertEquals(before,prefs.getString("secret_encrypted",null))
        }
        AppPreferences(context).apply{dns=DnsMode.SYSTEM;theme=ThemeMode.SYSTEM}
    }
}
