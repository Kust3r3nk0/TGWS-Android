package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PacketSplitterTest {
    @Test fun `splits intermediate frames even across TCP chunks`() {
        val splitter = PacketSplitter(byteArrayOf(-18, -18, -18, -18))
        val packetA = byteArrayOf(4, 0, 0, 0, 1, 2, 3, 4)
        val packetB = byteArrayOf(4, 0, 0, 0, 5, 6, 7, 8)
        val all = packetA + packetB
        assertEquals(0, splitter.split(all.copyOfRange(0, 6), all.copyOfRange(0, 6)).size)
        val frames = splitter.split(all.copyOfRange(6, all.size), all.copyOfRange(6, all.size))
        assertEquals(2, frames.size)
        assertArrayEquals(packetA, frames[0])
        assertArrayEquals(packetB, frames[1])
    }
    @Test fun `splits abridged packet`() {
        val splitter = PacketSplitter(byteArrayOf(-17, -17, -17, -17))
        val packet = byteArrayOf(1, 3, 2, 1, 0)
        val frames = splitter.split(packet, packet)
        assertEquals(1, frames.size)
        assertArrayEquals(packet, frames[0])
    }
}
