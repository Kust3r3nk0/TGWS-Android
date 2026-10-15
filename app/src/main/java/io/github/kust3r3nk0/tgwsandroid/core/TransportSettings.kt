package io.github.kust3r3nk0.tgwsandroid.core

enum class TransportMode { DIRECT, RELAY_ONLY, AUTO_CLOUDFLARE }

/** Immutable bounded configuration. Never render credential-bearing endpoints in diagnostics. */
class TransportSettings(
    val mode: TransportMode = TransportMode.DIRECT,
    val fakeTlsDomain: String = "",
    val cloudflareDomains: List<String> = emptyList(),
    val workerUrls: List<String> = emptyList(),
    val http2Media: Boolean = false,
) {
    init {
        require(fakeTlsDomain.isEmpty() || TelegramSecret.validDomain(fakeTlsDomain))
        require(cloudflareDomains.size<=8 && cloudflareDomains.distinct().size==cloudflareDomains.size)
        require(cloudflareDomains.all{TelegramSecret.validDomain(it) && it.length<=240})
        require(workerUrls.size<=8 && workerUrls.distinct().size==workerUrls.size)
        workerUrls.forEach{require(it.isNotEmpty());RelayEndpoint.parse(it)}
        if(mode==TransportMode.AUTO_CLOUDFLARE)require(cloudflareDomains.isNotEmpty() || workerUrls.isNotEmpty())
        if(http2Media)require(mode==TransportMode.AUTO_CLOUDFLARE && cloudflareDomains.isNotEmpty())
    }
    fun encode(): String = listOf(mode.name,fakeTlsDomain,http2Media.toString(),cloudflareDomains.joinToString(","),workerUrls.joinToString(",")).joinToString("\n")
    override fun toString()="TransportSettings(${mode.name}, redacted)"
    override fun equals(other:Any?)=other is TransportSettings && mode==other.mode && fakeTlsDomain==other.fakeTlsDomain &&
        cloudflareDomains==other.cloudflareDomains && workerUrls==other.workerUrls && http2Media==other.http2Media
    override fun hashCode()=listOf(mode,fakeTlsDomain,cloudflareDomains,workerUrls,http2Media).hashCode()
    companion object {
        const val MAX_ENCODED=12288
        fun decode(value:String):TransportSettings {
            require(value.length<=MAX_ENCODED)
            val lines=value.split('\n');require(lines.size==5 && lines[2] in setOf("true","false"))
            return TransportSettings(TransportMode.valueOf(lines[0]),lines[1],list(lines[3]),list(lines[4]),lines[2].toBoolean())
        }
        fun list(text:String):List<String> = if(text.isEmpty())emptyList() else text.split(',')
        /** Validate a whole HTTPS list before atomic adoption; no partial update on malformed content. */
        fun trustedDomains(text:String):List<String> {
            require(text.length<=16384)
            val result=text.lineSequence().map{it.trim()}.filter{it.isNotEmpty() && !it.startsWith('#')}.toList()
            require(result.isNotEmpty())
            return TransportSettings(cloudflareDomains=result).cloudflareDomains
        }
    }
}
