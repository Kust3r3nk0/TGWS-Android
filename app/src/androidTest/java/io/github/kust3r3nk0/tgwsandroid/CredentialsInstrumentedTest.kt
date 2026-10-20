package io.github.kust3r3nk0.tgwsandroid

import android.content.ClipboardManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import io.github.kust3r3nk0.tgwsandroid.core.TelegramSecret
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialsInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val original = "00112233445566778899aabbccddeeff"
    private val replacement = "ffeeddccbbaa99887766554433221100"
    @Before fun setup() {
        SettingsStore(context).recover(8443, original)
        instrumentation.runOnMainSync { ProxyState.snapshot = ProxySnapshot() }
    }
    private fun selector(tag: String) = By.pkg(context.packageName).res(tag)
    private fun node(tag: String, up: Boolean = false): UiObject2 {
        device.wait(Until.findObject(selector(tag)), 1000)?.let { return it }
        repeat(16) {
            val area = requireNotNull(device.findObject(selector("page.scroll")))
            val more = area.accessibilityNodeInfo.performAction(if (up)
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            device.wait(Until.findObject(selector(tag)), 1000)?.let { return it }
            check(more) { "Missing UI control: $tag" }
        }
        error("Missing UI control: $tag")
    }
    private fun ready() {
        assertTrue(device.wait(Until.hasObject(selector("nav.home")), 10_000))
        assertTrue(device.wait(Until.gone(selector("settings.loading")), 10_000))
    }
    private fun waitFor(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Credential operation did not finish" }
            SystemClock.sleep(20)
        }
    }
    @Test fun pastedPrefixedSecretsKeepWholeKeyAndDomainWhenSaved() {
        for (domain in listOf("", "example.com")) {
            ActivityScenario.launch(MainActivity::class.java).use {
                ready(); node("nav.settings").click();node("settings.connection").click()
                node("settings.secret").text = TelegramSecret.encode(replacement, domain)
                node("settings.save").click()
                waitFor { SettingsStore(context).load().let { it.secret == replacement && it.transport.fakeTlsDomain == domain } }
                assertFalse(ProxyState.snapshot.running)
            }
        }
    }
    @Test fun copyingDraftLinkPersistsExactCredentialsBeforeRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            ready(); node("nav.settings").click();node("settings.connection").click()
            node("settings.port").text = "2443"
            node("settings.secret").text = replacement
            node("nav.home").click()
            node("nav.settings").click();node("settings.connection").click()
            node("telegram.copy_link").click()
            waitFor { SettingsStore(context).load().let { it.port == 2443 && it.secret == replacement } }
            assertTrue(device.wait(Until.gone(selector("settings.loading")), 10_000))
            var link = ""
            waitFor {
                activity.onActivity { screen ->
                    link = screen.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                }
                link == SettingsValidation.telegramLink(2443, replacement)
            }
            val imported = TelegramSecret.import(link)
            assertEquals(2443, imported.port)
            assertEquals(replacement, imported.decoded.secret)
            activity.recreate(); ready()
            assertEquals(replacement, SettingsStore(context).load().secret)
            assertFalse(ProxyState.snapshot.running)
        }
    }
    @Test fun malformedLongSecretCannotBeTruncatedIntoValidSavedKey() {
        ActivityScenario.launch(MainActivity::class.java).use {
            ready(); node("nav.settings").click();node("settings.connection").click()
            node("settings.secret").text = replacement + "broken"
            assertFalse(node("settings.save").isEnabled)
            node("nav.home").click()
            assertFalse(node("telegram.share").isEnabled)
            assertEquals(original, SettingsStore(context).load().secret)
        }
    }
}
