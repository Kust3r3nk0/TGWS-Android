package io.github.kust3r3nk0.tgwsandroid

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Android/UI adapter only. The core never imports Compose or this state. */
object ProxyState {
    private val history=DiagnosticHistory(DiagnosticLog::emit)
    private var current by mutableStateOf(ProxySnapshot())
    private val trafficHistory=TrafficHistory()
    private var reconnectEvents=0
    internal var traffic by mutableStateOf<List<TrafficSample>>(emptyList())
        private set
    internal var diagnostics by mutableStateOf(DiagnosticHistoryState())
        private set
    internal var recordDiagnostics:Boolean
        get()=history.enabled
        set(value){history.enabled=value;diagnostics=history.state}
    internal fun record(event:DiagnosticEvent){
        history.record(event,SystemClock.elapsedRealtime());diagnostics=history.state
    }
    internal fun clearDiagnostics(){history.clear();diagnostics=history.state}
    internal fun networkReset(){reconnectEvents=(reconnectEvents.toLong()+1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()}
    var snapshot:ProxySnapshot
        get()=current
        internal set(value){
            if(value.phase==ProxyPhase.STARTING && current.phase!=ProxyPhase.STARTING){trafficHistory.reset();reconnectEvents=0}
            val changes=(value.health.abnormalClosures-current.health.abnormalClosures).coerceAtLeast(0)
            reconnectEvents=(reconnectEvents.toLong()+changes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            trafficHistory.accept(value.sent,value.received,value.sessions,reconnectEvents,SystemClock.elapsedRealtime())
            traffic=trafficHistory.samples
            history.accept(current,value,System.currentTimeMillis(),SystemClock.elapsedRealtime())
            current=value
            diagnostics=history.state
        }
}
