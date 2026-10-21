package io.github.kust3r3nk0.tgwsandroid

import io.github.kust3r3nk0.tgwsandroid.core.HandshakeRejection
import org.junit.Assert.*
import org.junit.Test

class DiagnosticAdviceTest {
    private val network=NetworkView(kind=AccessKind.WIFI,validated=true)
    @Test fun incompleteHeaderNeverSuggestsWrongSecretFromIssueOrHistoricalCounter() {
        for(phase in listOf(ProxyPhase.LISTENING,ProxyPhase.ACTIVE))for(issue in listOf(null,ProxyIssue.HANDSHAKE)){
            val snapshot=ProxySnapshot(phase=phase,issue=issue,network=network,rejectedHandshakes=42,
                lastHandshakeRejection=HandshakeRejection.INVALID_LENGTH)
            assertEquals(listOf(DiagnosticAdvice.INCOMPLETE_HANDSHAKE),diagnosticAdvice(snapshot))
        }
    }
    @Test fun unsupportedOrUnknownHeaderDoesNotImplyWrongSecret() {
        for(reason in listOf(null,HandshakeRejection.RESERVED_PREFIX,HandshakeRejection.UNSUPPORTED_DC)){
            val snapshot=ProxySnapshot(phase=ProxyPhase.LISTENING,issue=ProxyIssue.HANDSHAKE,network=network,
                rejectedHandshakes=1,lastHandshakeRejection=reason)
            assertEquals(listOf(DiagnosticAdvice.HANDSHAKE_FORMAT),diagnosticAdvice(snapshot))
        }
    }
    @Test fun authenticatedHeaderFailuresStillRetainConfigurationAdvice() {
        for(reason in listOf(HandshakeRejection.SECRET_OR_TRANSPORT,HandshakeRejection.FAKE_TLS)){
            val snapshot=ProxySnapshot(phase=ProxyPhase.LISTENING,network=network,rejectedHandshakes=1,lastHandshakeRejection=reason)
            assertEquals(listOf(DiagnosticAdvice.SECRET),diagnosticAdvice(snapshot))
        }
    }
    @Test fun remoteTimeoutAndIncompleteClientHeaderRemainIndependent() {
        val snapshot=ProxySnapshot(phase=ProxyPhase.LISTENING,issue=ProxyIssue.TIMEOUT,network=network,rejectedHandshakes=1,
            lastHandshakeRejection=HandshakeRejection.INVALID_LENGTH)
        assertEquals(listOf(DiagnosticAdvice.RETRY,DiagnosticAdvice.INCOMPLETE_HANDSHAKE),diagnosticAdvice(snapshot))
    }
}
