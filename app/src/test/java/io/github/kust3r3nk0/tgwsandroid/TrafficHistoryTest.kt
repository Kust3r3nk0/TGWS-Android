package io.github.kust3r3nk0.tgwsandroid

import org.junit.Assert.*
import org.junit.Test

class TrafficHistoryTest {
    @Test fun ratesUseActualCounterDeltasAndIgnoreSubsecondSnapshots(){
        val h=TrafficHistory();h.accept(100,200,1,0,1000)
        h.accept(500,1000,1,0,1500);assertEquals(1,h.samples.size)
        h.accept(1100,2200,2,1,2000)
        assertEquals(1000L,h.samples.last().outgoingPerSecond);assertEquals(2000L,h.samples.last().incomingPerSecond)
        assertEquals(2,h.samples.last().sessions);assertEquals(1,h.samples.last().reconnects)
    }
    @Test fun idleDoesNotGenerateSamplesAndLongGapsAreUnknownNotSyntheticSpikes(){
        val h=TrafficHistory();h.accept(0,0,0,0,0)
        repeat(100){h.accept(0,0,0,0,(it+1)*1000L)}
        assertEquals(1,h.samples.size)
        h.accept(1_000_000,1_000_000,1,0,120_000)
        assertNull(h.samples.last().incomingPerSecond);assertNull(h.samples.last().outgoingPerSecond)
    }
    @Test fun historyIsBoundedAndCounterResetStartsANewBaseline(){
        val h=TrafficHistory();repeat(4096){h.accept(it*100L,it*200L,1,0,it*1000L)}
        assertEquals(60,h.samples.size)
        h.accept(0,0,0,0,4_100_000);assertEquals(1,h.samples.size);assertNull(h.samples.single().incomingPerSecond)
        h.reset();assertTrue(h.samples.isEmpty())
    }
}
