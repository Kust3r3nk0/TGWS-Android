package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

internal fun interface SocketConnector {
    fun connect(host:String,port:Int,timeoutMillis:Int,own:(Socket)->Unit):Socket
}
internal interface NetworkAccess {
    val validated:Boolean
    val usable:Boolean get()=validated
    fun addresses(host:String):List<InetAddress>
    fun socket():Socket
}
internal class NetworkUnavailable : IOException("Active network unavailable")

/** Capture one immutable network per attempt; never resolve on one network and dial another. */
internal class NetworkConnector(private val current:()->NetworkAccess?) : SocketConnector,Closeable {
    private val dns=BoundedDns()
    @Volatile private var closed=false
    fun capture():NetworkAccess = current()?.takeIf{!closed && it.usable} ?: throw NetworkUnavailable()
    fun addresses(access:NetworkAccess,host:String,timeoutMillis:Int):List<InetAddress>{
        checkCurrent(access)
        val result=try{dns.resolveAll(host,timeoutMillis,access::addresses)}catch(_:IOException){
            checkCurrent(access);throw java.net.UnknownHostException("Network DNS unavailable")
        }
        checkCurrent(access);return result
    }
    fun checkCurrent(access:NetworkAccess){if(closed || current()!==access || !access.usable)throw NetworkUnavailable()}
    override fun connect(host:String,port:Int,timeoutMillis:Int,own:(Socket)->Unit):Socket {
        val access=capture()
        val deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong())
        val addresses=addresses(access,host,timeoutMillis).take(8)
        var last:IOException?=null
        for((index,address) in addresses.withIndex()){
            checkCurrent(access)
            val left=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime())
            if(left<=0)break
            val socket=access.socket()
            try{
                own(socket);socket.tcpNoDelay=true
                // Leave a bounded share for alternate IPv4/IPv6 answers; no fixed-IP family assumption.
                socket.connect(InetSocketAddress(address,port),(left/(addresses.size-index)).coerceIn(1,Int.MAX_VALUE.toLong()).toInt())
                checkCurrent(access);return socket
            }catch(error:Exception){socket.close();if(error !is IOException)throw error;last=error}
        }
        throw last ?: java.net.SocketTimeoutException("Connection deadline")
    }
    fun invalidate()=dns.invalidate()
    override fun close(){closed=true;dns.close()}
}

internal object SystemSocketConnector:SocketConnector {
    private val access=object:NetworkAccess{
        override val validated=true
        override fun addresses(host:String)=InetAddress.getAllByName(host).toList()
        override fun socket()=Socket()
    }
    private val connector=NetworkConnector{access}
    override fun connect(host:String,port:Int,timeoutMillis:Int,own:(Socket)->Unit)=connector.connect(host,port,timeoutMillis,own)
}
