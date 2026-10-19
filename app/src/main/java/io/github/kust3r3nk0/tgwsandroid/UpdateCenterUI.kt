package io.github.kust3r3nk0.tgwsandroid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Accent = Color(0xFF39E999)
private val Night = Color(0xFF090F11)
private val NightCard = Color(0xFF151E20)
private val NightBorder = Color(0xFF2A3B3C)
private val Dim = Color(0xFFADB8B9)

@Composable private fun UiColors(): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    return if (dark) Night to NightCard
    else MaterialTheme.colorScheme.background to MaterialTheme.colorScheme.surfaceContainer
}

private fun sections(notes: String): List<Pair<String, List<String>>> {
    val groups = linkedMapOf(
        "Новые функции" to mutableListOf<String>(),
        "Исправления" to mutableListOf(),
        "Оптимизация" to mutableListOf(),
        "Техническое" to mutableListOf()
    )
    notes.lineSequence().map { it.trim() }
        .filter { it.startsWith("- ") || it.startsWith("• ") }
        .map { it.drop(2).trim().take(450) }.filter { it.isNotBlank() }
        .take(30).forEach { line ->
            val lower = line.lowercase()
            val key = when {
                listOf("исправ", "устран", "почин", "ошиб", "сбой").any { lower.contains(it) } -> "Исправления"
                listOf("добав", "новая функц", "реализ", "поддерж").any { lower.contains(it) } -> "Новые функции"
                listOf("оптимиз", "ускор", "быстр", "производительн").any { lower.contains(it) } -> "Оптимизация"
                else -> "Техническое"
            }
            groups.getValue(key).add(line)
        }
    return groups.filterValues { it.isNotEmpty() }.toList()
}

@Composable private fun PremiumCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val (_, card) = UiColors()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f)),
        colors = CardDefaults.cardColors(containerColor = card),
        content = {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    )
}

