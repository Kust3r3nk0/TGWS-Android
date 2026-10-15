package io.github.kust3r3nk0.tgwsandroid.core

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** HTTP/2 /api, not WebSocket-over-H2. Two outstanding requests per native session. */
internal class Http2MediaTransport(private val client:OkHttpClient,private val endpoint:HttpUrl,private val tag:ByteArray):RemoteTransport {
    override val kind=TransportKind.HTTP2
    private val lock=java.lang.Object()
    @Volatile private var closed=false
    @Volatile private var failure=false
    @Volatile private var setup:Call?=null
    private var incoming:Cipher?=null
    private var outgoing:Cipher?=null
    private val splitter=PacketSplitter(tag)
    private class Pending(val call:Call,val bytes:Int)
    private class Ready(val body:ByteArray,val pending:Pending,val release:()->Unit)
    private val pending=mutableSetOf<Pending>()
    private var queuedBytes=0
    private val replies=ArrayBlockingQueue<Any>(4)
    private val replyCapacity=Semaphore(1)
    private val end=Any()
    private fun ctr(key:ByteArray,iv:ByteArray)=Cipher.getInstance("AES/CTR/NoPadding").apply{init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),IvParameterSpec(iv))}

    fun preflight(){
        val call=client.newCall(Request.Builder().url(endpoint).head().build())
        setup=call;call.timeout().timeout(8,TimeUnit.SECONDS)
        try{call.execute().use{response->
            if(response.protocol!=Protocol.HTTP_2 || !(response.code in 200..299 || response.code in setOf(405,501)))
                throw IOException("HTTP2 preflight failed")
            if(closed)throw IOException("HTTP2 closed")
        }}finally{setup=null}
    }
    override fun send(data:ByteArray){
        if(closed)throw IOException("HTTP2 closed")
        if(incoming==null){
            require(data.size==64)
            val material=data.copyOfRange(8,56)
            val decode=ctr(material.copyOfRange(0,32),material.copyOfRange(32,48))
            require(decode.update(data).copyOfRange(56,60).contentEquals(tag))
            incoming=decode
            val reversed=material.reversedArray();outgoing=ctr(reversed.copyOfRange(0,32),reversed.copyOfRange(32,48))
            return
        }
        val plain=requireNotNull(incoming).update(data)
        for(frame in splitter.split(plain,plain))submit(HttpMtprotoFraming.body(frame,tag))
    }
    private fun submit(body:ByteArray){
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        val request=Request.Builder().url(endpoint).header("Accept-Encoding","identity")
            .post(body.toRequestBody("application/octet-stream".toMediaType())).build()
        val call=client.newCall(request);call.timeout().timeout(65,TimeUnit.SECONDS)
        val ticket=Pending(call,body.size)
        synchronized(lock){
            while(!closed && (pending.size>=2 || queuedBytes+body.size>PacketSplitter.MAX_PAYLOAD_BYTES)){
                val remaining=deadline-System.nanoTime();if(remaining<=0)throw java.net.SocketTimeoutException("HTTP2 request capacity")
                TimeUnit.NANOSECONDS.timedWait(lock,remaining)
            }
            if(closed)throw IOException("HTTP2 closed")
            pending+=ticket;queuedBytes+=body.size
        }
        call.enqueue(object:Callback{
            override fun onFailure(call:Call,e:IOException){fail()}
            override fun onResponse(call:Call,response:Response){
                var owned=false;var adopted=false
                try{response.use{r->
                    val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(65)
                    while(!closed && !owned){
                        if(System.nanoTime()>=until)throw java.net.SocketTimeoutException("HTTP2 reply capacity")
                        owned=replyCapacity.tryAcquire(100,TimeUnit.MILLISECONDS)
                    }
                    if(closed)throw IOException("HTTP2 closed")
                    if(r.protocol!=Protocol.HTTP_2 || r.code!=200 || r.header("Content-Type").orEmpty().contains("text/html",true))
                        throw IOException("HTTP2 response rejected")
                    val entity=r.body
                    if(entity.contentLength()>PacketSplitter.MAX_PAYLOAD_BYTES)throw IOException("HTTP2 response too large")
                    val bytes=ByteArrayOutputStream()
                    entity.byteStream().use{input->val chunk=ByteArray(65536)
                        while(!closed){val n=input.read(chunk);if(n<0)break
                            if(bytes.size()+n>PacketSplitter.MAX_PAYLOAD_BYTES)throw IOException("HTTP2 response too large")
                            bytes.write(chunk,0,n)}
                    }
                    val result=bytes.toByteArray()
                    if(result.size%4!=0)throw IOException("Unaligned HTTP2 response")
                    if(result.isEmpty()){finish(ticket);return}
                    val released=AtomicBoolean()
                    val ready=Ready(result,ticket){if(released.compareAndSet(false,true))replyCapacity.release()}
                    synchronized(lock){if(!closed){if(!replies.offer(ready))throw IOException("HTTP2 reply queue full");adopted=true}}
                }}catch(_:Exception){fail()}
                finally{if(owned && !adopted)replyCapacity.release()}
            }
        })
    }
    private fun finish(ticket:Pending){synchronized(lock){if(pending.remove(ticket)){queuedBytes-=ticket.bytes;lock.notifyAll()}}}
    override fun receive():ByteArray? {
        val ready=replies.take()
        if(ready===end){if(failure)throw IOException("HTTP2 stream failed");return null}
        ready as Ready
        try{return requireNotNull(outgoing).update(HttpMtprotoFraming.reply(ready.body,tag))}
        finally{ready.release();finish(ready.pending)}
    }
    private fun fail(){failure=true;close()}
    override fun close(){
        val calls=synchronized(lock){
            if(closed)return
            closed=true
            val all=pending.map{it.call};pending.clear();queuedBytes=0;lock.notifyAll()
            replies.filterIsInstance<Ready>().forEach{it.release()};replies.clear();replies.offer(end)
            all
        }
        setup?.cancel();calls.forEach{it.cancel()}
    }
    companion object {
        fun open(client:OkHttpClient,endpoint:HttpUrl,tag:ByteArray,own:(Closeable)->Unit):Http2MediaTransport {
            val transport=Http2MediaTransport(client,endpoint,tag)
            try{own(transport);transport.preflight();return transport}catch(error:Exception){transport.close();throw error}
        }
    }
}
