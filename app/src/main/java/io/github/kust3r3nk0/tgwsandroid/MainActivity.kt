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
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SettingsStore(this)
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent {
            MaterialTheme {
                var portText by rememberSaveable { mutableStateOf(SettingsValidation.DEFAULT_PORT.toString()) }
                // Secret/reveal state is deliberately absent from savedInstanceState.
                var secretText by remember { mutableStateOf("") }
                var revealSecret by remember { mutableStateOf(false) }
                var secretUnavailable by remember { mutableStateOf(false) }
                var busy by remember { mutableStateOf(true) }
                var errorResource by remember { mutableStateOf<Int?>(null) }
                var feedbackResource by remember { mutableStateOf<Int?>(null) }
                val scope = rememberCoroutineScope()
                val snapshot = ProxyState.snapshot
                val editable = !snapshot.running && !busy
                val validConfiguration = portText.toIntOrNull()?.let(SettingsValidation::validPort) == true &&
                    SettingsValidation.validSecret(secretText) && !secretUnavailable

                LaunchedEffect(Unit) {
                    try {
                        val saved = withContext(Dispatchers.IO) { store.load() }
                        portText = saved.port.toString()
                        secretText = saved.secret
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

                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.version_label, version), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(statusResource(snapshot.phase)), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.local_address, SettingsValidation.LOOPBACK, portText))
                    Text(stringResource(R.string.session_counts, snapshot.sessions, snapshot.pending))
                    Text(stringResource(if (snapshot.sessions > 0) R.string.remote_transport_established else R.string.remote_transport_unconfirmed))
                    if (busy) Text(stringResource(R.string.settings_loading))
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { char -> char in '0'..'9' }.take(5); feedbackResource = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.local_port)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = editable,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = secretText,
                        onValueChange = { secretText = it.take(32); feedbackResource = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.secret_label)) },
                        visualTransformation = if (revealSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = editable && !secretUnavailable,
                        singleLine = true,
                        trailingIcon = {
                            TextButton(onClick = { revealSecret = !revealSecret }, enabled = secretText.isNotEmpty()) {
                                Text(stringResource(if (revealSecret) R.string.secret_hide else R.string.secret_show))
                            }
                        }
                    )
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
                                    withContext(Dispatchers.IO) { store.recover(port, generated) }
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
                    val error = errorResource ?: snapshot.issue?.let(::issueResource)
                    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    feedbackResource?.let { Text(stringResource(it)) }
                    Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && snapshot.phase != ProxyPhase.STOPPING,
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
                                        withContext(Dispatchers.IO) { store.save(port, secret) }
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
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = validConfiguration && !busy, onClick = {
                        copySensitive(getString(R.string.clipboard_link), SettingsValidation.telegramLink(portText.toInt(), secretText))
                        feedbackResource = R.string.link_copied
                    }) { Text(stringResource(R.string.copy_link)) }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = SettingsValidation.validSecret(secretText) && !busy, onClick = {
                        copySensitive(getString(R.string.clipboard_secret), SettingsValidation.normalizeSecret(secretText))
                        feedbackResource = R.string.secret_copied
                    }) { Text(stringResource(R.string.copy_secret)) }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = validConfiguration && !busy, onClick = {
                        try {
                            val link = SettingsValidation.telegramLink(portText.toInt(), secretText)
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                        } catch (_: ActivityNotFoundException) {
                            errorResource = R.string.error_telegram_missing
                        } catch (_: SecurityException) {
                            errorResource = R.string.error_telegram_missing
                        }
                    }) { Text(stringResource(R.string.open_telegram)) }
                    HorizontalDivider()
                    Text(stringResource(R.string.alpha_notice))
                    Text(stringResource(R.string.local_notice))
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
    ProxyIssue.SESSION -> R.string.error_session
    ProxyIssue.CAPACITY -> R.string.error_capacity
    ProxyIssue.TIMEOUT -> R.string.error_timeout
    ProxyIssue.NETWORK_CHANGED -> R.string.network_changed
    ProxyIssue.SERVICE_START -> R.string.error_service_start
}
