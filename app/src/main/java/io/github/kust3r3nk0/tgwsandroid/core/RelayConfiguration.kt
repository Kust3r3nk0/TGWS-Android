package io.github.kust3r3nk0.tgwsandroid.core

import java.util.Base64

enum class RelayStrategy { MANUAL, FAILOVER, AUTOMATIC }
/** User labels and URLs are private configuration, never diagnostic text. */
data class RelayNode(val id:String,val name:String,val url:String,val media:Boolean=true){
    init {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,40}")))
        require(name.isNotBlank() && name.length<=48 && name.none{it.isISOControl()})
        require(RelayEndpoint.parse(url)!=null)
    }
    override fun toString()="RelayNode(redacted)"
}
data class RelayConfiguration(val enabled:Boolean=false,val nodes:List<RelayNode> = emptyList(),
    val primary:String?=null,val backups:List<String> = emptyList(),val strategy:RelayStrategy=RelayStrategy.FAILOVER,
    val preferMedia:Boolean=false){
    init {
        require(nodes.size<=8 && nodes.map{it.id}.distinct().size==nodes.size)
        require(nodes.indices.all{i->(0 until i).none{j->RelayEndpoint.parse(nodes[i].url)!!.sameEndpoint(RelayEndpoint.parse(nodes[j].url)!!) }})
        require(if(nodes.isEmpty())primary==null && !enabled else nodes.any{it.id==primary})
        require(backups.distinct().size==backups.size && backups.all{id->id!=primary && nodes.any{it.id==id}})
    }
    fun candidates(media:Boolean):List<RelayNode> {
        if(!enabled)return emptyList()
        val ids=listOfNotNull(primary)+if(strategy==RelayStrategy.MANUAL)emptyList()else backups
        return ids.mapNotNull{id->nodes.find{it.id==id}}.filter{!media || it.media}
    }
    fun put(node:RelayNode):RelayConfiguration=copy(nodes=if(nodes.any{it.id==node.id})nodes.map{if(it.id==node.id)node else it}else nodes+node,primary=primary?:node.id)
    fun remove(id:String):RelayConfiguration {
        val next=nodes.filter{it.id!=id};val first=primary?.takeIf{it!=id}?:next.firstOrNull()?.id
        return copy(nodes=next,primary=first,backups=backups.filter{it!=id && it!=first},enabled=enabled && next.isNotEmpty())
    }
    override fun toString()="RelayConfiguration(enabled=$enabled, nodes=${nodes.size}, strategy=$strategy)"
    fun encode():String {
        fun b(s:String)=Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))
        return listOf("1",if(enabled)"1"else"0",strategy.name,primary.orEmpty(),backups.joinToString(","),if(preferMedia)"1"else"0",
            nodes.joinToString(","){listOf(it.id,b(it.name),b(it.url),if(it.media)"1"else"0").joinToString(";")}).joinToString("|")
    }
    companion object {
        fun decode(text:String):RelayConfiguration {
            require(text.length<=16384)
            val f=text.split('|');require(f.size==7 && f[0]=="1")
            fun flag(s:String):Boolean{require(s in setOf("0","1"));return s=="1"}
            fun b(s:String)=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(Base64.getUrlDecoder().decode(s))).toString()
            val nodes=if(f[6].isEmpty())emptyList()else f[6].split(',').map{row->val n=row.split(';');require(n.size==4);RelayNode(n[0],b(n[1]),b(n[2]),flag(n[3]))}
            return RelayConfiguration(flag(f[1]),nodes,f[3].ifEmpty{null},if(f[4].isEmpty())emptyList()else f[4].split(','),RelayStrategy.valueOf(f[2]),flag(f[5]))
        }
    }
}
