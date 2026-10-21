package io.github.kust3r3nk0.tgwsandroid

internal data class TrafficSample(val elapsed:Long,val sent:Long,val received:Long,
    val outgoingPerSecond:Long?,val incomingPerSecond:Long?,val sessions:Int,val reconnects:Int)

/** Passive real counters only. At most one sample/second, 60 in memory; no timer or IO. */
internal class TrafficHistory {
    var samples:List<TrafficSample> = emptyList();private set
    private var baseline:TrafficSample?=null
    fun reset(){samples=emptyList();baseline=null}
    fun accept(sent:Long,received:Long,sessions:Int,reconnects:Int,elapsed:Long){
        val previous=baseline
        if(previous!=null && (sent<previous.sent || received<previous.received || elapsed<previous.elapsed)){reset()}
        val before=baseline
        if(before!=null && elapsed-before.elapsed<1000)return
        if(before!=null && sent==before.sent && received==before.received && sessions==before.sessions && reconnects==before.reconnects)return
        val dt=before?.let{elapsed-it.elapsed}
        val valid=dt!=null && dt in 1000..30_000
        fun rate(value:Long,old:Long)=if(valid)((value-old).coerceAtLeast(0).toDouble()*1000/dt!!).toLong()else null
        val sample=TrafficSample(elapsed,sent,received,before?.let{rate(sent,it.sent)},before?.let{rate(received,it.received)},sessions,reconnects)
        baseline=sample;samples=(samples+sample).takeLast(60)
    }
}
