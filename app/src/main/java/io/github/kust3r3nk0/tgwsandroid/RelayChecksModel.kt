package io.github.kust3r3nk0.tgwsandroid

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kust3r3nk0.tgwsandroid.core.*
import kotlinx.coroutines.*
import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal data class RelayCheckResult(val issue:ServerIssue?,val millis:Long?,val at:Long,val upgrade:HttpUpgradeDiagnostic?=null)
/** One explicit check, Android-network-bound, cancelled on background/network/edit. No warm-up or MTProto data. */
internal class RelayChecksModel(app:Application):AndroidViewModel(app){
    var checking by mutableStateOf<String?>(null);private set
    var results by mutableStateOf<Map<String,RelayCheckResult>>(emptyMap());private set
    var limited by mutableStateOf(false);private set
    private val executor=ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,ArrayBlockingQueue(1),{r->Thread(r,"tgws-relay-check").apply{isDaemon=true}})
    private var operation:Closeable?=null
    private var job:Job?=null
    private var epoch=0
    private val last=linkedMapOf<String,Long>()
    fun check(id:String,url:String){
        if(checking!=null)return
        val now=SystemClock.elapsedRealtime()
        if(last[id]?.let{now-it<30_000}==true){limited=true;return}
        limited=false;last[id]=now;while(last.size>8)last.remove(last.keys.first())
        val request=++epoch;checking=id;results=results-id
        job=viewModelScope.launch{
            var monitor:AndroidNetworkMonitor?=null
            var factory:NetworkTransportFactory?=null
            try{
                var initial=true
                monitor=AndroidNetworkMonitor(getApplication(),Handler(Looper.getMainLooper())){changed,_->if(changed && !initial)this@RelayChecksModel.cancel()}
                monitor.start();initial=false
                val access=monitor
                factory=NetworkTransportFactory(AppPreferences(getApplication()).localSocksPort){access.access}
                val transport=factory
                operation=Closeable{transport.close();access.close()}
                val started=SystemClock.elapsedRealtime()
                val result=withTimeout(10_000){suspendCancellableCoroutine<RelayCheckResult>{continuation->
                    val future=executor.submit{
                        val outcome=try{
                            transport.openRelay(requireNotNull(RelayEndpoint.parse(url)),2){}.use{remote->
                                RelayCheckResult(null,SystemClock.elapsedRealtime()-started,System.currentTimeMillis(),remote.upgrade?:HttpUpgradeDiagnostic(101))
                            }
                        }catch(e:Exception){RelayCheckResult(setupFailure(e,ServerIssue.RELAY),null,System.currentTimeMillis(),upgradeDiagnostic(e))}
                        if(continuation.isActive)continuation.resume(outcome)
                    }
                    continuation.invokeOnCancellation{transport.close();future.cancel(true);executor.purge()}
                }}
                if(request==epoch)results=(results+(id to result)).entries.toList().takeLast(8).associate{it.toPair()}
            }catch(_:TimeoutCancellationException){if(request==epoch)results=results+(id to RelayCheckResult(ServerIssue.TIMEOUT,null,System.currentTimeMillis()))}
            catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){if(request==epoch)results=results+(id to RelayCheckResult(ServerIssue.NETWORK,null,System.currentTimeMillis()))}
            finally{factory?.close();monitor?.close();if(request==epoch){checking=null;operation=null}}
        }
    }
    fun clear(id:String){if(checking==id)cancel();results=results-id;last.remove(id)}
    fun cancel(){epoch++;job?.cancel();job=null;operation?.close();operation=null;checking=null;executor.purge()}
    override fun onCleared(){cancel();executor.shutdownNow()}
}
