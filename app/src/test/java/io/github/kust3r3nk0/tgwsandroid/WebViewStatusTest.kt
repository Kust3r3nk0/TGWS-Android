package io.github.kust3r3nk0.tgwsandroid

import org.junit.Assert.*
import org.junit.Test

class WebViewStatusTest {
    @Test fun allKnownChannelsKeepExactFutureVersionWithoutPinnedEngineDependency(){
        for((pkg,channel)in listOf("com.google.android.webview" to ProviderChannel.STABLE,
            "com.google.android.webview.beta" to ProviderChannel.BETA,"com.google.android.webview.dev" to ProviderChannel.DEV,
            "com.google.android.webview.canary" to ProviderChannel.CANARY,"com.android.webview" to ProviderChannel.SYSTEM)){
            val p=WebViewStatus.available(pkg,"999.0.123456.7",999)
            assertEquals(channel,p.channel);assertEquals("999.0.123456.7",p.version);assertEquals(pkg,p.packageName)
        }
    }
    @Test fun unknownOemAndMalformedMetadataCannotClaimStableOrLeakStrings(){
        assertEquals(ProviderChannel.UNKNOWN,WebViewStatus.available("org.vendor.browser","1.0",1).channel)
        assertEquals(ProviderState.QUERY_FAILED,WebViewStatus.available("private\nendpoint",null,1).state)
        val p=WebViewStatus.available("com.android.webview","private endpoint",-1)
        assertNull(p.version);assertNull(p.versionCode)
        assertNotEquals(WebViewStatus(ProviderState.MISSING),WebViewStatus(ProviderState.QUERY_FAILED))
    }
}
