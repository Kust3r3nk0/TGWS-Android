package io.github.kust3r3nk0.tgwsandroid.core

enum class ConnectionNetwork { UNKNOWN, WIFI, MOBILE, OTHER }
enum class ProfileId { AUTOMATIC, WIFI, MOBILE, CUSTOM }
enum class RoutingMode { AUTOMATIC, MANUAL, FAILOVER }
enum class PowerPolicy { BALANCED, SAVER }

/** Transport permissions only. Credentials stay in the existing encrypted configuration. */
data class ConnectionProfile(
    val mode:RoutingMode=RoutingMode.AUTOMATIC,
    val allowed:Set<TransportKind> = TransportKind.entries.toSet(),
    val primary:TransportKind=TransportKind.WSS,
    val backups:Set<TransportKind> = TransportKind.entries.toSet()-TransportKind.WSS,
    val adaptiveRecovery:Boolean=true,
    val power:PowerPolicy=PowerPolicy.BALANCED,
) {
    init { require(allowed.isNotEmpty() && primary in allowed && backups.all{it in allowed && it!=primary}) }
    fun permits(kind:TransportKind)=kind in allowed && when(mode){
        RoutingMode.AUTOMATIC->true
        RoutingMode.MANUAL->kind==primary
        RoutingMode.FAILOVER->kind==primary || kind in backups
    }
}

/** Opt-in migration: an old five-line transport record keeps exactly its old routing. */
data class ConnectionProfiles(
    val enabled:Boolean=false,val automaticNetwork:Boolean=true,val selected:ProfileId=ProfileId.AUTOMATIC,
    val automatic:ConnectionProfile=ConnectionProfile(),val wifi:ConnectionProfile=ConnectionProfile(),
    val mobile:ConnectionProfile=ConnectionProfile(),val custom:ConnectionProfile=ConnectionProfile(),
) {
    fun id(network:ConnectionNetwork)=if(!automaticNetwork)selected else when(network){
        ConnectionNetwork.WIFI->ProfileId.WIFI;ConnectionNetwork.MOBILE->ProfileId.MOBILE;else->ProfileId.AUTOMATIC
    }
    fun profile(id:ProfileId)=when(id){ProfileId.AUTOMATIC->automatic;ProfileId.WIFI->wifi;ProfileId.MOBILE->mobile;ProfileId.CUSTOM->custom}
    fun active(network:ConnectionNetwork)=profile(id(network))
    fun withProfile(id:ProfileId,value:ConnectionProfile)=when(id){
        ProfileId.AUTOMATIC->copy(automatic=value);ProfileId.WIFI->copy(wifi=value)
        ProfileId.MOBILE->copy(mobile=value);ProfileId.CUSTOM->copy(custom=value)
    }
    fun encode():String=listOf("1",if(enabled)"1" else "0",if(automaticNetwork)"1" else "0",selected.name,
        *ProfileId.entries.map{id->profile(id).let{p->listOf(p.mode.name,mask(p.allowed),p.primary.name,
            mask(p.backups),if(p.adaptiveRecovery)"1" else "0",p.power.name).joinToString(";")}}.toTypedArray()).joinToString("|")
    companion object {
        private fun mask(kinds:Set<TransportKind>)=kinds.fold(0){m,k->m or (1 shl k.ordinal)}.toString()
        private fun kinds(text:String):Set<TransportKind>{
            val value=text.toInt();require(value in 0 until (1 shl TransportKind.entries.size))
            return TransportKind.entries.filter{value and (1 shl it.ordinal)!=0}.toSet()
        }
        private fun flag(text:String):Boolean{require(text in setOf("0","1"));return text=="1"}
        fun decode(text:String):ConnectionProfiles {
            require(text.length<=1024)
            val fields=text.split('|');require(fields.size==8 && fields[0]=="1")
            val profiles=fields.drop(4).map{row->val p=row.split(';');require(p.size==6)
                ConnectionProfile(RoutingMode.valueOf(p[0]),kinds(p[1]),TransportKind.valueOf(p[2]),kinds(p[3]),flag(p[4]),PowerPolicy.valueOf(p[5]))}
            return ConnectionProfiles(flag(fields[1]),flag(fields[2]),ProfileId.valueOf(fields[3]),profiles[0],profiles[1],profiles[2],profiles[3])
        }
    }
}
