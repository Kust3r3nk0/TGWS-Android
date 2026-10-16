package io.github.kust3r3nk0.tgwsandroid

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Public UI preferences, separate from all Keystore-protected proxy configuration. */
class AppPreferences(context:Context){
    private val prefs=context.applicationContext.getSharedPreferences("app_preferences",Context.MODE_PRIVATE)
    // Retire the old provider selection without touching Keystore/proxy/UI settings.
    init { if(prefs.contains("dns"))prefs.edit().remove("dns").apply() }
    var transparency:Boolean
        get()=runCatching{prefs.getBoolean("transparency",false)}.getOrDefault(false)
        set(value){prefs.edit().putBoolean("transparency",value).apply()}
    var liquidGlass:Boolean
        get()=runCatching{prefs.getBoolean("liquid_glass",false)}.getOrDefault(false)
        set(value){prefs.edit().putBoolean("liquid_glass",value).apply()}
    var theme:ThemeMode
        get()=runCatching{ThemeMode.valueOf(prefs.getString("theme",ThemeMode.SYSTEM.name)!!)}.getOrDefault(ThemeMode.SYSTEM)
        set(value){prefs.edit().putString("theme",value.name).apply()}
}
