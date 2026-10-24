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
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal class AutoConfigModel(app:Application):AndroidViewModel(app){
    var running by mutableStateOf(false);private set
    var completed by mutableIntStateOf(0);private set
    var total by mutableIntStateOf(0);private set
    var message by mutableStateOf<Int?>(null);private set
    var outcomes by mutableStateOf<List<AutoConfigObservation>>(emptyList());private set
    private val executor=ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,ArrayBlockingQueue(1),{r->Thread(r,"tgws-auto-config").apply{isDaemon=true}})
    private var operation:Closeable?=null
    private var job:Job?=null
    private var epoch=0
    private var last:Long?=null
    fun start(form:ProxyFormModel,network:ConnectionNetwork){
        if(running || form.busy.value || ProxyState.snapshot.running || !form.valid)return
        val now=SystemClock.elapsedRealtime()
        if(last?.let{now-it<60_000}==true){message=R.string.auto_config_limited;return}
        last=now
        val settings=form.configuration?:return
        val relay=form.relayUrl.value
        val targets=NetworkAutoConfig.targets(settings,relay,network)
        val request=++epoch
        running=true;form.busy.value=true;message=null;completed=0;total=targets.size;outcomes=emptyList()
        job=viewModelScope.launch{
            var monitor:AndroidNetworkMonitor?=null
            var selected:TransportSettings?=null
            try{
                var initial=true
                monitor=AndroidNetworkMonitor(getApplication(),Handler(Looper.getMainLooper())){changed,_->if(changed && !initial)cancel()}
                monitor.start();initial=false
                val access=monitor
                require(access.access!=null)
                withTimeout(60_000){for(target in targets){
                    ensureActive()
                    val factory=NetworkTransportFactory{access.access}
                    operation=factory
                    val started=SystemClock.elapsedRealtime()
                    val outcome=try{
                        withTimeout(8_000){suspendCancellableCoroutine<AutoConfigObservation>{c->
                            val future=executor.submit{
                                val result=try{target.open(factory).use{MtprotoAvailabilityProbe.check(it)}
                                    AutoConfigObservation(target,SystemClock.elapsedRealtime()-started,null)
                                }catch(e:Exception){AutoConfigObservation(target,null,setupFailure(e,ServerIssue.SESSION),upgradeDiagnostic(e))}
                                if(c.isActive)c.resume(result)
                            }
                            c.invokeOnCancellation{factory.close();future.cancel(true);executor.purge()}
                        }}
                    }catch(_:TimeoutCancellationException){AutoConfigObservation(target,null,ServerIssue.TIMEOUT)}
                    finally{factory.close();operation=null}
                    outcomes=outcomes+outcome;completed++
                }}
                selected=NetworkAutoConfig.apply(settings,network,outcomes)
                message=if(selected==null)R.string.auto_config_failed else null
            }catch(_:TimeoutCancellationException){message=R.string.auto_config_failed}
            catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){message=R.string.auto_config_failed}
            finally{operation?.close();operation=null;monitor?.close()
                if(request==epoch){running=false;form.busy.value=false}
            }
            if(request==epoch && selected!=null && !ProxyState.snapshot.running && form.configuration==settings && form.relayUrl.value==relay){
                form.transportDraft.value=TransportDraft.from(selected)
                form.withSaved(onFailure={form.transportDraft.value=TransportDraft.from(settings);message=R.string.auto_config_failed}){
                    message=R.string.auto_config_success;form.feedbackResource.value=R.string.auto_config_success
                }
            }
        }.also{it.invokeOnCompletion{if(request!=epoch)form.busy.value=false}}
    }
    fun cancel(){if(!running)return;epoch++;job?.cancel();operation?.close();operation=null;executor.purge();running=false;message=R.string.auto_config_cancelled}
    override fun onCleared(){cancel();executor.shutdownNow()}
}
