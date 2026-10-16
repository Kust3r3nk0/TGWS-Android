package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import io.github.kust3r3nk0.tgwsandroid.core.DnsMode

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Public UI/DNS preferences, separate from all Keystore-protected proxy configuration. */
class AppPreferences(context:Context){
    private val prefs=context.applicationContext.getSharedPreferences("app_preferences",Context.MODE_PRIVATE)
    var theme:ThemeMode
        get()=runCatching{ThemeMode.valueOf(prefs.getString("theme",ThemeMode.SYSTEM.name)!!)}.getOrDefault(ThemeMode.SYSTEM)
        set(value){prefs.edit().putString("theme",value.name).apply()}
    var dns:DnsMode
        get()=runCatching{DnsMode.valueOf(prefs.getString("dns",DnsMode.SYSTEM.name)!!)}.getOrDefault(DnsMode.SYSTEM)
        set(value){prefs.edit().putString("dns",value.name).apply()}
}
