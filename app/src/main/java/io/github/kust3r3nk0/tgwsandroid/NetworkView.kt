package io.github.kust3r3nk0.tgwsandroid

enum class AccessKind { NONE, WIFI, CELLULAR, OTHER }
enum class RadioKind { UNKNOWN, G2, G3, LTE, LTE_PLUS, NR }
enum class IpSupport { UNKNOWN, IPV4, IPV6, DUAL }
/** Transport availability is distinct from Android's public-internet validation. */
internal data class NetworkAvailability(val internet:Boolean,val captivePortal:Boolean,val suspended:Boolean){
    val usable get()=internet && !captivePortal
    val signature get()="$internet|$captivePortal"
}
internal fun internetAddress(address:java.net.InetAddress)=!address.isLoopbackAddress && !address.isLinkLocalAddress && !address.isAnyLocalAddress
enum class CarrierBrand { MEGAFON, MTS, BEELINE, T2 }
data class CarrierCard(val brand:CarrierBrand,val primaryPlmn:String,val additional:Set<String>,val apn:String,val login:String?,val password:String?)
object CarrierReference {
    val cards=listOf(
        CarrierCard(CarrierBrand.MEGAFON,"25002",setOf("25014"),"internet",null,null),
        CarrierCard(CarrierBrand.MTS,"25001",emptySet(),"internet.mts.ru","mts","mts"),
        CarrierCard(CarrierBrand.BEELINE,"25099",emptySet(),"internet.beeline.ru","beeline","beeline"),
        CarrierCard(CarrierBrand.T2,"25020",emptySet(),"internet",null,null))
    fun hint(plmn:String?)=cards.firstOrNull{it.primaryPlmn==plmn || plmn in it.additional}
    fun radio(type:Int?):RadioKind = when(type){
        1,2,4,7,11,16->RadioKind.G2
        3,5,6,8,9,10,12,14,15,17->RadioKind.G3
        13->RadioKind.LTE;19->RadioKind.LTE_PLUS;20->RadioKind.NR;else->RadioKind.UNKNOWN
    }
}
data class OperatorView(val registeredName:String?=null,val registeredPlmn:String?=null,
    val simName:String?=null,val simPlmn:String?=null,val radio:RadioKind=RadioKind.UNKNOWN){
    companion object {
        fun fromActiveDataSim(available:Boolean,registeredName:String?,registeredPlmn:String?,simName:String?,simPlmn:String?,radio:Int?):OperatorView {
            if(!available)return OperatorView()
            fun plmn(value:String?)=value?.takeIf{it.matches(Regex("[0-9]{5,6}"))}
            fun name(value:String?)=value?.filter{!it.isISOControl()}?.trim()?.take(64)?.takeIf{it.isNotEmpty()}
            return OperatorView(name(registeredName),plmn(registeredPlmn),name(simName),plmn(simPlmn),CarrierReference.radio(radio))
        }
    }
}
data class NetworkView(val kind:AccessKind=AccessKind.NONE,val validated:Boolean=false,val captivePortal:Boolean=false,
    val metered:Boolean?=null,val roaming:Boolean?=null,val ipv4:Boolean=false,val ipv6:Boolean=false,
    val nat64Prefix:String?=null,val dnsServers:List<String> = emptyList(),val privateDns:Boolean=false,
    val privateDnsName:String?=null,val operator:OperatorView=OperatorView(),val observedAtMillis:Long=0){
    val ipSupport:IpSupport get()=when{ipv4&&ipv6->IpSupport.DUAL;ipv4->IpSupport.IPV4;ipv6->IpSupport.IPV6;else->IpSupport.UNKNOWN}
}

/** Ignore callbacks for a network which is no longer the system default. */
internal class DefaultNetworkState<T> {
    var current:T?=null;private set
    private var fingerprint:String?=null
    private var stopped=false
    fun update(network:T,actualDefault:T?,signature:String):Boolean {
        if(stopped || network!=actualDefault)return false
        if(current==network && fingerprint==signature)return false
        current=network;fingerprint=signature;return true
    }
    fun lost(network:T):Boolean {
        if(stopped || current!=network)return false
        current=null;fingerprint=null;return true
    }
    fun stop(){stopped=true;current=null;fingerprint=null}
}
