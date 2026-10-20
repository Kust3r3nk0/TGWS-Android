package io.github.kust3r3nk0.tgwsandroid

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Android/UI adapter only. The core never imports Compose or this state. */
object ProxyState {
    private val history=DiagnosticHistory()
    private var current by mutableStateOf(ProxySnapshot())
    internal var diagnostics by mutableStateOf(DiagnosticHistoryState())
        private set
    internal var recordDiagnostics:Boolean
        get()=history.enabled
        set(value){history.enabled=value;diagnostics=history.state}
    var snapshot:ProxySnapshot
        get()=current
        internal set(value){
            history.accept(current,value,System.currentTimeMillis(),SystemClock.elapsedRealtime())
            current=value
            diagnostics=history.state
        }
}
