package io.github.kust3r3nk0.tgwsandroid

import io.github.kust3r3nk0.tgwsandroid.core.RouteStatus
import io.github.kust3r3nk0.tgwsandroid.core.TransportKind
import io.github.kust3r3nk0.tgwsandroid.core.HandshakeRejection
import io.github.kust3r3nk0.tgwsandroid.core.RouteObservation
import io.github.kust3r3nk0.tgwsandroid.core.RouteStage
import io.github.kust3r3nk0.tgwsandroid.core.WebSocketCloseCategory
import io.github.kust3r3nk0.tgwsandroid.core.HttpUpgradeDiagnostic
import io.github.kust3r3nk0.tgwsandroid.core.SessionClosed
import io.github.kust3r3nk0.tgwsandroid.core.SessionEndReason

internal enum class EventCategory { SERVICE, CONFIGURATION, NETWORK, DNS, TLS, TCP, WSS, RELAY, HTTP2, ROUTE, CLIENT, WEBVIEW, CHECK }
internal enum class EventLevel { INFO, WARNING, ERROR }
internal enum class EventKind { STATE, NETWORK, ISSUE, CLIENT_REJECTED, CLIENT_CLOSED, ROUTE_READY, ROUTE_FAILED, ROUTE_CLOSED, WARMUP_FAILED, PROVIDER, LOCAL_CHECK, SESSION_CLOSED, ROUTE_SELECTED }
/** Deliberately excludes SetupTimings.resolvedAddresses and all configuration strings. */
internal data class EventRoute(val id:Int,val dc:Int,val media:Boolean,val stage:RouteStage,val attempts:Int,
    val setupMillis:Long?,val dnsMillis:Long?,val tcpMillis:Long?,val tlsMillis:Long?,val httpMillis:Long?,
    val sent:Long,val received:Long,val closeCode:Int?,val closeCategory:WebSocketCloseCategory?,
    val upgrade:HttpUpgradeDiagnostic?=null,val retryInMillis:Long?=null) {
    companion object { fun from(r:RouteObservation)=EventRoute(r.id,r.dc,r.media,r.stage,r.attempts,r.setupMillis,
        r.timings?.dnsMillis,r.timings?.tcpMillis,r.timings?.tlsMillis,r.timings?.httpMillis,r.sent,r.received,r.closeCode,r.closeCategory,r.upgrade,r.retryInMillis) }
}
internal data class DiagnosticEvent(val timeMillis: Long, val category: EventCategory,
    val success: Boolean, val phase: ProxyPhase? = null, val issue: ProxyIssue? = null,
    val transport: TransportKind? = null, val rejected: Int? = null,
    val kind:EventKind=EventKind.ISSUE,val level:EventLevel=if(success)EventLevel.INFO else EventLevel.WARNING,
    val rejection:HandshakeRejection?=null,val route:EventRoute?=null,val network:AccessKind?=null,
    val validated:Boolean?=null,val ipv4:Boolean?=null,val ipv6:Boolean?=null,
    val provider:WebViewStatus?=null,val localCheck:LocalCheckResult?=null,
    val occurrences:Int=1,val lastTimeMillis:Long=timeMillis,val session:SessionClosed?=null,
    val selection:io.github.kust3r3nk0.tgwsandroid.core.RouteSelection?=null)
internal data class SetupSample(val timeMillis: Long, val millis: Long, val transport: TransportKind)
internal data class DiagnosticHistoryState(val events: List<DiagnosticEvent> = emptyList(),
    val samples: List<SetupSample> = emptyList(), val startedElapsed: Long? = null,
    val stoppedElapsed: Long? = null)

