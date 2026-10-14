package io.github.kust3r3nk0.tgwsandroid

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.security.SecureRandom

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
        val store = SettingsStore(this)
        setContent {
            MaterialTheme {
                var portText by remember { mutableStateOf(store.port.toString()) }
                var secretText by remember { mutableStateOf(store.secret) }
                var error by remember { mutableStateOf("") }
                val running = ProxyState.running
                val proxyLink = "tg://proxy?server=127.0.0.1&port=${portText}&secret=dd${secretText}"
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("TGWS-Android", style = MaterialTheme.typography.headlineMedium)
                    Text("Android 14+ · 0.1.0-alpha", style = MaterialTheme.typography.labelMedium)
                    Text(ProxyState.description, style = MaterialTheme.typography.bodyLarge)
                    Text("Сессии: ${ProxyState.sessions}")
                    OutlinedTextField(value = portText, onValueChange = { portText = it.take(5).filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Локальный порт") }, enabled = !running)
                    OutlinedTextField(value = secretText, onValueChange = { secretText = it.take(32) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("MTProto secret (32 HEX)") }, enabled = !running)
                    OutlinedButton(onClick = {
                        secretText = ByteArray(16).also(SecureRandom()::nextBytes)
                            .joinToString("") { "%02x".format(it.toInt() and 255) }
                    }, enabled = !running) { Text("Сгенерировать новый ключ") }
                    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        if (running) {
                            startService(Intent(this@MainActivity, ProxyService::class.java).setAction(ProxyService.ACTION_STOP))
                        } else {
                            val port = portText.toIntOrNull()
                            if (port == null || port !in 1..65535 || !secretText.matches(Regex("[0-9a-fA-F]{32}"))) {
                                error = "Проверьте порт и 32-символьный HEX-ключ"
                            } else {
                                error = ""
                                store.port = port
                                store.secret = secretText
                                startForegroundService(Intent(this@MainActivity, ProxyService::class.java)
                                    .setAction(ProxyService.ACTION_START))
                            }
                        }
                    }) { Text(if (running) "Остановить" else "Запустить локальный прокси") }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Telegram proxy URL", proxyLink))
                    }) { Text("Скопировать ссылку для Telegram") }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(proxyLink))) }
                        catch (_: Exception) { error = "Не найдено приложение для открытия ссылки" }
                    }) { Text("Открыть в Telegram") }
                    HorizontalDivider()
                    Text("Предварительная версия: работают только прямой WebSocket и TCP fallback. " +
                        "Cloudflare Worker, HTTP/2, Fake TLS и полноценный IPv6 ещё не перенесены.")
                    Text("Локальный сервер: 127.0.0.1. Для работы Telegram нужен доступ к сети.")
                }
            }
        }
    }
}
