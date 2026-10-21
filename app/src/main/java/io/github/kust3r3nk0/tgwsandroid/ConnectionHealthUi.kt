package io.github.kust3r3nk0.tgwsandroid

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.kust3r3nk0.tgwsandroid.core.*

internal fun qualityResource(q:ConnectionQuality)=when(q){ConnectionQuality.EXCELLENT->R.string.smart_quality_excellent;ConnectionQuality.GOOD->R.string.smart_quality_good
    ConnectionQuality.UNSTABLE->R.string.smart_quality_unstable;ConnectionQuality.POOR->R.string.smart_quality_poor;ConnectionQuality.INSUFFICIENT->R.string.smart_quality_insufficient}
internal fun selectionResource(reason:SelectionReason)=when(reason){SelectionReason.CONFIGURED_ORDER->R.string.smart_reason_order;SelectionReason.USER_PRIMARY->R.string.smart_reason_primary
    SelectionReason.USER_MANUAL->R.string.smart_reason_manual;SelectionReason.RECENT_PROGRESS->R.string.smart_reason_progress;SelectionReason.CONTROLLED_PROBE->R.string.smart_reason_probe;SelectionReason.FALLBACK->R.string.smart_reason_fallback}

@Composable internal fun ConnectionHealthCard(health:ConnectionHealth,compact:Boolean=false,nowMillis:Long=System.nanoTime()/1_000_000){
    SectionCard(stringResource(R.string.smart_health),Modifier.testTag(if(compact)"home.health" else "diagnostics.health")){
        Text(stringResource(qualityResource(health.quality)),Modifier.testTag("health.quality"),style=MaterialTheme.typography.titleLarge)
        if(!compact){
            Text(stringResource(R.string.smart_health_counts,health.attempts,health.setupSucceeded,health.withInboundData,health.failures))
            Text(if(health.successPercent==null)stringResource(R.string.smart_success_unknown)else stringResource(R.string.smart_success,health.successPercent.toString()),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.smart_breaks,health.abnormalClosures,health.dropsPerMinute?.let{String.format(java.util.Locale.getDefault(),"%.1f",it)}?:stringResource(R.string.premium_no_data)),style=MaterialTheme.typography.bodySmall)
            Text(if(health.averageSetupMillis==null)stringResource(R.string.smart_setup_average_unknown)else stringResource(R.string.smart_setup_average,health.averageSetupMillis.toString()),style=MaterialTheme.typography.bodySmall)
            if(health.enabled)Text(stringResource(R.string.smart_active_profile,stringResource(profileResource(health.profile))))
            health.selection?.let{s->Text(stringResource(R.string.smart_selection,stringResource(kindResource(s.transport)),stringResource(selectionResource(s.reason)),s.dc),Modifier.testTag("health.selection"))}
            for(session in health.active.take(8)){
                val age=session.durationMillis+(nowMillis-health.measuredAtMillis).coerceAtLeast(0)
                Text(stringResource(R.string.smart_active_session,session.id,if(session.media)-session.dc else session.dc,
                    stringResource(kindResource(session.transport)),age/1000,stringResource(if(session.inboundProgress)R.string.premium_data_received else R.string.premium_no_confirmation)),style=MaterialTheme.typography.bodySmall)
            }
        }
        Text(stringResource(R.string.smart_health_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun LiveTrafficCard(samples:List<TrafficSample>,snapshot:ProxySnapshot,now:Long=SystemClock.elapsedRealtime()){
    val owner=LocalLifecycleOwner.current
    val state by owner.lifecycle.currentStateFlow.collectAsState()
    val incoming=MaterialTheme.colorScheme.secondary;val outgoing=MaterialTheme.colorScheme.primary;val error=MaterialTheme.colorScheme.error
    SectionCard(stringResource(R.string.smart_traffic),Modifier.testTag("diagnostics.traffic")){
        val last=samples.lastOrNull()
        @Composable fun speed(rate:Long?)=if(!snapshot.running)stringResource(R.string.smart_rate,formatBytes(0))else rate?.let{stringResource(R.string.smart_rate,formatBytes(if(last!=null && now-last.elapsed>2000)0 else it))}?:stringResource(R.string.premium_no_data)
        Text(stringResource(R.string.smart_traffic_rates,speed(last?.incomingPerSecond),speed(last?.outgoingPerSecond)),Modifier.testTag("traffic.rates"))
        val values=samples.filter{it.incomingPerSecond!=null && it.outgoingPerSecond!=null}
        if(values.size>=2 && state.isAtLeast(Lifecycle.State.STARTED)){
            val description=stringResource(R.string.smart_traffic_chart_description)
            Canvas(Modifier.fillMaxWidth().height(96.dp).testTag("traffic.chart").semantics{contentDescription=description}){
                val max=values.maxOf{maxOf(it.incomingPerSecond!!,it.outgoingPerSecond!!)}.coerceAtLeast(1).toFloat()
                val span=(values.last().elapsed-values.first().elapsed).coerceAtLeast(1).toFloat()
                fun x(v:TrafficSample)=size.width*(v.elapsed-values.first().elapsed)/span
                for(i in 1 until values.size){val a=values[i-1];val b=values[i]
                    drawLine(incoming,Offset(x(a),size.height*(1-a.incomingPerSecond!!/max)),Offset(x(b),size.height*(1-b.incomingPerSecond!!/max)),2.dp.toPx())
                    drawLine(outgoing,Offset(x(a),size.height*(1-a.outgoingPerSecond!!/max)),Offset(x(b),size.height*(1-b.outgoingPerSecond!!/max)),2.dp.toPx())
                    if(b.reconnects>a.reconnects)drawCircle(error,3.dp.toPx(),Offset(x(b),size.height-3.dp.toPx()))
                }
            }
        }else Text(stringResource(R.string.premium_no_measurements),style=MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.smart_traffic_totals,formatBytes(snapshot.received),formatBytes(snapshot.sent),snapshot.sessions),style=MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.smart_traffic_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
