package io.github.kust3r3nk0.tgwsandroid

import io.github.kust3r3nk0.tgwsandroid.core.WebSocketCloseCategory

internal enum class DiagnosticAdvice { CAPTIVE_PORTAL, NO_NETWORK, SECRET, PORT, DNS, TLS, ROUTE, LIMIT, RETRY, STORAGE }
/** Deterministic suggestions from observations; never claims to identify a carrier block. */
internal fun diagnosticAdvice(s:ProxySnapshot):List<DiagnosticAdvice> = buildList {
    if(s.network.captivePortal)add(DiagnosticAdvice.CAPTIVE_PORTAL)
    else if(s.running && s.network.kind==AccessKind.NONE)add(DiagnosticAdvice.NO_NETWORK)
    when(s.issue){
        ProxyIssue.SECRET_UNAVAILABLE,ProxyIssue.STORAGE->add(DiagnosticAdvice.STORAGE)
        ProxyIssue.CONFIGURATION,ProxyIssue.HANDSHAKE->add(DiagnosticAdvice.SECRET)
        ProxyIssue.PORT->add(DiagnosticAdvice.PORT)
        ProxyIssue.DNS->add(DiagnosticAdvice.DNS)
        ProxyIssue.TLS->add(DiagnosticAdvice.TLS)
        ProxyIssue.RELAY,ProxyIssue.TCP,ProxyIssue.WSS_HANDSHAKE,ProxyIssue.HTTP2->add(DiagnosticAdvice.ROUTE)
        ProxyIssue.NETWORK_CHANGED,ProxyIssue.RETRY_LATER,ProxyIssue.TIMEOUT->add(DiagnosticAdvice.RETRY)
        else->Unit
    }
    if(s.localAvailable && s.sessions==0 && s.rejectedHandshakes>0)add(DiagnosticAdvice.SECRET)
    if(s.routes.any{it.closeCategory==WebSocketCloseCategory.BYTE_LIMIT})add(DiagnosticAdvice.LIMIT)
}.distinct().take(3)

internal fun filterEvents(events:List<DiagnosticEvent>,level:EventLevel?,category:EventCategory?)=
    events.filter{(level==null || it.level==level) && (category==null || it.category==category)}.asReversed()
