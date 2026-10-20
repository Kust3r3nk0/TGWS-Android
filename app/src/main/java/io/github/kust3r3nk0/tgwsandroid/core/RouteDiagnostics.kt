package io.github.kust3r3nk0.tgwsandroid.core

enum class RouteStatus { UNCHECKED, CONNECTING, AVAILABLE, BACKOFF, UNAVAILABLE, CLOSED }
enum class WebSocketCloseCategory {
    BYTE_LIMIT, QUEUE_LIMIT, MESSAGE_LIMIT, FRAME_LIMIT, WRITE_TIMEOUT,
    TCP_ERROR, TCP_EOF, WS_ERROR, CLIENT_CLOSED, BAD_FRAME, BAD_INIT,
    INIT_TIMEOUT, IDLE_TIMEOUT, RELAY_ERROR
}
enum class RouteStage { SETUP, HANDSHAKE, READY, DNS, TLS, CLOSED }
data class RouteObservation(val id:Int,val dc:Int,val media:Boolean,val kind:TransportKind,
    val status:RouteStatus,val stage:RouteStage,val issue:ServerIssue?=null,
    val checkedAtMillis:Long=0,val setupMillis:Long?=null,val sent:Long=0,val received:Long=0,val timings:SetupTimings?=null,val attempts:Int=0,
    val closeCode:Int?=null,val closeCategory:WebSocketCloseCategory?=null,
    val upgrade:HttpUpgradeDiagnostic?=null,val retryInMillis:Long?=null)

/** Bounded, credential-free snapshots. Internal keys never cross the core/UI boundary. */
internal class RouteDiagnostics(private val nano:()->Long=System::nanoTime,private val wall:()->Long=System::currentTimeMillis){
    private val entries=linkedMapOf<String,RouteObservation>()
    private val starts=mutableMapOf<String,Long>()
    private var next=0
    @Synchronized fun mark(key:String,dc:Int,media:Boolean,kind:TransportKind,status:RouteStatus,issue:ServerIssue?=null,timings:SetupTimings?=null,
        upgrade:HttpUpgradeDiagnostic?=null,retryInMillis:Long?=null){
        if(!entries.containsKey(key) && entries.size>=32){val oldest=entries.keys.first();entries.remove(oldest);starts.remove(oldest)}
        val previous=entries[key]?:RouteObservation(++next,dc,media,kind,RouteStatus.UNCHECKED,RouteStage.SETUP)
        if(status==RouteStatus.CONNECTING)starts[key]=nano()
        val completed=status==RouteStatus.AVAILABLE || status==RouteStatus.UNAVAILABLE
        val elapsed=if(completed)starts.remove(key)?.let{java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(nano()-it).coerceAtLeast(0)}else previous.setupMillis
        entries[key]=previous.copy(media=media,kind=kind,status=status,issue=issue,
            stage=when{issue==ServerIssue.DNS->RouteStage.DNS;issue==ServerIssue.TLS->RouteStage.TLS;status==RouteStatus.AVAILABLE->RouteStage.READY;upgrade!=null->RouteStage.HANDSHAKE;else->RouteStage.SETUP},
            checkedAtMillis=if(completed)wall()else previous.checkedAtMillis,
            setupMillis=if(status==RouteStatus.CONNECTING)null else elapsed,
            timings=if(status==RouteStatus.CONNECTING)null else if(completed)timings else previous.timings,
            attempts=if(status==RouteStatus.CONNECTING)(previous.attempts+1).coerceAtLeast(0)else previous.attempts,
            closeCode=if(status==RouteStatus.CONNECTING || status==RouteStatus.AVAILABLE)null else previous.closeCode,
            closeCategory=if(status==RouteStatus.CONNECTING || status==RouteStatus.AVAILABLE)null else previous.closeCategory,
            upgrade=if(status==RouteStatus.CONNECTING)null else upgrade?:previous.upgrade,
            retryInMillis=retryInMillis)
    }
    @Synchronized fun traffic(key:String?,sent:Int=0,received:Int=0){
        val old=entries[key]?:return
        entries[key!!]=old.copy(sent=old.sent+sent,received=old.received+received)
    }
    @Synchronized fun snapshot():List<RouteObservation> = entries.values.toList()
    @Synchronized fun failed(key:String?,issue:ServerIssue,closeCode:Int?=null,closeCategory:WebSocketCloseCategory?=null){
        val old=entries[key]?:return
        entries[key!!]=old.copy(status=RouteStatus.UNAVAILABLE,stage=RouteStage.CLOSED,issue=issue,
            checkedAtMillis=wall(),closeCode=closeCode,closeCategory=closeCategory)
    }
    /** A closed session is not an unreachable route; never leave its stale READY state visible. */
    @Synchronized fun closed(key:String?,closeCode:Int?=null,closeCategory:WebSocketCloseCategory?=null):Boolean{
        val old=entries[key]?:return false
        if(old.status!=RouteStatus.AVAILABLE)return false
        entries[key!!]=old.copy(status=RouteStatus.CLOSED,stage=RouteStage.CLOSED,checkedAtMillis=wall(),
            closeCode=closeCode,closeCategory=closeCategory)
        return true
    }
    @Synchronized fun clear(){entries.clear();starts.clear()}
}

/** Classify the failed native setup layer, without copying exception text or private endpoints. */
internal fun setupFailure(error:Exception,fallback:ServerIssue):ServerIssue {
    var current:Throwable?=error
    val visited=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable,Boolean>())
    repeat(8){
        val cause=current?:return fallback
        if(!visited.add(cause))return fallback
        val issue=when(cause){
    is javax.net.ssl.SSLException->ServerIssue.TLS
    is java.net.UnknownHostException->ServerIssue.DNS
    is NetworkUnavailable->ServerIssue.NETWORK
    is java.net.SocketTimeoutException->ServerIssue.TIMEOUT
    is java.net.SocketException->ServerIssue.TCP
            else->null
        }
        if(issue!=null)return issue
        current=cause.cause
    }
    return fallback
}
