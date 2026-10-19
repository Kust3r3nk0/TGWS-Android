package io.github.kust3r3nk0.tgwsandroid

import android.graphics.drawable.AdaptiveIconDrawable
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrandingInstrumentedTest {
    @Test fun ownerResultsRemainAnchoredToTheTestedVersionInBothLanguages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (language in listOf("ru", "en")) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList(Locale.forLanguageTag(language)))
            }
            val localized = context.createConfigurationContext(configuration)
            val report = localized.getString(R.string.owner_validation_summary)
            assertTrue(report.contains("0.3.6"))
            assertTrue(report.contains("Android 16 / One UI 8.5"))
            assertFalse(localized.getString(R.string.alpha_notice).contains("pending", ignoreCase = true))
            assertFalse(localized.getString(R.string.alpha_notice).contains("ожидается", ignoreCase = true))
        }
    }
    @Test fun aboutLabelMatchesInstalledMinimumInBothLanguages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(36, context.applicationInfo.minSdkVersion)
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName!!
        for (language in listOf("ru", "en")) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList(Locale.forLanguageTag(language)))
            }
            val localized = context.createConfigurationContext(configuration)
            assertEquals("Android 16+ · $version", localized.getString(R.string.version_label, version))
        }
    }
    @Test fun installedApplicationHasUserBrandingAndAndroid16Minimum() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.applicationInfo
        assertEquals(36, info.minSdkVersion)
        assertTrue(Build.VERSION.SDK_INT >= 36)
        assertEquals("TGWS-Android", context.packageManager.getApplicationLabel(info).toString())
        val icon = context.packageManager.getApplicationIcon(info)
        assertTrue(icon is AdaptiveIconDrawable)
        assertTrue(icon.intrinsicWidth > 0 && icon.intrinsicHeight > 0)
    }
}