/** In-memory, bounded, typed observations only. Never accepts endpoints or exception text. */
internal class DiagnosticHistory(private val onEvent:(DiagnosticEvent)->Unit={}) {
    var state = DiagnosticHistoryState(); private set
    var enabled = true
        set(value) { field=value; if(!value)state=state.copy(events=emptyList(),samples=emptyList()) }
    private var lastEventElapsed:Long?=null
    private var sessionLogWindow:Long?=null
    private var sessionLogCount=0
    private var logWindow:Long?=null
    private var logCount=0
    fun clear(){state=state.copy(events=emptyList(),samples=emptyList());lastEventElapsed=null;sessionLogWindow=null;sessionLogCount=0;logWindow=null;logCount=0}
    fun record(event:DiagnosticEvent,elapsed:Long) {
        if(!enabled)return
        val previous=state.events.lastOrNull()
        val same=event.level!=EventLevel.INFO && previous!=null && previous.copy(timeMillis=event.timeMillis,lastTimeMillis=event.lastTimeMillis,
            occurrences=1,rejected=event.rejected)==event
        val recent=lastEventElapsed?.let{elapsed-it in 0..10_000}==true
        state=state.copy(events=if(same && recent){
            state.events.dropLast(1)+event.copy(timeMillis=previous.timeMillis,
                occurrences=if(previous.occurrences==Int.MAX_VALUE)Int.MAX_VALUE else previous.occurrences+1)
        }else (state.events+event).takeLast(MAX_EVENTS))
        lastEventElapsed=elapsed
        val recorded=state.events.last()
        // Individual IDs must remain in the journal, but malformed local-client
        // churn must not turn those distinct IDs into an unbounded logcat rate.
        if(recorded.kind==EventKind.SESSION_CLOSED) {
            if(sessionLogWindow?.let{elapsed-it in 0..999}!=true){sessionLogWindow=elapsed;sessionLogCount=0}
            if(sessionLogCount>=8)return
            sessionLogCount++
        }
        // Keep logcat useful under reconnect storms as well: 1, 2, 4, 8... repeats.
        if(!same || !recent || recorded.occurrences and (recorded.occurrences-1)==0) {
            if(logWindow?.let{elapsed-it in 0..999}!=true){logWindow=elapsed;logCount=0}
            if(logCount>=32)return
            logCount++;onEvent(recorded)
        }
    }
    fun accept(old: ProxySnapshot, next: ProxySnapshot, wall: Long, elapsed: Long) {
        var result=state
        if(next.phase==ProxyPhase.STARTING && old.phase!=ProxyPhase.STARTING)
            result=result.copy(startedElapsed=elapsed,stoppedElapsed=null,samples=emptyList())
        if(!next.running && old.running)result=result.copy(stoppedElapsed=elapsed)
        if(enabled) {
            val events=mutableListOf<DiagnosticEvent>()
            if(next.health.selection!=null && next.health.selection!=old.health.selection)
                events+=DiagnosticEvent(wall,EventCategory.ROUTE,true,kind=EventKind.ROUTE_SELECTED,
                    transport=next.health.selection.transport,selection=next.health.selection)
            if(old.phase!=next.phase)events+=DiagnosticEvent(wall,EventCategory.SERVICE,
                next.phase!=ProxyPhase.ERROR,phase=next.phase,issue=next.issue.takeIf{next.phase==ProxyPhase.ERROR},kind=EventKind.STATE,
                level=if(next.phase==ProxyPhase.ERROR)EventLevel.ERROR else EventLevel.INFO)
            if(old.network.kind!=next.network.kind || old.network.validated!=next.network.validated ||
                old.network.ipSupport!=next.network.ipSupport || old.network.captivePortal!=next.network.captivePortal)
                events+=DiagnosticEvent(wall,EventCategory.NETWORK,next.network.validated,kind=EventKind.NETWORK,
                    network=next.network.kind,validated=next.network.validated,ipv4=next.network.ipv4,ipv6=next.network.ipv6)
            if(next.issue!=null && next.issue!=old.issue)
                events+=issueEvent(next.issue,wall,next.phase==ProxyPhase.ERROR)
            if(next.rejectedHandshakes>old.rejectedHandshakes)
                events+=DiagnosticEvent(wall,EventCategory.CLIENT,false,rejected=next.rejectedHandshakes,
                    kind=EventKind.CLIENT_REJECTED,rejection=next.lastHandshakeRejection)
            val samples=mutableListOf<SetupSample>()
            for(route in next.routes) {
                val before=old.routes.find{it.id==route.id}
                // Traffic updates and closure times are not new latency measurements.
                if(route.status==RouteStatus.AVAILABLE && route.setupMillis!=null && route.checkedAtMillis>0 &&
                    (before==null || before.checkedAtMillis!=route.checkedAtMillis || before.status!=RouteStatus.AVAILABLE)) {
                    samples+=SetupSample(route.checkedAtMillis,route.setupMillis.coerceAtLeast(0),route.kind)
                    events+=DiagnosticEvent(wall,EventCategory.ROUTE,true,transport=route.kind,kind=EventKind.ROUTE_READY,route=EventRoute.from(route))
                }
                if(route.status in setOf(RouteStatus.UNAVAILABLE,RouteStatus.CLOSED,RouteStatus.BACKOFF) &&
                    (before==null || before.status!=route.status || before.checkedAtMillis!=route.checkedAtMillis || before.issue!=route.issue)) {
                    val issue=route.issue?.let{ProxyIssue.valueOf(it.name)}
                    val failed=route.status!=RouteStatus.CLOSED
                    events+=DiagnosticEvent(wall,issue?.let(::eventCategory)?:EventCategory.ROUTE,!failed,
                        issue=issue,transport=route.kind,kind=if(failed)EventKind.ROUTE_FAILED else EventKind.ROUTE_CLOSED,
                        route=EventRoute.from(route))
                }
            }
            state=if(samples.isEmpty())result else result.copy(samples=(result.samples+samples).takeLast(MAX_SAMPLES))
            events.forEach{record(it,elapsed)}
            result=state
        }
        state=result
    }
    companion object { const val MAX_EVENTS=128; const val MAX_SAMPLES=32 }
}

