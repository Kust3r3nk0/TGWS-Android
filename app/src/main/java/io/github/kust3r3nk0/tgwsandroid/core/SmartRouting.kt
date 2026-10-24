package io.github.kust3r3nk0.tgwsandroid.core

import kotlin.math.pow

enum class ConnectionQuality { EXCELLENT, GOOD, UNSTABLE, POOR, INSUFFICIENT }
enum class SelectionReason { CONFIGURED_ORDER, USER_PRIMARY, USER_MANUAL, RECENT_PROGRESS, CONTROLLED_PROBE, FALLBACK }
data class RouteSelection(val profile:ProfileId,val transport:TransportKind,val dc:Int,val media:Boolean,
    val reason:SelectionReason,val backup:Boolean,val relaySlot:Int?=null)
data class ActiveConnection(val id:Long,val dc:Int,val media:Boolean,val transport:TransportKind,
    val durationMillis:Long,val inboundProgress:Boolean,val relaySlot:Int?=null)
data class ConnectionHealth(val quality:ConnectionQuality=ConnectionQuality.INSUFFICIENT,
    val attempts:Int=0,val setupSucceeded:Int=0,val withInboundData:Int=0,val failures:Int=0,
    val abnormalClosures:Int=0,val successPercent:Int?=null,val dropsPerMinute:Double?=null,
    val averageSetupMillis:Long?=null,val selection:RouteSelection?=null,
    val active:List<ActiveConnection> = emptyList(),val measuredAtMillis:Long=0,
    val enabled:Boolean=false,val profile:ProfileId=ProfileId.AUTOMATIC)

/** Internal opaque keys may identify endpoints. They never enter a public snapshot or log. */
internal class RoutingCandidate(val key:String,val kind:TransportKind,val dc:Int,val media:Boolean) {
    override fun toString()="RoutingCandidate(${kind.name}, dc=$dc, media=$media)"
}
internal data class PlannedRoute(val candidate:RoutingCandidate,val reason:SelectionReason)

/** Passive, bounded, network-generation-scoped evidence. No worker, network polling or socket IO. */
internal class SmartRouting(private val now:()->Long={System.nanoTime()/1_000_000}) {
    class Token internal constructor(internal val id:Long,internal val generation:Long)
    private class Sample(val token:Token,val route:RoutingCandidate,val start:Long) {
        var setup:Long?=null;var data=false;var failure=false;var abnormal=false
        var ended:Long?=null
    }
    private val samples=ArrayDeque<Sample>()
    private var generation=0L
    private var sequence=0L
    private var nextProbe=0L
    private var firstObservation:Long?=null
    private var selection:RouteSelection?=null
    private val window=10*60_000L
    private fun prune(){val t=now();while(samples.isNotEmpty() && (samples.size>128 || t-samples.first().start>window))samples.removeFirst()}
    private fun sample(token:Token)=if(token.generation==generation)samples.find{it.token.id==token.id}else null
    @Synchronized fun begin(route:RoutingCandidate):Token {
        val token=Token(++sequence,generation);samples.addLast(Sample(token,route,now()))
        if(firstObservation==null)firstObservation=now()
        prune();return token
    }
    @Synchronized fun connected(token:Token,millis:Long){sample(token)?.setup=millis.coerceAtLeast(0)}
    @Synchronized fun progress(token:Token){sample(token)?.data=true}
    @Synchronized fun failed(token:Token){sample(token)?.let{if(it.ended==null){it.failure=true;it.ended=now()}}}
    @Synchronized fun cancel(token:Token){samples.removeAll{it.token.id==token.id && token.generation==generation}}
    @Synchronized fun end(token:Token,reason:SessionEndReason,close:WebSocketClose?) {
        val s=sample(token)?:return
        if(s.ended!=null)return
        s.ended=now()
        val remoteError=reason in setOf(SessionEndReason.REMOTE_IO,SessionEndReason.UPSTREAM_WRITE_TIMEOUT) ||
            reason==SessionEndReason.REMOTE_EOF && (!s.data || now()-s.start<30_000) ||
            reason==SessionEndReason.REMOTE_CLOSE && (close?.code !in setOf(1000,1001) || close?.category in setOf(WebSocketCloseCategory.BYTE_LIMIT,WebSocketCloseCategory.TCP_ERROR))
        s.abnormal=remoteError;s.failure=remoteError
        // Cancellation and malformed local clients cannot penalize an external route.
        if(!s.data && !remoteError)samples.remove(s)
    }
    @Synchronized fun reset(){generation++;samples.clear();firstObservation=null;selection=null;nextProbe=0}
    @Synchronized fun selected(value:RouteSelection){selection=value}

