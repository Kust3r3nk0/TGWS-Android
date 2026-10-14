package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.EOFException

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

    @Test fun `all supported transports and quick ack framing match Flowseal vectors`() {
        val count = FlowsealVectors.value("framing.count").toInt()
        assertEquals(6, count)
        for (index in 0 until count) {
            val prefix = "framing.$index."
            val plain = FlowsealVectors.bytes(prefix + "plain")
            val cipher = FlowsealVectors.bytes(prefix + "cipher")
            for (chunks in listOf(FlowsealVectors.numbers(prefix + "chunks"), List(plain.size) { 1 }, listOf(plain.size))) {
                val splitter = PacketSplitter(FlowsealVectors.bytes(prefix + "tag"))
                val output = mutableListOf<ByteArray>()
                var offset = 0
                for (size in chunks) {
                    output += splitter.split(cipher.copyOfRange(offset, offset + size), plain.copyOfRange(offset, offset + size))
                    offset += size
                }
                assertEquals(plain.size, offset)
                assertEquals(FlowsealVectors.numbers(prefix + "lengths"), output.map { it.size })
                assertArrayEquals(cipher, output.fold(ByteArray(0)) { acc, bytes -> acc + bytes })
                assertEquals(emptyList<ByteArray>(), splitter.flush())
                assertEquals(emptyList<ByteArray>(), splitter.flush())
            }
        }
    }

    @Test fun `padded intermediate preserves non aligned padding length`() {
        val packet = byteArrayOf(7, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7)
        val output = PacketSplitter(ByteArray(4) { 0xdd.toByte() }).split(packet, packet)
        assertEquals(1, output.size)
        assertArrayEquals(packet, output.single())
    }

    @Test fun `zero oversized and overflowing lengths are rejected from their headers`() {
        for (tag in listOf(0xef, 0xee, 0xdd)) {
            val invalid = if (tag == 0xef) listOf(byteArrayOf(0), byteArrayOf(0x80.toByte()),
                byteArrayOf(0x7f, 1, 0, 0x10), byteArrayOf(0x7f, -1, -1, -1))
            else listOf(byteArrayOf(0, 0, 0, 0), byteArrayOf(0, 0, 0, -128),
                byteArrayOf(1, 0, 0x40, 0), byteArrayOf(-1, -1, -1, 0x7f))
            for (bytes in invalid) {
                val splitter = PacketSplitter(ByteArray(4) { tag.toByte() })
                assertThrows(IllegalArgumentException::class.java) { splitter.split(bytes, bytes) }
                assertThrows(IllegalStateException::class.java) { splitter.split(byteArrayOf(1), byteArrayOf(1)) }
            }
        }
    }

    @Test fun `incomplete headers and bodies fail at EOF without forwarding`() {
        for (tag in listOf(0xef, 0xee, 0xdd)) {
            val packet = if (tag == 0xef) byteArrayOf(0x7f, -128, 0, 0) + ByteArray(512)
                else byteArrayOf(0, 2, 0, 0) + ByteArray(512)
            for (length in listOf(1, 2, 3, 4, packet.size - 1)) {
                val splitter = PacketSplitter(ByteArray(4) { tag.toByte() })
                val incomplete = packet.copyOf(length)
                assertEquals(emptyList<ByteArray>(), splitter.split(incomplete, incomplete))
                assertThrows(EOFException::class.java) { splitter.flush() }
                assertThrows(IllegalStateException::class.java) { splitter.flush() }
            }
        }
    }

    @Test fun `maximum packet is bounded and accepts many small TCP chunks`() {
        val payload = PacketSplitter.MAX_PAYLOAD_BYTES
        for (tag in listOf(0xef, 0xee, 0xdd)) {
            val header = if (tag == 0xef) byteArrayOf(0x7f, 0, 0, 0x10)
                else byteArrayOf(0, 0, 0x40, 0)
            val packet = header + ByteArray(payload) { (it % 251).toByte() }
            val splitter = PacketSplitter(ByteArray(4) { tag.toByte() })
            val parts = mutableListOf<ByteArray>()
            var offset = 0
            while (offset < packet.size) {
                val end = minOf(offset + 1023, packet.size)
                val chunk = packet.copyOfRange(offset, end)
                parts += splitter.split(chunk, chunk)
                offset = end
            }
            assertEquals(1, parts.size)
            assertArrayEquals(packet, parts.single())
            assertEquals(emptyList<ByteArray>(), splitter.flush())
        }
    }

    @Test fun `invalid tags and mismatched chunk sizes are rejected`() {
        for (tag in listOf(ByteArray(0), byteArrayOf(0xef.toByte()), ByteArray(4), byteArrayOf(-17, -17, -17, -18))) {
            assertThrows(IllegalArgumentException::class.java) { PacketSplitter(tag) }
        }
        val splitter = PacketSplitter(ByteArray(4) { 0xee.toByte() })
        assertEquals(emptyList<ByteArray>(), splitter.split(ByteArray(0), ByteArray(0)))
        assertThrows(IllegalArgumentException::class.java) { splitter.split(ByteArray(2), ByteArray(1)) }
    }
}