internal fun sessionEvent(session:SessionClosed,wall:Long):DiagnosticEvent {
    val normal=session.reason in setOf(SessionEndReason.CLIENT_EOF,SessionEndReason.REMOTE_EOF,
        SessionEndReason.SERVICE_STOPPED,SessionEndReason.NETWORK_CHANGED,SessionEndReason.CANCELLED) ||
        session.reason==SessionEndReason.REMOTE_CLOSE && session.peerClose?.code in setOf(null,1000,1001)
    val category=when(session.reason){
        SessionEndReason.NETWORK_CHANGED->EventCategory.NETWORK
        SessionEndReason.SERVICE_STOPPED->EventCategory.SERVICE
        SessionEndReason.CLIENT_EOF,SessionEndReason.CLIENT_IO,SessionEndReason.CLIENT_PROTOCOL,
        SessionEndReason.HANDSHAKE_REJECTED,SessionEndReason.HANDSHAKE_TIMEOUT->EventCategory.CLIENT
        else->when(session.transport){TransportKind.RELAY,TransportKind.WORKER->EventCategory.RELAY
            TransportKind.TCP->EventCategory.TCP;TransportKind.HTTP2->EventCategory.HTTP2;else->EventCategory.ROUTE}
    }
    return DiagnosticEvent(wall,category,normal,kind=EventKind.SESSION_CLOSED,transport=session.transport,session=session)
}

internal fun eventCategory(issue:ProxyIssue)=when(issue){
    ProxyIssue.CONFIGURATION,ProxyIssue.SECRET_UNAVAILABLE,ProxyIssue.STORAGE->EventCategory.CONFIGURATION
    ProxyIssue.PORT,ProxyIssue.SERVICE_START->EventCategory.SERVICE
    ProxyIssue.DNS->EventCategory.DNS;ProxyIssue.TLS->EventCategory.TLS;ProxyIssue.TCP->EventCategory.TCP
    ProxyIssue.WSS_HANDSHAKE->EventCategory.WSS;ProxyIssue.RELAY->EventCategory.RELAY;ProxyIssue.HTTP2->EventCategory.HTTP2
    ProxyIssue.HANDSHAKE->EventCategory.CLIENT;ProxyIssue.NETWORK,ProxyIssue.NETWORK_CHANGED->EventCategory.NETWORK
    else->EventCategory.ROUTE
}
internal fun issueEvent(issue:ProxyIssue,wall:Long,fatal:Boolean=false)=DiagnosticEvent(wall,eventCategory(issue),false,
    issue=issue,level=if(fatal || issue in setOf(ProxyIssue.CONFIGURATION,ProxyIssue.PORT,ProxyIssue.SECRET_UNAVAILABLE,
        ProxyIssue.STORAGE,ProxyIssue.SERVICE_START))EventLevel.ERROR else EventLevel.WARNING)

internal fun uptimeMillis(state:DiagnosticHistoryState,nowElapsed:Long):Long? = state.startedElapsed?.let {
    ((state.stoppedElapsed?:nowElapsed)-it).coerceAtLeast(0)
}
