package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class CollectionSnapshotTest {
    @Test fun removalBetweenSizeAndIterationDoesNotThrowDuringShutdown() {
        // Models the size==1 -> empty iterator race observed on a ConcurrentHashMap key set.
        val disappearing = object : AbstractCollection<Int>() {
            override val size get() = 1
            override fun iterator(): Iterator<Int> = emptyList<Int>().iterator()
        }
        assertTrue(disappearing.snapshotEntries().isEmpty())
        val singleton = setOf(42).snapshotEntries()
        assertEquals(listOf(42), singleton)
    }
    @Test(timeout = 5000) fun snapshotsRemainBoundedWhileConnectionResourcesAreAddedAndRemoved() {
        val resources = ConcurrentHashMap.newKeySet<Int>()
        val stopped = AtomicBoolean()
        val writer = Thread {
            while (!stopped.get()) for (id in 0 until 16) { resources.add(id); resources.remove(id) }
        }
        writer.start()
        try {
            repeat(10_000) {
                val snapshot = resources.snapshotEntries()
                assertTrue(snapshot.size <= 16)
                assertTrue(snapshot.all { it in 0 until 16 })
            }
        } finally {
            stopped.set(true)
            writer.join(1000)
        }
        assertFalse(writer.isAlive)
    }
}
