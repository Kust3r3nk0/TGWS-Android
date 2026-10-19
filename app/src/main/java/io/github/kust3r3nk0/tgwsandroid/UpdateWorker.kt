package io.github.kust3r3nk0.tgwsandroid

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class UpdateScheduler(private val context:Context){
    companion object { const val NAME="tgws-update-check" }
    fun configure(options:UpdateOptions,replace:Boolean=true){
        val work=WorkManager.getInstance(context)
        if(!options.auto){work.cancelUniqueWork(NAME);return}
        val request=PeriodicWorkRequestBuilder<UpdateWorker>(options.minutes,TimeUnit.MINUTES)
            .setInitialDelay(options.minutes,TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.MINUTES).build()
        work.enqueueUniquePeriodicWork(NAME,if(replace)ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,request)
    }
}
internal class UpdateNotifier(private val context:Context){
    companion object { const val CHANNEL="tgws_app_updates"; const val ID=306 }
    fun show(release:GithubRelease):Boolean {
        if(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false
        val manager=context.getSystemService(NotificationManager::class.java)
        if(!manager.areNotificationsEnabled())return false
        manager.createNotificationChannel(NotificationChannel(CHANNEL,context.getString(R.string.update_notification_channel),NotificationManager.IMPORTANCE_DEFAULT))
        if(manager.getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE)return false
        val intent=Intent(context,MainActivity::class.java).putExtra(OPEN_UPDATES,true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending=PendingIntent.getActivity(context,ID,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return try{
            manager.notify((release.id and 0x7fffffff).toInt(),Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(context.getString(R.string.update_notification_text,release.version))
                .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).build());true
        }catch(_:SecurityException){false}
    }
}
internal object UpdateGraph {
    @Volatile private var instance:UpdateRepository?=null
    fun repository(context:Context):UpdateRepository=instance?:synchronized(this){instance?:run{
        val app=context.applicationContext
        val info=app.packageManager.getPackageInfo(app.packageName,PackageManager.PackageInfoFlags.of(0))
        UpdateRepository(UpdateOptionsStore(app),GithubUpdateSource(GithubUpdateHttp()),info.versionName.orEmpty(),info.longVersionCode,
            Build.VERSION.SDK_INT,schedule={UpdateScheduler(app).configure(it)},notify={UpdateNotifier(app).show(it)}).also{instance=it}
    }}
    /** In-process instrumentation substitution; never populated from intents or saved files. */
    internal fun useForTests(repository:UpdateRepository?){instance=repository}
}
class TgwsApplication:Application(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onCreate(){super.onCreate();scope.launch{
        try{UpdateScheduler(this@TgwsApplication).configure(UpdateOptionsStore(this@TgwsApplication).options(),replace=false)}
        catch(_:Exception){UpdateGraph.repository(this@TgwsApplication).error(UpdateIssue.STORAGE)}
    }}
}
class UpdateWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters){
    override suspend fun doWork():Result {
        if(!UpdateOptionsStore(applicationContext).options().auto)return Result.success()
        return try{val repository=UpdateGraph.repository(applicationContext)
            repository.initialize();repository.check();Result.success()
        }catch(error:CancellationException){throw error}
        catch(error:UpdateException){if(runAttemptCount<3 && error.issue in listOf(UpdateIssue.NETWORK,UpdateIssue.TIMEOUT,UpdateIssue.RATE_LIMIT,UpdateIssue.FORBIDDEN))Result.retry()else Result.failure()}
    }
}
