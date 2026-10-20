package io.github.kust3r3nk0.tgwsandroid

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class UpdateViewModel(application:Application):AndroidViewModel(application){
    private val repository=UpdateGraph.repository(application)
    private val downloads=UpdateDownloads(application)
    val state=repository.state
    private var check:Job?=null
    private var download:Job?=null
    private var readyFile:File?=null
    private var pendingInstall=false
    init{viewModelScope.launch{try{repository.initialize()}
        catch(error:CancellationException){throw error}catch(_:Exception){repository.error(UpdateIssue.STORAGE)}}}
    fun check(){if(check?.isActive==true || download?.isActive==true)return
        check=viewModelScope.launch{try{repository.check()}catch(error:CancellationException){throw error}catch(error:UpdateException){repository.error(error.issue)}}
    }
    fun options(value:UpdateOptions){
        pendingInstall=false
        check?.cancel()
        download?.cancel();readyFile=null
        viewModelScope.launch{try{repository.options(value)}catch(error:CancellationException){throw error}
            catch(_:Exception){repository.error(UpdateIssue.STORAGE)}}
    }
    fun download(){if(download?.isActive==true || check?.isActive==true || state.value.checking)return
        val release=state.value.release?:return
        pendingInstall=true
        download=viewModelScope.launch{
            repository.downloading(release.apk.size)
            try{
                repository.verifying();readyFile=downloads.ready(release)
                if(readyFile==null){repository.downloading(release.apk.size)
                    readyFile=downloads.download(release,state.value.options.wifiOnly,repository::progress,repository::verifying)}
                repository.ready(true)
            }
            catch(error:CancellationException){repository.cancelled();throw error}
            catch(error:Exception){pendingInstall=false;repository.error(if(error is UpdateException)error.issue else UpdateIssue.STORAGE)}
        }
    }
    fun cancel(){pendingInstall=false;download?.cancel()}
    fun defer(){cancel()}
    fun takeInstallRequest()=pendingInstall.also{pendingInstall=false}
    /** Revalidate persisted bytes before every installer/permission handoff. */
    fun install(allowed:Boolean,launch:(File?)->Unit){
        if(download?.isActive==true)return
        download=viewModelScope.launch{
            try{val release=state.value.release?:return@launch
                readyFile=downloads.ready(release)
                val file=readyFile?:throw UpdateException(UpdateIssue.INVALID_APK)
                repository.permission(!allowed);launch(if(allowed)file else null)
            }catch(error:CancellationException){throw error}
            catch(error:Exception){repository.error(if(error is UpdateException)error.issue else UpdateIssue.INSTALLER)}
        }
    }
    fun installerFailed(){repository.error(UpdateIssue.INSTALLER)}
    fun permissionReturned(){repository.permission(false)}
}
