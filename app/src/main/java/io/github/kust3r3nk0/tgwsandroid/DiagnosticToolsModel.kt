package io.github.kust3r3nk0.tgwsandroid

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

internal class DiagnosticToolsModel:ViewModel() {
    var provider by mutableStateOf(WebViewStatus()); private set
    var checking by mutableStateOf(false); private set
    var localResult by mutableStateOf<LocalCheckResult?>(null); private set
    private val executor=Executors.newSingleThreadExecutor{Thread(it,"TGWS-local-check")}
    private val checker=LocalListenerCheck(executor)
    private var job:Job?=null
    private var generation=0
    private var targetPort:String?=null
    fun selectPort(port:String){
        if(targetPort!=port){cancelCheck();localResult=null;targetPort=port}
    }
    fun refreshProvider(){
        val next=WebViewProviderReader.read()
        if(next!=provider){
            provider=next
            ProxyState.record(DiagnosticEvent(System.currentTimeMillis(),EventCategory.WEBVIEW,
                next.state==ProviderState.AVAILABLE,kind=EventKind.PROVIDER,provider=next))
        }
    }
    fun checkLocal(port:String){
        selectPort(port)
        if(checking)return
        checking=true;localResult=null
        val request=++generation
        job=viewModelScope.launch {
            try {
                val result=checker.run(port.toIntOrNull()?:0)
                localResult=result
                ProxyState.record(DiagnosticEvent(System.currentTimeMillis(),EventCategory.CHECK,
                    result.outcome==LocalCheckOutcome.AVAILABLE,kind=EventKind.LOCAL_CHECK,localCheck=result))
            }finally{if(request==generation)checking=false}
        }
    }
    fun cancelCheck(){generation++;job?.cancel();job=null;checking=false}
    override fun onCleared(){cancelCheck();executor.shutdownNow()}
}
