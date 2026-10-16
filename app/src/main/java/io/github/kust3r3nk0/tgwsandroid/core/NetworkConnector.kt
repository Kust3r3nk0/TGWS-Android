package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

internal fun interface SocketConnector {
    fun connect(host:String,port:Int,timeoutMillis:Int,own:(Socket)->Unit):Socket
    fun takeTiming(socket:Socket):SetupTimings?=null
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
    private val timings=java.util.Collections.synchronizedMap(java.util.WeakHashMap<Socket,SetupTimings>())
    fun capture():NetworkAccess = current()?.takeIf{!closed && it.usable} ?: throw NetworkUnavailable()
    fun addresses(access:NetworkAccess,host:String,timeoutMillis:Int):List<InetAddress>{
        checkCurrent(access)
        val result=try{dns.resolveAll(host,timeoutMillis,access::addresses)}catch(_:IOException){
            checkCurrent(access);throw java.net.UnknownHostException("Network DNS unavailable")
        }
        checkCurrent(access);return result
    }
    fun checkCurrent(access:NetworkAccess){if(closed || current()!==access || !access.usable)throw NetworkUnavailable()}
    private val epoch=java.util.concurrent.atomic.AtomicLong()
    private val dialing=java.util.concurrent.ConcurrentHashMap.newKeySet<Socket>()
    private val dialers=java.util.concurrent.ThreadPoolExecutor(8,8,0,TimeUnit.MILLISECONDS,
        java.util.concurrent.ArrayBlockingQueue(32),{task->Thread(task,"tgws-connect").apply{isDaemon=true}},
        java.util.concurrent.ThreadPoolExecutor.AbortPolicy())

    /** Connection phase of Happy Eyeballs: preserve OS preference, alternate families, two in flight. */
    override fun connect(host:String,port:Int,timeoutMillis:Int,own:(Socket)->Unit):Socket {
        require(timeoutMillis>0 && port in 1..65535)
        val access=capture()
        val generation=epoch.get()
        fun currentAttempt(){checkCurrent(access);if(epoch.get()!=generation)throw NetworkUnavailable()}
        val deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis.toLong())
        val dnsStart=System.nanoTime()
        val resolved=interleave(addresses(access,host,timeoutMillis)).take(8)
        val dnsMillis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-dnsStart)
        val answers=resolved
        val results=java.util.concurrent.ExecutorCompletionService<Pair<Socket,Long>>(dialers)
        val attempts=mutableMapOf<java.util.concurrent.Future<Pair<Socket,Long>>,Socket>()
        var winner:Socket?=null
        var last:IOException?=null
        var index=0
        var nextStart=0L
        fun remaining()=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()).coerceAtLeast(0)
        fun socket():Socket {
            currentAttempt()
            val socket=access.socket()
            try{dialing+=socket;own(socket);currentAttempt();if(socket.isClosed)throw IOException("Connection canceled");socket.tcpNoDelay=true;return socket}
            catch(error:Exception){dialing-=socket;socket.close();throw error}
        }
        fun dial(socket:Socket,address:InetAddress):Pair<Socket,Long> {
            currentAttempt()
            val left=remaining();if(left<=0)throw java.net.SocketTimeoutException("Connection deadline")
            val started=System.nanoTime()
            socket.connect(InetSocketAddress(address,port),left.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            currentAttempt()
            return socket to TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)
        }
        fun accept(result:Pair<Socket,Long>):Socket {
            currentAttempt()
            if(remaining()<=0)throw java.net.SocketTimeoutException("Connection deadline")
            if(result.first.isClosed)throw IOException("Connection canceled")
            timings[result.first]=SetupTimings(dnsMillis,result.second,resolvedAddresses=resolved.mapNotNull{it.hostAddress})
            winner=result.first;return result.first
        }
        try {
            // Numeric DC routes and single-family DNS answers do not need an extra worker.
            if(answers.size==1){
                val candidate=socket()
                try{return accept(dial(candidate,answers[0]))}
                finally{dialing-=candidate;if(winner!==candidate)candidate.close()}
            }
            while(index<answers.size || attempts.isNotEmpty()){
                currentAttempt()
                if(remaining()<=0)throw java.net.SocketTimeoutException("Connection deadline")
                if(index<answers.size && attempts.size<2 && (attempts.isEmpty() || System.nanoTime()>=nextStart)){
                    val candidate=socket()
                    val address=answers[index++]
                    try{attempts[results.submit{dial(candidate,address)}]=candidate}
                    catch(_:java.util.concurrent.RejectedExecutionException){dialing-=candidate;candidate.close();throw IOException("Connection capacity exhausted")}
                    nextStart=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(250)
                }
                val untilNext=if(index<answers.size && attempts.size<2)
                    TimeUnit.NANOSECONDS.toMillis(nextStart-System.nanoTime()).coerceAtLeast(1)else 50L
                val completed=results.poll(minOf(50L,untilNext,remaining().coerceAtLeast(1)),TimeUnit.MILLISECONDS)?:continue
                val candidate=attempts.remove(completed)?:continue
                try{return accept(completed.get())}
                catch(error:java.util.concurrent.ExecutionException){
                    val cause=error.cause
                    if(cause !is IOException)throw IOException("Connection failed")
                    last=cause
                }finally{dialing-=candidate;if(winner!==candidate)candidate.close()}
            }
            throw last ?: java.net.SocketTimeoutException("Connection deadline")
        }catch(_:InterruptedException){Thread.currentThread().interrupt();throw IOException("Connection interrupted")}
        finally{attempts.forEach{(future,socket)->socket.close();dialing-=socket;future.cancel(true)};dialers.purge()}
    }
    /** Keep the first OS-selected address first; do not bury the other family behind many AAAAs. */
    internal fun interleave(addresses:List<InetAddress>):List<InetAddress>{
        if(addresses.isEmpty())return emptyList()
        val firstV6=addresses.first() is java.net.Inet6Address
        val preferred=addresses.filter{(it is java.net.Inet6Address)==firstV6}
        val alternate=addresses.filter{(it is java.net.Inet6Address)!=firstV6}
        return buildList{for(i in 0 until maxOf(preferred.size,alternate.size)){
            preferred.getOrNull(i)?.let{add(it)};alternate.getOrNull(i)?.let{add(it)}
        }}
    }
    fun invalidate(){epoch.incrementAndGet();dns.invalidate();dialing.toList().forEach{it.close()}}
    override fun takeTiming(socket:Socket):SetupTimings?=timings.remove(socket)
    override fun close(){closed=true;invalidate();dns.close();dialers.shutdownNow()}
}

internal object SystemSocketConnector:SocketConnector {
    private val access=object:NetworkAccess{
        override val validated=true
        override fun addresses(host:String)=InetAddress.getAllByName(host).toList()
        override fun socket()=Socket()
    }
    private val connector=NetworkConnector{access}
    override fun connect(host:String,port:Int,timeoutMillis:Int,own:(Socket)->Unit)=connector.connect(host,port,timeoutMillis,own)
    override fun takeTiming(socket:Socket)=connector.takeTiming(socket)
}
