package io.github.kust3r3nk0.tgwsandroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.kust3r3nk0.tgwsandroid.core.MtprotoServer
import io.github.kust3r3nk0.tgwsandroid.core.RelayEndpoint
import io.github.kust3r3nk0.tgwsandroid.core.HandshakeRejection
import io.github.kust3r3nk0.tgwsandroid.core.ServerEvent
import io.github.kust3r3nk0.tgwsandroid.core.ServerIssue
import io.github.kust3r3nk0.tgwsandroid.core.ServerPhase
import java.util.concurrent.Executors

/** A user-started local proxy, kept visible by a specialUse foreground notification. */
class ProxyService : Service() {
    companion object {
        const val ACTION_START = "io.github.kust3r3nk0.tgwsandroid.START"
        const val ACTION_STOP = "io.github.kust3r3nk0.tgwsandroid.STOP"
        const val ACTION_PROBE = "io.github.kust3r3nk0.tgwsandroid.PROBE"
        private const val CHANNEL_ID = "proxy_connection"
        private const val NOTIFICATION_ID = 1443
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val settingsExecutor = Executors.newSingleThreadExecutor { task -> Thread(task, "TGWS-settings") }
    private var server: MtprotoServer? = null
    private var loading = false
    private var destroyed = false
    private var stopping = false
    private var fatalError = false
    private var networkMonitor:AndroidNetworkMonitor?=null
    private var transportFactory:io.github.kust3r3nk0.tgwsandroid.core.NetworkTransportFactory?=null
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW
        ))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopping = true
                if (!fatalError) ProxyState.snapshot = ProxyState.snapshot.copy(phase = ProxyPhase.STOPPING, issue = null)
                stopSelf()
            }
            ACTION_START -> startFromUserAction()
            ACTION_PROBE -> {
                if (!destroyed && !stopping && server != null) {
                    networkMonitor?.refresh()
                    server?.probeRoutes()
                } else if (!loading) stopSelfResult(startId)
            }
            else -> if (server == null && !loading) stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    private fun startFromUserAction() {
        // Repeated explicit starts keep the existing listener and sessions intact.
        if (destroyed || stopping || loading || server != null) return
        fatalError = false
        ProxyState.recordDiagnostics = AppPreferences(this).diagnostics
        ProxyState.snapshot = ProxySnapshot(phase = ProxyPhase.STARTING)
        try {
            startForeground(NOTIFICATION_ID, notification())
        } catch (_: RuntimeException) {
            fail(ProxyIssue.SERVICE_START)
            return
        }
        loading = true
        settingsExecutor.execute {
            try {
                val settings = SettingsStore(this).load()
                mainHandler.post {
                    if (destroyed || stopping) return@post
                    loading = false
                    try {
                        val monitor=AndroidNetworkMonitor(this,mainHandler){changed,view->
                            if(!destroyed && !stopping){
                                ProxyState.snapshot=ProxyState.snapshot.copy(network=view)
                                if(changed){transportFactory?.invalidate();disconnectForNetworkChange()}
                            }
                        }
                        networkMonitor=monitor
                        monitor.start()
                        val factory=io.github.kust3r3nk0.tgwsandroid.core.NetworkTransportFactory{monitor.access}
                        transportFactory=factory
                        server = MtprotoServer(settings.port, settings.secret,
                            onEvent = { event -> mainHandler.post { receive(event) } },
                            onFatalError = { mainHandler.post { if (!destroyed && !stopping) fail(ProxyState.snapshot.issue ?: ProxyIssue.SESSION) } },
                            relayEndpoint = if(settings.transport.mode==io.github.kust3r3nk0.tgwsandroid.core.TransportMode.RELAY_ONLY)RelayEndpoint.parse(settings.relayUrl)else null,
                            transports=factory,
                            transportSettings=settings.transport
                        )
                        server?.start()
                    } catch (_: Exception) {
                        fail(ProxyIssue.CONFIGURATION)
                    }
                }
            } catch (error: SettingsException) {
                mainHandler.post {
                    if (!destroyed && !stopping) fail(when (error.issue) {
                        SettingsIssue.INVALID_CONFIGURATION -> ProxyIssue.CONFIGURATION
                        SettingsIssue.SECRET_UNAVAILABLE -> ProxyIssue.SECRET_UNAVAILABLE
                        SettingsIssue.WRITE_FAILED -> ProxyIssue.STORAGE
                    })
                }
            } catch (_: Exception) {
                mainHandler.post { if (!destroyed && !stopping) fail(ProxyIssue.STORAGE) }
            }
        }
    }

    private fun receive(event: ServerEvent) {
        // Worker callbacks already queued on main must not undo an explicit stop, or
        // replace the cause of a fatal listener failure with another session's issue.
        if (destroyed || stopping || fatalError) return
        when (event) {
            is ServerEvent.Routes -> { ProxyState.snapshot=ProxyState.snapshot.copy(routes=event.routes);return }
            is ServerEvent.Traffic -> { ProxyState.snapshot=ProxyState.snapshot.copy(sent=event.sent,received=event.received);return }
            is ServerEvent.BackgroundIssue -> {
                ProxyState.record(issueEvent(ProxyIssue.valueOf(event.issue.name),System.currentTimeMillis()).copy(kind=EventKind.WARMUP_FAILED))
                return
            }
            is ServerEvent.State -> {
                val phase = when (event.phase) {
                    ServerPhase.STARTING -> ProxyPhase.STARTING
                    ServerPhase.LISTENING -> if (ProxyState.snapshot.sessions > 0) ProxyPhase.ACTIVE else ProxyPhase.LISTENING
                    ServerPhase.STOPPING -> ProxyPhase.STOPPING
                    ServerPhase.STOPPED -> if (fatalError) ProxyPhase.ERROR else ProxyPhase.STOPPED
                    ServerPhase.ERROR -> ProxyPhase.ERROR
                }
                if (phase == ProxyPhase.ERROR) fatalError = true
                ProxyState.snapshot = ProxyState.snapshot.copy(phase = phase)
            }
            is ServerEvent.Sessions -> {
                val previous = ProxyState.snapshot
                ProxyState.snapshot = previous.withSessions(event.established, event.pending, event.wss, event.tcp, event.relay,event.cf,event.h2)
            }
            is ServerEvent.Diagnostic -> {
                if (event.issue == ServerIssue.HANDSHAKE) {
                    val reason = event.rejection ?: HandshakeRejection.SECRET_OR_TRANSPORT
                    ProxyState.snapshot = ProxyState.snapshot.withRejectedHandshake(reason)
                    return
                }
                val issue = when (event.issue) {
                    ServerIssue.CONFIGURATION -> ProxyIssue.CONFIGURATION
                    ServerIssue.PORT -> ProxyIssue.PORT
                    ServerIssue.HANDSHAKE -> ProxyIssue.HANDSHAKE
                    ServerIssue.WSS_HANDSHAKE -> ProxyIssue.WSS_HANDSHAKE
                    ServerIssue.TLS -> ProxyIssue.TLS
                    ServerIssue.TCP -> ProxyIssue.TCP
                    ServerIssue.RELAY -> ProxyIssue.RELAY
                    ServerIssue.RETRY_LATER -> ProxyIssue.RETRY_LATER
                    ServerIssue.SESSION -> ProxyIssue.SESSION
                    ServerIssue.CAPACITY -> ProxyIssue.CAPACITY
                    ServerIssue.TIMEOUT -> ProxyIssue.TIMEOUT
                    ServerIssue.HTTP2 -> ProxyIssue.HTTP2
                    ServerIssue.DNS -> ProxyIssue.DNS
                    ServerIssue.NETWORK -> ProxyIssue.NETWORK
                }
                if(ProxyState.snapshot.issue==issue)ProxyState.record(issueEvent(issue,System.currentTimeMillis()))
                ProxyState.snapshot = ProxyState.snapshot.copy(issue = issue)
            }
        }
        if (ProxyState.snapshot.running) notifications.notify(NOTIFICATION_ID, notification())
    }

    private fun fail(issue: ProxyIssue) {
        fatalError = true
        stopping = true
        loading = false
        ProxyState.snapshot = ProxyState.snapshot.copy(phase = ProxyPhase.ERROR, issue = issue)
        stopSelf()
    }

    private fun disconnectForNetworkChange() {
        if (destroyed || stopping) return
        if (ProxyState.snapshot.sessions > 0 || ProxyState.snapshot.pending > 0) {
            ProxyState.snapshot = ProxyState.snapshot.copy(issue = ProxyIssue.NETWORK_CHANGED)
        }
        server?.disconnectSessions()
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ProxyService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val message = if (ProxyState.snapshot.localAvailable) R.string.notification_listening else R.string.notification_starting
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(message))
            .setContentIntent(open)
            .setSmallIcon(R.drawable.ic_stat_tgws)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop), stop).build())
            .build()
    }

    override fun onDestroy() {
        destroyed = true
        networkMonitor?.close();networkMonitor=null
        server?.stop()
        server = null
        transportFactory?.close();transportFactory=null
        settingsExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        ProxyState.snapshot = ProxyState.snapshot.afterServiceStop(fatalError)
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifications.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
