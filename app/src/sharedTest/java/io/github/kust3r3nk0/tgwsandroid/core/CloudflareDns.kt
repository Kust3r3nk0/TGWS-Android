package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.dnsoverhttps.DnsOverHttps
import okio.Buffer
import okio.ForwardingSource
import okio.buffer



/** Historical RFC 8484 fixture retained for local JVM/Android regressions. Never packaged in the app. */
internal class CloudflareDns private constructor(private val client:OkHttpClient,private val dns:Dns,
    private val valid:()->Boolean):Closeable {
    @Volatile private var closed=false
    private class Lookup(val call:Dns.Call){
        val result=java.util.concurrent.CompletableFuture<List<InetAddress>>()
        val addresses=mutableListOf<InetAddress>()
    }
    private val pending=mutableSetOf<Lookup>()
    fun lookup(host:String):List<InetAddress> {
        if(closed || !valid())throw NetworkUnavailable()
        if(!TelegramSecret.validDomain(host))throw UnknownHostException("Invalid DNS hostname")
        val lookup=Lookup(dns.newCall(Dns.Request(host)))
        synchronized(pending){if(closed)throw NetworkUnavailable();pending+=lookup}
        try {
            lookup.call.enqueue(object:Dns.Callback{
                override fun onRecords(call:Dns.Call,last:Boolean,records:List<Dns.Record>){
                    for(record in records.filterIsInstance<Dns.Record.IpAddress>())if(lookup.addresses.size<8 && record.address !in lookup.addresses)lookup.addresses+=record.address
                    if(last)lookup.result.complete(lookup.addresses.toList())
                }
                override fun onFailure(call:Dns.Call,e:IOException){lookup.result.completeExceptionally(e)}
            })
            val result=lookup.result.get(6500,TimeUnit.MILLISECONDS)
            if(closed || !valid())throw NetworkUnavailable()
            if(result.isEmpty())throw UnknownHostException("Cloudflare DNS unavailable")
            return result
        }catch(error:Exception){
            if(error is InterruptedException)Thread.currentThread().interrupt()
            if(closed || !valid())throw NetworkUnavailable()
            throw UnknownHostException("Cloudflare DNS unavailable")
        }finally{synchronized(pending){pending-=lookup};lookup.call.cancel()}
    }
    override fun close(){
        val calls=synchronized(pending){closed=true;pending.toList().also{pending.clear()}}
        calls.forEach{it.result.completeExceptionally(NetworkUnavailable());it.call.cancel()}
        client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdownNow()
    }
    companion object {
        const val ENDPOINT="https://cloudflare-dns.com/dns-query"
        val IPV4=listOf("1.1.1.1","1.0.0.1")
        val IPV6=listOf("2606:4700:4700::1111","2606:4700:4700::1001")
        fun create(network:NetworkAccess,valid:()->Boolean):CloudflareDns {
            val sockets=object:SocketFactory(){
                override fun createSocket():Socket {if(!valid())throw NetworkUnavailable();return network.socket()}
                override fun createSocket(host:String,port:Int)=throw UnsupportedOperationException()
                override fun createSocket(host:String,port:Int,local:InetAddress,localPort:Int)=throw UnsupportedOperationException()
                override fun createSocket(host:InetAddress,port:Int)=throw UnsupportedOperationException()
                override fun createSocket(host:InetAddress,port:Int,local:InetAddress,localPort:Int)=throw UnsupportedOperationException()
            }
            val bootstrap=(IPV4+IPV6).map(InetAddress::getByName) // numeric literals; no system lookup
            val client=OkHttpClient.Builder().socketFactory(sockets).connectionPool(ConnectionPool(1,30,TimeUnit.SECONDS))
                .dispatcher(Dispatcher().apply{maxRequests=4;maxRequestsPerHost=4})
                .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
                .connectTimeout(3,TimeUnit.SECONDS).readTimeout(5,TimeUnit.SECONDS).callTimeout(6,TimeUnit.SECONDS).build()
            return using(client,ENDPOINT,bootstrap,valid)
        }
        /** Injected strict fixture client/endpoint for local TLS tests; production endpoint is fixed above. */
        internal fun using(base:OkHttpClient,url:String,bootstrap:List<InetAddress>,valid:()->Boolean={true}):CloudflareDns {
            require(url.toHttpUrl().isHttps)
            val client=base.newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).addInterceptor{chain->
                val response=chain.proceed(chain.request())
                val body=response.body
                if(!response.isSuccessful || body.contentType()?.toString()?.substringBefore(';')!="application/dns-message" || body.contentLength()>65535){
                    response.close();throw IOException("Invalid DNS response")
                }
                val limited=object:ForwardingSource(body.source()){
                    var count=0L
                    override fun read(sink:Buffer,byteCount:Long):Long {
                        val n=super.read(sink,minOf(byteCount,65536-count));if(n>0)count+=n
                        if(count>65535)throw IOException("DNS response limit")
                        return n
                    }
                }.buffer()
                response.newBuilder().body(object:ResponseBody(){
                    override fun contentType()=body.contentType()
                    override fun contentLength()=body.contentLength()
                    override fun source()=limited
                }).build()
            }.build()
            val dns=DnsOverHttps.Builder().client(client).url(url.toHttpUrl()).bootstrapDnsHosts(bootstrap)
                // Routing validates hostnames; avoid platform-dependent public-suffix classification.
                .resolvePrivateAddresses(true).resolvePublicAddresses(true)
                .post(true).includeIPv6(true).includeServiceMetadata(false).build()
            return CloudflareDns(client,dns,valid)
        }
    }
}
