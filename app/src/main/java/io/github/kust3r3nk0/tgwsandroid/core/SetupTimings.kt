package io.github.kust3r3nk0.tgwsandroid.core

/** Null means that phase was not measured (for example a reused H2 connection), never an estimate. */
data class SetupTimings(val dnsMillis:Long?=null,val tcpMillis:Long?=null,val tlsMillis:Long?=null,val httpMillis:Long?=null)

internal class HttpSetupRecorder:okhttp3.EventListener(){
    private var dnsStart=0L
    private var httpStart=0L
    private class Tls(val started:Long=System.nanoTime(),var millis:Long?=null)
    private val connecting=mutableMapOf<java.net.InetSocketAddress,Long>()
    private val handshaking=mutableMapOf<Long,Tls>()
    private val completed=mutableMapOf<java.net.InetSocketAddress,SetupTimings>()
    private var measured=SetupTimings()
    private fun elapsed(start:Long)=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start).coerceAtLeast(0)
    @Synchronized override fun dnsStart(call:okhttp3.Call,domainName:String){dnsStart=System.nanoTime()}
    @Synchronized override fun dnsEnd(call:okhttp3.Call,domainName:String,inetAddressList:List<java.net.InetAddress>){measured=measured.copy(dnsMillis=elapsed(dnsStart))}
    @Synchronized override fun connectStart(call:okhttp3.Call,inetSocketAddress:java.net.InetSocketAddress,proxy:java.net.Proxy){connecting[inetSocketAddress]=System.nanoTime()}
    @Synchronized override fun secureConnectStart(call:okhttp3.Call){handshaking[Thread.currentThread().id]=Tls()}
    @Synchronized override fun secureConnectEnd(call:okhttp3.Call,handshake:okhttp3.Handshake?){handshaking[Thread.currentThread().id]?.let{it.millis=elapsed(it.started)}}
    @Synchronized override fun connectEnd(call:okhttp3.Call,inetSocketAddress:java.net.InetSocketAddress,proxy:java.net.Proxy,protocol:okhttp3.Protocol?){
        val tls=handshaking.remove(Thread.currentThread().id)
        connecting.remove(inetSocketAddress)?.let{started->
            val tcp=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis((tls?.started?:System.nanoTime())-started).coerceAtLeast(0)
            completed[inetSocketAddress]=SetupTimings(tcpMillis=tcp,tlsMillis=tls?.millis)
        }
    }
    @Synchronized override fun connectFailed(call:okhttp3.Call,inetSocketAddress:java.net.InetSocketAddress,proxy:java.net.Proxy,protocol:okhttp3.Protocol?,ioe:java.io.IOException){connecting.remove(inetSocketAddress);handshaking.remove(Thread.currentThread().id)}
    @Synchronized override fun connectionAcquired(call:okhttp3.Call,connection:okhttp3.Connection){
        // Happy Eyeballs can race IPv4/IPv6 connects. Report only the acquired socket.
        val selected=completed[connection.socket().remoteSocketAddress]
        measured=measured.copy(tcpMillis=selected?.tcpMillis,tlsMillis=selected?.tlsMillis)
    }
    @Synchronized override fun requestHeadersEnd(call:okhttp3.Call,request:okhttp3.Request){httpStart=System.nanoTime()}
    @Synchronized override fun responseHeadersEnd(call:okhttp3.Call,response:okhttp3.Response){measured=measured.copy(httpMillis=elapsed(httpStart))}
    @Synchronized fun snapshot()=measured
}