    @Synchronized fun order(candidates:List<RoutingCandidate>,profile:ConnectionProfile,enabled:Boolean):List<PlannedRoute> {
        if(!enabled)return candidates.map{PlannedRoute(it,SelectionReason.CONFIGURED_ORDER)}
        prune();val t=now();val allowed=candidates.filter{profile.permits(it.kind)}
        if(profile.mode!=RoutingMode.AUTOMATIC)return allowed.sortedBy{if(it.kind==profile.primary)0 else 1}.map{
            PlannedRoute(it,if(profile.mode==RoutingMode.MANUAL)SelectionReason.USER_MANUAL else if(it.kind==profile.primary)SelectionReason.USER_PRIMARY else SelectionReason.FALLBACK)}
        fun records(c:RoutingCandidate)=samples.filter{it.route.key==c.key && it.route.dc==c.dc && it.route.media==c.media}
        fun score(c:RoutingCandidate):Double {
            val history=records(c);var value=if(c.kind==profile.primary).1 else 0.0
            for(s in history){val weight=.5.pow((t-s.start).coerceAtLeast(0)/120_000.0)
                val duration=(s.ended?:t)-s.start
                // TCP/TLS/Upgrade alone has no positive reachability weight.
                if(s.data)value+=weight*(1.0+duration.coerceIn(0,120_000)/120_000.0)
                if(s.failure)value-=weight*2.0
                if(s.data && s.setup!=null)value-=weight*(s.setup!!.coerceAtMost(30_000)/300_000.0)
            };return value
        }
        val sorted=allowed.sortedByDescending(::score).map{c->PlannedRoute(c,
            if(records(c).any{it.data})SelectionReason.RECENT_PROGRESS else if(c.kind==profile.primary)SelectionReason.USER_PRIMARY else SelectionReason.CONFIGURED_ORDER)}.toMutableList()
        // Demand-driven exploration: at most one previously failing route/minute.
        // RouteBackoff still decides whether its actual connection may begin.
        val probeInterval=if(profile.power==PowerPolicy.SAVER)300_000L else 60_000L
        if(t>=nextProbe && sorted.size>1){
            val probe=sorted.drop(1).firstOrNull{p->records(p.candidate).lastOrNull()?.let{it.failure && t-(it.ended?:t)>=probeInterval}==true}
            if(probe!=null){sorted.remove(probe);sorted.add(0,probe.copy(reason=SelectionReason.CONTROLLED_PROBE));nextProbe=t+probeInterval}
        }
        return sorted
    }
    @Synchronized fun health(enabled:Boolean,profile:ProfileId):ConnectionHealth {
        prune();val list=samples.toList();val t=now()
        val withData=list.count{it.data};val failures=list.count{it.failure};val conclusive=list.count{it.data || it.failure}
        val percent=if(conclusive==0)null else (100*list.count{it.data && !it.failure}/conclusive)
        val quality=when{conclusive<5->ConnectionQuality.INSUFFICIENT;percent!!>=90->ConnectionQuality.EXCELLENT
            percent>=75->ConnectionQuality.GOOD;percent>=40->ConnectionQuality.UNSTABLE;else->ConnectionQuality.POOR}
        val setups=list.mapNotNull{it.setup};val abnormal=list.count{it.abnormal}
        return ConnectionHealth(quality,list.size,setups.size,withData,failures,abnormal,percent,
            firstObservation?.let{abnormal/(maxOf(60_000L,(t-it).coerceAtMost(window))/60_000.0)},
            setups.takeIf{it.isNotEmpty()}?.average()?.toLong(),selection,
            measuredAtMillis=t,enabled=enabled,profile=profile)
    }
    internal fun retainedSamples()=synchronized(this){samples.size}
}
