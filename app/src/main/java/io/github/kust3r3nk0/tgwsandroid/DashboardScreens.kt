package io.github.kust3r3nk0.tgwsandroid

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.kust3r3nk0.tgwsandroid.core.TransportKind
import io.github.kust3r3nk0.tgwsandroid.core.RouteStatus
import kotlinx.coroutines.delay

@Composable internal fun transportLabel(s:ProxySnapshot):String {
    val kinds=buildList {
        if(s.wssSessions>0)add(stringResource(R.string.kind_wss))
        if(s.tcpSessions>0)add(stringResource(R.string.kind_tcp))
        if(s.relaySessions>0)add(stringResource(R.string.kind_relay))
        if(s.cfSessions>0)add(stringResource(R.string.kind_cf))
        if(s.h2Sessions>0)add(stringResource(R.string.kind_h2))
    }
    return kinds.joinToString(" · ").ifEmpty{stringResource(R.string.premium_no_data)}
}
@Composable internal fun networkLabel(n:NetworkView)=stringResource(when(n.kind){AccessKind.WIFI->R.string.access_wifi;AccessKind.CELLULAR->R.string.access_cell;AccessKind.OTHER->R.string.access_other;AccessKind.NONE->R.string.access_none})+" · "+
    stringResource(when(n.ipSupport){IpSupport.IPV4->R.string.ip_v4;IpSupport.IPV6->R.string.ip_v6;IpSupport.DUAL->R.string.ip_dual;IpSupport.UNKNOWN->R.string.ip_unknown})

