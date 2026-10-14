package io.github.kust3r3nk0.tgwsandroid

enum class ProxyPhase { STOPPED, STARTING, LISTENING, ACTIVE, ERROR, STOPPING }
enum class ProxyIssue {
    CONFIGURATION, PORT, SECRET_UNAVAILABLE, STORAGE, HANDSHAKE, WSS_HANDSHAKE,
    TLS, TCP, SESSION, CAPACITY, TIMEOUT, NETWORK_CHANGED, SERVICE_START
}

data class ProxySnapshot(
    val phase: ProxyPhase = ProxyPhase.STOPPED,
    val sessions: Int = 0,
    val pending: Int = 0,
    val issue: ProxyIssue? = null,
) {
    val running: Boolean get() = phase in setOf(
        ProxyPhase.STARTING, ProxyPhase.LISTENING, ProxyPhase.ACTIVE, ProxyPhase.STOPPING
    )
    val localAvailable: Boolean get() = phase == ProxyPhase.LISTENING || phase == ProxyPhase.ACTIVE

    fun withSessions(established: Int, connecting: Int): ProxySnapshot {
        require(established >= 0 && connecting >= 0)
        val nextPhase = if (localAvailable) {
            if (established == 0) ProxyPhase.LISTENING else ProxyPhase.ACTIVE
        } else phase
        return copy(phase = nextPhase, sessions = established, pending = connecting)
    }
}
