package io.github.kust3r3nk0.tgwsandroid

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.testTag
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import io.github.kust3r3nk0.tgwsandroid.core.HandshakeRejection
import io.github.kust3r3nk0.tgwsandroid.core.RelayEndpoint
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var updateRequest by mutableIntStateOf(0)
    private fun revealUpdateIntent(){intent.removeExtra(OPEN_UPDATES)}
    override fun onNewIntent(intent:Intent){
        // This intent is a navigation command, not a replacement for the launcher
        // identity. Keep the launch intent stable across lifecycle transitions.
        super.onNewIntent(intent)
        if(intent.getBooleanExtra(OPEN_UPDATES,false))updateRequest++
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SettingsStore(this)
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent {
            val preferences=remember{AppPreferences(this@MainActivity)}
            var themeMode by remember{mutableStateOf(preferences.theme)}
            var liquidGlass by remember{mutableStateOf(preferences.liquidGlass)}
            var transparency by remember{mutableStateOf(preferences.transparency)}
            TgwsTheme(themeMode,liquidGlass,transparency) {
                val isDark=darkTheme(themeMode)
                val windowBackground=MaterialTheme.colorScheme.background.toArgb()
                SideEffect{
                    // Transparent system bars can expose the native window on Android 16+.
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(windowBackground))
                    val bars=if(isDark)SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle=bars,navigationBarStyle=bars)
                }
                var page by rememberSaveable{mutableIntStateOf(if (intent.getBooleanExtra(OPEN_UPDATES, false)) 2 else 0)}
                LaunchedEffect(updateRequest){if(updateRequest>0 || intent.getBooleanExtra(OPEN_UPDATES,false)){page=2;revealUpdateIntent()}}
                var observedNetwork by remember{mutableStateOf(NetworkView())}
                DisposableEffect(Unit){
                    var monitor:AndroidNetworkMonitor?=null
                    fun startMonitor(){if(monitor==null){
                        monitor=AndroidNetworkMonitor(this@MainActivity,Handler(Looper.getMainLooper())){_,view->observedNetwork=view}.also{it.start()}
                    }}
                    val observer=LifecycleEventObserver{_,event->when(event){
                        Lifecycle.Event.ON_START->startMonitor()
                        Lifecycle.Event.ON_STOP->{monitor?.close();monitor=null}
                        else->Unit
                    }}
                    lifecycle.addObserver(observer)
                    if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))startMonitor()
                    onDispose{lifecycle.removeObserver(observer);monitor?.close()}
                }
                var portText by rememberSaveable { mutableStateOf(SettingsValidation.DEFAULT_PORT.toString()) }
                // Secret/reveal state is deliberately absent from savedInstanceState.
                var secretText by remember { mutableStateOf("") }
                var relayUrl by remember { mutableStateOf("") }
                var transportDraft by remember { mutableStateOf(TransportDraft()) }
                var imported by remember { mutableStateOf("") }
                var revealSecret by remember { mutableStateOf(false) }
                var secretUnavailable by remember { mutableStateOf(false) }
                var busy by remember { mutableStateOf(true) }
                var errorResource by remember { mutableStateOf<Int?>(null) }
                var feedbackResource by remember { mutableStateOf<Int?>(null) }
                val scope = rememberCoroutineScope()
                val snapshot = ProxyState.snapshot
                val editable = !snapshot.running && !busy
                val advancedConfig=runCatching{transportDraft.settings()}.getOrNull()
                val validConfiguration = advancedConfig!=null && portText.toIntOrNull()?.let(SettingsValidation::validPort) == true &&
                    SettingsValidation.validSecret(secretText) && !secretUnavailable &&
                    (advancedConfig.mode!=io.github.kust3r3nk0.tgwsandroid.core.TransportMode.RELAY_ONLY || relayUrl.isNotEmpty()) &&
                    runCatching { RelayEndpoint.parse(relayUrl) }.isSuccess

                // A shared link must survive recreation and match the next service start.
                // Persist first; a failed Keystore write must never export a draft secret.
                fun shareSavedSettings(action: (StoredSettings) -> Unit) {
                    if (busy || !validConfiguration) return
                    val draft = StoredSettings(portText.toInt(), secretText, relayUrl, requireNotNull(advancedConfig))
                    busy = true
                    feedbackResource = null
                    scope.launch {
                        try {
                            val saved = withContext(Dispatchers.IO) {
                                if (!snapshot.running) store.save(draft.port, draft.secret, draft.relayUrl, draft.transport)
                                store.load()
                            }
                            secretText = saved.secret
                            errorResource = null
                            revealSecret = false
                            action(saved)
                        } catch (error: SettingsException) {
                            errorResource = settingsError(error.issue)
                            if (error.issue == SettingsIssue.SECRET_UNAVAILABLE) secretUnavailable = true
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: RuntimeException) {
                            errorResource = R.string.error_storage
                        } finally { busy = false }
                    }
                }

                LaunchedEffect(Unit) {
                    try {
                        val saved = withContext(Dispatchers.IO) { store.load() }
                        portText = saved.port.toString()
                        secretText = saved.secret
                        relayUrl = saved.relayUrl
                        transportDraft=TransportDraft.from(saved.transport)
                    } catch (error: SettingsException) {
                        secretUnavailable = error.issue == SettingsIssue.SECRET_UNAVAILABLE
                        errorResource = settingsError(error.issue)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        errorResource = R.string.error_storage
                    } finally {
                        busy = false
                    }
                }
                DisposableEffect(revealSecret) {
                    if (revealSecret) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
                }
                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_PAUSE) revealSecret = false
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                if (page == 2 || page == 3) {
                    TgwsUpdateHub(
                        page = page, currentVersion = version, onPage = { page = it },
                        onBack = { page = 1 },
                        requestNotificationPermission = {
                            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
                            }
                        }
                    )
                } else {
                AppScaffold(page=page,onPage={page=it;revealSecret=false},version=version,isDark=isDark,onToggleTheme={
                    themeMode=if(isDark)ThemeMode.LIGHT else ThemeMode.DARK;preferences.theme=themeMode
                }){
                    if(busy){
                        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().testTag("settings.loading"))
                        Text(stringResource(R.string.settings_loading),style=MaterialTheme.typography.bodySmall)
                    }
                    val error = errorResource ?: snapshot.issue?.let(::issueResource)
                    error?.let {
                        val warning = errorResource == null && snapshot.issueIsWarning
                        val message = stringResource(it)
                        Text(if (warning) stringResource(R.string.session_event, message) else message,
                            color = if (warning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    }
                    feedbackResource?.let { Text(stringResource(it)) }
                    if(page==0){
                        SectionCard(stringResource(R.string.proxy_overview),Modifier.testTag("home.status")){
                    Text(stringResource(statusResource(snapshot.phase)), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.local_address, SettingsValidation.LOOPBACK, portText))
                    Text(stringResource(R.string.session_counts, snapshot.sessions, snapshot.pending))
                    Text(stringResource(when {
                        snapshot.sessions > 0 -> R.string.remote_transport_established
                        snapshot.pending > 0 -> R.string.remote_transport_connecting
                        else -> R.string.remote_transport_unconfirmed
                    }),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if (snapshot.rejectedHandshakes > 0) {
                        Text(stringResource(if (snapshot.running) R.string.handshake_rejections else R.string.handshake_rejections_previous, snapshot.rejectedHandshakes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        snapshot.lastHandshakeRejection?.let {
                            Text(stringResource(rejectionResource(it)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(modifier = Modifier.fillMaxWidth().then(Modifier.testTag("proxy.toggle")), enabled = !busy && snapshot.phase != ProxyPhase.STOPPING,
                        onClick = {
                            feedbackResource = null
                            if (snapshot.running) {
                                startService(Intent(this@MainActivity, ProxyService::class.java).setAction(ProxyService.ACTION_STOP))
                            } else if (!validConfiguration) {
                                errorResource = if (secretUnavailable) R.string.error_secret_unavailable else R.string.error_configuration
                            } else {
                                val port = portText.toInt()
                                val secret = secretText
                                scope.launch {
                                    busy = true
                                    try {
                                        withContext(Dispatchers.IO) { store.save(port, secret, relayUrl,requireNotNull(advancedConfig)) }
                                        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
                                        }
                                        startForegroundService(Intent(this@MainActivity, ProxyService::class.java).setAction(ProxyService.ACTION_START))
                                        errorResource = null
                                        revealSecret = false
                                    } catch (error: SettingsException) {
                                        errorResource = settingsError(error.issue)
                                        if (error.issue == SettingsIssue.SECRET_UNAVAILABLE) secretUnavailable = true
                                    } catch (_: RuntimeException) {
                                        errorResource = R.string.error_service_start
                                    } finally { busy = false }
                                }
                            }
                        }) {
                        Text(stringResource(if (snapshot.running) R.string.stop else R.string.start_proxy))
                    }
                        }
                        NetworkCard(observedNetwork)
                        SectionCard(stringResource(R.string.telegram_actions)){
                    Text(stringResource(R.string.telegram_link_notice), style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(modifier = Modifier.fillMaxWidth().testTag("telegram.copy_link"), enabled = validConfiguration && !busy, onClick = {
                        shareSavedSettings { saved ->
                            copySensitive(getString(R.string.clipboard_link), SettingsValidation.telegramLink(saved.port, saved.secret, saved.transport.fakeTlsDomain))
                            feedbackResource = R.string.link_copied
                        }
                    }) { Text(stringResource(R.string.copy_link)) }
                    OutlinedButton(modifier = Modifier.fillMaxWidth().testTag("telegram.open"), enabled = validConfiguration && !busy, onClick = {
                        shareSavedSettings { saved ->
                            try {
                                val link = SettingsValidation.telegramLink(saved.port, saved.secret, saved.transport.fakeTlsDomain)
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                            } catch (_: ActivityNotFoundException) {
                                errorResource = R.string.error_telegram_missing
                            } catch (_: SecurityException) {
                                errorResource = R.string.error_telegram_missing
                            }
                        }
                    }) { Text(stringResource(R.string.open_telegram)) }
                        }
                        SectionCard(stringResource(R.string.connection_details)){
                            Text(stringResource(R.string.transport_counts,snapshot.wssSessions,snapshot.tcpSessions,snapshot.relaySessions,snapshot.cfSessions,snapshot.h2Sessions),style=MaterialTheme.typography.bodySmall)
                    DiagnosticsPanel(snapshot.copy(network=observedNetwork),onProbe={
                        try{startService(Intent(this@MainActivity,ProxyService::class.java).setAction(ProxyService.ACTION_PROBE));feedbackResource=R.string.probe_requested}
                        catch(_:RuntimeException){errorResource=R.string.error_service_start}
                    },onCopy={copySensitive(getString(R.string.diagnostics),it)})
                        }
                        Text(stringResource(R.string.local_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }else{
                        AppearanceSettings(themeMode,{themeMode=it;preferences.theme=it},
                            liquidGlass,{liquidGlass=it;preferences.liquidGlass=it},
                            transparency,{transparency=it;preferences.transparency=it})
                        SectionCard(stringResource(R.string.mtproxy_settings)){
                            if(!editable)Text(stringResource(R.string.stop_to_edit),style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { char -> char in '0'..'9' }.take(5); feedbackResource = null },
                        modifier = Modifier.fillMaxWidth().testTag("settings.port"),
                        label = { Text(stringResource(R.string.local_port)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = editable,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = secretText,
                        onValueChange = { input ->
                            // Never truncate dd/ee prefixes into an apparently valid but wrong key.
                            val text = input.trim()
                            val decoded = runCatching { io.github.kust3r3nk0.tgwsandroid.core.TelegramSecret.decode(text) }.getOrNull()
                            if (decoded != null && text.length > 32) {
                                secretText = decoded.secret
                                transportDraft = transportDraft.copy(fakeDomain = decoded.domain)
                                feedbackResource = R.string.import_success
                            } else {
                                secretText = text.take(2048)
                                feedbackResource = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("settings.secret"),
                        label = { Text(stringResource(R.string.secret_label)) },
                        visualTransformation = if (revealSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = editable && !secretUnavailable,
                        singleLine = true,
                        isError = secretText.isNotEmpty() && !SettingsValidation.validSecret(secretText),
                        supportingText = { Text(stringResource(R.string.secret_input_help)) },
                        trailingIcon = {
                            TextButton(onClick = { revealSecret = !revealSecret }, enabled = secretText.isNotEmpty() || relayUrl.isNotEmpty()) {
                                Text(stringResource(if (revealSecret) R.string.secret_hide else R.string.secret_show))
                            }
                        }
                    )
                    OutlinedTextField(
                        value = relayUrl,
                        onValueChange = { relayUrl = it.take(RelayEndpoint.MAX_URL_LENGTH); feedbackResource = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.relay_label)) },
                        visualTransformation = if (revealSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        enabled = editable,
                        singleLine = true,
                        isError = runCatching { RelayEndpoint.parse(relayUrl) }.isFailure,
                    )
                    if(transportDraft.mode!=io.github.kust3r3nk0.tgwsandroid.core.TransportMode.AUTO_CLOUDFLARE)Text(stringResource(if (transportDraft.mode!=io.github.kust3r3nk0.tgwsandroid.core.TransportMode.RELAY_ONLY) R.string.relay_direct_notice else R.string.relay_enabled_notice),
                        style = MaterialTheme.typography.bodySmall)
                    TransportEditor(transportDraft,{transportDraft=it;feedbackResource=null},editable,revealSecret,relayUrl)
                    OutlinedTextField(value=imported,onValueChange={imported=it.take(2048)},modifier=Modifier.fillMaxWidth(),
                        label={Text(stringResource(R.string.import_label))},enabled=editable,singleLine=true,
                        visualTransformation=if(revealSecret)VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
                    OutlinedButton(enabled=editable && imported.isNotEmpty(),onClick={
                        try{
                            val decoded=if(imported.startsWith("tg://") || imported.startsWith("https://")){
                                val link=io.github.kust3r3nk0.tgwsandroid.core.TelegramSecret.import(imported)
                                portText=link.port.toString();link.decoded
                            }else io.github.kust3r3nk0.tgwsandroid.core.TelegramSecret.decode(imported)
                            secretText=decoded.secret;transportDraft=transportDraft.copy(fakeDomain=decoded.domain)
                            imported="";revealSecret=false;feedbackResource=R.string.import_success;errorResource=null
                        }catch(_:IllegalArgumentException){errorResource=R.string.import_invalid}
                    }){Text(stringResource(R.string.import_button))}
                    if (secretUnavailable) Text(stringResource(R.string.secret_recovery_notice), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = {
                        val generated = SettingsValidation.generateSecret()
                        revealSecret = false
                        feedbackResource = null
                        if (secretUnavailable) {
                            val port = portText.toIntOrNull()
                            if (port == null || !SettingsValidation.validPort(port)) errorResource = R.string.error_configuration
                            else scope.launch {
                                busy = true
                                try {
                                    withContext(Dispatchers.IO) { store.recover(port, generated, relayUrl,advancedConfig) }
                                    secretText = generated
                                    secretUnavailable = false
                                    errorResource = null
                                    feedbackResource = R.string.secret_recovered
                                } catch (error: SettingsException) {
                                    errorResource = settingsError(error.issue)
                                } finally { busy = false }
                            }
                        } else {
                            secretText = generated
                            errorResource = null
                        }
                    }, enabled = editable) {
                        Text(stringResource(if (secretUnavailable) R.string.secret_replace else R.string.secret_generate))
                    }
                    Text(stringResource(R.string.secret_change_notice), style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = validConfiguration && !busy, onClick = {
                        shareSavedSettings { saved ->
                            copySensitive(getString(R.string.clipboard_secret), saved.secret)
                            feedbackResource = R.string.secret_copied
                        }
                    }) { Text(stringResource(R.string.copy_secret)) }
                        Button(modifier=Modifier.fillMaxWidth().testTag("settings.save"),enabled=editable && validConfiguration,onClick={
                            scope.launch{
                                busy=true
                                try{
                                    withContext(Dispatchers.IO){store.save(portText.toInt(),secretText,relayUrl,requireNotNull(advancedConfig))}
                                    errorResource=null;feedbackResource=R.string.settings_saved;revealSecret=false
                                }catch(error:SettingsException){errorResource=settingsError(error.issue)}
                                finally{busy=false}
                            }
                        }){Text(stringResource(R.string.save_settings))}
                        }
                        SectionCard(stringResource(R.string.update_section), Modifier.testTag("settings.updates")) {
                            Text(stringResource(R.string.update_source_description),
                                style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(modifier = Modifier.fillMaxWidth().testTag("settings.update.center"),
                                onClick = { page = 2;revealSecret=false }) { Text(stringResource(R.string.update_center)) }
                            OutlinedButton(modifier = Modifier.fillMaxWidth().testTag("settings.update.options"),
                                onClick = { page = 3;revealSecret=false }) { Text(stringResource(R.string.update_settings)) }
                        }
                        SectionCard(stringResource(R.string.about_title)){
                            Text(stringResource(R.string.version_label,version),modifier=Modifier.testTag("about.version"))
                            Text(stringResource(R.string.alpha_notice),style=MaterialTheme.typography.bodySmall,
                                modifier=Modifier.testTag("about.notice"))
                        }
                    }
                }
                }
            }
        }
    }

    private fun copySensitive(label: String, value: String) {
        val clip = ClipData.newPlainText(label, value)
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    }
}

private fun settingsError(issue: SettingsIssue): Int = when (issue) {
    SettingsIssue.INVALID_CONFIGURATION -> R.string.error_configuration
    SettingsIssue.SECRET_UNAVAILABLE -> R.string.error_secret_unavailable
    SettingsIssue.WRITE_FAILED -> R.string.error_storage
}

private fun statusResource(phase: ProxyPhase): Int = when (phase) {
    ProxyPhase.STOPPED -> R.string.status_stopped
    ProxyPhase.STARTING -> R.string.status_starting
    ProxyPhase.LISTENING -> R.string.status_listening
    ProxyPhase.ACTIVE -> R.string.status_active
    ProxyPhase.ERROR -> R.string.status_error
    ProxyPhase.STOPPING -> R.string.status_stopping
}

private fun issueResource(issue: ProxyIssue): Int = when (issue) {
    ProxyIssue.CONFIGURATION -> R.string.error_configuration
    ProxyIssue.PORT -> R.string.error_port
    ProxyIssue.SECRET_UNAVAILABLE -> R.string.error_secret_unavailable
    ProxyIssue.STORAGE -> R.string.error_storage
    ProxyIssue.HANDSHAKE -> R.string.error_handshake
    ProxyIssue.WSS_HANDSHAKE -> R.string.error_wss_handshake
    ProxyIssue.TLS -> R.string.error_tls
    ProxyIssue.TCP -> R.string.error_tcp
    ProxyIssue.RELAY -> R.string.error_relay
    ProxyIssue.RETRY_LATER -> R.string.retry_later
    ProxyIssue.SESSION -> R.string.error_session
    ProxyIssue.CAPACITY -> R.string.error_capacity
    ProxyIssue.TIMEOUT -> R.string.error_timeout
    ProxyIssue.NETWORK_CHANGED -> R.string.network_changed
    ProxyIssue.SERVICE_START -> R.string.error_service_start
    ProxyIssue.HTTP2 -> R.string.error_http2
    ProxyIssue.DNS -> R.string.error_dns
    ProxyIssue.NETWORK -> R.string.error_network
}

private fun rejectionResource(reason: HandshakeRejection): Int = when (reason) {
    HandshakeRejection.INVALID_LENGTH -> R.string.rejection_length
    HandshakeRejection.RESERVED_PREFIX -> R.string.rejection_prefix
    HandshakeRejection.SECRET_OR_TRANSPORT -> R.string.rejection_secret_or_transport
    HandshakeRejection.UNSUPPORTED_DC -> R.string.rejection_dc
    HandshakeRejection.FAKE_TLS -> R.string.rejection_fake_tls
}
