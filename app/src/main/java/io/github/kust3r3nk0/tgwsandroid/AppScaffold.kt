package io.github.kust3r3nk0.tgwsandroid

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable internal fun AppScaffold(page:Int,onPage:(Int)->Unit,version:String,isDark:Boolean,onToggleTheme:()->Unit,
    onBack:()->Unit={},pages:androidx.compose.runtime.saveable.SaveableStateHolder=rememberSaveableStateHolder(),content:@Composable ColumnScope.()->Unit){
    val glass=LocalLiquidGlass.current || LocalTransparency.current
    val c=MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().liquidGlass(panel=false,rounded=false)){
        Scaffold(modifier=Modifier.semantics{testTagsAsResourceId=true},containerColor=if(glass)Color.Transparent else c.background,
            contentColor=c.onBackground,
            topBar={Surface(color=if(glass)Color.Transparent else c.background,modifier=Modifier.liquidGlass(rounded=false)){
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                    if(page>=5)IconButton(onClick=onBack,modifier=Modifier.testTag("settings.back")){
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.update_back))
                    }else Image(painterResource(R.drawable.tgws_symbol),null,Modifier.size(40.dp))
                    Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                        Text(stringResource(R.string.app_name),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                        Text(stringResource(when(page){0->R.string.premium_home_subtitle;4->R.string.premium_diagnostics_subtitle;else->R.string.premium_settings_subtitle}),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                    }
                    IconButton(onClick=onToggleTheme,modifier=Modifier.testTag("theme.toggle")){
                        Icon(painterResource(if(isDark)R.drawable.ic_sun else R.drawable.ic_moon),stringResource(if(isDark)R.string.theme_light else R.string.theme_dark),tint=c.onSurfaceVariant)
                    }
                }
            }},
            bottomBar={NavigationBar(Modifier.imePadding().liquidGlass(rounded=false),containerColor=if(glass)Color.Transparent else c.background,tonalElevation=0.dp){
                listOf(Triple(0,"nav.home",R.drawable.ic_home),Triple(4,"nav.diagnostics",R.drawable.ic_premium_monitor_heart),Triple(1,"nav.settings",R.drawable.ic_settings)).forEach{(target,tag,icon)->
                    NavigationBarItem(selected=page==target || (target==1 && page>=5),onClick={onPage(target)},modifier=Modifier.testTag(tag),
                        colors=NavigationBarItemDefaults.colors(selectedIconColor=c.primary,selectedTextColor=c.primary,indicatorColor=c.primary.copy(alpha=.10f)),
                        icon={Icon(painterResource(icon),null)},label={Text(stringResource(when(target){0->R.string.premium_home;4->R.string.diagnostics;else->R.string.premium_settings}),style=MaterialTheme.typography.labelSmall)})
                }
            }}){insets->
            pages.SaveableStateProvider(page){
                Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).testTag("page.scroll")
                    .verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=10.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
            }
        }
    }
}

@Composable internal fun SectionCard(title:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    Card(modifier.fillMaxWidth().liquidGlass(),shape=RoundedCornerShape(20.dp),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f)),colors=CardDefaults.cardColors(
        containerColor=if(LocalLiquidGlass.current || LocalTransparency.current)Color.Transparent else MaterialTheme.colorScheme.surface,
        contentColor=MaterialTheme.colorScheme.onSurface)){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(title,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable internal fun NetworkCard(network:NetworkView){
    SectionCard(stringResource(R.string.network_card),Modifier.testTag("home.network")){
        Text(stringResource(when(network.kind){AccessKind.NONE->R.string.access_none;AccessKind.WIFI->R.string.access_wifi;AccessKind.CELLULAR->R.string.access_cell;AccessKind.OTHER->R.string.access_other}),
            style=MaterialTheme.typography.headlineSmall)
        Text(stringResource(when(network.ipSupport){IpSupport.UNKNOWN->R.string.ip_unknown;IpSupport.IPV4->R.string.ip_v4;IpSupport.IPV6->R.string.ip_v6;IpSupport.DUAL->R.string.ip_dual}),style=MaterialTheme.typography.titleMedium)
        Text(stringResource(if(network.captivePortal)R.string.network_portal else if(network.validated)R.string.network_validated else R.string.network_unvalidated),color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.dns_active,stringResource(R.string.dns_system)),style=MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.ip_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun AppearanceSettings(theme:ThemeMode,onTheme:(ThemeMode)->Unit,
    liquidGlass:Boolean,onLiquidGlass:(Boolean)->Unit,transparency:Boolean,onTransparency:(Boolean)->Unit){
    SectionCard(stringResource(R.string.appearance_title)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            for(mode in ThemeMode.entries)FilterChip(selected=theme==mode,onClick={onTheme(mode)},modifier=Modifier.testTag("theme.${mode.name}"),
                label={Text(stringResource(when(mode){ThemeMode.SYSTEM->R.string.theme_system;ThemeMode.LIGHT->R.string.theme_light;ThemeMode.DARK->R.string.theme_dark}))})
        }
        val glassTitle=stringResource(R.string.liquid_glass_title)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(glassTitle,style=MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.liquid_glass_description),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked=liquidGlass,onCheckedChange=onLiquidGlass,modifier=Modifier.testTag("appearance.glass").semantics{contentDescription=glassTitle})
        }
        val transparencyTitle=stringResource(R.string.transparency_title)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(transparencyTitle,style=MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.transparency_description),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked=transparency,onCheckedChange=onTransparency,modifier=Modifier.testTag("appearance.transparency").semantics{contentDescription=transparencyTitle})
        }
    }
    SectionCard(stringResource(R.string.dns_title)){
        Text(stringResource(R.string.dns_system),Modifier.testTag("dns.system"))
        Text(stringResource(R.string.dns_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
