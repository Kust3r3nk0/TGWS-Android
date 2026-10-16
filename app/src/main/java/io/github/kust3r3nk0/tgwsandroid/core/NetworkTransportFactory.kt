package io.github.kust3r3nk0.tgwsandroid.core

import java.io.Closeable
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol

/** One service-owned factory; network changes close HTTP pools and invalidate bounded DNS. */
internal class NetworkTransportFactory(private val dnsMode:DnsMode=DnsMode.SYSTEM,private val current:()->NetworkAccess?):TransportFactory,Closeable {
    val connector=NetworkConnector{networkAccess()}
    private var dnsNetwork:NetworkAccess?=null
    private var dnsAccess:NetworkAccess?=null
    private var cloudflare:CloudflareDns?=null
    @Synchronized private fun networkAccess():NetworkAccess? {
        val network=current()?:return null
        if(dnsMode==DnsMode.SYSTEM)return network
        if(dnsNetwork!==network){
            disposeDns()
            val resolver=CloudflareDns.create(network){!closed && current()===network}
            cloudflare=resolver;dnsNetwork=network
            dnsAccess=object:NetworkAccess by network {
                override fun addresses(host:String):List<InetAddress> =
                    if(host.matches(Regex("[0-9.]+")) || host.contains(':'))network.addresses(host) else resolver.lookup(host)
            }
        }
        return dnsAccess
    }
    override val supportsWarmWebSockets=true
    private var clientNetwork:NetworkAccess?=null
    private var client:OkHttpClient?=null
    @Volatile private var closed=false
    private fun wrap(ws:RawWebSocket,kind:TransportKind)=object:RemoteTransport {
        override val kind=kind
        override val setupTimings get()=ws.setupTimings
        override fun send(data:ByteArray)=ws.send(data)
        override fun receive()=ws.receive()
        override fun verifyIdle()=ws.probeIdle()
        override fun close()=ws.abort()
        override fun abort()=ws.abort()
    }
    override fun openWebSocket(address:String,domain:String,own:(Closeable)->Unit):RemoteTransport {
        if(closed)throw NetworkUnavailable()
        return wrap(RawWebSocket.connect(address,domain,socketOwner={own(it)},connector=connector),TransportKind.WSS)
    }
    override fun openRelay(endpoint:RelayEndpoint,dc:Int,own:(Closeable)->Unit):RemoteTransport {
        if(closed)throw NetworkUnavailable()
        return wrap(RawWebSocket.connect(endpoint.domain,endpoint.domain,endpoint.requestPath(dc),socketOwner={own(it)},connector=connector),TransportKind.RELAY)
    }
    override fun openTcp(address:String,own:(Closeable)->Unit):RemoteTransport {
        val socket=connector.connect(address,443,8000){own(it)}
        socket.soTimeout=0
        val timing=connector.takeTiming(socket)
        return object:RemoteTransport {
            override val kind=TransportKind.TCP
            override val setupTimings=timing
            override fun send(data:ByteArray){socket.getOutputStream().write(data);socket.getOutputStream().flush()}
            override fun receive():ByteArray?{val bytes=ByteArray(65536);val n=socket.getInputStream().read(bytes);return if(n<0)null else bytes.copyOf(n)}
            override fun close()=socket.close()
        }
    }
    @Synchronized private fun httpClient():OkHttpClient {
        if(closed)throw NetworkUnavailable()
        val access=connector.capture()
        if(clientNetwork===access)return requireNotNull(client)
        disposeClient()
        val factory=object:SocketFactory(){
            override fun createSocket():Socket{connector.checkCurrent(access);return access.socket()}
            override fun createSocket(host:String,port:Int)=throw UnsupportedOperationException("Unconnected sockets only")
            override fun createSocket(host:String,port:Int,local:InetAddress,localPort:Int)=throw UnsupportedOperationException("Unconnected sockets only")
            override fun createSocket(host:InetAddress,port:Int)=throw UnsupportedOperationException("Unconnected sockets only")
            override fun createSocket(host:InetAddress,port:Int,local:InetAddress,localPort:Int)=throw UnsupportedOperationException("Unconnected sockets only")
        }
        val dispatcher=Dispatcher().apply{maxRequests=8;maxRequestsPerHost=8}
        val result=OkHttpClient.Builder().socketFactory(factory).dns(Dns{host->connector.addresses(access,host,8000)})
            .dispatcher(dispatcher).connectionPool(ConnectionPool(2,30,TimeUnit.SECONDS))
            .protocols(listOf(Protocol.HTTP_2,Protocol.HTTP_1_1)).retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false).connectTimeout(8,TimeUnit.SECONDS)
            .readTimeout(65,TimeUnit.SECONDS).writeTimeout(10,TimeUnit.SECONDS).callTimeout(65,TimeUnit.SECONDS).build()
        clientNetwork=access;client=result;return result
    }
    override fun openHttpMedia(domain:String,tag:ByteArray,own:(Closeable)->Unit):RemoteTransport {
        require(TelegramSecret.validDomain(domain))
        return Http2MediaTransport.open(httpClient(),"https://$domain/api".toHttpUrl(),tag,own)
    }
    @Synchronized private fun disposeClient(){
        client?.let{it.dispatcher.cancelAll();it.connectionPool.evictAll();it.dispatcher.executorService.shutdownNow()}
        client=null;clientNetwork=null
    }
    @Synchronized private fun disposeDns(){cloudflare?.close();cloudflare=null;dnsNetwork=null;dnsAccess=null}
    fun invalidate(){connector.invalidate();disposeClient();disposeDns()}
    override fun close(){closed=true;connector.close();disposeClient();disposeDns()}
}
