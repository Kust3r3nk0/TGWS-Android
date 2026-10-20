package io.github.kust3r3nk0.tgwsandroid

/** Android documents that this metadata API does not load the browser engine. */
internal object WebViewProviderReader {
    fun read():WebViewStatus = try {
        val info=android.webkit.WebView.getCurrentWebViewPackage()
        if(info==null)WebViewStatus(ProviderState.MISSING)
        else WebViewStatus.available(info.packageName,info.versionName,info.longVersionCode)
    } catch(_:RuntimeException) {
        WebViewStatus(ProviderState.QUERY_FAILED)
    } catch(_:LinkageError) {
        WebViewStatus(ProviderState.QUERY_FAILED)
    }
}
