package io.github.kust3r3nk0.tgwsandroid.core

import java.io.OutputStream

internal const val TRANSFER_CHUNK_BYTES = 65_536

/** A completed bounded write renews inactivity deadlines; a blocked write never does. */
internal fun writeWithProgress(output: OutputStream, data: ByteArray, progress: () -> Unit) {
    var offset = 0
    while (offset < data.size) {
        val count = minOf(TRANSFER_CHUNK_BYTES, data.size - offset)
        output.write(data, offset, count)
        output.flush()
        offset += count
        progress()
    }
}
