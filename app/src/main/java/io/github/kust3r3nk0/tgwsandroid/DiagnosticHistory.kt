package io.github.kust3r3nk0.tgwsandroid

import io.github.kust3r3nk0.tgwsandroid.core.RouteStatus
import io.github.kust3r3nk0.tgwsandroid.core.TransportKind

internal enum class EventCategory { SERVICE, NETWORK, ROUTE, CLIENT }
internal data class DiagnosticEvent(val timeMillis: Long, val category: EventCategory,
    val success: Boolean, val phase: ProxyPhase? = null, val issue: ProxyIssue? = null,
    val transport: TransportKind? = null, val rejected: Int? = null)
internal data class SetupSample(val timeMillis: Long, val millis: Long, val transport: TransportKind)
internal data class DiagnosticHistoryState(val events: List<DiagnosticEvent> = emptyList(),
    val samples: List<SetupSample> = emptyList(), val startedElapsed: Long? = null,
    val stoppedElapsed: Long? = null)

/** In-memory, bounded, typed observations only. Never accepts endpoints or exception text. */
internal class DiagnosticHistory {
    var state = DiagnosticHistoryState(); private set
    var enabled = true
        set(value) { field=value; if(!value)state=state.copy(events=emptyList(),samples=emptyList()) }
    fun accept(old: ProxySnapshot, next: ProxySnapshot, wall: Long, elapsed: Long) {
        var result=state
        if(next.phase==ProxyPhase.STARTING && old.phase!=ProxyPhase.STARTING)
            result=result.copy(startedElapsed=elapsed,stoppedElapsed=null,samples=emptyList())
        if(!next.running && old.running)result=result.copy(stoppedElapsed=elapsed)
        if(enabled) {
            val events=mutableListOf<DiagnosticEvent>()
            if(old.phase!=next.phase)events+=DiagnosticEvent(wall,EventCategory.SERVICE,
                next.phase!=ProxyPhase.ERROR,phase=next.phase)
            if(old.network.kind!=next.network.kind || old.network.validated!=next.network.validated)
                events+=DiagnosticEvent(wall,EventCategory.NETWORK,next.network.validated)
            if(next.issue!=null && next.issue!=old.issue)
                events+=DiagnosticEvent(wall,EventCategory.ROUTE,false,issue=next.issue)
            if(next.rejectedHandshakes!=old.rejectedHandshakes)
                events+=DiagnosticEvent(wall,EventCategory.CLIENT,false,rejected=next.rejectedHandshakes)
            val samples=mutableListOf<SetupSample>()
            for(route in next.routes) {
                val before=old.routes.find{it.id==route.id}
                // Traffic updates and closure times are not new latency measurements.
                if(route.status==RouteStatus.AVAILABLE && route.setupMillis!=null && route.checkedAtMillis>0 &&
                    (before==null || before.checkedAtMillis!=route.checkedAtMillis || before.status!=RouteStatus.AVAILABLE)) {
                    samples+=SetupSample(route.checkedAtMillis,route.setupMillis.coerceAtLeast(0),route.kind)
                    events+=DiagnosticEvent(wall,EventCategory.ROUTE,true,transport=route.kind)
                }
            }
            if(events.isNotEmpty() || samples.isNotEmpty())result=result.copy(
                events=(result.events+events).takeLast(MAX_EVENTS),samples=(result.samples+samples).takeLast(MAX_SAMPLES))
        }
        state=result
    }
    companion object { const val MAX_EVENTS=128; const val MAX_SAMPLES=32 }
}

internal fun uptimeMillis(state:DiagnosticHistoryState,nowElapsed:Long):Long? = state.startedElapsed?.let {
    ((state.stoppedElapsed?:nowElapsed)-it).coerceAtLeast(0)
}
