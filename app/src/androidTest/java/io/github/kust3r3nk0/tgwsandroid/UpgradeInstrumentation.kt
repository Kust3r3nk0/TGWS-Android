package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import android.app.Activity
import android.os.Build
import android.os.Bundle
import org.json.JSONObject
import androidx.test.runner.AndroidJUnitRunner

/** Separate CI process before and after adb install -r. Contains only public test fixtures. */
class UpgradeInstrumentation : AndroidJUnitRunner() {
    private var phase = ""
    private var expectedVersion = -1L
    override fun onCreate(arguments: Bundle?) {
        phase = arguments?.getString("phase").orEmpty()
        expectedVersion = arguments?.getString("version")?.toLongOrNull() ?: -1
        super.onCreate(arguments)
    }
    override fun onStart() {
        if (phase.isEmpty()) {
            super.onStart()
            return
        }
        var checkpoint = "ENVIRONMENT"
        try {
            check(Build.MODEL.startsWith("sdk_gphone"))
            val context = targetContext
            checkpoint = "VERSION"
            check(context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode == expectedVersion)
            val store = SettingsStore(context)
            val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val fixture = context.getSharedPreferences("upgrade_fixture", Context.MODE_PRIVATE)
            val sample = "00112233445566778899aabbccddeeff"
            val relay = "wss://relay.example/upgrade-fixture/apiws"
            when (phase) {
                "seed" -> {
                    checkpoint = "EMPTY_SETTINGS"
                    check(preferences.all.isEmpty())
                    checkpoint = "SAVE_FIXTURE"
                    store.save(8443, sample, relay)
                    AppPreferences(context).apply { theme = ThemeMode.DARK; liquidGlass = true; transparency = true }
                    // Unlike an Activity lifecycle, this runner exits immediately. Drain the
                    // same preference file's pending apply() writes before killing the process.
                    check(context.applicationContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                        .edit().commit())
                    checkpoint = "APPEARANCE"
                    AppPreferences(context).let { check(it.theme == ThemeMode.DARK && it.liquidGlass && it.transparency) }
                    check(fixture.edit().putString("protected", JSONObject(preferences.all).toString()).commit())
                }
                "verify" -> {
                    checkpoint = "LOAD_FIXTURE"
                    val before = JSONObject(requireNotNull(fixture.getString("protected", null)))
                    val saved = store.load()
                    check(saved.secret == sample && saved.port == 8443 && saved.relayUrl == relay)
                    check(saved.transport.mode == io.github.kust3r3nk0.tgwsandroid.core.TransportMode.RELAY_ONLY)
                    // No rewrite of encrypted records: validates both preferences and Keystore continuity.
                    checkpoint = "CIPHERTEXT"
                    check(preferences.all.size == before.length())
                    check(before.keys().asSequence().all { preferences.all[it] == before.get(it) })
                    val appearance = AppPreferences(context)
                    checkpoint = "THEME"
                    check(appearance.theme == ThemeMode.DARK)
                    checkpoint = "GLASS"
                    check(appearance.liquidGlass)
                    checkpoint = "TRANSPARENCY"
                    check(appearance.transparency)
                }
                else -> error("Invalid phase")
            }
            finish(Activity.RESULT_OK, Bundle().apply { putString("TGWS_UPGRADE", "$phase:passed") })
        } catch (_: Throwable) {
            // Never serialize configuration or exception text into the CI log.
            finish(Activity.RESULT_CANCELED, Bundle().apply {
                putString("TGWS_UPGRADE", "$phase:failed")
                putString("TGWS_UPGRADE_CHECK", checkpoint)
            })
        }
    }
}
