package io.github.kust3r3nk0.tgwsandroid.core

/** Public numbers and fixed categories only. Never retain HTTP headers, URLs or peer text. */
enum class UpgradeFailure { STATUS, STATUS_LINE, HEADERS, ACCEPT, SUBPROTOCOL, EXTENSIONS, HEADER_LIMIT, EOF, TIMEOUT, IO }
enum class RelayUpgradeReason { CAPACITY, CONFIGURATION, UPSTREAM_CONNECT, UPSTREAM_TIMEOUT }
data class HttpUpgradeDiagnostic(val status: Int? = null, val failure: UpgradeFailure? = null,
    val relayReason: RelayUpgradeReason? = null, val retryAfterMillis: Int? = null)
data class WebSocketClose(val code: Int?, val category: WebSocketCloseCategory? = null)

internal class WebSocketUpgradeException(val diagnostic: HttpUpgradeDiagnostic) :
    java.net.ProtocolException("WebSocket Upgrade failed")

internal fun upgradeDiagnostic(error: Throwable): HttpUpgradeDiagnostic? {
    var cause: Throwable? = error
    val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    repeat(8) {
        val next = cause ?: return null
        if (!visited.add(next)) return null
        if (next is WebSocketUpgradeException) return next.diagnostic
        cause = next.cause
    }
    return null
}

enum class SessionEndReason {
    CLIENT_EOF, CLIENT_IO, CLIENT_PROTOCOL, REMOTE_EOF, REMOTE_IO, REMOTE_CLOSE,
    HANDSHAKE_REJECTED, HANDSHAKE_TIMEOUT, SETUP_FAILED, RETRY_BACKOFF,
    CONNECT_TIMEOUT, IDLE_TIMEOUT, UPSTREAM_WRITE_TIMEOUT, DOWNSTREAM_WRITE_TIMEOUT,
    NETWORK_CHANGED, SERVICE_STOPPED, CANCELLED
}
data class SessionClosed(val id: Long, val reason: SessionEndReason, val transport: TransportKind?,
    val established: Boolean, val durationMillis: Long, val sent: Long, val received: Long,
    val peerClose: WebSocketClose? = null, val upgrade: HttpUpgradeDiagnostic? = null)