@Composable private fun Header(title: String, subtitle: String, back: () -> Unit, settings: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = back, modifier = Modifier.testTag("updates.back")) {
            Text("‹", fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (settings != null) {
            TextButton(onClick = settings, modifier = Modifier.testTag("updates.settings")) {
                Text("⋮", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
internal fun TgwsUpdateHub(
    page: Int, currentVersion: String, onPage: (Int) -> Unit,
    onBack: () -> Unit, requestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    val updates = remember(context) { GithubUpdates(context) }
    val store = remember(context) { UpdateOptionsStore(context) }
    var options by remember { mutableStateOf(store.read()) }
    var latest by remember { mutableStateOf<GithubRelease?>(null) }
    var loading by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun change(next: UpdateOptions) {
        options = next
        store.save(next)
        updates.configureWorker(replace = true)
    }
    fun check() {
        if (loading) return
        scope.launch {
            loading = true
            status = ""
            updates.check().fold(
                onSuccess = {
                    latest = it
                    status = if (it == null) "Установлена последняя доступная версия" else ""
                    if (it != null) updates.notify(it)
                },
                onFailure = { status = "Проверка не удалась: " + (it.message ?: "ошибка сети") }
            )
            loading = false
        }
    }
    LaunchedEffect(page) { if (page == 2) check() }

    val (background, _) = UiColors()
    val foreground = MaterialTheme.colorScheme.onBackground
    Scaffold(
        containerColor = background,
        topBar = {
            Header(
                title = if (page == 2) "TGWS-Android" else "Настройки обновлений",
                subtitle = if (page == 2) "Центр обновлений" else "Автоматическая проверка GitHub",
                back = if (page == 2) onBack else ({ onPage(2) }),
                settings = if (page == 2) ({ onPage(3) }) else null
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (page == 2) {
                val info = latest
                Card(
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().testTag("updates.hero"),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(
                        Modifier.fillMaxWidth().background(
                            Brush.linearGradient(listOf(Color(0xFF173E33), Color(0xFF10201F), Color(0xFF121A1C)))
                        ).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (info != null) "Доступно обновление" else "Проверка обновлений", color = Dim,
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(100.dp), color = Accent.copy(alpha = .14f),
                                border = BorderStroke(1.dp, Accent.copy(alpha = .55f))) {
                                Text(if (info != null) "Новая версия" else "GitHub Releases",
                                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = Accent, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text(info?.version?.let { "v" + it } ?: "TGWS-Android",
                            fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            if (info != null) "Опубликовано новое обновление приложения. Ознакомьтесь с изменениями перед установкой."
                            else "Проверка новых версий из официального GitHub-репозитория.",
                            color = Dim, style = MaterialTheme.typography.bodyMedium
                        )
                        Text("↓", fontSize = 38.sp, color = Accent, modifier = Modifier.align(Alignment.End))
                    }
                }
                Text("Что нового", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (info != null) {
                    val changes = sections(info.notes)
                    if (changes.isEmpty()) {
                        PremiumCard {
                            Text("Примечания к релизу отсутствуют", color = foreground)
                        }
                    } else {
                        changes.forEach { (category, lines) ->
                            PremiumCard {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val iconColor = when (category) {
                                        "Исправления" -> Color(0xFF90A8FF)
                                        "Оптимизация" -> Color(0xFFFFC763)
                                        else -> Accent
                                    }
                                    Text(when (category) {
                                        "Новые функции" -> "✧"
                                        "Исправления" -> "⚒"
                                        "Оптимизация" -> "◉"
                                        else -> "ⓘ"
                                    }, color = iconColor, fontSize = 25.sp)
                                    Text(category, style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold)
                                }
                                lines.forEach { line ->
                                    Text("• " + line, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                } else {
                    PremiumCard {
                        Text("Список изменений появится, когда GitHub опубликует более новую совместимую версию.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (loading || downloading) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = Accent)
                    Text(if (downloading) "Загрузка и проверка APK…" else "Проверка GitHub…",
                        modifier = Modifier.align(Alignment.CenterHorizontally), color = Dim)
                }
                if (info != null) {
                    Button(
                        enabled = !loading && !downloading,
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("updates.install"),
                        shape = RoundedCornerShape(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                        onClick = {
                            scope.launch {
                                downloading = true
                                status = ""
                                try {
                                    val apk = updates.downloadVerified(info, options.wifiOnly)
                                    updates.install(apk)
                                } catch (e: Exception) {
                                    status = e.message ?: "Не удалось установить обновление"
                                } finally { downloading = false }
                            }
                        }
                    ) { Text("↓  Обновить", fontWeight = FontWeight.Bold) }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    onClick = onBack,
                    shape = RoundedCornerShape(50.dp)
                ) { Text("Позже") }
                TextButton(modifier = Modifier.fillMaxWidth().testTag("updates.refresh"),
                    onClick = { check() }, enabled = !loading && !downloading) {
                    Text("⟳  Проверить сейчас")
                }
            } else {
                PremiumCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Автоматическая проверка", fontWeight = FontWeight.SemiBold)
                            Text("Поиск новых версий на GitHub",
                                style = MaterialTheme.typography.bodySmall, color = Dim)
                        }
                        Switch(checked = options.auto, colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color(0xFF082017)), onCheckedChange = {
                            change(options.copy(auto = it))
                        }, modifier = Modifier.testTag("updates.auto"))
                    }
                }
                PremiumCard {
                    Text("Периодичность проверки", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    UpdateOptions.periods.forEach { period ->
                        val label = when (period) {
                            15L -> "Каждые 15 минут"
                            60L -> "Каждый час"
                            180L -> "Каждые 3 часа"
                            360L -> "Каждые 6 часов (рекомендую)"
                            720L -> "Каждые 12 часов"
                            1440L -> "Раз в сутки"
                            else -> "Раз в неделю"
                        }
                        val selected = period == options.minutes
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Accent.copy(alpha = .12f) else Color.Transparent)
                                .clickable(enabled = options.auto) { change(options.copy(minutes = period)) }
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, enabled = options.auto,
                                colors = RadioButtonDefaults.colors(selectedColor = Accent),
                                onClick = { change(options.copy(minutes = period)) })
                            Text(label, modifier = Modifier.padding(start = 6.dp),
                                color = if (options.auto) MaterialTheme.colorScheme.onSurface else Dim,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                PremiumCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Уведомления об обновлениях", fontWeight = FontWeight.SemiBold)
                            Text("Системные уведомления Android",
                                color = Dim, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = options.notify, colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color(0xFF082017)), onCheckedChange = {
                            change(options.copy(notify = it))
                            if (it && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED) requestNotificationPermission()
                        }, modifier = Modifier.testTag("updates.notify"))
                    }
                }
                PremiumCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Загрузка только по Wi-Fi", fontWeight = FontWeight.SemiBold)
                            Text("Не расходовать мобильный трафик на APK",
                                color = Dim, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = options.wifiOnly,
                            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color(0xFF082017)),
                            onCheckedChange = { change(options.copy(wifiOnly = it)) },
                            modifier = Modifier.testTag("updates.wifi"))
                    }
                }
                PremiumCard {
                    Row {
                        Text("Текущая версия", Modifier.weight(1f), color = Dim)
                        Text(currentVersion, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = NightBorder)
                    Text("Расписание: " + if (options.auto) {
                        when (options.minutes) {
                            15L -> "15 минут"; 60L -> "1 час"; 180L -> "3 часа"
                            360L -> "6 часов"; 720L -> "12 часов"; 1440L -> "24 часа"
                            else -> "7 дней"
                        }
                    } else "выключено", color = Dim)
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("updates.manual"),
                    onClick = { onPage(2) },
                    shape = RoundedCornerShape(100.dp)
                ) { Text("⟳  Проверить сейчас") }
                Text("Фоновые проверки выполняются Android приблизительно, а не строго по таймеру.",
                    color = Dim, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
