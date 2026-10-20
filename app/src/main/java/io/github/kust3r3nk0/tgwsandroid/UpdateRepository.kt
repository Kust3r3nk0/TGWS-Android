package io.github.kust3r3nk0.tgwsandroid

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class UpdateRepository(
    private val storage:UpdatePersistence,private val source:GithubUpdateSource,
    val installedVersion:String,val installedCode:Long,private val sdk:Int,
    private val clock:()->Long=System::currentTimeMillis,
    private val schedule:(UpdateOptions)->Unit={},private val notify:(GithubRelease)->Boolean={false}
) {
    private val checks=Mutex()
    private val writes=Mutex()
    private var initialized=false
    private val mutable=MutableStateFlow(UpdateState())
    val state=mutable.asStateFlow()
    suspend fun initialize()=withContext(Dispatchers.IO){writes.withLock{
        if(initialized)return@withLock
        val options=storage.options();val history=storage.history()
        val cached=storage.cached()?.takeIf{compatible(it,options)}
        mutable.value=UpdateState(options=options,release=cached,lastSuccess=history.lastSuccess,retryAt=history.retryAt,
            result=if(cached==null && history.result==UpdateCheckResult.AVAILABLE)UpdateCheckResult.NEVER else history.result,issue=history.issue)
        initialized=true
    }}
    private fun compatible(release:GithubRelease,options:UpdateOptions)=release.versionCode>installedCode &&
        numericVersion(release.version)?.let{it>requireNotNull(numericVersion(installedVersion))}==true &&
        release.minSdk in 36..sdk && (options.channel==UpdateChannel.PREVIEW || release.channel==UpdateChannel.STABLE)
    suspend fun options(next:UpdateOptions)=withContext(Dispatchers.IO){initialize();writes.withLock{
        val previous=storage.options();storage.save(next)
        if(previous.channel!=next.channel){storage.cache(null);storage.save(storage.history().copy(result=UpdateCheckResult.NEVER,issue=null))}
        mutable.update{it.copy(options=next,release=if(previous.channel!=next.channel)null else it.release,
            result=if(previous.channel!=next.channel)UpdateCheckResult.NEVER else it.result,ready=false,issue=null)}
        schedule(next)
    }}
    suspend fun check():GithubRelease?=withContext(Dispatchers.IO){initialize();checks.withLock runCheck@{
        val start=writes.withLock{
            val options=storage.options();val now=clock();val history=storage.history()
            val allowedAt=maxOf(history.retryAt,if(history.lastAttempt>0)history.lastAttempt+60_000 else 0)
            if(now<allowedAt){mutable.update{it.copy(retryAt=allowedAt)};null}
            else{storage.save(history.copy(lastAttempt=now));mutable.update{it.copy(checking=true,issue=null,options=options,retryAt=0)}
                Triple(options,now,history)}
        }?:return@runCheck mutable.value.release
        val(options,now,history)=start
        try{
            val release=source.check(installedVersion,installedCode,sdk,options.channel)
            writes.withLock{
                val current=storage.options()
                if(current.channel!=options.channel)return@withLock mutable.value.release
                val result=if(release==null)UpdateCheckResult.CURRENT else UpdateCheckResult.AVAILABLE
                val success=clock()
                storage.cache(release);storage.save(UpdateHistory(success,now,0,result))
                mutable.update{it.copy(release=release,result=result,lastSuccess=success,retryAt=now+60_000,ready=false)}
                // Mark delivered only when Android accepts a permitted notification.
                if(release!=null && current.notify && !storage.notified(release.id) && notify(release))storage.markNotified(release.id)
                release
            }
        }catch(error:CancellationException){throw error}
        catch(error:Exception){
            val failure=if(error is UpdateException)error else UpdateException(UpdateIssue.NETWORK)
            val retry=maxOf(failure.retryAt,clock()+if(failure.issue in listOf(UpdateIssue.FORBIDDEN,UpdateIssue.RATE_LIMIT))15*60_000 else 60_000)
            writes.withLock{if(storage.options().channel==options.channel){
                storage.save(history.copy(lastAttempt=now,retryAt=retry,result=UpdateCheckResult.FAILED,issue=failure.issue))
                mutable.update{it.copy(result=UpdateCheckResult.FAILED,issue=failure.issue,retryAt=retry)}
            }}
            throw failure
        }finally{mutable.update{it.copy(checking=false)}}
    }}
    fun downloading(total:Long){mutable.update{it.copy(downloading=true,downloaded=0,total=total,issue=null,ready=false,verifying=false,installPermissionNeeded=false)}}
    fun progress(bytes:Long){mutable.update{it.copy(downloaded=bytes)}}
    fun verifying(){mutable.update{it.copy(verifying=true)}}
    fun ready(value:Boolean){mutable.update{it.copy(ready=value,downloading=false,verifying=false)}}
    fun cancelled(){mutable.update{it.copy(downloading=false,verifying=false,downloaded=0,issue=null)}}
    fun error(issue:UpdateIssue){mutable.update{it.copy(downloading=false,verifying=false,ready=false,installPermissionNeeded=false,issue=issue)}}
    fun permission(value:Boolean){mutable.update{it.copy(installPermissionNeeded=value)}}
}
