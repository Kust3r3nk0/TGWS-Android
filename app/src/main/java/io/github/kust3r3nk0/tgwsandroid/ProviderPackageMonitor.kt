package io.github.kust3r3nk0.tgwsandroid

import android.content.*
import androidx.core.content.ContextCompat
import java.io.Closeable

/** Observe package changes only while the Activity is visible. Never inventories installed apps. */
internal class ProviderPackageMonitor(private val context:Context,private val selected:()->String?,private val refresh:()->Unit):Closeable {
    private val receiver=object:BroadcastReceiver(){
        override fun onReceive(context:Context?,intent:Intent?){
            val provider=selected()
            if(provider==null || intent?.data?.schemeSpecificPart==provider)refresh()
        }
    }
    private var registered=false
    fun start(){
        if(registered)return
        val filter=IntentFilter().apply{
            addAction(Intent.ACTION_PACKAGE_ADDED);addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED);addAction(Intent.ACTION_PACKAGE_CHANGED);addDataScheme("package")
        }
        ContextCompat.registerReceiver(context,receiver,filter,ContextCompat.RECEIVER_NOT_EXPORTED)
        registered=true
    }
    override fun close(){if(registered){context.unregisterReceiver(receiver);registered=false}}
}
