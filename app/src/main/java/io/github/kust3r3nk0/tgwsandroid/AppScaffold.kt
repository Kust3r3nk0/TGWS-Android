package io.github.kust3r3nk0.tgwsandroid

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
import io.github.kust3r3nk0.tgwsandroid.core.DnsMode

@Composable internal fun AppScaffold(page:Int,onPage:(Int)->Unit,version:String,isDark:Boolean,onToggleTheme:()->Unit,
    content:@Composable ColumnScope.()->Unit){
    val glass=LocalLiquidGlass.current
    Box(Modifier.fillMaxSize().liquidGlass(panel=false,rounded=false)){
    Scaffold(modifier=Modifier.semantics{testTagsAsResourceId=true},containerColor=if(glass)Color.Transparent else MaterialTheme.colorScheme.background,
        contentColor=MaterialTheme.colorScheme.onBackground,
        topBar={Surface(modifier=Modifier.liquidGlass(rounded=false),color=if(glass)Color.Transparent else MaterialTheme.colorScheme.background,
            contentColor=MaterialTheme.colorScheme.onSurface){
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
                Image(painterResource(R.drawable.tgws_symbol),null,Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(start=12.dp)){
                    Text(stringResource(R.string.app_name),style=MaterialTheme.typography.titleLarge)
                    Text(version,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick=onToggleTheme,modifier=Modifier.testTag("theme.toggle")){
                    Icon(painterResource(if(isDark)R.drawable.ic_sun else R.drawable.ic_moon),stringResource(if(isDark)R.string.theme_light else R.string.theme_dark))
                }
            }
        }},
        bottomBar={NavigationBar(Modifier.imePadding().liquidGlass(rounded=false),containerColor=if(glass)Color.Transparent else NavigationBarDefaults.containerColor) {
            NavigationBarItem(selected=page==0,onClick={onPage(0)},modifier=Modifier.testTag("nav.home"),
                icon={Icon(painterResource(R.drawable.ic_home),null)},label={Text(stringResource(R.string.nav_home))})
            NavigationBarItem(selected=page==1,onClick={onPage(1)},modifier=Modifier.testTag("nav.settings"),
                icon={Icon(painterResource(R.drawable.ic_settings),null)},label={Text(stringResource(R.string.nav_settings))})
        }}){insets->
        key(page){Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).testTag("page.scroll")
            .verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp),content=content)}
    }
    }
}

@Composable internal fun SectionCard(title:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    Card(modifier.fillMaxWidth().liquidGlass(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(
        containerColor=if(LocalLiquidGlass.current)Color.Transparent else MaterialTheme.colorScheme.surface,
        contentColor=MaterialTheme.colorScheme.onSurface)){
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            Text(title,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable internal fun NetworkCard(network:NetworkView,dns:DnsMode){
    SectionCard(stringResource(R.string.network_card),Modifier.testTag("home.network")){
        Text(stringResource(when(network.kind){AccessKind.NONE->R.string.access_none;AccessKind.WIFI->R.string.access_wifi;AccessKind.CELLULAR->R.string.access_cell;AccessKind.OTHER->R.string.access_other}),
            style=MaterialTheme.typography.headlineSmall)
        Text(stringResource(when(network.ipSupport){IpSupport.UNKNOWN->R.string.ip_unknown;IpSupport.IPV4->R.string.ip_v4;IpSupport.IPV6->R.string.ip_v6;IpSupport.DUAL->R.string.ip_dual}),style=MaterialTheme.typography.titleMedium)
        Text(stringResource(if(network.captivePortal)R.string.network_portal else if(network.validated)R.string.network_validated else R.string.network_unvalidated),color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.dns_active,stringResource(if(dns==DnsMode.CLOUDFLARE)R.string.dns_cloudflare else R.string.dns_system)),style=MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.ip_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun AppearanceSettings(theme:ThemeMode,onTheme:(ThemeMode)->Unit,dns:DnsMode,onDns:(DnsMode)->Unit,editable:Boolean,
    liquidGlass:Boolean,onLiquidGlass:(Boolean)->Unit){
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
    }
    SectionCard(stringResource(R.string.dns_title)){
        for(mode in DnsMode.entries)Row(verticalAlignment=Alignment.CenterVertically){
            RadioButton(selected=dns==mode,onClick={onDns(mode)},enabled=editable,modifier=Modifier.testTag("dns.${mode.name}"))
            Text(stringResource(if(mode==DnsMode.CLOUDFLARE)R.string.dns_cloudflare else R.string.dns_system))
        }
        Text(stringResource(R.string.cloudflare_addresses),style=MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.dns_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!editable)Text(stringResource(R.string.stop_to_edit),style=MaterialTheme.typography.bodySmall)
    }
}
