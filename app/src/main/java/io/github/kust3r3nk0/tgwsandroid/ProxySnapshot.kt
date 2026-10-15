package io.github.kust3r3nk0.tgwsandroid

import io.github.kust3r3nk0.tgwsandroid.core.HandshakeRejection

enum class ProxyPhase { STOPPED, STARTING, LISTENING, ACTIVE, ERROR, STOPPING }
enum class ProxyIssue {
    CONFIGURATION, PORT, SECRET_UNAVAILABLE, STORAGE, HANDSHAKE, WSS_HANDSHAKE,
    TLS, TCP, RELAY, SESSION, CAPACITY, TIMEOUT, NETWORK_CHANGED, SERVICE_START
}

data class ProxySnapshot(
    val phase: ProxyPhase = ProxyPhase.STOPPED,
    val sessions: Int = 0,
    val pending: Int = 0,
    val issue: ProxyIssue? = null,
    val wssSessions: Int = 0,
    val tcpSessions: Int = 0,
    val relaySessions: Int = 0,
    val rejectedHandshakes: Int = 0,
    val lastHandshakeRejection: HandshakeRejection? = null,
) {
    val running: Boolean get() = phase in setOf(
        ProxyPhase.STARTING, ProxyPhase.LISTENING, ProxyPhase.ACTIVE, ProxyPhase.STOPPING
    )
    val localAvailable: Boolean get() = phase == ProxyPhase.LISTENING || phase == ProxyPhase.ACTIVE

    val issueIsWarning: Boolean get() = localAvailable && sessions > 0 && issue != null && issue !in localIssues

    /** Rejecting a client is not a failure of the listener or of other sessions. */
    fun withRejectedHandshake(reason: HandshakeRejection): ProxySnapshot = copy(
        rejectedHandshakes = if (rejectedHandshakes == Int.MAX_VALUE) Int.MAX_VALUE else rejectedHandshakes + 1,
        lastHandshakeRejection = reason,
    )

    fun withSessions(established: Int, connecting: Int, wss: Int = 0, tcp: Int = 0, relay: Int = 0): ProxySnapshot {
        require(established >= 0 && connecting >= 0 && wss >= 0 && tcp >= 0 && relay >= 0 && wss + tcp + relay <= established)
        val nextPhase = if (localAvailable) {
            if (established == 0) ProxyPhase.LISTENING else ProxyPhase.ACTIVE
        } else phase
        val recovered = localAvailable && established > sessions && issue !in localIssues
        return copy(phase = nextPhase, sessions = established, pending = connecting,
            wssSessions = wss, tcpSessions = tcp, relaySessions = relay, issue = if (recovered) null else issue)
    }

    private companion object {
        val localIssues = setOf(ProxyIssue.CONFIGURATION, ProxyIssue.PORT, ProxyIssue.SECRET_UNAVAILABLE,
            ProxyIssue.STORAGE, ProxyIssue.SERVICE_START)
    }
}
