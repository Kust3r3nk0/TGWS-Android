package io.github.kust3r3nk0.tgwsandroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import io.github.kust3r3nk0.tgwsandroid.core.MtprotoServer

/** User-started foreground service; NO Android VpnService is used. */
class ProxyService : Service() {
    companion object {
        const val ACTION_START = "io.github.kust3r3nk0.tgwsandroid.START"
        const val ACTION_STOP = "io.github.kust3r3nk0.tgwsandroid.STOP"
        private const val CHANNEL_ID = "proxy_connection"
        private const val NOTIFICATION_ID = 1443
    }

    private var server: MtprotoServer? = null

    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Proxy connection", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("TGWS-Android")
            .setContentText("Локальный MTProto-прокси активен")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        server?.stop()
        try {
            val store = SettingsStore(this)
            server = MtprotoServer(store.port, store.secret,
                report = { status -> ProxyState.description = status },
                sessionCount = { ProxyState.sessions = it },
                onFatalError = { stopSelf() }).also { it.start() }
        } catch (error: Exception) {
            Log.e("TGWSProxy", "Unable to start server", error)
            ProxyState.description = "Ошибка запуска: ${error.javaClass.simpleName}"
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        ProxyState.running = false
        ProxyState.sessions = 0
        if (!ProxyState.description.startsWith("Ошибка")) {
            ProxyState.description = "Остановлен"
        }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
