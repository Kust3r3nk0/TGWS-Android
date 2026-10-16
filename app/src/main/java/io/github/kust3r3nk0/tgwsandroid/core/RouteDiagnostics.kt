package io.github.kust3r3nk0.tgwsandroid.core

enum class RouteStatus { UNCHECKED, CONNECTING, AVAILABLE, BACKOFF, UNAVAILABLE }
enum class RouteStage { SETUP, HANDSHAKE, READY, DNS, TLS, CLOSED }
data class RouteObservation(val id:Int,val dc:Int,val media:Boolean,val kind:TransportKind,
    val status:RouteStatus,val stage:RouteStage,val issue:ServerIssue?=null,
    val checkedAtMillis:Long=0,val setupMillis:Long?=null,val sent:Long=0,val received:Long=0,val timings:SetupTimings?=null,val attempts:Int=0)

/** Bounded, credential-free snapshots. Internal keys never cross the core/UI boundary. */
internal class RouteDiagnostics(private val nano:()->Long=System::nanoTime,private val wall:()->Long=System::currentTimeMillis){
    private val entries=linkedMapOf<String,RouteObservation>()
    private val starts=mutableMapOf<String,Long>()
    private var next=0
    @Synchronized fun mark(key:String,dc:Int,media:Boolean,kind:TransportKind,status:RouteStatus,issue:ServerIssue?=null,timings:SetupTimings?=null){
        if(!entries.containsKey(key) && entries.size>=32){val oldest=entries.keys.first();entries.remove(oldest);starts.remove(oldest)}
        val previous=entries[key]?:RouteObservation(++next,dc,media,kind,RouteStatus.UNCHECKED,RouteStage.SETUP)
        if(status==RouteStatus.CONNECTING)starts[key]=nano()
        val completed=status==RouteStatus.AVAILABLE || status==RouteStatus.UNAVAILABLE
        val elapsed=if(completed)starts.remove(key)?.let{java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(nano()-it).coerceAtLeast(0)}else previous.setupMillis
        entries[key]=previous.copy(media=media,kind=kind,status=status,issue=issue,
            stage=when{issue==ServerIssue.DNS->RouteStage.DNS;issue==ServerIssue.TLS->RouteStage.TLS;status==RouteStatus.AVAILABLE->RouteStage.READY;else->RouteStage.SETUP},
            checkedAtMillis=if(completed)wall()else previous.checkedAtMillis,setupMillis=elapsed,timings=if(completed)timings else previous.timings,
            attempts=if(status==RouteStatus.CONNECTING)(previous.attempts+1).coerceAtLeast(0)else previous.attempts)
    }
    @Synchronized fun traffic(key:String?,sent:Int=0,received:Int=0){
        val old=entries[key]?:return
        entries[key!!]=old.copy(sent=old.sent+sent,received=old.received+received)
    }
    @Synchronized fun snapshot():List<RouteObservation> = entries.values.toList()
    @Synchronized fun failed(key:String?,issue:ServerIssue){
        val old=entries[key]?:return
        entries[key!!]=old.copy(status=RouteStatus.UNAVAILABLE,stage=RouteStage.CLOSED,issue=issue,checkedAtMillis=wall())
    }
    @Synchronized fun clear(){entries.clear();starts.clear()}
}
