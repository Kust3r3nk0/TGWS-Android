package io.github.kust3r3nk0.tgwsandroid

internal enum class ProviderState { UNCHECKED, AVAILABLE, MISSING, QUERY_FAILED }
internal enum class ProviderChannel { STABLE, BETA, DEV, CANARY, SYSTEM, UNKNOWN }

/** Selected provider metadata only. No browser engine, pages, console or browsing history. */
internal data class WebViewStatus(val state:ProviderState=ProviderState.UNCHECKED,val packageName:String?=null,
    val version:String?=null,val versionCode:Long?=null,val channel:ProviderChannel=ProviderChannel.UNKNOWN) {
    companion object {
        fun available(packageName:String?,version:String?,code:Long):WebViewStatus {
            val pkg=packageName?.takeIf{it.length<=200 && it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))}
                ?:return WebViewStatus(ProviderState.QUERY_FAILED)
            val numericVersion=version?.takeIf{it.length<=80 && it.matches(Regex("[0-9]+(?:\\.[0-9]+){0,5}"))}
            val channel=when(pkg){
                "com.google.android.webview"->ProviderChannel.STABLE
                "com.google.android.webview.beta"->ProviderChannel.BETA
                "com.google.android.webview.dev"->ProviderChannel.DEV
                "com.google.android.webview.canary"->ProviderChannel.CANARY
                "com.android.webview"->ProviderChannel.SYSTEM
                else->ProviderChannel.UNKNOWN
            }
            return WebViewStatus(ProviderState.AVAILABLE,pkg,numericVersion,code.takeIf{it>=0},channel)
        }
    }
}
