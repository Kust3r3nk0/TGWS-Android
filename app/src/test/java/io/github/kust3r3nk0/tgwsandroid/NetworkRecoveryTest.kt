package io.github.kust3r3nk0.tgwsandroid

import org.junit.Assert.*
import org.junit.Test

class NetworkRecoveryTest {
    @Test fun suspensionAndResumeOfSameDefaultTriggerExactlyOneResetEach(){
        val state=DefaultNetworkState<String>()
        val ready=NetworkAvailability(true,false,false)
        assertTrue(ready.usable)
        assertTrue(state.update("network","network",ready.signature))
        val suspended=ready.copy(suspended=true)
        assertFalse(suspended.usable)
        assertTrue(state.update("network","network",suspended.signature))
        assertFalse(state.update("network","network",suspended.signature))
        assertTrue(state.update("network","network",ready.signature))
        assertFalse(state.update("network","network",ready.signature))
        assertFalse(state.update("old-network","network",suspended.signature))
    }
    @Test fun publicInternetValidationIsNotRequiredButCaptiveAndUnavailableNetworksAreBlocked(){
        assertTrue(NetworkAvailability(true,false,false).usable)
        assertFalse(NetworkAvailability(true,true,false).usable)
        assertFalse(NetworkAvailability(false,false,false).usable)
        assertFalse(NetworkAvailability(true,false,true).usable)
    }
}
