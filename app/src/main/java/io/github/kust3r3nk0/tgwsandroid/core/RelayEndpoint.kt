package io.github.kust3r3nk0.tgwsandroid.core

import java.net.URI
import java.util.Locale

/** User-controlled Worker-compatible WSS relay. Never include its credential-bearing URL in diagnostics. */
class RelayEndpoint private constructor(val domain: String, private val path: String) {
    internal fun sameEndpoint(other:RelayEndpoint)=domain==other.domain && path==other.path
    fun requestPath(dc: Int): String {
        val destination = requireNotNull(TelegramRoutes.ip(dc)) { "Unsupported relay DC" }
        return "$path?dst=$destination&dc=$dc"
    }

    override fun toString() = "RelayEndpoint(redacted)"

    companion object {
        const val MAX_URL_LENGTH = 1024

        /** Blank selects original direct WSS/TCP routes. Explicit relay mode never silently connects directly. */
        fun parse(value: String): RelayEndpoint? {
            if (value.isEmpty()) return null
            require(value.length <= MAX_URL_LENGTH && value.all { it.code in 33..126 }) { "Invalid relay URL" }
            val uri = try { URI(value) } catch (_: Exception) { throw IllegalArgumentException("Invalid relay URL") }
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            require(uri.scheme == "wss" && uri.rawUserInfo == null && uri.rawQuery == null &&
                uri.rawFragment == null && uri.port in listOf(-1, 443)) { "Invalid relay URL" }
            // Public DNS host only; IP literals, loopback names and empty labels are configuration errors.
            require(host.length in 4..253 && host.contains('.') && host.any { it in 'a'..'z' } &&
                !host.endsWith(".localhost") && !host.endsWith(".local") &&
                host.split('.').all { it.length in 1..63 && it.first().isLetterOrDigit() &&
                    it.last().isLetterOrDigit() && it.all { ch -> ch in 'a'..'z' || ch in '0'..'9' || ch == '-' } }) {
                "Invalid relay host"
            }
            val path = uri.rawPath.takeUnless { it.isNullOrEmpty() || it == "/" } ?: "/apiws"
            require(path.matches(Regex("/[a-zA-Z0-9/_-]+")) && !path.contains("//")) { "Invalid relay path" }
            return RelayEndpoint(host, path)
        }
    }
}
