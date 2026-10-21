package io.github.kust3r3nk0.tgwsandroid

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.SystemClock

/** Optional alerts never replace or hide the mandatory foreground-service notification. */
internal class ConnectionNotifications(private val context:Context,private val handler:Handler):java.io.Closeable {
    companion object {
        const val CHANNEL="tgws_connection_alerts"
        fun clear(context:Context){val manager=context.getSystemService(NotificationManager::class.java)
            ConnectionAlert.entries.forEach{manager.cancel(1450+it.ordinal)}}
    }
    private val policy=ConnectionAlertPolicy()
    private val preferences=AppPreferences(context)
    private var snapshot=ProxySnapshot()
    private var closed=false
    private var scheduled:Long?=null
    private val timer=Runnable{scheduled=null;update(snapshot)}
    private val subscription=preferences.observeConnectionAlerts{if(!closed){if(!preferences.connectionAlerts)clear(context);update(snapshot)}}
    fun update(s:ProxySnapshot){
        if(closed)return
        snapshot=s
        val critical=s.issue in setOf(ProxyIssue.CONFIGURATION,ProxyIssue.PORT,ProxyIssue.SECRET_UNAVAILABLE,ProxyIssue.STORAGE)
        val observation=AlertObservation(s.running,s.pending,s.network.kind==AccessKind.NONE,critical,s.health)
        policy.update(preferences.connectionAlerts,observation,SystemClock.elapsedRealtime()).forEach(::show)
        if(scheduled!=policy.deadline){handler.removeCallbacks(timer);scheduled=policy.deadline
            scheduled?.let{handler.postDelayed(timer,(it-SystemClock.elapsedRealtime()).coerceAtLeast(1))}}
    }
    private fun show(alert:ConnectionAlert){
        if(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val manager=context.getSystemService(NotificationManager::class.java)
        if(!manager.areNotificationsEnabled())return
        manager.createNotificationChannel(NotificationChannel(CHANNEL,context.getString(R.string.smart_alert_channel),NotificationManager.IMPORTANCE_DEFAULT))
        if(manager.getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE)return
        val text=when(alert){ConnectionAlert.OUTAGE->R.string.smart_alert_outage;ConnectionAlert.RESTORED->R.string.smart_alert_restored
            ConnectionAlert.FALLBACK->R.string.smart_alert_fallback;ConnectionAlert.CONFIGURATION->R.string.smart_alert_configuration}
        val open=PendingIntent.getActivity(context,1450,Intent(context,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try{manager.notify(1450+alert.ordinal,Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_stat_tgws)
            .setContentTitle(context.getString(R.string.app_name)).setContentText(context.getString(text))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build())}catch(_:SecurityException){}
    }
    override fun close(){
        closed=true;handler.removeCallbacks(timer);subscription.close();policy.reset()
        val manager=context.getSystemService(NotificationManager::class.java)
        ConnectionAlert.entries.filter{it!=ConnectionAlert.CONFIGURATION}.forEach{manager.cancel(1450+it.ordinal)}
    }
}
