package io.github.kust3r3nk0.tgwsandroid.core

import java.io.EOFException

/** Align ciphertext with validated MTProto packets without buffering their plaintext bodies. */
internal class PacketSplitter(tag: ByteArray) {
    companion object {
        const val MAX_PAYLOAD_BYTES = 4 * 1024 * 1024
    }

    private val abridged: Boolean
    private val header = ByteArray(4)
    private var headerBytes = 0
    private var headerSize: Int
    private var packet = ByteArray(256)
    private var packetBytes = 0
    private var expectedBytes = 0
    private var failed = false

    init {
        require(tag.size == 4 && listOf(0xef, 0xee, 0xdd).any { value ->
            tag.all { (it.toInt() and 255) == value }
        }) { "Unsupported MTProto transport" }
        abridged = (tag[0].toInt() and 255) == 0xef
        headerSize = if (abridged) 1 else 4
    }

    fun split(encrypted: ByteArray, plaintext: ByteArray): List<ByteArray> {
        check(!failed) { "MTProto packet stream already failed" }
        if (encrypted.size != plaintext.size) invalid("Ciphertext and plaintext lengths differ")
        val output = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < encrypted.size) {
            if (expectedBytes == 0) {
                val count = minOf(headerSize - headerBytes, encrypted.size - offset)
                plaintext.copyInto(header, headerBytes, offset, offset + count)
                append(encrypted, offset, count)
                offset += count
                headerBytes += count
                if (abridged && headerBytes == 1 &&
                    (header[0].toInt() and 0x7f) == 0x7f) headerSize = 4
                if (headerBytes < headerSize) continue

                val payload = if (abridged) {
                    if (headerSize == 1) (header[0].toInt() and 0x7f).toLong() * 4
                    else ((header[1].toInt() and 255).toLong() or
                        ((header[2].toInt() and 255).toLong() shl 8) or
                        ((header[3].toInt() and 255).toLong() shl 16)) * 4
                } else {
                    (header[0].toInt() and 255).toLong() or
                        ((header[1].toInt() and 255).toLong() shl 8) or
                        ((header[2].toInt() and 255).toLong() shl 16) or
                        ((header[3].toInt() and 0x7f).toLong() shl 24)
                }
                if (payload !in 1..MAX_PAYLOAD_BYTES.toLong()) invalid("Invalid MTProto packet length")
                expectedBytes = headerSize + payload.toInt()
            }

            val count = minOf(expectedBytes - packetBytes, encrypted.size - offset)
            append(encrypted, offset, count)
            offset += count
            if (packetBytes == expectedBytes) {
                output += packet.copyOf(packetBytes)
                headerBytes = 0
                headerSize = if (abridged) 1 else 4
                packetBytes = 0
                expectedBytes = 0
                // Do not retain large packet allocations for the lifetime of an idle session.
                if (packet.size > 65536) packet = ByteArray(256)
            }
        }
        return output
    }

    /** EOF at a partial header or body is a truncated packet, never a raw relay frame. */
    fun flush(): List<ByteArray> {
        check(!failed) { "MTProto packet stream already failed" }
        if (packetBytes != 0) {
            failed = true
            packet = ByteArray(0)
            throw EOFException("Truncated MTProto packet")
        }
        return emptyList()
    }

    private fun append(source: ByteArray, offset: Int, count: Int) {
        val required = packetBytes + count
        if (required > packet.size) {
            packet = packet.copyOf(minOf(MAX_PAYLOAD_BYTES + 4, maxOf(required, packet.size * 2)))
        }
        source.copyInto(packet, packetBytes, offset, offset + count)
        packetBytes = required
    }

    private fun invalid(message: String): Nothing {
        failed = true
        packet = ByteArray(0)
        throw IllegalArgumentException(message)
    }
}
