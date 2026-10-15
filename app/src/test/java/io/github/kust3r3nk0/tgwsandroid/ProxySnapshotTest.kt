package io.github.kust3r3nk0.tgwsandroid

import org.junit.Assert.*
import org.junit.Test
import io.github.kust3r3nk0.tgwsandroid.core.HandshakeRejection

class ProxySnapshotTest {
    @Test fun rejectionIsCountedWithoutClaimingThatListenerOrOtherSessionsFailed() {
        for (sessions in 0..1) {
            val original = ProxySnapshot(phase = if (sessions == 0) ProxyPhase.LISTENING else ProxyPhase.ACTIVE,
                sessions = sessions, pending = 1)
            val rejected = original.withRejectedHandshake(HandshakeRejection.SECRET_OR_TRANSPORT)
            assertEquals(original.phase, rejected.phase)
            assertEquals(sessions, rejected.sessions)
            assertTrue(rejected.localAvailable)
            assertNull(rejected.issue)
            assertEquals(1, rejected.rejectedHandshakes)
            assertEquals(HandshakeRejection.SECRET_OR_TRANSPORT, rejected.lastHandshakeRejection)
        }
    }

    @Test fun rejectionCannotHideNetworkOrLocalErrorsAndCounterIsBounded() {
        for (issue in listOf(ProxyIssue.PORT, ProxyIssue.TCP)) {
            val original = ProxySnapshot(phase = ProxyPhase.LISTENING, issue = issue, rejectedHandshakes = Int.MAX_VALUE)
            val rejected = original.withRejectedHandshake(HandshakeRejection.RESERVED_PREFIX)
            assertEquals(issue, rejected.issue)
            assertEquals(Int.MAX_VALUE, rejected.rejectedHandshakes)
        }
    }
    @Test fun successfulConnectionClearsObsoleteFallbackError() {
        val failed = ProxySnapshot(phase = ProxyPhase.LISTENING, pending = 1, issue = ProxyIssue.WSS_HANDSHAKE)
        val recovered = failed.withSessions(1, 0)
        assertNull(recovered.issue)
        assertEquals(ProxyPhase.ACTIVE, recovered.phase)
    }

    @Test fun anotherPendingSessionCannotClearLastFailure() {
        val failed = ProxySnapshot(phase = ProxyPhase.LISTENING, issue = ProxyIssue.TCP)
        assertEquals(ProxyIssue.TCP, failed.withSessions(0, 1).issue)
    }

    @Test fun lateConnectionCannotClearFatalLocalError() {
        val failed = ProxySnapshot(phase = ProxyPhase.ERROR, issue = ProxyIssue.PORT)
        assertEquals(ProxyIssue.PORT, failed.withSessions(1, 0).issue)
        assertEquals(ProxyPhase.ERROR, failed.withSessions(1, 0).phase)
    }

    @Test fun failureInOneSessionIsWarningWhenAnotherTransportIsEstablished() {
        val state = ProxySnapshot(phase = ProxyPhase.ACTIVE, sessions = 1, issue = ProxyIssue.TCP)
        assertTrue(state.issueIsWarning)
        assertFalse(state.copy(sessions = 0).issueIsWarning)
        assertFalse(state.copy(issue = ProxyIssue.PORT).issueIsWarning)
        assertFalse(state.copy(phase = ProxyPhase.ERROR).issueIsWarning)
    }

    @Test fun transportCountsTrackCurrentSessionsAndClearOnClose() {
        val state = ProxySnapshot(phase = ProxyPhase.LISTENING).withSessions(2, 1, wss = 1, tcp = 1)
        assertEquals(1, state.wssSessions)
        assertEquals(1, state.tcpSessions)
        val stopped = state.withSessions(0, 0)
        assertEquals(0, stopped.wssSessions + stopped.tcpSessions)
        assertThrows(IllegalArgumentException::class.java) { state.withSessions(1, 0, wss = 1, tcp = 1) }
    }
    @Test fun listeningStateDoesNotImplyEstablishedRemoteTransport() {
        val state = ProxySnapshot(phase = ProxyPhase.LISTENING)
        assertTrue(state.localAvailable)
        assertTrue(state.running)
        assertEquals(0, state.sessions)
    }

    @Test fun pendingClientsDoNotCountAsEstablishedSessions() {
        val state = ProxySnapshot(phase = ProxyPhase.LISTENING).withSessions(0, 2)
        assertEquals(ProxyPhase.LISTENING, state.phase)
        assertEquals(2, state.pending)
        assertEquals(0, state.sessions)
    }

    @Test fun sessionTransitionsReflectOnlyLocalListenerAndEstablishedTransports() {
        val active = ProxySnapshot(phase = ProxyPhase.LISTENING).withSessions(2, 1)
        assertEquals(ProxyPhase.ACTIVE, active.phase)
        val listeningAgain = active.withSessions(0, 0)
        assertEquals(ProxyPhase.LISTENING, listeningAgain.phase)
        assertTrue(listeningAgain.localAvailable)
    }

    @Test fun lateSessionUpdatesCannotResurrectStoppedOrFailedListener() {
        listOf(ProxyPhase.STOPPED, ProxyPhase.ERROR, ProxyPhase.STOPPING).forEach { phase ->
            val state = ProxySnapshot(phase = phase).withSessions(0, 0)
            assertEquals(phase, state.phase)
            assertFalse(state.localAvailable)
        }
    }

    @Test fun rejectedNegativeSessionCountsCannotEnterUiState() {
        assertThrows(IllegalArgumentException::class.java) { ProxySnapshot().withSessions(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { ProxySnapshot().withSessions(0, -1) }
    }
}
