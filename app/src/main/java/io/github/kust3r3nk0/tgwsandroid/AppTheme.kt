package io.github.kust3r3nk0.tgwsandroid

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors=lightColorScheme(primary=Color(0xFF155C94),onPrimary=Color.White,
    primaryContainer=Color(0xFFD3E8FF),onPrimaryContainer=Color(0xFF073453),
    secondary=Color(0xFF006A69),secondaryContainer=Color(0xFF9CF1E9),onSecondaryContainer=Color(0xFF004746),
    background=Color(0xFFF2F6FB),surface=Color(0xFFF8FAFF),surfaceContainer=Color(0xFFE9F0F8))
private val DarkColors=darkColorScheme(primary=Color(0xFF9FCAFF),onPrimary=Color(0xFF003259),
    primaryContainer=Color(0xFF134A74),onPrimaryContainer=Color(0xFFD3E8FF),
    secondary=Color(0xFF7CD7D0),secondaryContainer=Color(0xFF00504E),onSecondaryContainer=Color(0xFFA0F2EB),
    background=Color(0xFF0C141E),surface=Color(0xFF111D2B),surfaceContainer=Color(0xFF1B2A3B))

@Composable internal fun darkTheme(mode:ThemeMode)=when(mode){ThemeMode.SYSTEM->isSystemInDarkTheme();ThemeMode.DARK->true;ThemeMode.LIGHT->false}
internal val LocalLiquidGlass=staticCompositionLocalOf{false}
@Composable internal fun TgwsTheme(mode:ThemeMode,liquidGlass:Boolean=false,content:@Composable ()->Unit){
    CompositionLocalProvider(LocalLiquidGlass provides liquidGlass){
        MaterialTheme(colorScheme=if(darkTheme(mode))DarkColors else LightColors,content=content)
    }
}
