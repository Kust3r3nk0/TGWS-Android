package io.github.kust3r3nk0.tgwsandroid

import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.util.concurrent.ExecutorService
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal enum class LocalCheckOutcome { AVAILABLE, REFUSED, TIMEOUT, INVALID_PORT, IO_ERROR }
internal data class LocalCheckResult(val outcome:LocalCheckOutcome,val millis:Long?=null)

/** A single empty loopback TCP check: never sends a secret or contacts an external host. */
internal class LocalListenerCheck(private val executor:ExecutorService,private val socketFactory:()->Socket={Socket()}) {
    suspend fun run(port:Int):LocalCheckResult {
        if(port !in 1..65535)return LocalCheckResult(LocalCheckOutcome.INVALID_PORT)
        return suspendCancellableCoroutine { continuation->
            val socket=try{socketFactory()}catch(_:Exception){
                continuation.resume(LocalCheckResult(LocalCheckOutcome.IO_ERROR));return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation{runCatching{socket.close()}}
            try {
                executor.execute {
                    val started=System.nanoTime()
                    val outcome=try {
                        socket.use { if(!continuation.isActive)return@execute
                            it.connect(InetSocketAddress("127.0.0.1",port),1500) }
                        LocalCheckOutcome.AVAILABLE
                    }catch(_:SocketTimeoutException){LocalCheckOutcome.TIMEOUT}
                    catch(_:ConnectException){LocalCheckOutcome.REFUSED}
                    catch(_:Exception){LocalCheckOutcome.IO_ERROR}
                    val result=LocalCheckResult(outcome,java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started).coerceAtLeast(0))
                    if(continuation.isActive)continuation.resume(result)
                }
            }catch(_:java.util.concurrent.RejectedExecutionException){
                runCatching{socket.close()}
                if(continuation.isActive)continuation.resume(LocalCheckResult(LocalCheckOutcome.IO_ERROR))
            }
        }
    }
}
