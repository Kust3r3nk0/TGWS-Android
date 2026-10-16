package io.github.kust3r3nk0.tgwsandroid

import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrandingInstrumentedTest {
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
