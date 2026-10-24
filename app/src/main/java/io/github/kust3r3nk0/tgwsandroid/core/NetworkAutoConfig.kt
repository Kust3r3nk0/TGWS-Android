package io.github.kust3r3nk0.tgwsandroid.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/** Account-free req_pq_multi probe. Never uses the user's secret or Telegram session. */
internal object MtprotoAvailabilityProbe {
    fun check(remote:RemoteTransport) {
        val random=SecureRandom()
        val nonce=ByteArray(16).also(random::nextBytes)
        val handshake=Handshake(2,false,ByteArray(4){0xee.toByte()},2,ByteArray(48).also(random::nextBytes))
        val relay=MtprotoCrypto.createRelayInit(handshake)
        val crypto=MtprotoCrypto.buildContext(handshake,ByteArray(16),relay)
        val request=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(40).putLong(0).putLong((System.currentTimeMillis()/1000) shl 32)
            .putInt(20).putInt(0xbe7e8ef1.toInt()).put(nonce).array()
        remote.send(relay);remote.send(crypto.toTelegram.update(request))
        val bytes=java.io.ByteArrayOutputStream()
        while(bytes.size()<4096){
            val chunk=remote.receive()?:throw java.io.EOFException()
            require(chunk.isNotEmpty() && chunk.size<=4096-bytes.size())
            bytes.write(crypto.fromTelegram.update(chunk))
            val response=bytes.toByteArray()
            if(response.size>=4){
                val length=ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN).int
                require(length in 40..4092 && length%4==0)
                if(response.size>=length+4){require(validResponse(response.copyOfRange(4,length+4),nonce));return}
            }
        }
        throw java.net.ProtocolException("Probe response limit")
    }
    internal fun validResponse(body:ByteArray,nonce:ByteArray):Boolean {
        if(body.size<76 || nonce.size!=16)return false
        val b=ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
        if(b.long!=0L || b.long and 1L!=1L || b.int!=body.size-20 ||
            b.int!=0x05162463 || !ByteArray(16).also(b::get).contentEquals(nonce))return false
        b.position(b.position()+16) // server nonce
        val pq=b.get().toInt() and 255
        if(pq !in 1..8)return false
        val padded=((pq+1+3)/4)*4
        if(b.remaining()<padded-1+8)return false
        b.position(b.position()+padded-1)
        if(b.int!=0x1cb5c415)return false
        val count=b.int
        return count in 1..64 && b.remaining()==count*8
    }
}

internal class AutoConfigTarget(val kind:TransportKind,val nodeId:String?=null,val worker:String?=null,
    val open:(TransportFactory)->RemoteTransport) {
    override fun toString()="AutoConfigTarget($kind)"
}
internal data class AutoConfigObservation(val target:AutoConfigTarget,val millis:Long?,val issue:ServerIssue?,val upgrade:HttpUpgradeDiagnostic?=null)
internal object NetworkAutoConfig {
    fun targets(settings:TransportSettings,relay:String,network:ConnectionNetwork):List<AutoConfigTarget> {
        val allowed=if(settings.profiles.enabled)settings.profiles.active(network).allowed else TransportKind.entries.toSet()
        val result=mutableListOf<AutoConfigTarget>()
        if(settings.mode!=TransportMode.DIRECT && TransportKind.RELAY in allowed){
            if(settings.relays.enabled)settings.relays.candidates(false).take(2).forEach{node->
                result+=AutoConfigTarget(TransportKind.RELAY,node.id){it.openRelay(RelayEndpoint.parse(node.url)!!,2){}}
            }else RelayEndpoint.parse(relay)?.let{endpoint->result+=AutoConfigTarget(TransportKind.RELAY){it.openRelay(endpoint,2){}}}
        }
        if(settings.mode==TransportMode.AUTO_CLOUDFLARE){
            if(TransportKind.WORKER in allowed)settings.workerUrls.take(2).forEach{url->result+=AutoConfigTarget(TransportKind.WORKER,worker=url){it.openRelay(RelayEndpoint.parse(url)!!,2){}}}
            if(TransportKind.CLOUDFLARE in allowed)settings.cloudflareDomains.take(2).forEach{domain->result+=AutoConfigTarget(TransportKind.CLOUDFLARE){it.openWebSocket("kws2.$domain","kws2.$domain"){}}}
        }
        if(settings.mode!=TransportMode.RELAY_ONLY){
            if(TransportKind.WSS in allowed)result+=AutoConfigTarget(TransportKind.WSS){it.openWebSocket(TelegramRoutes.webSocketIp(2)!!,TelegramRoutes.webSocketDomains(2,false).first()){}}
            if(TransportKind.TCP in allowed)result+=AutoConfigTarget(TransportKind.TCP){it.openTcp(TelegramRoutes.ip(2)!!){}}
        }
        // One candidate from each kind before trying another alias of that kind.
        return result.withIndex().sortedBy{(index,t)->result.take(index).count{it.kind==t.kind}}.map{it.value}
    }
    fun apply(settings:TransportSettings,network:ConnectionNetwork,results:List<AutoConfigObservation>):TransportSettings? {
        val good=results.filter{it.issue==null && it.millis!=null}.sortedBy{it.millis}
        val winner=good.firstOrNull()?.target?:return null
        val id=when(network){ConnectionNetwork.WIFI->ProfileId.WIFI;ConnectionNetwork.MOBILE->ProfileId.MOBILE;else->ProfileId.AUTOMATIC}
        val previous=settings.profiles.profile(id)
        // A single failed probe must not permanently disable a route. Retain
        // explicit permissions; normal backoff controls subsequent fallback.
        val kinds=if(settings.profiles.enabled)settings.profiles.active(network).allowed else previous.allowed
        val profile=previous.copy(mode=RoutingMode.FAILOVER,allowed=kinds,primary=winner.kind,backups=kinds-winner.kind,adaptiveRecovery=true)
        val profiles=settings.profiles.withProfile(id,profile).copy(enabled=true,automaticNetwork=true)
        val workers=winner.worker?.let{listOf(it)+settings.workerUrls.filter{url->url!=it}}?:settings.workerUrls
        val relays=winner.nodeId?.let{node->settings.relays.copy(primary=node,backups=(listOfNotNull(settings.relays.primary)+settings.relays.backups).distinct().filter{it!=node})}?:settings.relays
        return TransportSettings(settings.mode,settings.fakeTlsDomain,settings.cloudflareDomains,workers,settings.http2Media,profiles,relays)
    }
}
