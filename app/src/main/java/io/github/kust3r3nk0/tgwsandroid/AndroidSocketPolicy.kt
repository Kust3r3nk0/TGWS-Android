package io.github.kust3r3nk0.tgwsandroid

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.net.Socket
import java.net.SocketException

/** Bound unacknowledged TCP data even when the default Network does not change. */
internal object AndroidSocketPolicy {
    const val UNACKNOWLEDGED_TIMEOUT_MILLIS = 30_000

    fun configure(socket: Socket, unacknowledgedTimeoutMillis: Int = UNACKNOWLEDGED_TIMEOUT_MILLIS) {
        require(unacknowledgedTimeoutMillis in 1..UNACKNOWLEDGED_TIMEOUT_MILLIS)
        try {
            socket.keepAlive = true
            // Android 16+ fromSocket duplicates the descriptor. Closing this local
            // duplicate leaves ownership of the live connection with the Socket.
            val descriptor = ParcelFileDescriptor.fromSocket(socket)
                ?: throw SocketException("TCP descriptor unavailable")
            descriptor.use {
                Os.setsockoptInt(it.fileDescriptor, OsConstants.IPPROTO_TCP,
                    OsConstants.TCP_USER_TIMEOUT, unacknowledgedTimeoutMillis)
            }
        } catch (error: Exception) {
            socket.close()
            throw SocketException("Unable to configure TCP liveness").apply { initCause(error) }
        }
    }
}
