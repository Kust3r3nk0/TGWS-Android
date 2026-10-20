package io.github.kust3r3nk0.tgwsandroid

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary=Color(0xFF0062C6), onPrimary=Color.White,
    primaryContainer=Color(0xFFD9E8FF), onPrimaryContainer=Color(0xFF003064),
    secondary=Color(0xFF006B4A), onSecondary=Color.White,
    secondaryContainer=Color(0xFFCBF5E2), onSecondaryContainer=Color(0xFF003925),
    background=Color(0xFFF2F5F9), onBackground=Color(0xFF14202D),
    surface=Color.White, onSurface=Color(0xFF14202D),
    surfaceContainer=Color(0xFFE5ECF4), surfaceVariant=Color(0xFFEAF0F7),
    onSurfaceVariant=Color(0xFF47596B), outline=Color(0xFF687B8D),
    outlineVariant=Color(0xFFD4DFEA), error=Color(0xFFB91F3C))
private val DarkColors = darkColorScheme(
    primary=Color(0xFF2387FF), onPrimary=Color(0xFF001C3D),
    primaryContainer=Color(0xFF123D6E), onPrimaryContainer=Color(0xFFD8E9FF),
    secondary=Color(0xFF32D994), onSecondary=Color(0xFF003824),
    secondaryContainer=Color(0xFF103D30), onSecondaryContainer=Color(0xFFA4F4CD),
    background=Color(0xFF0B1016), onBackground=Color(0xFFF5F7FB),
    surface=Color(0xFF151D27), onSurface=Color(0xFFF5F7FB),
    surfaceContainer=Color(0xFF1C2733), surfaceVariant=Color(0xFF1C2733),
    onSurfaceVariant=Color(0xFFB0BFCD), outline=Color(0xFF8397AA),
    outlineVariant=Color(0xFF2A3747), error=Color(0xFFFF6474))

@Composable internal fun darkTheme(mode:ThemeMode)=when(mode){ThemeMode.SYSTEM->isSystemInDarkTheme();ThemeMode.DARK->true;ThemeMode.LIGHT->false}
internal val LocalLiquidGlass=staticCompositionLocalOf{false}
internal val LocalTransparency=staticCompositionLocalOf{false}
@Composable internal fun TgwsTheme(mode:ThemeMode,liquidGlass:Boolean=false,transparency:Boolean=false,content:@Composable ()->Unit){
    CompositionLocalProvider(LocalLiquidGlass provides liquidGlass,LocalTransparency provides transparency){
        MaterialTheme(colorScheme=if(darkTheme(mode))DarkColors else LightColors,content=content)
    }
}
