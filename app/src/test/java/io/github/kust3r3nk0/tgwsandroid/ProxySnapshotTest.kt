package io.github.kust3r3nk0.tgwsandroid

import org.junit.Assert.*
import org.junit.Test

class ProxySnapshotTest {
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
