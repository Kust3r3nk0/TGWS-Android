package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.CancellationSignal
import android.net.DnsResolver
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import io.github.kust3r3nk0.tgwsandroid.core.NetworkAccess
import java.net.Inet4Address
import java.net.Inet6Address

/** No process-wide binding or phone-state permission request. Unknown optional fields stay unknown. */
internal class AndroidNetworkMonitor(private val context:Context,private val handler:Handler,
    private val changed:(Boolean,NetworkView)->Unit):java.io.Closeable {
    private val connectivity=context.getSystemService(ConnectivityManager::class.java)
    private val state=DefaultNetworkState<Network>()
    @Volatile private var closed=false
    private val queries=java.util.concurrent.ConcurrentHashMap.newKeySet<CancellationSignal>()
    private var registered=false
    @Volatile var access:NetworkAccess?=null;private set
    private val callback=object:ConnectivityManager.NetworkCallback(){
        override fun onAvailable(network:Network)=refresh(network)
        override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities)=refresh(network)
        override fun onLinkPropertiesChanged(network:Network,properties:LinkProperties)=refresh(network)
        override fun onLost(network:Network){
            if(closed || state.current!=network)return
            val next=connectivity.activeNetwork
            if(next!=null && next!=network)refresh(next)
            else if(state.lost(network)){access=null;changed(true,NetworkView())}
        }
    }
    fun start(){
        check(!closed && !registered)
        connectivity.registerDefaultNetworkCallback(callback,handler);registered=true
        refresh()
    }
    fun refresh(network:Network?=connectivity.activeNetwork){
        if(closed)return
        val actual=connectivity.activeNetwork
        if(network==null){
            state.current?.let{if(state.lost(it)){access=null;changed(true,NetworkView())}}
            if(state.current==null)changed(false,NetworkView())
            return
        }
        if(network!=actual)return
        val caps=connectivity.getNetworkCapabilities(network)
        val links=connectivity.getLinkProperties(network)
        val kind=when{caps==null->AccessKind.NONE;caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)->AccessKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)->AccessKind.CELLULAR;else->AccessKind.OTHER}
        val validated=caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true
        val portal=caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)==true
        val internet=caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true
        val dns=links?.dnsServers.orEmpty().mapNotNull{it.hostAddress}.take(8)
        val ipv4=links?.linkAddresses.orEmpty().any{it.address is Inet4Address && internetAddress(it.address)}
        val ipv6=links?.linkAddresses.orEmpty().any{it.address is Inet6Address && internetAddress(it.address)}
        val signature=listOf(internet,portal,links?.linkAddresses,links?.routes,dns,links?.nat64Prefix,links?.privateDnsServerName).joinToString("|")
        val replaced=state.update(network,actual,signature)
        if(replaced){
            queries.forEach{it.cancel()}
            access=object:NetworkAccess {
                override val validated=validated
                // Lack of Android validation does not prove that a configured relay is unreachable.
                override val usable=internet && !portal
                override fun addresses(host:String):List<java.net.InetAddress>{
                    if(android.net.InetAddresses.isNumericAddress(host))return listOf(android.net.InetAddresses.parseNumericAddress(host))
                    val result=java.util.concurrent.CompletableFuture<List<java.net.InetAddress>>()
                    val cancel=CancellationSignal();queries+=cancel
                    try{
                        if(closed)throw java.net.UnknownHostException("Network closed")
                        DnsResolver.getInstance().query(network,host,DnsResolver.FLAG_EMPTY,
                            java.util.concurrent.Executor{it.run()},cancel,object:DnsResolver.Callback<List<java.net.InetAddress>>{
                                override fun onAnswer(answer:List<java.net.InetAddress>,rcode:Int){
                                    if(rcode==0 && answer.isNotEmpty())result.complete(answer)
                                    else result.completeExceptionally(java.net.UnknownHostException("DNS unavailable"))
                                }
                                override fun onError(error:DnsResolver.DnsException){result.completeExceptionally(java.net.UnknownHostException("DNS unavailable"))}
                            })
                        return result.get(8,java.util.concurrent.TimeUnit.SECONDS)
                    }finally{cancel.cancel();queries-=cancel}
                }
                override fun socket()=network.socketFactory.createSocket()
            }
        }
        changed(replaced,NetworkView(kind,validated,portal,
            caps?.let{!it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)},
            caps?.let{!it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)},ipv4,ipv6,
            links?.nat64Prefix?.toString(),dns,links?.isPrivateDnsActive==true,links?.privateDnsServerName,
            if(kind==AccessKind.CELLULAR)operator(caps)else OperatorView(),System.currentTimeMillis()))
    }
    private fun operator(caps:NetworkCapabilities?):OperatorView {
        val id=try{caps?.subscriptionIds?.singleOrNull() ?: SubscriptionManager.getActiveDataSubscriptionId()}
            catch(_:RuntimeException){return OperatorView()}
        if(id<0)return OperatorView()
        val base=context.getSystemService(TelephonyManager::class.java)?:return OperatorView()
        val manager=try{base.createForSubscriptionId(id)}catch(_:RuntimeException){return OperatorView()}
        fun text(block:()->String):String?=try{block()}catch(_:RuntimeException){null}
        val radio=if(context.checkSelfPermission(android.Manifest.permission.READ_BASIC_PHONE_STATE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)null
            else try{manager.dataNetworkType}catch(_:SecurityException){null}catch(_:UnsupportedOperationException){null}
        return OperatorView.fromActiveDataSim(true,text{manager.networkOperatorName},text{manager.networkOperator},
            text{manager.simOperatorName},text{manager.simOperator},radio)
    }
    override fun close(){
        if(closed)return
        closed=true;state.stop();access=null
        queries.forEach{it.cancel()};queries.clear()
        if(registered){connectivity.unregisterNetworkCallback(callback);registered=false}
    }
}
