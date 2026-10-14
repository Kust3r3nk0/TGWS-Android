package io.github.kust3r3nk0.tgwsandroid.core

import java.io.ByteArrayOutputStream

/** Align encrypted chunks with abridged/intermediate MTProto packet boundaries. */
internal class PacketSplitter(private val tag: ByteArray) {
    private var cipher = ByteArray(0)
    private var plain = ByteArray(0)
    private var disabled = false
    fun split(encrypted: ByteArray, plaintext: ByteArray): List<ByteArray> {
        if (disabled) return listOf(encrypted)
        cipher += encrypted
        plain += plaintext
        val output = mutableListOf<ByteArray>()
        var cursor = 0
        while (cursor < plain.size) {
            val left = plain.size - cursor
            val length: Int = if ((tag[0].toInt() and 255) == 0xef) {
                val lead = plain[cursor].toInt() and 255
                if (lead == 0x7f || lead == 0xff) {
                    if (left < 4) break
                    val words = (plain[cursor + 1].toInt() and 255) or
                        ((plain[cursor + 2].toInt() and 255) shl 8) or
                        ((plain[cursor + 3].toInt() and 255) shl 16)
                    4 + words * 4
                } else { 1 + (lead and 0x7f) * 4 }
            } else {
                if (left < 4) break
                val payload = ((plain[cursor].toInt() and 255) or
                    ((plain[cursor + 1].toInt() and 255) shl 8) or
                    ((plain[cursor + 2].toInt() and 255) shl 16) or
                    ((plain[cursor + 3].toInt() and 0x7f) shl 24))
                4 + payload
            }
            if (length <= 4 || length > 4 * 1024 * 1024 + 260) {
                disabled = true
                output += cipher.copyOfRange(cursor, cipher.size)
                cursor = cipher.size
                break
            }
            if (length > left) break
            output += cipher.copyOfRange(cursor, cursor + length)
            cursor += length
        }
        cipher = cipher.copyOfRange(cursor, cipher.size)
        plain = plain.copyOfRange(cursor, plain.size)
        if (cipher.size > 4 * 1024 * 1024 + 260) {
            disabled = true
            return output + flush()
        }
        return output
    }
    fun flush(): List<ByteArray> {
        if (cipher.isEmpty()) return emptyList()
        val last = cipher
        cipher = ByteArray(0); plain = ByteArray(0)
        return listOf(last)
    }
}
