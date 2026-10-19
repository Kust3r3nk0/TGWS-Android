package io.github.kust3r3nk0.tgwsandroid

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateOptions(
    val auto: Boolean = true, val minutes: Long = 360,
    val notify: Boolean = true, val wifiOnly: Boolean = false
) {
    companion object { val periods = listOf(15L, 60L, 180L, 360L, 720L, 1440L, 10080L) }
}

class UpdateOptionsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tgws_update_options", Context.MODE_PRIVATE)
    fun read() = UpdateOptions(
        prefs.getBoolean("auto", true),
        prefs.getLong("period", 360).takeIf { it in UpdateOptions.periods } ?: 360,
        prefs.getBoolean("notify", true), prefs.getBoolean("wifi", false)
    )
    fun save(options: UpdateOptions) {
        prefs.edit().putBoolean("auto", options.auto).putLong("period", options.minutes)
            .putBoolean("notify", options.notify).putBoolean("wifi", options.wifiOnly).apply()
    }
    fun unseen(tag: String) = prefs.getString("last_tag", null) != tag
    fun notified(tag: String) { prefs.edit().putString("last_tag", tag).apply() }
}

data class GithubRelease(
    val tag: String, val version: String, val notes: String,
    val apkName: String, val apkUrl: String, val shaUrl: String
)
internal data class NumericVersion(val major: Int, val minor: Int, val patch: Int, val pre: Boolean) : Comparable<NumericVersion> {
    override fun compareTo(other: NumericVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }, { !it.pre })
}
internal fun numericVersion(raw: String): NumericVersion? {
    val match = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([A-Za-z0-9-]+))?$""").matchEntire(raw.trim()) ?: return null
    return NumericVersion(match.groupValues[1].toIntOrNull() ?: return null,
        match.groupValues[2].toIntOrNull() ?: return null,
        match.groupValues[3].toIntOrNull() ?: return null, match.groupValues[4].isNotEmpty())
}

/** Only published assets of our repository are eligible; no arbitrary update URLs. */
class GithubUpdates(private val context: Context) {
    private val http = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    companion object {
        const val REPO = "Kust3r3nk0/TGWS-Android"
        const val OPEN_UPDATES = "tgws_open_updates"
        private const val MAX_APK = 160L * 1024 * 1024
    }
    private fun trusted(url: String): Boolean = runCatching {
        val u = Uri.parse(url)
        u.scheme == "https" && u.host == "github.com" &&
            u.path.orEmpty().startsWith("/" + REPO + "/releases/download/")
    }.getOrDefault(false)

    private fun fetch(url: String, limit: Int = 2_000_000): String {
        http.newCall(Request.Builder().url(url).header("User-Agent", "TGWS-Android-UpdateCenter")
            .build()).execute().use { reply ->
            if (!reply.isSuccessful) error("GitHub HTTP " + reply.code)
            val bytes = reply.body?.byteStream() ?: error("Пустой ответ GitHub")
            ByteArrayOutputStream().use { out ->
                bytes.use { input ->
                    val buffer = ByteArray(16_384)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (out.size() + n > limit) error("Ответ сервера слишком велик")
                        out.write(buffer, 0, n)
                    }
                }
                return out.toString("UTF-8")
            }
        }
    }

    private fun installedVersion(): String {
        @Suppress("DEPRECATION")
        return context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    suspend fun check(): Result<GithubRelease?> = withContext(Dispatchers.IO) {
        runCatching {
            val installed = numericVersion(installedVersion()) ?: error("Некорректная версия приложения")
            val alpha = installed.pre
            val json = JSONArray(fetch("https://api.github.com/repos/" + REPO + "/releases?per_page=30"))
            val candidates = mutableListOf<GithubRelease>()
            for (i in 0 until json.length()) {
                val entry = json.optJSONObject(i) ?: continue
                if (entry.optBoolean("draft") || (entry.optBoolean("prerelease") && !alpha)) continue
                val tag = entry.optString("tag_name")
                val version = numericVersion(tag) ?: continue
                if (version <= installed) continue
                var apkName = ""
                var apkUrl = ""
                var shaUrl = ""
                val assets = entry.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val a = assets.optJSONObject(j) ?: continue
                    val name = a.optString("name")
                    val url = a.optString("browser_download_url")
                    if (!trusted(url)) continue
                    if (name == "SHA256SUMS") shaUrl = url
                    if (name.startsWith("TGWS-Android-") && name.endsWith(".apk") &&
                        name.contains("debug", ignoreCase = true) == alpha) {
                        apkName = name
                        apkUrl = url
                    }
                }
                if (apkUrl.isNotEmpty() && shaUrl.isNotEmpty())
                    candidates += GithubRelease(tag, tag.removePrefix("v").removeSuffix("-debug"),
                        entry.optString("body"), apkName, apkUrl, shaUrl)
            }
            candidates.maxWithOrNull(compareBy { numericVersion(it.tag) })
        }
    }

    private fun onWifi(): Boolean {
        val conn = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return conn.allNetworks.any { network ->
            conn.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    /** SHA-256 AND installed package signing certificate AND monotonically newer versionCode. */
    suspend fun downloadVerified(release: GithubRelease, wifiOnly: Boolean): File = withContext(Dispatchers.IO) {
        if (!trusted(release.apkUrl) || !trusted(release.shaUrl)) error("Недоверенный URL")
        if (wifiOnly && !onWifi()) error("Для загрузки необходим Wi-Fi")
        val sha = fetch(release.shaUrl, 100_000).lineSequence().mapNotNull { line ->
            Regex("""^([A-Fa-f0-9]{64})\s+\*?(.+?)\s*$""")
                .matchEntire(line.trim())?.takeIf { it.groupValues[2] == release.apkName }
                ?.groupValues?.get(1)
        }.firstOrNull() ?: error("Контрольная сумма APK отсутствует")
        val folder = File(context.cacheDir, "verified_updates").also { it.mkdirs() }
        val tmp = File(folder, "tgws.pending")
        val target = File(folder, "tgws-verified.apk")
        tmp.delete()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var length = 0L
            http.newCall(Request.Builder().url(release.apkUrl)
                .header("User-Agent", "TGWS-Android-UpdateCenter").build()).execute().use { reply ->
                if (!reply.isSuccessful) error("APK HTTP " + reply.code)
                if ((reply.body?.contentLength() ?: -1) > MAX_APK) error("APK слишком велик")
                (reply.body?.byteStream() ?: error("Пустой APK")).use { input ->
                    FileOutputStream(tmp).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            length += n
                            if (length > MAX_APK) error("APK слишком велик")
                            digest.update(buffer, 0, n)
                            output.write(buffer, 0, n)
                        }
                        output.fd.sync()
                    }
                }
            }
            if (length == 0L) error("APK пуст")
            if (!digest.digest().joinToString("") { "%02x".format(it) }.equals(sha, true))
                error("SHA-256 APK не совпадает")
            val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            val pm = context.packageManager
            val installed = pm.getPackageInfo(context.packageName, flags)
            val archive = pm.getPackageArchiveInfo(tmp.absolutePath, flags) ?: error("Недопустимый APK")
            if (archive.packageName != context.packageName ||
                archive.longVersionCode <= installed.longVersionCode) error("APK не подходит для обновления")
            val existing = installed.signingInfo?.apkContentsSigners ?: error("Нет подписи приложения")
            val incoming = archive.signingInfo?.apkContentsSigners ?: error("Нет подписи APK")
            if (existing.size != incoming.size || existing.any { old ->
                    incoming.none { next -> old.toByteArray().contentEquals(next.toByteArray()) }
                }) error("Подпись APK отличается")
            if (target.exists() && !target.delete()) error("Не удалось заменить APK")
            if (!tmp.renameTo(target)) error("Не удалось сохранить APK")
            target
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    /** Android controls installation and asks the user for confirmation. */
    fun install(apk: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.packageName)))
            error("Разрешите установку для TGWS-Android и повторите нажатие «Обновить»")
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    fun configureWorker(replace: Boolean = false) {
        val options = UpdateOptionsStore(context).read()
        val manager = WorkManager.getInstance(context)
        if (!options.auto) {
            manager.cancelUniqueWork("tgws-update-check")
        } else {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(options.minutes, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            manager.enqueueUniquePeriodicWork("tgws-update-check",
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    fun notify(release: GithubRelease) {
        val prefs = UpdateOptionsStore(context)
        if (!prefs.read().notify || !prefs.unseen(release.tag)) return
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = "tgws_app_updates"
        notifications.createNotificationChannel(NotificationChannel(
            channel, "Обновления TGWS-Android", NotificationManager.IMPORTANCE_DEFAULT))
        val launch = Intent(context, MainActivity::class.java)
            .putExtra(OPEN_UPDATES, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(context, 306, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        notifications.notify(306, Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Обновление TGWS-Android")
            .setContentText("Доступна версия " + release.version)
            .setContentIntent(pending).setAutoCancel(true).build())
        prefs.notified(release.tag)
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!UpdateOptionsStore(applicationContext).read().auto) return Result.success()
        val updates = GithubUpdates(applicationContext)
        val next = updates.check().getOrElse { return Result.retry() }
        if (next != null) updates.notify(next)
        return Result.success()
    }
}
