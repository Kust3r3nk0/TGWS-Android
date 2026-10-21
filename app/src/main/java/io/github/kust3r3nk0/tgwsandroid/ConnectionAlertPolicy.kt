package io.github.kust3r3nk0.tgwsandroid

import io.github.kust3r3nk0.tgwsandroid.core.ConnectionHealth
import io.github.kust3r3nk0.tgwsandroid.core.TransportKind

internal enum class ConnectionAlert { OUTAGE, RESTORED, FALLBACK, CONFIGURATION }
internal data class AlertObservation(val running:Boolean,val pending:Int=0,val networkUnavailable:Boolean=false,
    val criticalConfiguration:Boolean=false,val health:ConnectionHealth=ConnectionHealth())

/** One outstanding deadline, no polling, five-minute per-category notification budget. */
internal class ConnectionAlertPolicy {
    private var lossSince:Long?=null
    private var evaluated=false
    private var announced=false
    private var hadProgress=false
    private var previousFailures=0
    private var previousSelection:Pair<TransportKind,Int?>?=null
    private var configuration=false
    private val last=mutableMapOf<ConnectionAlert,Long>()
    val deadline:Long? get()=if(evaluated)null else lossSince?.plus(30_000)
    fun update(enabled:Boolean,s:AlertObservation,now:Long):List<ConnectionAlert> = buildList {
        fun allow(type:ConnectionAlert):Boolean {
            if(last[type]?.let{now-it<300_000}==true)return false
            last[type]=now;return true
        }
        if(!enabled){reset();return@buildList}
        if(s.criticalConfiguration && !configuration && allow(ConnectionAlert.CONFIGURATION))add(ConnectionAlert.CONFIGURATION)
        configuration=s.criticalConfiguration
        if(!s.running){lossSince=null;evaluated=false;announced=false;return@buildList}
        val up=s.health.active.any{it.inboundProgress} && !s.networkUnavailable
        if(up){
            hadProgress=true
            if(announced && allow(ConnectionAlert.RESTORED))add(ConnectionAlert.RESTORED)
            lossSince=null;evaluated=false;announced=false
        }else{
            val failed=s.health.failures>previousFailures
            if(lossSince==null && (s.pending>0 || failed || s.networkUnavailable && hadProgress))lossSince=now
            if(s.pending==0 && s.health.attempts==0 && !s.networkUnavailable){lossSince=null;evaluated=false}
            if(deadline?.let{now>=it}==true){
                evaluated=true
                if(allow(ConnectionAlert.OUTAGE)){announced=true;add(ConnectionAlert.OUTAGE)}
            }
        }
        previousFailures=s.health.failures
        s.health.selection?.let{selection->
            if(selection.backup && previousSelection!=(selection.transport to selection.relaySlot) && allow(ConnectionAlert.FALLBACK))add(ConnectionAlert.FALLBACK)
            previousSelection=selection.transport to selection.relaySlot
        }
    }
    fun reset(){lossSince=null;evaluated=false;announced=false;hadProgress=false;previousFailures=0;previousSelection=null;configuration=false;last.clear()}
}
