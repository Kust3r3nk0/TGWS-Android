package io.github.kust3r3nk0.tgwsandroid.core

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Telegram ee is Fake TLS; ee transport marker inside Obfuscated2 is a separate concept. */
object TelegramSecret {
    class Decoded(val secret: String, val domain: String) { override fun toString()="TelegramSecret(redacted)" }
    class Imported(val port: Int, val decoded: Decoded) { override fun toString()="TelegramLink(redacted)" }
    fun validDomain(value: String): Boolean = value.length in 4..253 && value.contains('.') &&
        value.any{it in 'a'..'z'} && !value.endsWith(".local") && !value.endsWith(".localhost") &&
        value.split('.').all{it.length in 1..63 && it.first().isLetterOrDigit() && it.last().isLetterOrDigit() &&
            it.all{c->c in 'a'..'z' || c in '0'..'9' || c=='-'}}
    fun decode(value: String): Decoded {
        require(value.length in 32..540 && value.all{it in "0123456789abcdefABCDEF"}){"Invalid Telegram secret"}
        val normalized=value.lowercase(java.util.Locale.ROOT)
        if(normalized.length==32)return Decoded(normalized,"")
        require(normalized.length%2==0)
        val prefix=normalized.take(2)
        if(prefix=="dd" && normalized.length==34)return Decoded(normalized.drop(2),"")
        require(prefix=="ee" && normalized.length>34)
        val domainBytes=normalized.drop(34).chunked(2).map{it.toInt(16).toByte()}.toByteArray()
        require(domainBytes.all{it.toInt() in 33..126})
        val domain=domainBytes.toString(Charsets.US_ASCII)
        require(validDomain(domain))
        return Decoded(normalized.substring(2,34),domain)
    }
    fun encode(secret: String, domain: String = ""): String {
        require(secret.matches(Regex("[a-fA-F0-9]{32}")))
        require(domain.isEmpty() || validDomain(domain))
        return if(domain.isEmpty())"dd${secret.lowercase()}" else "ee${secret.lowercase()}"+
            domain.toByteArray(Charsets.US_ASCII).joinToString(""){"%02x".format(it.toInt() and 255)}
    }
    fun import(value: String): Imported {
        require(value.length<=2048)
        val uri=try{URI(value)}catch(_:Exception){throw IllegalArgumentException("Invalid Telegram link")}
        require((uri.scheme=="tg" && uri.host=="proxy" && uri.path.orEmpty().isEmpty()) ||
            (uri.scheme=="https" && uri.host=="t.me" && uri.path=="/proxy"))
        require(uri.rawFragment==null && uri.rawUserInfo==null && uri.port==-1)
        val pairs=uri.rawQuery.orEmpty().split('&').map{part->
            val v=part.split('=',limit=2);require(v.size==2)
            URLDecoder.decode(v[0],StandardCharsets.UTF_8) to URLDecoder.decode(v[1],StandardCharsets.UTF_8)
        }
        require(pairs.size==3 && pairs.map{it.first}.toSet()==setOf("server","port","secret"))
        val query=pairs.toMap();require(query["server"]=="127.0.0.1")
        val port=requireNotNull(query["port"]?.toIntOrNull());require(port in 1..65535)
        return Imported(port,decode(requireNotNull(query["secret"])))
    }
}