@Composable internal fun PremiumHome(s:ProxySnapshot,network:NetworkView,port:String,relay:String,transport:TransportDraft,
    ready:Boolean,valid:Boolean,onToggle:()->Unit,onShare:()->Unit,onCopy:()->Unit,onOpen:()->Unit,onRelay:()->Unit,onDiagnostics:()->Unit){
    val c=MaterialTheme.colorScheme
    SectionCard(stringResource(R.string.proxy_overview),Modifier.testTag("home.status")){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
            Image(painterResource(R.drawable.tgws_symbol),null,Modifier.size(64.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(stringResource(when{ s.localAvailable && s.issue==ProxyIssue.NETWORK_CHANGED->R.string.premium_reconnecting;else->statusResource(s.phase)}),
                    style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,
                    color=if(s.phase==ProxyPhase.ACTIVE)c.secondary else c.onSurface)
                Text(stringResource(R.string.local_address,SettingsValidation.LOOPBACK,port),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            }
        }
        Text(stringResource(when{ s.sessions>0->R.string.remote_transport_established;s.pending>0->R.string.remote_transport_connecting;else->R.string.remote_transport_unconfirmed}),
            style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
        Button(onClick=onToggle,enabled=ready && s.phase!=ProxyPhase.STOPPING,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("proxy.toggle"),shape=RoundedCornerShape(16.dp)){
            Text(stringResource(if(s.running)R.string.stop else R.string.start_proxy),fontWeight=FontWeight.SemiBold)
        }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Metric(stringResource(R.string.premium_telegram),stringResource(if(s.sessions>0 && s.received>0)R.string.premium_data_received else R.string.premium_no_confirmation),R.drawable.ic_premium_send,Modifier.weight(1f))
        Metric(stringResource(R.string.premium_transport),transportLabel(s),R.drawable.ic_premium_hub,Modifier.weight(1f))
    }
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Metric(stringResource(R.string.premium_network),networkLabel(network),if(network.kind==AccessKind.CELLULAR)R.drawable.ic_premium_signal_cellular_alt else R.drawable.ic_update_wifi,Modifier.weight(1f).testTag("home.network"))
        Metric(stringResource(R.string.premium_route),stringResource(modeResource(transport.mode)),R.drawable.ic_premium_route,Modifier.weight(1f))
    }
    SectionCard(stringResource(R.string.premium_relay)){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(stringResource(if(relay.isBlank() && !transport.relays.enabled && transport.workers.isBlank() && transport.domains.isBlank())R.string.premium_not_configured else R.string.premium_endpoint_hidden),color=c.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                Text(stringResource(if(s.relaySessions+s.cfSessions+s.h2Sessions>0)R.string.route_available else R.string.premium_no_confirmation),style=MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick=onRelay,modifier=Modifier.testTag("home.relay.settings")){
                Icon(painterResource(R.drawable.ic_settings),stringResource(R.string.premium_relay_edit),tint=c.primary)
            }
        }
        s.routes.filter{it.status==RouteStatus.AVAILABLE && it.kind in setOf(TransportKind.RELAY,TransportKind.WORKER,TransportKind.CLOUDFLARE,TransportKind.HTTP2)}
            .maxByOrNull{it.checkedAtMillis}?.setupMillis?.let{Text(stringResource(R.string.premium_setup_value,it),style=MaterialTheme.typography.bodySmall)}
    }
    if(transport.profiles.enabled || transport.relays.enabled)ConnectionHealthCard(s.health,compact=true)
    Text(stringResource(R.string.premium_quick_actions),style=MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Action(R.string.premium_share,R.drawable.ic_premium_share,"telegram.share",ready&&valid,onShare,Modifier.weight(1f))
        Action(R.string.copy_secret,R.drawable.ic_premium_content_copy,"telegram.copy_secret",ready&&valid,onCopy,Modifier.weight(1f))
        Action(R.string.open_telegram,R.drawable.ic_premium_send,"telegram.open",ready&&valid,onOpen,Modifier.weight(1f))
    }
    TextButton(onClick=onDiagnostics,modifier=Modifier.testTag("home.diagnostics")){Text(stringResource(R.string.premium_open_diagnostics))}
    Text(stringResource(R.string.local_notice),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
}
@Composable private fun Metric(label:String,value:String,icon:Int,modifier:Modifier=Modifier){
    val c=MaterialTheme.colorScheme
    Surface(modifier.heightIn(min=88.dp).liquidGlass(),shape=RoundedCornerShape(18.dp),
        color=if(LocalLiquidGlass.current||LocalTransparency.current)androidx.compose.ui.graphics.Color.Transparent else c.surface,
        contentColor=c.onSurface,border=androidx.compose.foundation.BorderStroke(1.dp,c.outlineVariant.copy(alpha=.45f))){
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Icon(painterResource(icon),null,Modifier.size(22.dp),tint=c.onSurfaceVariant)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(label,style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
                Text(value,style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
@Composable private fun Action(label:Int,icon:Int,tag:String,enabled:Boolean,click:()->Unit,modifier:Modifier){
    FilledTonalButton(onClick=click,enabled=enabled,modifier=modifier.heightIn(min=88.dp).testTag(tag),shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(8.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface)){
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
            Icon(painterResource(icon),null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary);Text(stringResource(label),style=MaterialTheme.typography.labelMedium)
        }
    }
}
@Composable internal fun PremiumDiagnostics(s:ProxySnapshot,history:DiagnosticHistoryState,onProbe:()->Unit,onCopy:(String)->Unit,recording:Boolean=true,report:()->String={DiagnosticReport.render(s,history)},
    onShare:(String)->Unit={},onClear:()->Unit={},extra:@Composable ()->Unit={}){
    val owner=LocalLifecycleOwner.current
    var now by remember{mutableLongStateOf(SystemClock.elapsedRealtime())}
    LaunchedEffect(owner,s.running){owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
        do{now=SystemClock.elapsedRealtime();if(s.running)delay(1000)}while(s.running)
    }}
    SectionCard(stringResource(R.string.premium_service),Modifier.testTag("diagnostics.status")){
        Text(stringResource(statusResource(s.phase)),style=MaterialTheme.typography.headlineSmall)
        val uptime=uptimeMillis(history,now)?.div(1000)
        Text(stringResource(R.string.premium_uptime,uptime?.let{"${it/3600}:${(it/60%60).toString().padStart(2,'0')}:${(it%60).toString().padStart(2,'0')}"}?:stringResource(R.string.premium_no_data)))
        Text(stringResource(R.string.session_counts,s.sessions,s.pending))
        Text(stringResource(R.string.transport_counts,s.wssSessions,s.tcpSessions,s.relaySessions,s.cfSessions,s.h2Sessions),style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Column(Modifier.weight(1f)){Text(stringResource(R.string.premium_received),style=MaterialTheme.typography.bodySmall);Text(formatBytes(s.received))}
            Column(Modifier.weight(1f)){Text(stringResource(R.string.premium_sent),style=MaterialTheme.typography.bodySmall);Text(formatBytes(s.sent))}
        }
        if(s.rejectedHandshakes>0){
            Text(stringResource(if(s.running)R.string.handshake_rejections else R.string.handshake_rejections_previous,s.rejectedHandshakes),style=MaterialTheme.typography.bodySmall)
            s.lastHandshakeRejection?.let{Text(stringResource(rejectionResource(it)),style=MaterialTheme.typography.bodySmall)}
        }
    }
    ConnectionHealthCard(s.health,nowMillis=System.nanoTime()/1_000_000)
    LiveTrafficCard(ProxyState.traffic,s,now)
    SectionCard(stringResource(R.string.premium_setup_chart),Modifier.testTag("diagnostics.latency")){
        Text(history.samples.lastOrNull()?.let{stringResource(R.string.premium_millis,it.millis)}?:stringResource(R.string.premium_no_measurements),style=MaterialTheme.typography.headlineSmall)
        val values=history.samples
        val description=values.joinToString{it.millis.toString()}
        val color=MaterialTheme.colorScheme.primary
        if(values.isNotEmpty())Canvas(Modifier.fillMaxWidth().height(64.dp).semantics{contentDescription=description}){
            val max=values.maxOf{it.millis}.coerceAtLeast(1).toFloat()
            val step=size.width/values.size
            values.forEachIndexed{i,value->drawLine(color,Offset((i+.5f)*step,size.height),Offset((i+.5f)*step,size.height*(1-value.millis/max)),strokeWidth=(step*.55f).coerceAtMost(9.dp.toPx()))}
        }
        Text(stringResource(R.string.premium_setup_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Metric(stringResource(R.string.premium_transport),transportLabel(s),R.drawable.ic_premium_hub,Modifier.weight(1f))
        Metric(stringResource(R.string.premium_network),networkLabel(s.network),if(s.network.kind==AccessKind.CELLULAR)R.drawable.ic_premium_signal_cellular_alt else R.drawable.ic_update_wifi,Modifier.weight(1f))
    }
    DiagnosticJournal(history,recording,report,onCopy,onShare,onClear)
    extra()
    SectionCard(stringResource(R.string.premium_route_checks)){
        DiagnosticsPanel(s,onProbe,onCopy,report)
    }
}
internal fun formatBytes(bytes:Long):String {
    val value=bytes.coerceAtLeast(0)
    return when{value>=1024L*1024*1024->String.format(java.util.Locale.getDefault(),"%.1f GiB",value/(1024.0*1024*1024));value>=1024*1024->String.format(java.util.Locale.getDefault(),"%.1f MiB",value/(1024.0*1024));value>=1024->String.format(java.util.Locale.getDefault(),"%.1f KiB",value/1024.0);else->"$value B"}
}
