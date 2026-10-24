package io.github.kust3r3nk0.tgwsandroid.core

import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/** RFC 1928, numeric destinations resolved by the active Android network. No credentials. */
internal object LocalSocks {
    fun negotiate(socket:Socket,address:InetAddress,port:Int,deadline:Long) {
        val input=socket.getInputStream();val output=socket.getOutputStream()
        fun read():Int {
            val left=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime())
            if(left<=0)throw java.net.SocketTimeoutException("SOCKS deadline")
            socket.soTimeout=left.coerceAtMost(Int.MAX_VALUE.toLong()).coerceAtLeast(1).toInt()
            return input.read().takeIf{it>=0}?:throw EOFException("SOCKS EOF")
        }
        output.write(byteArrayOf(5,1,0));output.flush()
        if(read()!=5 || read()!=0)throw IOException("SOCKS authentication rejected")
        val bytes=address.address
        output.write(byteArrayOf(5,1,0,if(bytes.size==4)1 else 4)+bytes+byteArrayOf((port ushr 8).toByte(),port.toByte()));output.flush()
        if(read()!=5)throw IOException("SOCKS version")
        val reply=read()
        if(read()!=0 || reply!=0)throw IOException("SOCKS CONNECT rejected")
        val length=when(read()){1->4;4->16;3->read().also{if(it==0)throw IOException("SOCKS address")};else->throw IOException("SOCKS address type")}
        repeat(length+2){read()}
        socket.soTimeout=0
    }
}
