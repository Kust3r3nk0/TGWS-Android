package io.github.kust3r3nk0.tgwsandroid

/** Only enums/numbers are accepted by the logcat formatter; never Throwable.message/stack. */
internal fun diagnosticLogLine(e:DiagnosticEvent)=buildString {
    append('[').append(e.level.name).append('/').append(e.category.name).append("] ").append(e.kind.name)
    append(" count=").append(e.occurrences)
    e.phase?.let{append(" phase=").append(it.name)}
    e.issue?.let{append(" issue=").append(it.name)}
    e.transport?.let{append(" transport=").append(it.name)}
    e.rejection?.let{append(" rejection=").append(it.name)}
    e.rejected?.let{append(" rejected_total=").append(it)}
    e.route?.let{r->
        append(" route=").append(r.id).append(" dc=").append(r.dc).append(" media=").append(r.media)
        append(" stage=").append(r.stage.name).append(" attempts=").append(r.attempts)
        append(" setup_ms=").append(r.setupMillis).append(" dns_ms=").append(r.dnsMillis)
        append(" tcp_ms=").append(r.tcpMillis).append(" tls_ms=").append(r.tlsMillis).append(" http_ms=").append(r.httpMillis)
        append(" close=").append(r.closeCode).append('/').append(r.closeCategory?.name)
    }
    e.provider?.let{append(" provider_state=").append(it.state.name)}
    e.localCheck?.let{append(" local_check=").append(it.outcome.name).append(" elapsed_ms=").append(it.millis)}
}

internal object DiagnosticLog {
    fun emit(event:DiagnosticEvent){
        val priority=when(event.level){EventLevel.INFO->android.util.Log.INFO;EventLevel.WARNING->android.util.Log.WARN;EventLevel.ERROR->android.util.Log.ERROR}
        android.util.Log.println(priority,"TGWSDiagnostics",diagnosticLogLine(event))
    }
}
